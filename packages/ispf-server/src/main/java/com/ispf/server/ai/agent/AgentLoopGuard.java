package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class AgentLoopGuard {

    private static final int RECENT_WINDOW = 5;
    private static final int REPEAT_THRESHOLD = 3;

    private AgentLoopGuard() {
    }

    static String continuationHint(
            String lastTool,
            List<Map<String, Object>> steps,
            int maxStepsTotal
    ) {
        if (steps == null || steps.isEmpty()) {
            return defaultHint(steps, maxStepsTotal);
        }
        if ("run_report".equals(lastTool)) {
            return """
                    Report data received. Prefer finish with a summary if the goal is met; \
                    otherwise continue with one more targeted read tool.""";
        }
        if (isRepeatedTool(lastTool, steps)) {
            return """
                    You called the same tool repeatedly. Change strategy or emit {"type":"finish",...}. \
                    If the user's intent is unclear, prefer finish with a short question and result.suggestions \
                    (label + message per option, interactive=true) instead of more blind tool calls. \
                    For dashboards: list_variables first; use set_dashboard_layout template= instead of many add_dashboard_widget; \
                    never set_variable name=widgets. get_widget_catalog type=<type> for exact widget fields. \
                    For SCADA mimics: save_mimic_diagram or add_mimic_elements with non-empty elements[]; \
                    list_mimic_symbols for symbolId; get_mimic_diagram to verify elementCount; never set_variable name=diagram. \
                    For platform docs: list_drivers, get_driver_help, get_example_bundle, get_automation_schema instead of search_context loops. \
                    Before invoke_bff: use list_functions and get_function instead of guessing function names.""";
        }
        long recentSearch = recentToolCount(steps, "search_context");
        if (recentSearch >= REPEAT_THRESHOLD) {
            return """
                    Stop search_context loops. Use list_drivers, get_driver_help, list_examples, \
                    get_example_bundle, or concrete tree tools (set_variable, configure_driver). \
                    Finish with {"type":"finish",...} when done.""";
        }
        return defaultHint(steps, maxStepsTotal);
    }

    private static String defaultHint(List<Map<String, Object>> steps, int maxStepsTotal) {
        int stepCount = steps != null ? steps.size() : 0;
        int remainingTotal = Math.max(0, maxStepsTotal - stepCount);
        if (remainingTotal <= 3) {
            return "Step limit almost reached (" + stepCount + "/" + maxStepsTotal
                    + ") — finish now with {\"type\":\"finish\",\"summary\":\"...\",\"result\":{...}}.";
        }
        return "Continue with another tool action or finish when the goal is complete.";
    }

    private static boolean isRepeatedTool(String lastTool, List<Map<String, Object>> steps) {
        if (lastTool == null || lastTool.isBlank()) {
            return false;
        }
        return recentToolCount(steps, lastTool) >= REPEAT_THRESHOLD;
    }

    private static long recentToolCount(List<Map<String, Object>> steps, String toolName) {
        int start = Math.max(0, steps.size() - RECENT_WINDOW);
        return steps.stream()
                .skip(start)
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .map(step -> step.get("tool"))
                .filter(Objects::nonNull)
                .map(tool -> String.valueOf(tool).toLowerCase(Locale.ROOT))
                .filter(tool -> tool.equals(toolName.toLowerCase(Locale.ROOT)))
                .count();
    }
}
