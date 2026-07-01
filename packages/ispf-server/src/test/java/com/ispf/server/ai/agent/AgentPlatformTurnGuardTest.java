package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlatformTurnGuardTest {

    @Test
    void blocksFinishWhenDeviceCreatedWithoutVariableVerification() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DEVICE", "name", "pump-01"),
                        "result", Map.of("status", "OK", "path", "root.platform.devices.pump-01")
                )
        );

        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps);
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("pump-01");
    }

    @Test
    void allowsFinishWhenListVariablesVerifiedDevice() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DEVICE"),
                        "result", Map.of("status", "OK", "path", "root.platform.devices.pump-01")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "list_variables",
                        "arguments", Map.of("path", "root.platform.devices.pump-01"),
                        "result", Map.of("status", "OK", "count", 8)
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps)).isEmpty();
    }

    @Test
    void allowsFinishWhenCreateVirtualDeviceReturnedTelemetry() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_virtual_device",
                        "arguments", Map.of("profile", "lab"),
                        "result", Map.of(
                                "status", "OK",
                                "path", "root.platform.devices.pump-01",
                                "telemetryVariableCount", 4
                        )
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps)).isEmpty();
    }

    @Test
    void blocksFinishWhenMonitoringIntentWithoutConfigureAlert() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DASHBOARD", "name", "monitoring-main"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.monitoring-main")
                )
        );

        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps, "Сделай мониторинг с дашбордом");
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("configure_alert");
    }

    @Test
    void allowsFinishWhenMonitoringIntentAndAlertConfigured() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DASHBOARD"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.monitoring-main")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "arguments", Map.of("name", "temperature-high"),
                        "result", Map.of("status", "OK", "path", "root.platform.alert-rules.temperature-high")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_dashboard_layout",
                        "arguments", Map.of("path", "root.platform.dashboards.monitoring-main"),
                        "result", Map.of("status", "OK", "widgets", List.of(Map.of("id", "w1")))
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "monitoring dashboard with alerts")).isEmpty();
    }

    @Test
    void blocksFinishWhenScadaIntentAndMimicDiagramIsEmpty() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "get_mimic_diagram",
                        "arguments", Map.of("path", "root.platform.mimics.pump-station"),
                        "result", Map.of("status", "OK", "path", "root.platform.mimics.pump-station", "elementCount", 0)
                )
        );

        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps, "Нужна SCADA mimic диаграмма");
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("elementCount=0");
    }

    @Test
    void allowsFinishWhenScadaIntentAndMimicDiagramHasElements() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "get_mimic_diagram",
                        "arguments", Map.of("path", "root.platform.mimics.pump-station"),
                        "result", Map.of("status", "OK", "path", "root.platform.mimics.pump-station", "elementCount", 3)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "arguments", Map.of("name", "pump-alert"),
                        "result", Map.of("status", "OK", "path", "root.platform.alert-rules.pump-alert")
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "build scada mimic dashboard")).isEmpty();
    }

    @Test
    void blocksFinishWhenErrorStepInTurn() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "result", Map.of("status", "ERROR", "error", "parent not discovered")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "create_virtual_device",
                        "result", Map.of("status", "OK", "path", "root.platform.devices.pump-01", "telemetryVariableCount", 4)
                )
        );
        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps);
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("ERROR");
    }

    @Test
    void dashboardOnlyIntentDoesNotForceAlert() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "result", Map.of("status", "OK")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_dashboard_layout",
                        "result", Map.of("status", "OK", "widgets", List.of(Map.of("id", "w1")))
                )
        );
        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "Создай дашборд для устройства")).isEmpty();
    }

    @Test
    void blocksFinishWhenMimicCreatedWithoutGetMimicDiagram() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "MIMIC"),
                        "result", Map.of("status", "OK", "path", "root.platform.mimics.facility")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "save_mimic_diagram",
                        "result", Map.of("status", "OK")
                )
        );
        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps, "SCADA mimic");
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("diagram not verified");
    }

    @Test
    void allowsFinishWhenCreateObjectErrorRecoveredByLaterOk() {
        String mimicPath = "root.platform.mimics.pump-station-mimic";
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of(
                                "parentPath", "root.platform.mimics",
                                "name", "pump-station-mimic",
                                "type", "MIMIC"
                        ),
                        "result", Map.of(
                                "status", "ERROR",
                                "error", "Cannot create under pump-station-mimic: parent path was not discovered in this turn: root.platform.mimics"
                        )
                ),
                Map.of(
                        "type", "tool",
                        "tool", "list_objects",
                        "arguments", Map.of("parent", "root.platform"),
                        "result", Map.of("status", "OK")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of(
                                "parentPath", "root.platform.mimics",
                                "name", "pump-station-mimic",
                                "type", "MIMIC"
                        ),
                        "result", Map.of("status", "OK", "path", mimicPath)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "save_mimic_diagram",
                        "arguments", Map.of("path", mimicPath),
                        "result", Map.of("status", "OK")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_mimic_diagram",
                        "arguments", Map.of("path", mimicPath),
                        "result", Map.of("status", "OK", "elementCount", 5)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DASHBOARD"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.pump-station-overview")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "result", Map.of("status", "OK")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_dashboard_layout",
                        "arguments", Map.of("path", "root.platform.dashboards.pump-station-overview"),
                        "result", Map.of("status", "OK", "widgets", List.of(Map.of("id", "w1")))
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "result", Map.of("status", "OK")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "create_virtual_device",
                        "result", Map.of(
                                "status", "OK",
                                "path", "root.platform.devices.pump-station.pump-01",
                                "telemetryVariableCount", 4
                        )
                )
        );
        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "SCADA mimic monitoring")).isEmpty();
    }

    @Test
    void allowsFinishWhenSetDashboardLayoutReportsWidgetCount() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "save_mimic_diagram",
                        "result", Map.of("status", "OK")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_mimic_diagram",
                        "result", Map.of("status", "OK", "elementCount", 2)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "result", Map.of("status", "OK", "widgetCount", 3)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "result", Map.of("status", "OK")
                )
        );
        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "SCADA")).isEmpty();
    }

    @Test
    void allowsFinishWhenGetDashboardLayoutReturnsWidgetCountOnly() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.pump-overview")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_dashboard_layout",
                        "arguments", Map.of("path", "root.platform.dashboards.pump-overview"),
                        "result", Map.of(
                                "status", "OK",
                                "layoutJson", "{\"columns\":84,\"rowHeight\":8,\"widgets\":[{\"id\":\"m1\"}]}",
                                "widgetCount", 1
                        )
                ),
                Map.of(
                        "type", "tool",
                        "tool", "get_mimic_diagram",
                        "result", Map.of("status", "OK", "elementCount", 4)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "result", Map.of("status", "OK")
                )
        );
        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "SCADA dashboard")).isEmpty();
    }

    @Test
    void allowsFinishWhenSaveMimicReportsElementCountWithoutGet() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "save_mimic_diagram",
                        "result", Map.of("status", "OK", "elementCount", 8)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "add_dashboard_widget",
                        "result", Map.of("status", "OK", "widgetCount", 4)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "result", Map.of("status", "OK")
                )
        );
        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "SCADA")).isEmpty();
    }
}
