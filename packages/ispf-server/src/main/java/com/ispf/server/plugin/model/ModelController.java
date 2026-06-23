package com.ispf.server.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.plugin.model.ModelAttachment;
import com.ispf.plugin.model.ModelBindingRule;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelException;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.dto.ModelAttachmentDto;
import com.ispf.server.plugin.model.dto.ModelDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for the Models plugin.
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelController {

    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ObjectManager objectManager;
    private final ModelPersistenceService modelPersistence;
    private final ModelMergeService modelMergeService;
    private final ModelApplicationService modelApplicationService;

    public ModelController(
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ObjectManager objectManager,
            ModelPersistenceService modelPersistence,
            ModelMergeService modelMergeService,
            ModelApplicationService modelApplicationService
    ) {
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.objectManager = objectManager;
        this.modelPersistence = modelPersistence;
        this.modelMergeService = modelMergeService;
        this.modelApplicationService = modelApplicationService;
    }

    @GetMapping
    public List<ModelDto> list() {
        return modelRegistry.all().stream()
                .map(m -> ModelDto.from(m, modelEngine.modelsRoot()))
                .toList();
    }

    @GetMapping("/{id}")
    public ModelDto get(@PathVariable String id) {
        return ModelDto.from(modelRegistry.requireById(id), modelEngine.modelsRoot());
    }

    @GetMapping("/by-name/{name}")
    public ModelDto getByName(@PathVariable String name) {
        return ModelDto.from(modelRegistry.requireByName(name), modelEngine.modelsRoot());
    }

    @PostMapping
    public ModelDto create(@Valid @RequestBody CreateModelRequest request) {
        Instant now = Instant.now();
        ModelDefinition model = new ModelDefinition(
                UUID.randomUUID().toString(),
                request.name(),
                request.description(),
                request.type(),
                request.targetObjectType(),
                request.suitabilityExpression(),
                request.variables(),
                request.events(),
                request.functions(),
                request.bindings(),
                request.parameters(),
                now,
                now
        );
        try {
            ModelDefinition created = modelEngine.createModel(model);
            modelPersistence.persist(created, false);
            return ModelDto.from(created, modelEngine.modelsRoot());
        } catch (ModelException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ModelDto update(@PathVariable String id, @Valid @RequestBody UpdateModelRequest request) {
        ModelDefinition existing = modelRegistry.requireById(id);
        ModelDefinition updated = new ModelDefinition(
                existing.id(),
                request.name() != null ? request.name() : existing.name(),
                request.description() != null ? request.description() : existing.description(),
                request.type() != null ? request.type() : existing.type(),
                request.targetObjectType() != null ? request.targetObjectType() : existing.targetObjectType(),
                request.suitabilityExpression() != null ? request.suitabilityExpression() : existing.suitabilityExpression(),
                request.variables() != null ? request.variables() : existing.variables(),
                request.events() != null ? request.events() : existing.events(),
                request.functions() != null ? request.functions() : existing.functions(),
                request.bindings() != null ? request.bindings() : existing.bindings(),
                request.parameters() != null ? request.parameters() : existing.parameters(),
                existing.createdAt(),
                Instant.now()
        );
        ModelDefinition saved = modelEngine.updateModel(updated);
        modelPersistence.persist(saved, false);
        return ModelDto.from(saved, modelEngine.modelsRoot());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        modelEngine.deleteModel(id);
        modelPersistence.delete(id);
    }

    @PostMapping("/{id}/apply")
    public ModelAttachmentDto apply(
            @PathVariable String id,
            @RequestParam @NotBlank String objectPath
    ) {
        try {
            ModelAttachmentDto attachment = ModelAttachmentDto.from(
                    modelApplicationService.applyModelWithRules(id, objectPath)
            );
            return attachment;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/instantiate")
    public ObjectDto instantiate(
            @PathVariable String id,
            @Valid @RequestBody InstantiateModelRequest request
    ) {
        try {
            modelApplicationService.instantiateWithRules(
                    id,
                    request.parentPath(),
                    request.instanceName(),
                    request.parameters() != null ? request.parameters() : Map.of()
            );
            String path = objectManager.tree().resolveChildPath(request.parentPath(), request.instanceName());
            return ObjectDto.from(
                    objectManager.require(path),
                    null,
                    com.ispf.server.plugin.model.dto.AppliedModelDto.resolve(
                            objectManager.require(path),
                            modelRegistry
                    )
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/from-object")
    public ModelDto createFromObject(@Valid @RequestBody CreateFromObjectRequest request) {
        try {
            ModelDefinition model = modelEngine.createFromObject(
                    request.sourcePath(),
                    request.modelName(),
                    request.description(),
                    request.type() != null ? request.type() : ModelType.INSTANCE
            );
            modelPersistence.persist(model, false);
            objectManager.persistNodeTree(model.catalogObjectPath());
            return ModelDto.from(model, modelEngine.modelsRoot());
        } catch (ModelException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/attachments")
    public List<ModelAttachmentDto> attachments(@RequestParam(required = false) String objectPath) {
        List<ModelAttachment> list = objectPath != null && !objectPath.isBlank()
                ? modelEngine.attachmentsForObject(objectPath)
                : modelEngine.attachments();
        return list.stream().map(ModelAttachmentDto::from).toList();
    }

    @PostMapping("/{id}/upgrade")
    public Map<String, Object> upgradeModel(
            @PathVariable String id,
            @RequestParam @NotBlank String targetPath,
            @RequestParam(required = false) String targetVersion
    ) {
        try {
            ModelDefinition model = modelRegistry.requireById(id);
            if (targetVersion != null && !targetVersion.isBlank()
                    && !targetVersion.equals(model.modelVersion())) {
                throw new ModelException(
                        "Model version mismatch: requested " + targetVersion + ", actual " + model.modelVersion()
                );
            }
            ModelAttachmentDto attachment = ModelAttachmentDto.from(
                    modelApplicationService.applyModelWithRules(id, targetPath)
            );
            return Map.of(
                    "status", "OK",
                    "targetPath", targetPath,
                    "modelVersion", model.modelVersion(),
                    "attachmentId", attachment.id(),
                    "warnings", attachment.warnings()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/merge-preview")
    public Map<String, Object> mergePreview(@Valid @RequestBody MergePreviewRequest request) {
        return modelMergeService.mergePreview(
                request.baseModelId(),
                request.theirsModelId(),
                request.objectPath()
        );
    }

    @PostMapping("/merge-apply")
    public Map<String, Object> mergeApply(@Valid @RequestBody MergeApplyRequest request) {
        return modelMergeService.applyMerge(
                request.baseModelId(),
                request.theirsModelId(),
                request.objectPath(),
                request.resolutions()
        );
    }

    @PostMapping("/{id}/upgrade-instances")
    public Map<String, Object> upgradeInstances(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean dryRun
    ) {
        try {
            ModelDefinition model = modelRegistry.requireById(id);
            LinkedHashSet<String> targets = new LinkedHashSet<>();
            for (ModelAttachment attachment : modelEngine.attachments()) {
                if (id.equals(attachment.modelId())) {
                    targets.add(attachment.objectPath());
                }
            }
            for (com.ispf.core.object.PlatformObject node : objectManager.tree().all()) {
                if (modelEngine.isModelCatalogPath(node.path())) {
                    continue;
                }
                if (model.type() == com.ispf.plugin.model.ModelType.INSTANCE
                        && node.type() != model.targetObjectType()) {
                    continue;
                }
                node.templateId().ifPresent(templateId -> {
                    if (id.equals(templateId) || model.name().equals(templateId)) {
                        targets.add(node.path());
                    }
                });
            }
            List<String> upgraded = new ArrayList<>();
            for (String targetPath : targets) {
                if (dryRun) {
                    upgraded.add(targetPath);
                    continue;
                }
                modelApplicationService.applyModelWithRules(id, targetPath);
                upgraded.add(targetPath);
            }
            return Map.of(
                    "status", dryRun ? "DRY_RUN" : "OK",
                    "dryRun", dryRun,
                    "modelVersion", model.modelVersion(),
                    "upgraded", upgraded,
                    "count", upgraded.size()
            );
        } catch (ModelException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}/diff")
    public Map<String, Object> diff(
            @PathVariable String id,
            @RequestParam @NotBlank String objectPath
    ) {
        ModelDefinition model = modelRegistry.requireById(id);
        PlatformObject object = objectManager.require(objectPath);
        LinkedHashSet<String> modelVariables = model.variables().stream()
                .map(ModelVariableDefinition::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> objectVariables = new LinkedHashSet<>(object.variables().keySet());
        LinkedHashSet<String> modelEvents = model.events().stream()
                .map(EventDescriptor::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> objectEvents = new LinkedHashSet<>(object.events().keySet());
        LinkedHashSet<String> modelFunctions = model.functions().stream()
                .map(FunctionDescriptor::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> objectFunctions = new LinkedHashSet<>(object.functions().keySet());

        List<String> variablesToAdd = modelVariables.stream()
                .filter(name -> !objectVariables.contains(name))
                .toList();
        List<String> variablesOnlyOnObject = objectVariables.stream()
                .filter(name -> !modelVariables.contains(name))
                .toList();
        List<String> eventsToAdd = modelEvents.stream()
                .filter(name -> !objectEvents.contains(name))
                .toList();
        List<String> functionsToAdd = modelFunctions.stream()
                .filter(name -> !objectFunctions.contains(name))
                .toList();

        return Map.of(
                "objectPath", objectPath,
                "modelVersion", model.modelVersion(),
                "variablesToAdd", variablesToAdd,
                "variablesOnlyOnObject", variablesOnlyOnObject,
                "eventsToAdd", eventsToAdd,
                "functionsToAdd", functionsToAdd,
                "bindingsCount", model.bindings().size()
        );
    }

    @GetMapping("/{id}/instances")
    public List<Map<String, String>> listInstances(@PathVariable String id) {
        ModelDefinition model = modelRegistry.requireById(id);
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (ModelAttachment attachment : modelEngine.attachments()) {
            if (id.equals(attachment.modelId())) {
                paths.add(attachment.objectPath());
            }
        }
        for (com.ispf.core.object.PlatformObject node : objectManager.tree().all()) {
            if (node.path().startsWith(modelEngine.modelsRoot() + ".")
                    || node.path().equals(modelEngine.modelsRoot())) {
                continue;
            }
            if (model.type() == com.ispf.plugin.model.ModelType.INSTANCE
                    && node.type() != model.targetObjectType()) {
                continue;
            }
            node.templateId().ifPresent(templateId -> {
                if (id.equals(templateId) || model.name().equals(templateId)) {
                    paths.add(node.path());
                }
            });
        }
        return paths.stream()
                .map(path -> Map.of("objectPath", path))
                .toList();
    }

    public record CreateModelRequest(
            @NotBlank String name,
            String description,
            ModelType type,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<ModelVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<ModelBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record UpdateModelRequest(
            String name,
            String description,
            ModelType type,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<ModelVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<ModelBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record InstantiateModelRequest(
            @NotBlank String parentPath,
            @NotBlank String instanceName,
            Map<String, String> parameters
    ) {
    }

    public record CreateFromObjectRequest(
            @NotBlank String sourcePath,
            @NotBlank String modelName,
            String description,
            ModelType type
    ) {
    }

    public record MergePreviewRequest(
            @NotBlank String baseModelId,
            @NotBlank String theirsModelId,
            @NotBlank String objectPath
    ) {
    }

    public record MergeApplyRequest(
            @NotBlank String baseModelId,
            @NotBlank String theirsModelId,
            @NotBlank String objectPath,
            List<ModelMergeService.MergeResolution> resolutions
    ) {
    }
}
