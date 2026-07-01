package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates chart/history widget bindings before add_dashboard_widget.
 */
final class AgentWidgetBindingGuard {

    private static final Set<String> HISTORY_WIDGET_TYPES = Set.of("chart", "sparkline", "history-table");

    private AgentWidgetBindingGuard() {
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    @SuppressWarnings("unchecked")
    static Optional<BlockDecision> checkBeforeTool(
            String toolName,
            Map<String, Object> arguments,
            List<Map<String, Object>> steps
    ) {
        if (!"add_dashboard_widget".equalsIgnoreCase(toolName)) {
            return Optional.empty();
        }
        Map<String, Object> widget = widgetMap(arguments);
        if (widget.isEmpty()) {
            return Optional.empty();
        }
        String type = String.valueOf(widget.getOrDefault("type", "")).toLowerCase(Locale.ROOT);
        if (!HISTORY_WIDGET_TYPES.contains(type)) {
            return Optional.empty();
        }
        String objectPath = stringValue(widget.get("objectPath"));
        String variableName = stringValue(widget.get("variableName"));
        if (objectPath.isBlank() || variableName.isBlank()) {
            return Optional.empty();
        }
        if (hasHistoryEnabled(steps, objectPath, variableName)) {
            return Optional.empty();
        }
        return Optional.of(new BlockDecision(
                "Cannot add " + type + " widget: configure_variable_history not completed for "
                        + objectPath + "." + variableName,
                "Call configure_variable_history path=" + objectPath + " name=" + variableName
                        + " historyEnabled=true after list_variables, then retry add_dashboard_widget."
        ));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> widgetMap(Map<String, Object> arguments) {
        Object widget = arguments.get("widget");
        if (widget instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static boolean hasHistoryEnabled(List<Map<String, Object>> steps, String objectPath, String variableName) {
        if (steps == null) {
            return false;
        }
        String canonicalPath = AgentGroundTruthGuard.resolveCanonicalPath(steps, objectPath);
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            String tool = String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
            Map<String, Object> result = stepMap(step, "result");
            if (!"OK".equals(String.valueOf(result.get("status")))) {
                continue;
            }
            Map<String, Object> args = stepMap(step, "arguments");
            if ("configure_variable_history".equals(tool)) {
                String path = AgentGroundTruthGuard.resolveCanonicalPath(steps, stringValue(args.get("path")));
                String name = stringValue(args.get("name"));
                if (pathsEqual(path, canonicalPath) && name.equalsIgnoreCase(variableName)) {
                    return true;
                }
            }
            if ("describe_variables".equals(tool)) {
                String path = stringValue(args.get("path"));
                if (pathsEqual(path, canonicalPath) && describeShowsHistory(result, variableName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean describeShowsHistory(Map<String, Object> result, String variableName) {
        Object variables = result.get("variables");
        if (!(variables instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            if (!variableName.equalsIgnoreCase(String.valueOf(row.get("name")))) {
                continue;
            }
            Object history = row.get("historyEnabled");
            return Boolean.TRUE.equals(history) || "true".equalsIgnoreCase(String.valueOf(history));
        }
        return false;
    }

    private static boolean pathsEqual(String a, String b) {
        return AgentGroundTruthGuard.resolveCanonicalPath(List.of(), a)
                .equalsIgnoreCase(AgentGroundTruthGuard.resolveCanonicalPath(List.of(), b));
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
