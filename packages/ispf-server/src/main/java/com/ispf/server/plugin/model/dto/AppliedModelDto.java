package com.ispf.server.plugin.model.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record AppliedModelDto(
        String id,
        String name,
        ModelType type,
        boolean primary
) {
    public static AppliedModelDto of(ModelDefinition model, boolean primary) {
        return new AppliedModelDto(model.id(), model.name(), model.type(), primary);
    }

    public static List<AppliedModelDto> resolve(PlatformObject node, ModelRegistry registry) {
        String primary = node.templateId().orElse(null);
        Set<String> seen = new LinkedHashSet<>();
        List<AppliedModelDto> result = new ArrayList<>();
        for (String modelId : node.appliedModelIds()) {
            addResolved(registry, modelId, primary, seen, result);
        }
        if (primary != null && !primary.isBlank()) {
            addResolved(registry, primary, primary, seen, result);
        }
        return result;
    }

    private static void addResolved(
            ModelRegistry registry,
            String modelRef,
            String primary,
            Set<String> seen,
            List<AppliedModelDto> result
    ) {
        if (modelRef == null || modelRef.isBlank() || !seen.add(modelRef)) {
            return;
        }
        Optional<ModelDefinition> model = registry.findById(modelRef);
        if (model.isEmpty()) {
            model = registry.findByName(modelRef);
        }
        model.ifPresent(definition ->
                result.add(AppliedModelDto.of(definition, modelRef.equals(primary) || definition.id().equals(primary)))
        );
    }
}
