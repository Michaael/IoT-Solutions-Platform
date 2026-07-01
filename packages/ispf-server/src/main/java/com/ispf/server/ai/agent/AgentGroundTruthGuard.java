package com.ispf.server.ai.agent;

import com.ispf.server.api.dto.ObjectDto;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Blocks tree mutations until the agent has discovered real paths and catalog entries via tools —
 * not from playbooks, recipes, or invented names.
 * <p>
 * Universal order for every object type:
 * {@code list_objects parent=<folder>} → {@code create_object parentPath=<folder>} → configure/save tools on returned path.
 */
final class AgentGroundTruthGuard {

    private static final Set<String> TREE_DISCOVERY_TOOLS = Set.of(
            "list_objects",
            "get_object",
            "search_objects",
            "search_by_haystack_tags"
    );

    private static final Set<String> OBJECT_READ_TOOLS = Set.of(
            "get_object",
            "get_workflow",
            "get_mimic_diagram",
            "get_dashboard_layout",
            "list_variables",
            "describe_variables"
    );

    private static final Set<String> MODEL_DISCOVERY_TOOLS = Set.of(
            "list_relative_models",
            "list_instance_types",
            "list_absolute_models",
            "list_object_models",
            "get_object_model",
            "list_virtual_profiles"
    );

    /** Mutations on an existing object path — object must be discovered or created in this turn. */
    private static final Set<String> OBJECT_PATH_MUTATION_TOOLS = Set.of(
            "delete_object",
            "set_variable",
            "configure_driver",
            "driver_control",
            "save_mimic_diagram",
            "add_mimic_elements",
            "set_dashboard_layout",
            "add_dashboard_widget",
            "save_workflow_bpmn",
            "update_workflow_status",
            "run_workflow",
            "configure_alert",
            "configure_correlator",
            "configure_report",
            "run_report",
            "create_variable",
            "create_binding_rule",
            "configure_variable_history",
            "configure_operator_ui",
            "deploy_tree_function"
    );

    private AgentGroundTruthGuard() {
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    static Optional<BlockDecision> checkBeforeTool(
            String toolName,
            Map<String, Object> arguments,
            List<Map<String, Object>> steps
    ) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        String tool = toolName.toLowerCase(Locale.ROOT);
        if ("create_object".equals(tool) || "create_virtual_device".equals(tool)) {
            return checkParentDiscovered(arguments, steps, tool);
        }
        if ("instantiate_instance_type".equals(tool)) {
            Optional<BlockDecision> parentBlock = checkParentDiscovered(arguments, steps, tool);
            if (parentBlock.isPresent()) {
                return parentBlock;
            }
            return checkModelDiscovered(arguments, steps, "instanceType", "modelName", "modelId");
        }
        if ("apply_relative_model".equals(tool)) {
            String objectPath = resolveObjectPath(arguments);
            if (!objectPath.isBlank()) {
                Optional<BlockDecision> pathBlock = checkObjectPathDiscovered(objectPath, tool, steps);
                if (pathBlock.isPresent()) {
                    return pathBlock;
                }
            }
            return checkModelDiscovered(arguments, steps, "modelName", "modelId");
        }
        if (OBJECT_PATH_MUTATION_TOOLS.contains(tool)) {
            String objectPath = resolveObjectPath(arguments);
            if (objectPath.isBlank()) {
                return Optional.empty();
            }
            return checkObjectPathDiscovered(objectPath, tool, steps);
        }
        if ("ensure_absolute_instance".equals(tool)) {
            return checkModelDiscovered(arguments, steps, "modelName", "modelId");
        }
        return Optional.empty();
    }

    private static Optional<BlockDecision> checkParentDiscovered(
            Map<String, Object> arguments,
            List<Map<String, Object>> steps,
            String toolName
    ) {
        String parentPath = resolveParentPath(arguments);
        if (parentPath.isBlank()) {
            return Optional.empty();
        }
        if (isParentGrounded(steps, parentPath)) {
            return Optional.empty();
        }
        return Optional.of(new BlockDecision(
                "Cannot " + mutationLabel(arguments, toolName) + ": parent path was not discovered in this turn: " + parentPath,
                treeFirstOrderHint(parentPath, null, "create_object")
        ));
    }

    private static Optional<BlockDecision> checkObjectPathDiscovered(
            String objectPath,
            String toolName,
            List<Map<String, Object>> steps
    ) {
        if (isObjectPathGrounded(steps, objectPath)) {
            return Optional.empty();
        }
        String parent = parentOf(objectPath);
        return Optional.of(new BlockDecision(
                "Cannot " + toolName + ": object path was not created or discovered in this turn: " + objectPath,
                treeFirstOrderHint(parent.isBlank() ? objectPath : parent, objectPath, toolName)
        ));
    }

