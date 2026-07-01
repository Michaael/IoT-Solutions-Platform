package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlanGuardTest {

    @Test
    void autoModeEntersPlanningForScadaRequest() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.AUTO);
        AgentPlanGuard.beginTurn(state, "Создай SCADA мнемосхему насосной станции с дашбордом", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.PLANNING);
    }

    @Test
    void simpleListRequestSkipsPlanningInAutoMode() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.AUTO);
        AgentPlanGuard.beginTurn(state, "Покажи список устройств на платформе", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.NONE);
    }

    @Test
    void blocksMutatingToolDuringPlanning() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.PLANNING);
        var block = AgentPlanGuard.checkBeforeTool(state, "create_object", AgentProfile.ADMIN);
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("blocked during planning");
    }

    @Test
    void allowsDiscoveryToolDuringPlanning() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.PLANNING);
        var block = AgentPlanGuard.checkBeforeTool(state, "list_objects", AgentProfile.ADMIN);
        assertThat(block).isEmpty();
    }

    @Test
    void approvalMessageApprovesPlan() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        AgentPlanGuard.beginTurn(state, "Утверждаю план, начинай выполнение", AgentProfile.ADMIN);
        assertThat(state.isPlanApproved()).isTrue();
    }

    @Test
    void blocksExecutionFinishWithoutPlan() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.PLANNING);
        Map<String, Object> result = Map.of("devicePath", "root.platform.devices.pump-01");
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                result,
                List.of(),
                "Создай SCADA насосную станцию",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN);
    }

    @Test
    void allowsPlanFinishDuringPlanning() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.PLANNING);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", "plan");
        result.put("plan", Map.of("goal", "NPS SCADA"));
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                result,
                List.of(toolStep("list_objects")),
                "Создай SCADA насосную станцию",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.ALLOW_PLAN);
    }

    @Test
    void askModeBlocksMutations() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.ASK);
        AgentPlanGuard.beginTurn(state, "Создай устройство", AgentProfile.ADMIN);
        var block = AgentPlanGuard.checkBeforeTool(state, "set_variable", AgentProfile.ADMIN);
        assertThat(block).isPresent();
    }

    @Test
    void executeModeSkipsPlanningGate() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.EXECUTE);
        AgentPlanGuard.beginTurn(state, "Создай SCADA насосную станцию", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.NONE);
        var block = AgentPlanGuard.checkBeforeTool(state, "create_object", AgentProfile.ADMIN);
        assertThat(block).isEmpty();
    }

    @Test
    void awaitingApprovalIsNotResetToPlanning() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.AUTO);
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        AgentPlanGuard.beginTurn(state, "Уточни: какой насос использовать?", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.AWAITING_APPROVAL);
    }

    @Test
    void shortAffirmativeApprovesPlanWhenAwaiting() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        AgentPlanGuard.beginTurn(state, "да", AgentProfile.ADMIN);
        assertThat(state.isPlanApproved()).isTrue();
    }

    @Test
    void bareWorkflowKeywordDoesNotForcePlanning() {
        assertThat(AgentPlanGuard.requiresPlanning("get_automation_schema workflow")).isFalse();
    }

    @Test
    void createWorkflowKeywordForcesPlanning() {
        assertThat(AgentPlanGuard.requiresPlanning("Создай workflow для симуляции гидроудара")).isTrue();
    }

    @Test
    void capturePlanMergesNewStepsIntoExistingDraft() {
        AgentRunState state = new AgentRunState();
        state.setStoredPlan(Map.of(
                "goal", "Pump station MVP",
                "steps", List.of("1. list_objects parentPath=root.platform.devices")
        ));
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put("phase", "plan");
        finish.put("plan", Map.of(
                "steps", List.of("2. list_virtual_profiles — pick pump profile")
        ));
        AgentPlanGuard.capturePlan(state, finish);

        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) ((Map<String, Object>) finish.get("plan")).get("steps");
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0)).contains("list_objects");
        assertThat(steps.get(1)).contains("list_virtual_profiles");
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.AWAITING_APPROVAL);
    }

    @Test
    void capturePlanAcceptsSupersetWhenIncomingStartsWithExistingPrefix() {
        AgentRunState state = new AgentRunState();
        state.setStoredPlan(Map.of(
                "goal", "Pump station MVP",
                "steps", List.of(
                        "1. list_objects parentPath=root.platform.devices",
                        "2. list_virtual_profiles"
                )
        ));
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put("phase", "plan");
        finish.put("plan", Map.of(
                "steps", List.of(
                        "1. list_objects parentPath=root.platform.devices",
                        "2. list_virtual_profiles",
                        "3. create_virtual_device for MNA-1"
                )
        ));
        AgentPlanGuard.capturePlan(state, finish);

        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) ((Map<String, Object>) finish.get("plan")).get("steps");
        assertThat(steps).hasSize(3);
        assertThat(steps.get(2)).contains("create_virtual_device");
    }

    @Test
    void mergePlansUnionsLayersWithoutDuplicates() {
        Map<String, Object> merged = AgentPlanGuard.mergePlans(
                Map.of("layers", List.of("devices", "mimic")),
                Map.of("layers", List.of("mimic", "dashboard"))
        );
        @SuppressWarnings("unchecked")
        List<String> layers = (List<String>) merged.get("layers");
        assertThat(layers).containsExactly("devices", "mimic", "dashboard");
    }

    @Test
    void planningContinuationHintAfterDiscovery() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.PLANNING);
        List<Map<String, Object>> steps = List.of(
                toolStep("get_automation_schema"),
                toolStep("list_objects")
        );
        String hint = AgentPlanGuard.planningContinuationHint(state, steps, "list_objects");
        assertThat(hint).contains("phase=plan");
        assertThat(hint).contains("EXTEND");
    }

    private static Map<String, Object> toolStep(String tool) {
        return Map.of("type", "tool", "tool", tool);
    }
}
