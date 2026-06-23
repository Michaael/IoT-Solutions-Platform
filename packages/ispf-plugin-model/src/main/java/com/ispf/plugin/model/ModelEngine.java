package com.ispf.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.Variable;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.expression.ExpressionEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core engine for model lifecycle — create, apply, instantiate, template export.
 */
public class ModelEngine {

    public static final String DEFAULT_MODELS_ROOT = ModelCatalogRoots.LEGACY;

    private final ModelRegistry registry;
    private final ObjectTree objectTree;
    private final ExpressionEngine expressionEngine;
    private final List<ModelAttachment> attachments = new CopyOnWriteArrayList<>();

    public ModelEngine(
            ModelRegistry registry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine
    ) {
        this(registry, objectTree, expressionEngine, DEFAULT_MODELS_ROOT);
    }

    public ModelEngine(
            ModelRegistry registry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine,
            String modelsRoot
    ) {
        this.registry = registry;
        this.objectTree = objectTree;
        this.expressionEngine = expressionEngine;
    }

    public ModelDefinition createModel(ModelDefinition model) {
        validateModelType(model);
        ensureCatalogContainers();
        ModelDefinition stored = registry.register(model);
        registerModelObject(stored);
        if (stored.type() == ModelType.ABSOLUTE) {
            ensureAbsoluteInstance(stored);
        }
        return stored;
    }

    public ModelDefinition updateModel(ModelDefinition model) {
        validateModelType(model);
        ensureCatalogContainers();
        ModelDefinition stored = registry.update(model);
        registerModelObject(stored);
        if (stored.type() == ModelType.ABSOLUTE) {
            syncAbsoluteInstance(stored);
        }
        return stored;
    }

    public void deleteModel(String modelId) {
        ModelDefinition model = registry.requireById(modelId);
        registry.delete(modelId);
        objectTree.findByPath(model.catalogObjectPath()).ifPresent(node ->
                attachments.removeIf(a -> a.modelId().equals(modelId))
        );
        objectTree.findByPath(model.objectPath(ModelCatalogRoots.LEGACY)).ifPresent(node ->
                attachments.removeIf(a -> a.modelId().equals(modelId))
        );
    }

    /**
     * Merges model variables, events, functions into an existing object (RELATIVE / manual apply).
     * Binding rules are merged separately via {@code ModelBindingRulesMerger} in ispf-server.
     */
    public ModelApplyResult applyModel(String modelId, String targetPath) {
        ModelDefinition model = registry.requireById(modelId);
        PlatformObject target = objectTree.require(targetPath);
        assertSuitable(model, target);
        List<ModelMergeWarning> warnings = new ArrayList<>();
        mergeModelChain(model, target, model.parameters(), warnings);
        target.addAppliedModelId(model.id());
        ModelAttachment attachment = recordAttachment(model, targetPath);
        return new ModelApplyResult(attachment, warnings);
    }

    /**
     * Creates a new child object from a model (INSTANCE / ABSOLUTE semantics).
     */
    public ModelApplyResult instantiateModel(
            String modelId,
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
        ModelDefinition model = registry.requireById(modelId);
        if (model.type() == ModelType.RELATIVE) {
            throw new ModelException("RELATIVE models cannot be instantiated as child objects. Use apply instead.");
        }
        if (model.type() == ModelType.ABSOLUTE) {
            throw new ModelException(
                    "ABSOLUTE models use a fixed singleton instance. Open the instance path instead of instantiate."
            );
        }

        String fullPath = objectTree.resolveChildPath(parentPath, instanceName);
        if (objectTree.findByPath(fullPath).isPresent()) {
            throw new ModelException("Object already exists: " + fullPath);
        }

        ObjectType objectType = model.targetObjectType() != null
                ? model.targetObjectType()
                : ObjectType.CUSTOM;

        PlatformObject instance = new PlatformObject(
                UUID.randomUUID().toString(),
                fullPath,
                objectType,
                instanceName,
                model.description(),
                model.id()
        );
        objectTree.register(instance);
        List<ModelMergeWarning> warnings = new ArrayList<>();
        mergeModelChain(model, instance, parameters, warnings);
        instance.addAppliedModelId(model.id());

        ModelAttachment attachment = recordAttachment(model, fullPath);
        return new ModelApplyResult(attachment, warnings);
    }

