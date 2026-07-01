package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        if ("create_object".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            String blockedParent = blockedParentPath(steps);
            return "Parent path not discovered for create_object. "
                    + "Call list_objects parent=" + blockedParent
                    + " (exact folder — listing parent=root does NOT ground root.platform.* paths). "
                    + "Verify objects[].path in the result, then retry create_object.";
        }
        if ("save_workflow_bpmn".equals(lastTool) && lastStepErrorContains(steps, "not discovered in this turn")) {
            return """
                    save_workflow_bpmn blocked: WORKFLOW object must exist first. \
                    Order: list_objects parent=root.platform.workflows → create_object type=WORKFLOW → \
                    save_workflow_bpmn path=<path from create_object result>.""";
        }
        if ("save_workflow_bpmn".equals(lastTool) && lastStepErrorContains(steps, "not found")) {
            return """
                    WORKFLOW object not found. create_object type=WORKFLOW must succeed before save_workflow_bpmn. \
                    Use the exact path returned by create_object (e.g. root.platform.workflows.hydraulic-shock).""";
        }
        if ("list_objects".equals(lastTool) && lastListedParentIsRoot(steps)) {
            return """
                    Listed root — for objects under root.platform.* call list_objects on the exact parent folder \
                    (e.g. parent=root.platform.workflows), not parent=root.""";
        }
        if ("run_report".equals(lastTool)) {
            return """
                    Report data received. Prefer finish with a summary if the goal is met; \
                    otherwise continue with one more targeted read tool.""";
        }
        if ("create_object".equals(lastTool)) {
            String typeHint = createObjectTypeHint(steps);
            if (typeHint != null) {
                return typeHint;
            }
            return """
                    Object created. For virtual simulators use create_virtual_device next time. \
                    If driverId=virtual: set templateId from list_relative_models OR recreate with create_virtual_device; \
                    set driverConfigJson profile from list_virtual_profiles, configure_driver autoStart=true, list_variables before finish.""";
        }
        if ("save_workflow_bpmn".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            return AgentGroundTruthGuard.treeFirstOrderHint(
                    "root.platform.workflows",
                    blockedObjectPath(steps),
                    "save_workflow_bpmn"
            );
        }
        if ("save_mimic_diagram".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            return AgentGroundTruthGuard.treeFirstOrderHint(
                    "root.platform.mimics",
                    blockedObjectPath(steps),
                    "save_mimic_diagram"
            );
        }
        if ("set_dashboard_layout".equals(lastTool) && lastStepGroundTruthBlocked(steps)) {
            return AgentGroundTruthGuard.treeFirstOrderHint(
                    "root.platform.dashboards",
                    blockedObjectPath(steps),
                    "set_dashboard_layout"
            );
        }
        if (lastStepGroundTruthBlocked(steps)) {
            String objectPath = blockedObjectPath(steps);
            String parent = objectPath.contains(".") ? objectPath.substring(0, objectPath.lastIndexOf('.')) : objectPath;
            return AgentGroundTruthGuard.treeFirstOrderHint(parent, objectPath, lastTool);
        }
        if ("search_platform_recipes".equals(lastTool) || "get_automation_schema".equals(lastTool)) {
            return """
                    Recipe/schema loaded — this is a pattern, not live tree state. \
                    Next: list_objects on the real parent folder, list_relative_models / list_virtual_profiles, \
                    then create using paths and modelName from those tool results only.""";
        }
        if ("create_virtual_device".equals(lastTool)) {
            return """
                    Virtual device provisioned. Verify telemetryVariableCount>0; bind SCADA/dashboard using returned variable names; \
                    list_variables on each device before finish.""";
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
        String pace = AgentTurnPaceHints.gentlePaceSuffix(steps, maxStepsTotal);
        if (remainingTotal <= 8) {
            return "Step limit almost reached (" + stepCount + "/" + maxStepsTotal
                    + ") — finish now with {\"type\":\"finish\",\"summary\":\"...\",\"result\":{...}}."
                    + pace;
        }
        String base = "Continue with another tool action or finish when the goal is complete.";
        return pace.isEmpty() ? base : base + pace;
    }

    static Optional<BlockDecision> checkHardBlock(String toolName, List<Map<String, Object>> steps) {
        if (repeatGroundTruthError(toolName, steps) >= 2) {
            String parent = blockedParentPath(steps);
            return Optional.of(new BlockDecision(
                    "Hard block: same ground-truth error repeated for " + toolName,
                    "Mandatory: list_objects parent=" + parent
                            + " — use exact paths from result, then retry. Problem Brief: do not replan — fix grounding."
            ));
        }
        return Optional.empty();
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    private static int repeatGroundTruthError(String toolName, List<Map<String, Object>> steps) {
        if (steps == null || toolName == null) {
            return 0;
        }
        int count = 0;
        int start = Math.max(0, steps.size() - 4);
        for (int i = start; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            if (!toolName.equalsIgnoreCase(String.valueOf(step.get("tool")))) {
                continue;
            }
            if (lastStepGroundTruthBlocked(List.of(step))) {
                count++;
            }
        }
        return count;
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

    private static boolean lastStepGroundTruthBlocked(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return false;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"tool".equals(String.valueOf(last.get("type")))) {
            return false;
        }
        Map<String, Object> result = stepMap(last, "result");
        if (!"ERROR".equals(String.valueOf(result.get("status")))) {
            return false;
        }
        String error = String.valueOf(result.get("error"));
        return error.contains("not discovered in this turn");
    }

    private static String blockedObjectPath(List<Map<String, Object>> steps) {
        Map<String, Object> last = steps.get(steps.size() - 1);
        String error = String.valueOf(stepMap(last, "result").get("error"));
        int marker = error.lastIndexOf(": ");
        if (marker >= 0 && marker + 2 < error.length()) {
            return error.substring(marker + 2).trim();
        }
        return "";
    }

    private static String createObjectTypeHint(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return null;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"create_object".equals(String.valueOf(last.get("tool")))) {
            return null;
        }
        if (!"OK".equals(String.valueOf(stepMap(last, "result").get("status")))) {
            return null;
        }
        Map<String, Object> args = stepMap(last, "arguments");
        String type = stringArg(args, "type").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "WORKFLOW" -> """
                    WORKFLOW created. Next: save_workflow_bpmn path=<path from create_object result>, \
                    then update_workflow_status status=ACTIVE, then run_workflow.""";
            case "MIMIC" -> """
                    MIMIC created. Next: list_mimic_symbols, then save_mimic_diagram path=<path from create_object result> \
                    with non-empty elements[].""";
            case "DASHBOARD" -> """
                    DASHBOARD created. Next: list_variables on source devices, then set_dashboard_layout path=<path from create_object result> \
                    template= or add_dashboard_widget.""";
            case "DEVICE" -> """
                    DEVICE created. Next: configure_driver, list_variables — verify telemetry before dashboard/SCADA bindings.""";
            case "ALERT" -> "ALERT created. Next: configure_alert path=<path from create_object result>.";
            case "REPORT" -> "REPORT created. Next: configure_report path=<path from create_object result>.";
            default -> null;
        };
    }

    private static String blockedParentPath(List<Map<String, Object>> steps) {
        Map<String, Object> last = steps.get(steps.size() - 1);
        String error = String.valueOf(stepMap(last, "result").get("error"));
        int marker = error.lastIndexOf(": ");
        if (marker >= 0 && marker + 2 < error.length()) {
            return error.substring(marker + 2).trim();
        }
        return "root.platform.workflows";
    }

    private static boolean lastStepErrorContains(List<Map<String, Object>> steps, String fragment) {
        if (steps.isEmpty()) {
            return false;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"tool".equals(String.valueOf(last.get("type")))) {
            return false;
        }
        String error = String.valueOf(stepMap(last, "result").get("error"));
        return error.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static boolean lastListedParentIsRoot(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return false;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"tool".equals(String.valueOf(last.get("type")))) {
            return false;
        }
        if (!"list_objects".equals(String.valueOf(last.get("tool")))) {
            return false;
        }
        Map<String, Object> args = stepMap(last, "arguments");
        String parent = stringArg(args, "parent");
        if (parent.isBlank()) {
            parent = stringArg(args, "parentPath");
        }
        if (parent.isBlank()) {
            parent = "root";
        }
        return "root".equalsIgnoreCase(parent.trim())
                && "OK".equals(String.valueOf(stepMap(last, "result").get("status")));
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return "";
        }
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
