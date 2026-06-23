package com.ispf.server.plugin.model;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared CRUD and operations for typed model API facades.
 */
public class TypedModelFacade {

    private final ModelType modelType;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ModelPersistenceService modelPersistence;
    private final ModelApplicationService modelApplicationService;
    private final ObjectManager objectManager;

    public TypedModelFacade(
            ModelType modelType,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ModelPersistenceService modelPersistence,
            ModelApplicationService modelApplicationService,
            ObjectManager objectManager
    ) {
        this.modelType = modelType;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.modelPersistence = modelPersistence;
        this.modelApplicationService = modelApplicationService;
        this.objectManager = objectManager;
    }

    public List<ModelDto> list() {
        return modelRegistry.all().stream()
                .filter(model -> model.type() == modelType)
                .map(m -> ModelDto.from(m, modelEngine.modelsRoot()))
                .toList();
    }

    public List<ModelDto> listForCreate(ObjectType platformType, String parentPath) {
        return modelRegistry.all().stream()
                .filter(model -> model.type() == modelType)
                .filter(model -> platformType == null || model.targetObjectType() == platformType)
                .map(m -> ModelDto.from(m, modelEngine.modelsRoot()))
                .toList();
    }

    public ModelDto get(String id) {
        return ModelDto.from(requireTyped(id), modelEngine.modelsRoot());
    }

    public ModelDto getByName(String name) {
        ModelDefinition model = modelRegistry.requireByName(name);
        assertType(model);
        return ModelDto.from(model, modelEngine.modelsRoot());
    }

    public ModelDto create(CreatePayload request) {
        validateCreate(request);
        Instant now = Instant.now();
        ModelDefinition model = new ModelDefinition(
                UUID.randomUUID().toString(),
                request.name(),
                request.description(),
                modelType,
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

    public ModelDto update(String id, UpdatePayload request) {
        ModelDefinition existing = requireTyped(id);
        ModelDefinition updated = new ModelDefinition(
                existing.id(),
                request.name() != null ? request.name() : existing.name(),
                request.description() != null ? request.description() : existing.description(),
                modelType,
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
        objectManager.persistNodeTree(saved.catalogObjectPath());
        return ModelDto.from(saved, modelEngine.modelsRoot());
    }

    public void delete(String id) {
        requireTyped(id);
        modelEngine.deleteModel(id);
        modelPersistence.delete(id);
    }

    public ModelAttachmentDto apply(String id, String objectPath) {
        requireTyped(id);
        try {
            return ModelAttachmentDto.from(modelApplicationService.applyModelWithRules(id, objectPath));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public ObjectDto instantiate(String id, InstantiatePayload request) {
        if (modelType != ModelType.INSTANCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instantiate is only supported for INSTANCE models");
        }
        requireTyped(id);
        try {
            modelApplicationService.instantiateWithRules(
                    id,
                    request.parentPath(),
                    request.instanceName(),
                    request.parameters() != null ? request.parameters() : Map.of()
            );
            String path = objectManager.tree().resolveChildPath(request.parentPath(), request.instanceName());
            return ObjectDto.from(objectManager.require(path));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public PlatformObject absoluteInstance(String id) {
        if (modelType != ModelType.ABSOLUTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "singleton instance is only for ABSOLUTE models");
        }
        ModelDefinition model = requireTyped(id);
        return modelEngine.ensureAbsoluteInstance(model);
    }

    private ModelDefinition requireTyped(String id) {
        ModelDefinition model = modelRegistry.requireById(id);
        assertType(model);
        return model;
    }

    private void assertType(ModelDefinition model) {
        if (model.type() != modelType) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Model " + model.name() + " is not a " + modelType + " model"
            );
        }
    }

    private void validateCreate(CreatePayload request) {
        if (modelType == ModelType.INSTANCE || modelType == ModelType.RELATIVE) {
            if (request.targetObjectType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, modelType + " models require targetObjectType");
            }
        }
        if (modelType == ModelType.INSTANCE && request.name() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
    }

    public record CreatePayload(
            String name,
            String description,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<ModelVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<com.ispf.plugin.model.ModelBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record UpdatePayload(
            String name,
            String description,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<ModelVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<com.ispf.plugin.model.ModelBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record InstantiatePayload(
            String parentPath,
            String instanceName,
            Map<String, String> parameters
    ) {
    }
}
