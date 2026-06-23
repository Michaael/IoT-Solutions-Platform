package com.ispf.server.plugin.model.dto;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.plugin.model.ModelAttachment;
import com.ispf.plugin.model.ModelBindingRule;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ModelDto(
        String id,
        String name,
        String description,
        ModelType type,
        ObjectType targetObjectType,
        String suitabilityExpression,
        String objectPath,
        List<ModelVariableDefinition> variables,
        List<EventDescriptor> events,
        List<FunctionDescriptor> functions,
        List<ModelBindingRule> bindings,
        Map<String, String> parameters,
        Instant createdAt,
        Instant updatedAt
) {
    public static ModelDto from(ModelDefinition model, String modelsRoot) {
        return new ModelDto(
                model.id(),
                model.name(),
                model.description(),
                model.type(),
                model.targetObjectType(),
                model.suitabilityExpression(),
                model.catalogObjectPath(),
                model.variables(),
                model.events(),
                model.functions(),
                model.bindingRules(),
                model.parameters(),
                model.createdAt(),
                model.updatedAt()
        );
    }
}