    public PlatformObject ensureAbsoluteInstance(ModelDefinition model) {
        if (model.type() != ModelType.ABSOLUTE) {
            throw new ModelException("ensureAbsoluteInstance requires ABSOLUTE model");
        }
        String instancePath = absoluteInstancePath(model);
        return objectTree.findByPath(instancePath).orElseGet(() -> {
            ensureInstancesContainer();
            int lastDot = instancePath.lastIndexOf('.');
            String parentPath = instancePath.substring(0, lastDot);
            String name = instancePath.substring(lastDot + 1);
            if (objectTree.findByPath(parentPath).isEmpty()) {
                throw new ModelException("Absolute instance parent missing: " + parentPath);
            }
            ObjectType objectType = model.targetObjectType() != null
                    ? model.targetObjectType()
                    : ObjectType.CUSTOM;
            PlatformObject instance = new PlatformObject(
                    UUID.randomUUID().toString(),
                    instancePath,
                    objectType,
                    model.name(),
                    model.description(),
                    model.id()
            );
            objectTree.register(instance);
            List<ModelMergeWarning> warnings = new ArrayList<>();
            mergeModelChain(model, instance, model.parameters(), warnings);
            instance.addAppliedModelId(model.id());
            recordAttachment(model, instancePath);
            return instance;
        });
    }

    private void syncAbsoluteInstance(ModelDefinition model) {
        String instancePath = absoluteInstancePath(model);
        if (objectTree.findByPath(instancePath).isEmpty()) {
            ensureAbsoluteInstance(model);
            return;
        }
        PlatformObject target = objectTree.require(instancePath);
        List<ModelMergeWarning> warnings = new ArrayList<>();
        mergeModelChain(model, target, model.parameters(), warnings);
        target.addAppliedModelId(model.id());
        recordAttachment(model, instancePath);
    }

    public static String absoluteInstancePath(ModelDefinition model) {
        String configured = model.parameters().get("absoluteInstancePath");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return ModelCatalogRoots.INSTANCES + "." + model.name();
    }

