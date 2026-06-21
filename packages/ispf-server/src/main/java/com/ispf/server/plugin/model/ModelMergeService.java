package com.ispf.server.plugin.model;

import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ModelMergeService {

    private final ModelRegistry modelRegistry;
    private final ObjectManager objectManager;

    public ModelMergeService(ModelRegistry modelRegistry, ObjectManager objectManager) {
        this.modelRegistry = modelRegistry;
        this.objectManager = objectManager;
    }

    public Map<String, Object> mergePreview(String baseModelId, String theirsModelId, String objectPath) {
        ModelDefinition base = modelRegistry.requireById(baseModelId);
        ModelDefinition theirs = modelRegistry.requireById(theirsModelId);
        PlatformObject object = objectManager.require(objectPath);

        List<Map<String, Object>> variableConflicts = new ArrayList<>();
        LinkedHashSet<String> baseVars = names(base.variables());
        LinkedHashSet<String> theirsVars = names(theirs.variables());
        LinkedHashSet<String> objectVars = new LinkedHashSet<>(object.variables().keySet());

        for (String name : union(baseVars, theirsVars)) {
            ModelVariableDefinition baseVar = findVariable(base, name);
            ModelVariableDefinition theirsVar = findVariable(theirs, name);
            if (baseVar != null && theirsVar != null && !sameVariable(baseVar, theirsVar)) {
                variableConflicts.add(Map.of(
                        "name", name,
                        "baseSchema", baseVar.schema().name(),
                        "theirsSchema", theirsVar.schema().name(),
                        "onObject", objectVars.contains(name)
                ));
            }
        }

        List<String> eventsToAdd = diffEventNames(base.events(), theirs.events(), object.events().keySet());
        List<String> functionsToAdd = diffFunctionNames(base.functions(), theirs.functions(), object.functions().keySet());

        return Map.of(
                "objectPath", objectPath,
                "baseModelId", baseModelId,
                "theirsModelId", theirsModelId,
                "baseModelVersion", base.modelVersion(),
                "theirsModelVersion", theirs.modelVersion(),
                "variableConflicts", variableConflicts,
                "eventsToAdd", eventsToAdd,
                "functionsToAdd", functionsToAdd,
                "conflictCount", variableConflicts.size()
        );
    }

    public Map<String, Object> applyMerge(
            String baseModelId,
            String theirsModelId,
            String objectPath,
            List<MergeResolution> resolutions
    ) {
        Map<String, Object> preview = mergePreview(baseModelId, theirsModelId, objectPath);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) preview.get("variableConflicts");
        Map<String, String> resolutionByName = new LinkedHashMap<>();
        for (MergeResolution resolution : resolutions) {
            resolutionByName.put(resolution.name(), resolution.policy());
        }
        for (Map<String, Object> conflict : conflicts) {
            String name = conflict.get("name").toString();
            if (!resolutionByName.containsKey(name)) {
                throw new IllegalArgumentException("Missing resolution for variable conflict: " + name);
            }
        }
        return Map.of(
                "status", "OK",
                "objectPath", objectPath,
                "resolvedConflicts", resolutions.size(),
                "preview", preview
        );
    }

    private static LinkedHashSet<String> names(List<ModelVariableDefinition> variables) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (ModelVariableDefinition variable : variables) {
            result.add(variable.name());
        }
        return result;
    }

    private static ModelVariableDefinition findVariable(ModelDefinition model, String name) {
        return model.variables().stream()
                .filter(variable -> variable.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static boolean sameVariable(ModelVariableDefinition left, ModelVariableDefinition right) {
        return left.name().equals(right.name())
                && left.schema().name().equals(right.schema().name())
                && left.readable() == right.readable()
                && left.writable() == right.writable();
    }

    private static LinkedHashSet<String> union(LinkedHashSet<String> left, LinkedHashSet<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return result;
    }

    private static List<String> diffEventNames(
            List<EventDescriptor> baseEvents,
            List<EventDescriptor> theirsEvents,
            java.util.Set<String> objectEventNames
    ) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (EventDescriptor event : theirsEvents) {
            if (baseEvents.stream().noneMatch(base -> base.name().equals(event.name()))
                    && !objectEventNames.contains(event.name())) {
                names.add(event.name());
            }
        }
        return List.copyOf(names);
    }

    private static List<String> diffFunctionNames(
            List<FunctionDescriptor> baseFunctions,
            List<FunctionDescriptor> theirsFunctions,
            java.util.Set<String> objectFunctionNames
    ) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (FunctionDescriptor function : theirsFunctions) {
            if (baseFunctions.stream().noneMatch(base -> base.name().equals(function.name()))
                    && !objectFunctionNames.contains(function.name())) {
                names.add(function.name());
            }
        }
        return List.copyOf(names);
    }

    public record MergeResolution(String name, String policy) {
    }
}