    private static String mutationLabel(Map<String, Object> arguments, String toolName) {
        String name = stringArg(arguments, "name");
        if (!name.isBlank()) {
            return "create under " + name;
        }
        return toolName.replace('_', ' ');
    }

    static String treeFirstOrderHint(String parentFolder, String objectPath, String configureTool) {
        String step3 = configureTool != null && !configureTool.isBlank()
                ? configureTool + " path=<path from create_object result>"
                : "configure/save tool path=<path from create_object result>";
        if (objectPath != null && !objectPath.isBlank()) {
            step3 = configureTool + " path=" + objectPath + " (only after create_object returns this path)";
        }
        return """
                Tree-first order (ALL object types — WORKFLOW, MIMIC, DASHBOARD, DEVICE, ALERT, …):
                1. list_objects parent=%s — exact target folder (NOT parent=root for root.platform.* paths)
                2. create_object parentPath=%s name=<name> type=<TYPE> [templateId=…]
                3. %s
                Reuse existing objects: get_object or list_objects must return the path in this turn before mutating.
                """.formatted(parentFolder, parentFolder, step3);
    }

    private static Optional<BlockDecision> checkModelDiscovered(
            Map<String, Object> arguments,
            List<Map<String, Object>> steps,
            String... modelArgKeys
    ) {
        String modelRef = "";
        for (String key : modelArgKeys) {
            modelRef = stringArg(arguments, key);
            if (!modelRef.isBlank()) {
                break;
            }
        }
        if (modelRef.isBlank()) {
            return Optional.empty();
        }
        if (isModelGrounded(steps, modelRef)) {
            return Optional.empty();
        }
        return Optional.of(new BlockDecision(
                "Cannot apply model: \"" + modelRef + "\" was not returned by a catalog tool in this turn",
                "Call list_relative_models (RELATIVE), list_instance_types (INSTANCE), "
                        + "list_absolute_models (ABSOLUTE), or list_virtual_profiles — then pick modelName / "
                        + "profile / instanceType only from that tool result."
        ));
    }