    /**
     * Creates a model definition by snapshotting an existing object (variables/events/functions only).
     */
    public ModelDefinition createFromObject(
            String sourcePath,
            String modelName,
            String description,
            ModelType type
    ) {
        PlatformObject source = objectTree.require(sourcePath);
        List<ModelVariableDefinition> variables = source.variables().values().stream()
                .filter(v -> !BindingRulesConstants.isReservedVariable(v.name()))
                .map(v -> ModelVariableDefinition.of(
                        v.name(),
                        "",
                        "default",
                        v.schema(),
                        v.readable(),
                        v.writable(),
                        v.value().orElse(null),
                        v.historyEnabled(),
                        v.historyRetentionDays().orElse(null)
                ))
                .toList();

        ModelDefinition model = new ModelDefinition(
                UUID.randomUUID().toString(),
                modelName,
                description != null ? description : "Created from " + sourcePath,
                type,
                source.type(),
                "",
                variables,
                new ArrayList<>(source.events().values()),
                new ArrayList<>(source.functions().values()),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        return createModel(model);
    }

    /**
     * Applies all RELATIVE models whose suitability expression matches the object.
     */
    public List<ModelApplyResult> applyRelativeModels(String targetPath) {
        PlatformObject target = objectTree.require(targetPath);
        List<ModelApplyResult> applied = new ArrayList<>();
        for (ModelDefinition model : registry.all()) {
            if (model.type() != ModelType.RELATIVE) {
                continue;
            }
            if (target.appliedModelIds().contains(model.id())) {
                continue;
            }
            if (!isSuitable(model, target)) {
                continue;
            }
            applied.add(applyModel(model.id(), targetPath));
        }
        return applied;
    }

    public void restoreAttachmentsFromObjects() {
        attachments.clear();
        for (PlatformObject node : objectTree.all()) {
            if (ModelCatalogRoots.isDefinitionPath(node.path())) {
                continue;
            }
            for (String modelId : node.appliedModelIds()) {
                registry.findById(modelId).ifPresent(model ->
                        recordAttachment(model, node.path())
                );
            }
        }
    }

    public List<ModelAttachment> attachments() {
        return List.copyOf(attachments);
    }

    public List<ModelAttachment> attachmentsForObject(String objectPath) {
        return attachments.stream()
                .filter(a -> a.objectPath().equals(objectPath))
                .toList();
    }

    /** @deprecated use {@link ModelCatalogRoots#isCatalogPath(String)} */
    public String modelsRoot() {
        return ModelCatalogRoots.LEGACY;
    }

    public boolean isModelCatalogPath(String path) {
        return ModelCatalogRoots.isCatalogPath(path);
    }

    private ModelAttachment recordAttachment(ModelDefinition model, String targetPath) {
        ModelAttachment attachment = new ModelAttachment(
                UUID.randomUUID().toString(),
                model.id(),
                model.name(),
                model.type(),
                targetPath,
                Instant.now()
        );
        attachments.removeIf(a -> a.modelId().equals(model.id()) && a.objectPath().equals(targetPath));
        attachments.add(attachment);
        return attachment;
    }

    public void ensureCatalogContainers() {
        ensureCatalogContainer(ModelCatalogRoots.RELATIVE, "Relative Models", "Mixin blueprints applied to existing objects");
        ensureCatalogContainer(ModelCatalogRoots.INSTANCE, "Instance Types", "Blueprints for new object instances");
        ensureCatalogContainer(ModelCatalogRoots.ABSOLUTE, "Absolute Models", "Singleton object blueprints");
        ensureCatalogContainer(ModelCatalogRoots.LEGACY, "Models", "Legacy model catalog (migrated to typed folders)");
        ensureInstancesContainer();
    }

    private void ensureInstancesContainer() {
        if (objectTree.findByPath(ModelCatalogRoots.INSTANCES).isEmpty()) {
            objectTree.register(new PlatformObject(
                    UUID.randomUUID().toString(),
                    ModelCatalogRoots.INSTANCES,
                    ObjectType.CUSTOM,
                    "Instances",
                    "Singleton absolute model instances",
                    null
            ));
        }
    }

    private void ensureCatalogContainer(String path, String displayName, String description) {
        if (objectTree.findByPath(path).isEmpty()) {
            String parentPath = path.substring(0, path.lastIndexOf('.'));
            if (objectTree.findByPath(parentPath).isEmpty()) {
                throw new ModelException("Catalog parent object missing: " + parentPath);
            }
            objectTree.register(new PlatformObject(
                    UUID.randomUUID().toString(),
                    path,
                    ObjectType.MODEL,
                    displayName,
                    description,
                    null
            ));
        }
    }

    private void registerModelObject(ModelDefinition model) {
        ensureCatalogContainers();
        String path = model.catalogObjectPath();
        String legacyPath = model.objectPath(ModelCatalogRoots.LEGACY);
        objectTree.findByPath(legacyPath).ifPresent(node -> {
            if (!legacyPath.equals(path)) {
                objectTree.delete(legacyPath);
            }
        });

        PlatformObject modelObject = objectTree.findByPath(path).orElseGet(() -> {
            PlatformObject node = new PlatformObject(
                    UUID.randomUUID().toString(),
                    path,
                    ObjectType.MODEL,
                    model.name(),
                    model.description(),
                    model.id()
            );
            objectTree.register(node);
            return node;
        });

        modelObject.addVariable(new Variable(
                "modelType",
                com.ispf.core.model.DataSchema.builder("modelType").field("value", com.ispf.core.model.FieldType.STRING).build(),
                true,
                false,
                com.ispf.core.model.DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("modelType").field("value", com.ispf.core.model.FieldType.STRING).build(),
                        Map.of("value", model.type().name())
                )
        ));
        modelObject.addVariable(new Variable(
                "suitabilityExpression",
                com.ispf.core.model.DataSchema.builder("expr").field("value", com.ispf.core.model.FieldType.STRING).build(),
                true,
                true,
                com.ispf.core.model.DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("expr").field("value", com.ispf.core.model.FieldType.STRING).build(),
                        Map.of("value", model.suitabilityExpression())
                )
        ));
    }

    private void mergeModelChain(
            ModelDefinition model,
            PlatformObject target,
            Map<String, String> parameters,
            List<ModelMergeWarning> warnings
    ) {
        String parentRef = model.parameters().get("extendsModelId");
        if (parentRef != null && !parentRef.isBlank()) {
            ModelDefinition parent = registry.findById(parentRef)
                    .or(() -> registry.findByName(parentRef))
                    .orElseThrow(() -> new ModelException("Parent model not found: " + parentRef));
            mergeModelChain(parent, target, parameters, warnings);
        }
        mergeModelIntoObject(model, target, parameters, warnings);
    }

    private void mergeModelIntoObject(
            ModelDefinition model,
            PlatformObject target,
            Map<String, String> parameters,
            List<ModelMergeWarning> warnings
    ) {
        String previousModelId = target.lastAppliedModelId();
        for (ModelVariableDefinition varDef : model.variables()) {
            if (target.getVariable(varDef.name()).isPresent()) {
                warnings.add(new ModelMergeWarning(
                        ModelMergeWarning.KIND_VARIABLE,
                        varDef.name(),
                        previousModelId,
                        model.id()
                ));
            }
            Variable variable = new Variable(
                    varDef.name(),
                    varDef.schema(),
                    varDef.readable(),
                    varDef.writable(),
                    varDef.defaultValue(),
                    varDef.historyEnabled(),
                    varDef.historyRetentionDays()
            );
            target.addVariable(variable);
        }
        for (EventDescriptor event : model.events()) {
            if (target.events().containsKey(event.name())) {
                warnings.add(new ModelMergeWarning(
                        ModelMergeWarning.KIND_EVENT,
                        event.name(),
                        previousModelId,
                        model.id()
                ));
            }
            target.addEvent(event);
        }
        for (FunctionDescriptor function : model.functions()) {
            if (target.functions().containsKey(function.name())) {
                warnings.add(new ModelMergeWarning(
                        ModelMergeWarning.KIND_FUNCTION,
                        function.name(),
                        previousModelId,
                        model.id()
                ));
            }
            target.addFunction(function);
        }
    }

    private void validateModelType(ModelDefinition model) {
        if (model.type() == ModelType.RELATIVE && model.targetObjectType() == null) {
            throw new ModelException("RELATIVE models require targetObjectType");
        }
        if (model.type() == ModelType.INSTANCE && model.targetObjectType() == null) {
            throw new ModelException("INSTANCE models require targetObjectType");
        }
    }

    private void assertSuitable(ModelDefinition model, PlatformObject target) {
        if (!isSuitable(model, target)) {
            throw new ModelException("Model " + model.name() + " is not suitable for object " + target.path());
        }
    }

    private boolean isSuitable(ModelDefinition model, PlatformObject target) {
        if (model.targetObjectType() != null && target.type() != model.targetObjectType()) {
            return false;
        }
        if (model.suitabilityExpression() == null || model.suitabilityExpression().isBlank()) {
            return true;
        }
        try {
            Object result = expressionEngine.evaluate(model.suitabilityExpression(), target);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (Exception e) {
            return false;
        }
    }
}
