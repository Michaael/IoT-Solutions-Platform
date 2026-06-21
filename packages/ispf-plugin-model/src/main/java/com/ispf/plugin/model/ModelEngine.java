package com.ispf.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingEvaluator;
import com.ispf.expression.ExpressionEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core engine for model lifecycle — create, apply, instantiate, template export.
 */
public class ModelEngine {

    public static final String DEFAULT_MODELS_ROOT = "root.platform.models";

    private final ModelRegistry registry;
    private final ObjectTree objectTree;
    private final ExpressionEngine expressionEngine;
    private final BindingEvaluator bindingEvaluator;
    private final List<ModelAttachment> attachments = new CopyOnWriteArrayList<>();
    private final String modelsRoot;

    public ModelEngine(
            ModelRegistry registry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine,
            BindingEvaluator bindingEvaluator
    ) {
        this(registry, objectTree, expressionEngine, bindingEvaluator, DEFAULT_MODELS_ROOT);
    }

    public ModelEngine(
            ModelRegistry registry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine,
            BindingEvaluator bindingEvaluator,
            String modelsRoot
    ) {
        this.registry = registry;
        this.objectTree = objectTree;
        this.expressionEngine = expressionEngine;
        this.bindingEvaluator = bindingEvaluator;
        this.modelsRoot = modelsRoot;
    }

    public ModelDefinition createModel(ModelDefinition model) {
        ensureModelsContainer();
        ModelDefinition stored = registry.register(model);
        registerModelObject(stored);
        return stored;
    }

    public ModelDefinition updateModel(ModelDefinition model) {
        ModelDefinition stored = registry.update(model);
        registerModelObject(stored);
        return stored;
    }

    public void deleteModel(String modelId) {
        ModelDefinition model = registry.requireById(modelId);
        registry.delete(modelId);
        objectTree.findByPath(model.objectPath(modelsRoot)).ifPresent(node ->
                attachments.removeIf(a -> a.modelId().equals(modelId))
        );
    }

    /**
     * Merges model variables, events, functions and bindings into an existing object (RELATIVE / manual apply).
     */
    public ModelAttachment applyModel(String modelId, String targetPath) {
        ModelDefinition model = registry.requireById(modelId);
        PlatformObject target = objectTree.require(targetPath);
        assertSuitable(model, target);
        mergeModelChain(model, target, model.parameters());
        bindingEvaluator.evaluateBindings(target);
        ModelAttachment attachment = new ModelAttachment(
                UUID.randomUUID().toString(),
                model.id(),
                model.name(),
                model.type(),
                targetPath,
                Instant.now()
        );
        attachments.add(attachment);
        return attachment;
    }

    /**
     * Creates a new child object from a model (INSTANCE semantics).
     */
    public PlatformObject instantiateModel(
            String modelId,
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
        ModelDefinition model = registry.requireById(modelId);
        if (model.type() == ModelType.RELATIVE) {
            throw new ModelException("RELATIVE models cannot be instantiated as child objects. Use apply instead.");
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
        mergeModelChain(model, instance, parameters);
        bindingEvaluator.evaluateBindings(instance);

        attachments.add(new ModelAttachment(
                UUID.randomUUID().toString(),
                model.id(),
                model.name(),
                model.type(),
                fullPath,
                Instant.now()
        ));
        return instance;
    }

    /**
     * Creates a model definition by snapshotting an existing object.
     */
    public ModelDefinition createFromObject(
            String sourcePath,
            String modelName,
            String description,
            ModelType type
    ) {
        PlatformObject source = objectTree.require(sourcePath);
        List<ModelVariableDefinition> variables = source.variables().values().stream()
                .map(v -> ModelVariableDefinition.of(
                        v.name(),
                        "",
                        "default",
                        v.schema(),
                        v.readable(),
                        v.writable(),
                        v.bindingExpression().orElse(null),
                        v.value().orElse(null),
                        v.historyEnabled(),
                        v.historyRetentionDays().orElse(null)
                ))
                .toList();

        List<ModelBindingDefinition> bindings = source.variables().values().stream()
                .filter(v -> v.bindingExpression().isPresent())
                .map(v -> new ModelBindingDefinition(v.name(), v.bindingExpression().get()))
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
                bindings,
                Map.of(),
                Instant.now(),
                Instant.now()
        );
        return createModel(model);
    }

