package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards and tools must agree on result field names — regression tests for finish-loop false negatives.
 */
class AgentToolGuardContractTest {

    @Test
    void getDashboardLayoutWidgetCountParsedFromLayoutJson() {
        Map<String, Object> result = Map.of(
                "status", "OK",
                "layoutJson", "{\"columns\":84,\"widgets\":[{\"id\":\"w1\"},{\"id\":\"w2\"}]}"
        );
        assertThat(AgentToolResultMetrics.widgetCountFromResult(result)).isEqualTo(2);
        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(dashboardSteps(result), "SCADA dashboard")).isEmpty();
    }

    @Test
    void saveMimicElementCountSatisfiesMimicGuardWithoutGet() {
        List<Map<String, Object>> steps = List.of(
                toolOk("save_mimic_diagram", Map.of("elementCount", 6)),
                toolOk("set_dashboard_layout", Map.of("widgetCount", 2)),
                toolOk("configure_alert", Map.of())
        );
        assertThat(AgentToolResultMetrics.lastMimicElementCount(steps)).isEqualTo(6);
        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "SCADA mimic")).isEmpty();
    }

    @Test
    void addDashboardWidgetWidgetCountSatisfiesGuard() {
        List<Map<String, Object>> steps = List.of(
                toolOk("add_dashboard_widget", Map.of("widgetCount", 1)),
                toolOk("save_mimic_diagram", Map.of("elementCount", 3))
        );
        assertThat(AgentToolResultMetrics.hasVerifiedDashboardLayout(steps)).isTrue();
    }

    @Test
    void conformanceSmokeCasesMatchGuardMetrics() {
        List<Map<String, Object>> steps = List.of(
                toolOk("list_variables", Map.of("count", 5)),
                toolOk("get_mimic_diagram", Map.of("elementCount", 4)),
                toolOk("get_dashboard_layout", Map.of(
                        "layoutJson", "{\"widgets\":[{\"id\":\"a\"}]}",
                        "widgetCount", 1
                ))
        );
        assertThat(AgentConformanceEvaluator.verifySmokeCases(steps, AgentAssignmentType.INDUSTRIAL_FACILITY))
                .isEmpty();
    }

    @Test
    void stuckGuardDetectedAfterThreeIdenticalErrors() {
        String error = "Cannot finish: DASHBOARD created but layout not verified (widgetCount=0).";
        List<Map<String, Object>> steps = List.of(
                guardStep(error),
                guardStep(error),
                guardStep(error)
        );
        assertThat(AgentPlatformTurnGuard.isStuckGuardLoop(steps, error)).isTrue();
    }

    private static List<Map<String, Object>> dashboardSteps(Map<String, Object> layoutResult) {
        return List.of(
                toolOk("save_mimic_diagram", Map.of("elementCount", 2)),
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.overview")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_dashboard_layout",
                        "result", layoutResult
                ),
                toolOk("configure_alert", Map.of())
        );
    }

    private static Map<String, Object> toolOk(String tool, Map<String, Object> resultFields) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", "OK");
        result.putAll(resultFields);
        return Map.of("type", "tool", "tool", tool, "result", result);
    }

    private static Map<String, Object> guardStep(String error) {
        return Map.of("type", "guard", "error", error);
    }
}
