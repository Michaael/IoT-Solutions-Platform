package com.ispf.plugin.model;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Full model definition — blueprint for object structure and bindings.
 */
public record ModelDefinition(
        String id,
        String name,
        String description,
        ModelType type,
        ObjectType targetObjectType,
        String suitabilityExpression,
        List<ModelVariableDefinition> variables,
        List<EventDescriptor> events,
        List<FunctionDescriptor> functions,
        List<ModelBindingDefinition> bindings,
        Map<String, String> parameters,
        Instant createdAt,
        Instant updatedAt
) {
    public ModelDefinition {
        if (description == null) {
            description = "";
        }
        if (suitabilityExpression == null) {
            suitabilityExpression = "";
        }
        if (parameters == null) {
            parameters = Map.of();
        }
        variables = List.copyOf(variables != null ? variables : List.of());
        events = List.copyOf(events != null ? events : List.of());
        functions = List.copyOf(functions != null ? functions : List.of());
        bindings = List.copyOf(bindings != null ? bindings : List.of());
    }

    public String objectPath(String modelsRoot) {
        return modelsRoot + "." + name;
    }

    /**
     * Returns the binding expression for a variable from {@link #bindings()} or {@link ModelVariableDefinition#defaultBinding()}.
     */
    public String bindingFor(String variableName) {
        if (variableName == null) {
            return null;
        }
        for (ModelBindingDefinition binding : bindings) {
            if (variableName.equals(binding.targetVariable())) {
                return binding.expression();
            }
        }
        return variables.stream()
                .filter(variable -> variableName.equals(variable.name()))
                .map(ModelVariableDefinition::defaultBinding)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Semantic version string stored in {@link #parameters()} under {@code modelVersion}. */
    public String modelVersion() {
        return parameters.getOrDefault("modelVersion", "1");
    }
}