    /**
     * Applies all RELATIVE models whose suitability expression matches the object.
     */
    public List<ModelAttachment> applyRelativeModels(String targetPath) {
        PlatformObject target = objectTree.require(targetPath);
        List<ModelAttachment> applied = new ArrayList<>();
        for (ModelDefinition model : registry.all()) {
            if (model.type() != ModelType.RELATIVE) {
                continue;
            }
            if (!isSuitable(model, target)) {
                continue;
            }
            applied.add(applyModel(model.id(), targetPath));
        }
        return applied;
    }

    public List<ModelAttachment> attachments() {
        return List.copyOf(attachments);
    }

    public List<ModelAttachment> attachmentsForObject(String objectPath) {
        return attachments.stream()
                .filter(a -> a.objectPath().equals(objectPath))
                .toList();
    }

    public String modelsRoot() {
        return modelsRoot;
    }

    private void ensureModelsContainer() {
        if (objectTree.findByPath(modelsRoot).isEmpty()) {
            String parentPath = modelsRoot.substring(0, modelsRoot.lastIndexOf('.'));
            String name = modelsRoot.substring(modelsRoot.lastIndexOf('.') + 1);
            if (objectTree.findByPath(parentPath).isEmpty()) {
                throw new ModelException("Models parent object missing: " + parentPath);
            }
            objectTree.register(new PlatformObject(
                    UUID.randomUUID().toString(),
                    modelsRoot,
                    ObjectType.MODEL,
                    "Models",
                    "Model definitions container",
                    null
            ));
        }
    }

    private void registerModelObject(ModelDefinition model) {
        ensureModelsContainer();
        String path = model.objectPath(modelsRoot);
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
                null,
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
                null,
                com.ispf.core.model.DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("expr").field("value", com.ispf.core.model.FieldType.STRING).build(),
                        Map.of("value", model.suitabilityExpression())
                )
        ));
    }

    private void mergeModelChain(ModelDefinition model, PlatformObject target, Map<String, String> parameters) {
        String parentRef = model.parameters().get("extendsModelId");
        if (parentRef != null && !parentRef.isBlank()) {
            ModelDefinition parent = registry.findById(parentRef)
                    .or(() -> registry.findByName(parentRef))
                    .orElseThrow(() -> new ModelException("Parent model not found: " + parentRef));
            mergeModelChain(parent, target, parameters);
        }
        mergeModelIntoObject(model, target, parameters);
    }

    private void mergeModelIntoObject(ModelDefinition model, PlatformObject target) {
        mergeModelIntoObject(model, target, model.parameters());
    }

    private void mergeModelIntoObject(ModelDefinition model, PlatformObject target, Map<String, String> parameters) {
        for (ModelVariableDefinition varDef : model.variables()) {
            String binding = resolveParameters(varDef.defaultBinding(), parameters);
            Variable variable = new Variable(
                    varDef.name(),
                    varDef.schema(),
                    varDef.readable(),
                    varDef.writable(),
                    binding,
                    varDef.defaultValue(),
                    varDef.historyEnabled(),
                    varDef.historyRetentionDays()
            );
            target.addVariable(variable);
        }
        for (EventDescriptor event : model.events()) {
            target.addEvent(event);
        }
        for (FunctionDescriptor function : model.functions()) {
            target.addFunction(function);
        }
        for (ModelBindingDefinition binding : model.bindings()) {
            target.getVariable(binding.targetVariable()).ifPresentOrElse(variable -> {
                // Re-create variable with binding expression from model bindings table
                Variable updated = new Variable(
                        variable.name(),
                        variable.schema(),
                        variable.readable(),
                        variable.writable(),
                        resolveParameters(binding.expression(), parameters),
                        variable.value().orElse(null),
                        variable.historyEnabled(),
                        variable.historyRetentionDays().orElse(null)
                );
                target.addVariable(updated);
            }, () -> {
                throw new ModelException("Binding target not found: " + binding.targetVariable());
            });
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

    private static String resolveParameters(String expression, Map<String, String> parameters) {
        if (expression == null || parameters == null || parameters.isEmpty()) {
            return expression;
        }
        String resolved = expression;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }
}
