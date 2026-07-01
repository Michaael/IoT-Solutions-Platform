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
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.NONE);
        var block = AgentPlanGuard.checkBeforeTool(state, "set_variable", AgentProfile.ADMIN);
        assertThat(block).isPresent();
    }

    @Test
    void askModeBlocksPlanFinish() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.ASK);
        AgentPlanGuard.beginTurn(state, "Какие устройства и дашборды есть?", AgentProfile.ADMIN);
        Map<String, Object> finish = Map.of(
                "phase", "plan",
                "plan", Map.of("goal", "Demo", "steps", List.of("create_virtual_device")),
                "questions", List.of(Map.of("id", "q1", "text", "Какой сценарий?"))
        );
        var outcome = AgentPlanGuard.evaluateFinish(
                state, finish, List.of(), "Какие устройства и дашборды есть?", AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN);
        assertThat(AgentPlanGuard.shouldCapturePlan(state, finish)).isFalse();
    }

    @Test
    void askModeAllowsPlainAnswerFinish() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.ASK);
        AgentPlanGuard.beginTurn(state, "Какие устройства есть?", AgentProfile.ADMIN);
        Map<String, Object> finish = Map.of("interactive", true);
        var outcome = AgentPlanGuard.evaluateFinish(
                state, finish, List.of(), "Какие устройства есть?", AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.ALLOW_EXECUTION);
    }

    @Test
    void executeModeBlocksPlanFinish() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.EXECUTE);
        AgentPlanGuard.beginTurn(state, "Создай SNMP демо", AgentProfile.ADMIN);
        Map<String, Object> finish = Map.of(
                "phase", "plan",
                "plan", Map.of("goal", "SNMP demo", "steps", List.of("create_virtual_device")),
                "questions", List.of(Map.of("id", "q1", "text", "Какой сценарий?"))
        );
        var outcome = AgentPlanGuard.evaluateFinish(
                state, finish, List.of(), "Создай SNMP демо", AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN);
    }

    @Test
    void executeModeSkipsPlanningGateForComplexTask() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.EXECUTE);
        AgentPlanGuard.beginTurn(state, "Создай SCADA насосную станцию", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.NONE);
        Map<String, Object> finish = Map.of("devicePath", "root.platform.devices.pump-01");
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                finish,
                List.of(Map.of("type", "tool", "tool", "create_object")),
                "Создай SCADA насосную станцию",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.ALLOW_EXECUTION);
    }

    @Test
    void planModeDoesNotPlanForReadOnlyQuestion() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.PLAN);
        AgentPlanGuard.beginTurn(state, "Какие устройства и дашборды есть?", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.NONE);
    }

    @Test
    void planModePlansForMutationTask() {
        AgentRunState state = new AgentRunState();
        state.setInteractionMode(AgentInteractionMode.PLAN);
        AgentPlanGuard.beginTurn(state, "Создай насосную станцию", AgentProfile.ADMIN);
        assertThat(state.planPhase()).isEqualTo(AgentPlanPhase.PLANNING);
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
                "goal", "Pump station full TZ",
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
                "goal", "Pump station full TZ",
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
    void shouldCapturePlanWhenInteractiveRefinementWithoutPlanKey() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        state.setStoredPlan(Map.of(
                "goal", "Pump station full TZ",
                "steps", List.of("1. Create pumps", "2. SCADA mimic")
        ));
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put("interactive", true);
        finish.put("suggestions", List.of(Map.of("label", "Approve", "message", "Go")));

        assertThat(AgentPlanGuard.shouldCapturePlan(state, finish)).isTrue();
    }

    @Test
    void capturePlanWithoutPlanKeyPreservesStoredDraft() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        state.setStoredPlan(Map.of(
                "goal", "Pump station full TZ",
                "steps", List.of("1. Create pumps", "2. SCADA mimic")
        ));
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put("interactive", true);
        finish.put("suggestions", List.of(Map.of("label", "Approve", "message", "Go")));

        AgentPlanGuard.capturePlan(state, finish);

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) finish.get("plan");
        assertThat(plan.get("goal")).isEqualTo("Pump station full TZ");
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) plan.get("steps");
        assertThat(steps).hasSize(2);
        assertThat(finish.get("phase")).isEqualTo("plan");
        assertThat(finish.get("interactive")).isEqualTo(true);
    }

    @Test
    void capturePlanCoercesMapShapedSteps() {
        AgentRunState state = new AgentRunState();
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put("phase", "plan");
        finish.put("plan", Map.of(
                "goal", "Tank farm",
                "steps", List.of(
                        Map.of("text", "Create tank-01"),
                        Map.of("description", "Add level sensor")
                )
        ));
        AgentPlanGuard.capturePlan(state, finish);

        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) ((Map<String, Object>) finish.get("plan")).get("steps");
        assertThat(steps).containsExactly("Create tank-01", "Add level sensor");
    }

    @Test
    void mergePlansReturnsExistingWhenIncomingEmpty() {
        Map<String, Object> existing = Map.of(
                "goal", "Pump station",
                "steps", List.of("1. list_objects")
        );
        Map<String, Object> merged = AgentPlanGuard.mergePlans(existing, Map.of());
        assertThat(merged.get("goal")).isEqualTo("Pump station");
        @SuppressWarnings("unchecked")
        List<String> steps = (List<String>) merged.get("steps");
        assertThat(steps).hasSize(1);
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

    @Test
    void blocksInteractiveFinishWithoutStructuredPlanDuringPlanning() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.PLANNING);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interactive", true);
        result.put("suggestions", List.of(Map.of("label", "Да, начинаем", "message", "Да, начинаем")));
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                result,
                List.of(),
                "Создай цифровой двойник насосной станции",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN);
    }

    @Test
    void allowsInteractiveFinishWhenStoredPlanExists() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        state.setStoredPlan(Map.of(
                "goal", "Pump station full TZ",
                "steps", List.of("1. Create pumps", "2. SCADA mimic")
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interactive", true);
        result.put("suggestions", List.of(Map.of("label", "Approve", "message", "Go")));
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                result,
                List.of(),
                "Покажи структуру плана",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.ALLOW_PLAN);
    }

    @Test
    void blocksEmptyPlanFinishDuringPlanning() {
        AgentRunState state = new AgentRunState();
        state.setPlanPhase(AgentPlanPhase.PLANNING);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", "plan");
        result.put("plan", Map.of());
        var outcome = AgentPlanGuard.evaluateFinish(
                state,
                result,
                List.of(toolStep("list_objects")),
                "Создай SCADA насосную станцию",
                AgentProfile.ADMIN
        );
        assertThat(outcome).isEqualTo(AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN);
    }

    @Test
    void mergePlansMergesSectionsByIdAndSyncsFlatSteps() {
        Map<String, Object> existing = Map.of(
                "goal", "Pump station full TZ",
                "sections", List.of(Map.of(
                        "id", "source_layer",
                        "title", "4. Devices",
                        "summary", "Create virtual devices.",
                        "steps", List.of("list_objects parentPath=root.platform.devices")
                ))
        );
        Map<String, Object> incoming = Map.of(
                "sections", List.of(Map.of(
                        "id", "source_layer",
                        "title", "4. Источники данных (DEVICE)",
                        "summary", "Создать виртуальные устройства по specBrief с list_variables.",
                        "steps", List.of(
                                "list_objects parentPath=root.platform.devices",
                                "create_virtual_device profile=lab name=nm-1"
                        )
                ), Map.of(
                        "id", "operator_layer",
                        "title", "8. Operator HMI",
                        "steps", List.of("save_mimic_diagram", "set_dashboard_layout")
                ))
        );
        Map<String, Object> merged = AgentPlanGuard.mergePlans(existing, incoming);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) merged.get("sections");
        assertThat(sections).hasSize(2);
        assertThat(sections.getFirst().get("title")).isEqualTo("4. Источники данных (DEVICE)");
        @SuppressWarnings("unchecked")
        List<String> sourceSteps = (List<String>) sections.getFirst().get("steps");
        assertThat(sourceSteps).hasSize(2);
        @SuppressWarnings("unchecked")
        List<String> flatSteps = (List<String>) merged.get("steps");
        assertThat(flatSteps.size()).isGreaterThanOrEqualTo(3);
    }

    private static Map<String, Object> toolStep(String tool) {
        return Map.of("type", "tool", "tool", tool);
    }
}