    static boolean isParentGrounded(List<Map<String, Object>> steps, String parentPath) {
        if (steps == null || steps.isEmpty() || parentPath == null || parentPath.isBlank()) {
            return false;
        }
        String normalized = normalizePathForCompare(parentPath);
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            String tool = toolName(step);
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");

            if ("create_object".equals(tool)) {
                String createdPath = pathFromCreateResult(result);
                if (!createdPath.isBlank()) {
                    String existingParent = parentOf(createdPath);
                    if (pathsEqual(parentPath, createdPath)
                            || pathsEqual(parentPath, existingParent)
                            || normalized.startsWith(normalizePathForCompare(createdPath) + ".")) {
                        return true;
                    }
                }
            }
            if ("create_virtual_device".equals(tool)) {
                String createdPath = pathFromCreateResult(result);
                String createdParent = parentOf(createdPath);
                if (pathsEqual(parentPath, createdParent)) {
                    return true;
                }
            }
            if (!isOkToolStep(step)) {
                continue;
            }
            if (TREE_DISCOVERY_TOOLS.contains(tool)) {
                if (parentMatchesDiscovery(normalized, tool, args, result)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isObjectPathGrounded(List<Map<String, Object>> steps, String objectPath) {
        if (steps == null || steps.isEmpty() || objectPath == null || objectPath.isBlank()) {
            return false;
        }
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            String tool = toolName(step);
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");

            if ("create_object".equals(tool) || "create_virtual_device".equals(tool)) {
                if (pathsEqual(objectPath, pathFromCreateResult(result))) {
                    return true;
                }
                if (!isOkToolStep(step) && existingObjectPath(result, objectPath)) {
                    return true;
                }
            }
            if (!isOkToolStep(step)) {
                continue;
            }
            if (OBJECT_READ_TOOLS.contains(tool)) {
                String queriedPath = normalizePath(resolveObjectPath(args));
                if (pathsEqual(objectPath, queriedPath)) {
                    return true;
                }
            }
            if (TREE_DISCOVERY_TOOLS.contains(tool)) {
                if (objectListContainsPath(result, objectPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isModelGrounded(List<Map<String, Object>> steps, String modelRef) {
        if (steps == null || steps.isEmpty() || modelRef == null || modelRef.isBlank()) {
            return false;
        }
        String needle = modelRef.trim().toLowerCase(Locale.ROOT);
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!MODEL_DISCOVERY_TOOLS.contains(tool)) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            if (catalogResultContainsModel(result, needle, tool)) {
                return true;
            }
        }
        return false;
    }

    static String resolveObjectPath(Map<String, Object> arguments) {
        if (arguments == null) {
            return "";
        }
        for (String key : List.of("path", "objectPath", "dashboardPath", "mimicPath", "workflowPath")) {
            String value = stringArg(arguments, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static String resolveParentPath(Map<String, Object> arguments) {
        if (arguments == null) {
            return "";
        }
        String parentPath = stringArg(arguments, "parentPath");
        if (parentPath.isBlank()) {
            parentPath = stringArg(arguments, "parent");
        }
        return parentPath;
    }

    private static boolean parentMatchesDiscovery(
            String parentPathNormalized,
            String tool,
            Map<String, Object> args,
            Map<String, Object> result
    ) {
        if ("get_object".equals(tool)) {
            String path = normalizePath(stringArg(args, "path"));
            return parentPathNormalized.equals(normalizePathForCompare(path))
                    || parentPathNormalized.startsWith(normalizePathForCompare(path) + ".");
        }
        if ("list_objects".equals(tool)) {
            String listedParent = normalizePathForCompare(listedParentFromArgs(args));
            if (parentPathNormalized.equals(listedParent)) {
                return true;
            }
            if (parentPathNormalized.startsWith(listedParent + ".")) {
                if (objectListContainsPath(result, parentPathNormalized)) {
                    return true;
                }
                String remainder = parentPathNormalized.substring(listedParent.length() + 1);
                if (!remainder.contains(".")) {
                    return objectListContainsSegment(result, listedParentFromArgs(args), remainder);
                }
                String firstSegment = remainder.substring(0, remainder.indexOf('.'));
                if (!objectListContainsSegment(result, listedParentFromArgs(args), firstSegment)) {
                    return false;
                }
                return objectListContainsPath(result, parentPathNormalized);
            }
        }
        if ("search_objects".equals(tool) || "search_by_haystack_tags".equals(tool)) {
            return objectListContainsPath(result, parentPathNormalized);
        }
        return false;
    }

    private static boolean catalogResultContainsModel(Map<String, Object> result, String needle, String tool) {
        if ("list_virtual_profiles".equals(tool)) {
            return listContainsField(result, "profiles", "profile", needle)
                    || listContainsField(result, "profiles", "templateId", needle);
        }
        return listContainsField(result, "models", "modelName", needle)
                || listContainsField(result, "models", "name", needle)
                || listContainsField(result, "models", "modelId", needle)
                || listContainsField(result, "instanceTypes", "modelName", needle)
                || listContainsField(result, "instanceTypes", "name", needle)
                || stringFieldEquals(result, "modelName", needle)
                || stringFieldEquals(result, "name", needle);
    }

    @SuppressWarnings("unchecked")
    private static boolean listContainsField(
            Map<String, Object> result,
            String listKey,
            String field,
            String needle
    ) {
        Object listObj = result.get(listKey);
        if (!(listObj instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                Object value = row.get(field);
                if (value != null && needle.equals(String.valueOf(value).trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean stringFieldEquals(Map<String, Object> result, String field, String needle) {
        Object value = result.get(field);
        return value != null && needle.equals(String.valueOf(value).trim().toLowerCase(Locale.ROOT));
    }

    private static String listedParentFromArgs(Map<String, Object> args) {
        String listedParent = normalizePath(stringArg(args, "parent"));
        if (listedParent.isBlank()) {
            listedParent = normalizePath(stringArg(args, "parentPath"));
        }
        if (listedParent.isBlank()) {
            listedParent = "root";
        }
        return listedParent;
    }

    @SuppressWarnings("unchecked")
    private static List<?> objectsList(Map<String, Object> result) {
        Object listObj = result.get("objects");
        return listObj instanceof List<?> list ? list : List.of();
    }

    private static Optional<String> rowPath(Object item) {
        if (item instanceof ObjectDto dto) {
            return Optional.ofNullable(dto.path()).filter(path -> !path.isBlank());
        }
        if (item instanceof Map<?, ?> row) {
            Object path = row.get("path");
            if (path != null && !String.valueOf(path).isBlank()) {
                return Optional.of(String.valueOf(path));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> rowName(Object item) {
        if (item instanceof ObjectDto dto) {
            String path = dto.path();
            if (path != null && path.contains(".")) {
                return Optional.of(path.substring(path.lastIndexOf('.') + 1));
            }
            return Optional.ofNullable(dto.displayName()).filter(name -> !name.isBlank());
        }
        if (item instanceof Map<?, ?> row) {
            Object rowName = row.get("name");
            if (rowName != null && !String.valueOf(rowName).isBlank()) {
                return Optional.of(String.valueOf(rowName));
            }
        }
        return Optional.empty();
    }

    private static boolean objectListContainsSegment(
            Map<String, Object> result,
            String listedParent,
            String segment
    ) {
        String needle = segment.trim().toLowerCase(Locale.ROOT);
        String expectedPath = normalizePath(listedParent + "." + segment);
        for (Object item : objectsList(result)) {
            Optional<String> pathOpt = rowPath(item);
            if (pathOpt.isPresent()) {
                String rowPath = normalizePath(pathOpt.get());
                if (rowPath.equals(expectedPath) || rowPath.endsWith("." + segment)) {
                    return true;
                }
            }
            Optional<String> nameOpt = rowName(item);
            if (nameOpt.isPresent() && needle.equals(nameOpt.get().trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean objectListContainsPath(Map<String, Object> result, String path) {
        String needle = normalizePathForCompare(path);
        for (Object item : objectsList(result)) {
            Optional<String> pathOpt = rowPath(item);
            if (pathOpt.isPresent() && needle.equals(normalizePathForCompare(pathOpt.get()))) {
                return true;
            }
        }
        return false;
    }

    private static String parentOf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot <= 0) {
            return "";
        }
        return path.substring(0, lastDot);
    }

    static String resolveCanonicalPath(List<Map<String, Object>> steps, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return "";
        }
        String normalizedNeedle = normalizePathForCompare(requestedPath);
        if (steps != null) {
            for (Map<String, Object> step : steps) {
                if (!"tool".equals(String.valueOf(step.get("type")))) {
                    continue;
                }
                String tool = toolName(step);
                Map<String, Object> args = stepMap(step, "arguments");
                Map<String, Object> result = stepMap(step, "result");
                if ("create_object".equals(tool) || "create_virtual_device".equals(tool)) {
                    String created = pathFromCreateResult(result);
                    if (pathsEqual(created, requestedPath)) {
                        return normalizePath(created);
                    }
                }
                if (!isOkToolStep(step)) {
                    continue;
                }
                if (TREE_DISCOVERY_TOOLS.contains(tool)) {
                    String discovered = findPathInDiscoveryResult(result, normalizedNeedle);
                    if (!discovered.isBlank()) {
                        return discovered;
                    }
                }
                if (OBJECT_READ_TOOLS.contains(tool)) {
                    String queried = normalizePath(resolveObjectPath(args));
                    if (pathsEqual(queried, requestedPath)) {
                        return queried;
                    }
                }
            }
        }
        return normalizePath(requestedPath);
    }

    private static String findPathInDiscoveryResult(Map<String, Object> result, String normalizedNeedle) {
        for (Object item : objectsList(result)) {
            Optional<String> pathOpt = rowPath(item);
            if (pathOpt.isPresent() && normalizePathForCompare(pathOpt.get()).equals(normalizedNeedle)) {
                return normalizePath(pathOpt.get());
            }
        }
        return "";
    }

    private static boolean pathsEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return normalizePathForCompare(a).equals(normalizePathForCompare(b));
    }

    private static String normalizePathForCompare(String path) {
        return normalizePath(path).toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replaceAll("\\.+", ".").replaceAll("\\.$", "");
    }

    private static String pathFromCreateResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        String path = normalizePath(String.valueOf(result.get("path")));
        if (!path.isBlank() && !"null".equalsIgnoreCase(path)) {
            return path;
        }
        String existing = normalizePath(String.valueOf(result.get("existingPath")));
        if (!existing.isBlank() && !"null".equalsIgnoreCase(existing)) {
            return existing;
        }
        String error = String.valueOf(result.get("error"));
        if (error.startsWith("Object exists:")) {
            return normalizePath(error.substring("Object exists:".length()));
        }
        return "";
    }

    private static boolean existingObjectPath(Map<String, Object> result, String objectPath) {
        String existing = pathFromCreateResult(result);
        return !existing.isBlank() && pathsEqual(objectPath, existing);
    }

    private static boolean isOkToolStep(Map<String, Object> step) {
        if (!"tool".equals(String.valueOf(step.get("type")))) {
            return false;
        }
        Map<String, Object> result = stepMap(step, "result");
        return "OK".equals(String.valueOf(result.get("status")));
    }

    private static String toolName(Map<String, Object> step) {
        return String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return "";
        }
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
