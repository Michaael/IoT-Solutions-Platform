package com.ispf.server.ai.agent;

import com.ispf.server.ai.agent.fixtures.SpecIntakeFixtures;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecIntakeScenarioTest {

    @Test
    void pumpStationTzClassifiesAsIndustrialFacility() {
        var classification = AgentAssignmentClassifier.classify(SpecIntakeFixtures.PUMP_STATION_TZ_EXCERPT);
        assertThat(classification.type()).isEqualTo(AgentAssignmentType.INDUSTRIAL_FACILITY);
        assertThat(classification.fastPath()).isFalse();
        assertThat(classification.domainAdapter()).isEqualTo("industrial_oil_gas");
        assertThat(AgentAssignmentClassifier.isComplexAssignment(SpecIntakeFixtures.PUMP_STATION_TZ_EXCERPT)).isTrue();
    }

    @Test
    void snmpPromptUsesMonitoringLabFastPath() {
        var classification = AgentAssignmentClassifier.classify(SpecIntakeFixtures.SNMP_PROMPT);
        assertThat(classification.type()).isEqualTo(AgentAssignmentType.MONITORING_LAB);
        assertThat(classification.fastPath()).isTrue();
        assertThat(AgentAssignmentClassifier.isComplexAssignment(SpecIntakeFixtures.SNMP_PROMPT)).isFalse();
    }

    @Test
    void mesBundleClassifiesAsApplicationBundle() {
        var classification = AgentAssignmentClassifier.classify(SpecIntakeFixtures.MES_BUNDLE_PROMPT);
        assertThat(classification.type()).isEqualTo(AgentAssignmentType.APPLICATION_BUNDLE);
        assertThat(classification.domainAdapter()).isEqualTo("mes_terminal");
    }

    @Test
    void gapCatalogMarksPlatformCapabilitiesAsFull() {
        Map<String, Object> row = AgentSpecGapCatalog.defaultGapRow("FR-1", "realtime", "Telemetry");
        assertThat(row.get("status")).isEqualTo("full");
        assertThat(row.get("blocksDev")).isEqualTo(false);
    }

    @Test
    void gapCatalogMarksMlAsOutOfScope() {
        Map<String, Object> row = AgentSpecGapCatalog.defaultGapRow("FR-3", "ml", "LSTM forecast");
        assertThat(row.get("status")).isEqualTo("out_of_scope");
        assertThat(row.get("blocksDev")).isEqualTo(true);
    }

    @Test
    void handoffFrameValidatorRequiresCoreFields() {
        Map<String, Object> frame = AgentHandoffFrameValidator.minimalHandoffFrame(
                AgentAssignmentType.INDUSTRIAL_FACILITY,
                "Pump station full TZ",
                List.of(AgentSpecGapCatalog.defaultGapRow("FR-1", "realtime", "Telemetry")),
                List.of(
                        Map.of("phaseId", "full", "steps", List.of("list_objects", "create_virtual_device", "configure_alert", "save_mimic_diagram"))
                )
        );
        var result = AgentHandoffFrameValidator.validateHandoffFrame(frame);
        assertThat(result.ok()).isTrue();
    }

    @Test
    void judgeBlocksOpenGap() {
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("gapMatrix", List.of(Map.of(
                "gapId", "GAP-ML-01",
                "blocksDev", true,
                "gapStatus", "open"
        )));
        Map<String, Object> finish = Map.of("handoffFrame", handoff);
        AgentRunState state = new AgentRunState();
        state.setStoredPlan(finish);
        var judge = AgentJudgeService.evaluate(List.of(), state, finish, SpecIntakeFixtures.PUMP_STATION_TZ_EXCERPT);
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.GAP_REQUIRED);
    }

    @Test
    void judgeBlocksErrorStepsInTurn() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "create_object",
                "result", Map.of("status", "ERROR", "error", "parent path not discovered")
        ));
        var judge = AgentJudgeService.evaluate(steps, new AgentRunState(), Map.of(), "create pump station");
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.REWORK);
    }

    @Test
    void existingObjectExistsErrorDoesNotBlockJudgeWhenGrounded() {
        String mimicPath = "root.platform.mimics.pump-station-mimic";
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "create_object",
                "result", Map.of(
                        "status", "ERROR",
                        "error", "Object exists: " + mimicPath,
                        "existingPath", mimicPath
                )
        ));
        var judge = AgentJudgeService.evaluate(
                steps,
                new AgentRunState(),
                Map.of("assignmentType", "follow_up"),
                "continue"
        );
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.APPROVE);
    }

    @Test
    void judgeUserModerationAfterTwoReworkRounds() {
        AgentRunState state = new AgentRunState();
        state.incrementReworkRound();
        state.incrementReworkRound();
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "save_mimic_diagram",
                "result", Map.of("status", "ERROR", "error", "Cannot save_mimic_diagram: not grounded")
        ));
        var judge = AgentJudgeService.evaluate(steps, state, Map.of(), "finish");
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.USER_MODERATION_REQUIRED);
    }

    @Test
    void judgeApprovesWhenEarlierErrorsRecoveredByLaterOkSteps() {
        String mimicPath = "root.platform.mimics.pump-station-mimic";
        String dashPath = "root.platform.dashboards.pump-station-overview";
        List<Map<String, Object>> steps = List.of(
                toolError("create_object", Map.of("parentPath", "root.platform.mimics", "name", "pump-station-mimic"),
                        "Cannot create under pump-station-mimic: parent path was not discovered in this turn: root.platform.mimics"),
                toolOk("list_objects", Map.of("parent", "root.platform")),
                toolError("create_object", Map.of("parentPath", "root.platform.mimics", "name", "pump-station-mimic"),
                        "Object exists: " + mimicPath),
                toolError("save_mimic_diagram", Map.of("path", mimicPath),
                        "Cannot save_mimic_diagram: object path was not created or discovered in this turn: " + mimicPath),
                toolOk("list_objects", Map.of("parent", "root.platform.mimics")),
                toolOk("save_mimic_diagram", Map.of("path", mimicPath)),
                toolError("create_object", Map.of("parentPath", "root.platform.dashboards", "name", "pump-station-overview"),
                        "Object exists: " + dashPath),
                toolError("set_dashboard_layout", Map.of("path", dashPath),
                        "Cannot set_dashboard_layout: object path was not created or discovered in this turn: " + dashPath),
                toolOk("list_objects", Map.of("parent", "root.platform.dashboards")),
                toolOk("set_dashboard_layout", Map.of("path", dashPath)),
                toolOk("get_mimic_diagram", Map.of("path", mimicPath)),
                toolOk("get_dashboard_layout", Map.of("path", dashPath))
        );
        var judge = AgentJudgeService.evaluate(
                steps,
                new AgentRunState(),
                Map.of("assignmentType", "follow_up"),
                "finish pump station"
        );
        assertThat(judge.verdict()).isEqualTo(AgentJudgeService.Verdict.APPROVE);
    }

    @Test
    void judgeUserModerationRequiresUserIntervention() {
        assertThat(AgentJudgeService.Verdict.USER_MODERATION_REQUIRED.requiresUserIntervention()).isTrue();
        assertThat(AgentJudgeService.Verdict.GAP_REQUIRED.requiresUserIntervention()).isTrue();
        assertThat(AgentJudgeService.Verdict.REWORK.requiresUserIntervention()).isFalse();
    }

    private static Map<String, Object> toolError(String tool, Map<String, Object> args, String error) {
        return Map.of(
                "type", "tool",
                "tool", tool,
                "arguments", args,
                "result", Map.of("status", "ERROR", "error", error)
        );
    }

    private static Map<String, Object> toolOk(String tool, Map<String, Object> args) {
        return Map.of(
                "type", "tool",
                "tool", tool,
                "arguments", args,
                "result", Map.of("status", "OK")
        );
    }

    @Test
    void preflightHintsWhenParentNotGrounded() {
        var hint = AgentPreflightService.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.devices.nps", "name", "nm-1", "type", "DEVICE"),
                List.of()
        );
        assertThat(hint).isPresent();
        assertThat(hint.get().hint()).contains("list_objects");
    }

    @Test
    void approvalPhraseDaNachinaem() {
        assertThat(AgentPlanGuard.isApprovalMessage("Да, начинаем")).isTrue();
        assertThat(AgentPlanGuard.isApprovalMessage("начинаем")).isTrue();
    }

    @Test
    void blockNeedsApprovalWhenAwaitingAndExecutionFinish() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                Map.of("devicePath", "root.platform.devices.pump-01"),
                List.of(),
                "Создай насосную станцию",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_APPROVAL);
    }

    @Test
    void antiReplanWhenApprovedAndPlanFinish() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.APPROVED);
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                Map.of("phase", "plan", "plan", Map.of("goal", "test")),
                List.of(),
                "продолжай",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN);
    }

    @Test
    void tankFarmTankProfileRegistered() {
        assertThat(VirtualDeviceProfileCatalog.resolve("tank-farm-tank")).isPresent();
        var spec = VirtualDeviceProfileCatalog.resolve("tank-farm-tank").orElseThrow();
        assertThat(spec.expectedVariables()).contains("fillLevelMm");
        Map<String, Object> row = VirtualDeviceProfileCatalog.profileCatalogRow("tank-farm-tank", spec);
        assertThat(row).containsKey("semanticLabels");
    }
}
