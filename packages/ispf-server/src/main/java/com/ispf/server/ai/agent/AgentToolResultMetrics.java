package com.ispf.server.ai.agent;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared parsing of agent tool result payloads — used by finish guards and dashboard/mimic tools
 * so validation always reads the same fields the tools emit.
 */
final class AgentToolResultMetrics {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentToolResultMetrics() {
    }

    static int widgetCountFromLayoutJson(String layoutJson) {
        return widgetCountFromLayoutJson(MAPPER, layoutJson);
    }

    static int widgetCountFromLayoutJson(ObjectMapper objectMapper, String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return 0;
        }
        try {
            var root = objectMapper.readTree(layoutJson);
            return root.path("widgets").isArray() ? root.path("widgets").size() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    static int widgetCountFromResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        Object widgetCount = result.get("widgetCount");
        if (widgetCount instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        Object widgets = result.get("widgets");
        if (widgets instanceof List<?> list && !list.isEmpty()) {
            return list.size();
        }
        Object layoutJson = result.get("layoutJson");
        if (layoutJson instanceof String json) {
            return widgetCountFromLayoutJson(json);
        }
        return 0;
    }

    static int elementCountFromResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return -1;
        }
        Object elementCount = result.get("elementCount");
        if (elementCount instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(elementCount));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Last OK elementCount from save_mimic_diagram or get_mimic_diagram in step order. */
    static int lastMimicElementCount(List<Map<String, Object>> steps) {
        Integer last = null;
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!"get_mimic_diagram".equals(tool) && !"save_mimic_diagram".equals(tool)) {
                continue;
            }
            int count = elementCountFromResult(stepMap(step, "result"));
            if (count >= 0) {
                last = count;
            }
        }
        return last == null ? -1 : last;
    }

    /** Last OK widget count from dashboard layout tools in step order. */
    static int lastDashboardWidgetCount(List<Map<String, Object>> steps) {
        int last = 0;
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step)) {
                continue;
            }
            String tool = toolName(step);
            if (!"get_dashboard_layout".equals(tool)
                    && !"set_dashboard_layout".equals(tool)
                    && !"add_dashboard_widget".equals(tool)) {
                continue;
            }
            int count = widgetCountFromResult(stepMap(step, "result"));
            if (count > 0) {
                last = count;
            }
        }
        return last;
    }

    static boolean hasVerifiedDashboardLayout(List<Map<String, Object>> steps) {
        return lastDashboardWidgetCount(steps) > 0;
    }

    static boolean hasVerifiedMimicDiagram(List<Map<String, Object>> steps) {
        return lastMimicElementCount(steps) > 0;
    }

    static int listVariablesMaxCount(List<Map<String, Object>> steps) {
        int max = 0;
        for (Map<String, Object> step : steps) {
            if (!isOkToolStep(step) || !"list_variables".equals(toolName(step))) {
                continue;
            }
            Object count = stepMap(step, "result").get("count");
            if (count instanceof Number number) {
                max = Math.max(max, number.intValue());
            }
        }
        return max;
    }

    private static boolean isOkToolStep(Map<String, Object> step) {
        return "tool".equals(String.valueOf(step.get("type")))
                && "OK".equals(String.valueOf(stepMap(step, "result").get("status")));
    }

    private static String toolName(Map<String, Object> step) {
        return String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
