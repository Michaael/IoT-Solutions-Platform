package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopGuardTest {

    @Test
    void warnsOnRepeatedSearchContext() {
        List<Map<String, Object>> steps = List.of(
                step("search_context"),
                step("search_context"),
                step("search_context")
        );
        String hint = AgentLoopGuard.continuationHint("search_context", steps, 96);
        assertTrue(hint.toLowerCase().contains("search_context"));
    }

    @Test
    void warnsNearStepBudget() {
        List<Map<String, Object>> steps = List.of(
                step("list_objects"),
                step("get_object"),
                step("set_variable"),
                step("list_variables"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget")
        );
        String hint = AgentLoopGuard.continuationHint("add_dashboard_widget", steps, 18);
        assertTrue(hint.contains("finish"));
    }

    @Test
    void hintsListObjectsAfterGroundTruthBlock() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of("parentPath", "root.platform.mimics", "name", "hmi"),
                "result", Map.of(
                        "status", "ERROR",
                        "error", "Cannot create under hmi: parent path was not discovered in this turn: root.platform.mimics"
                )
        ));
        String hint = AgentLoopGuard.continuationHint("create_object", steps, 96);
        assertTrue(hint.contains("list_objects parent=root.platform.mimics"));
    }

    @Test
    void hintsDiscoveryAfterRecipeSearch() {
        String hint = AgentLoopGuard.continuationHint("search_platform_recipes", List.of(step("search_platform_recipes")), 96);
        assertTrue(hint.contains("list_objects"));
        assertTrue(hint.contains("not live tree"));
    }

    @Test
    void hintsDiscoveryAfterAutomationSchema() {
        String hint = AgentLoopGuard.continuationHint("get_automation_schema", List.of(step("get_automation_schema")), 96);
        assertTrue(hint.contains("list_relative_models"));
    }

    @Test
    void hintsDrillDownAfterListRoot() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "list_objects",
                "arguments", Map.of("parent", "root"),
                "result", Map.of("status", "OK", "parent", "root", "objects", List.of())
        ));
        String hint = AgentLoopGuard.continuationHint("list_objects", steps, 96);
        assertTrue(hint.contains("root.platform"));
    }

    @Test
    void hintsWorkflowOrderAfterSaveBpmnNotFound() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "save_workflow_bpmn",
                "arguments", Map.of("path", "root.platform.workflows.hydraulic-shock"),
                "result", Map.of("status", "ERROR", "error", "Object not found: root.platform.workflows.hydraulic-shock")
        ));
        String hint = AgentLoopGuard.continuationHint("save_workflow_bpmn", steps, 96);
        assertTrue(hint.contains("create_object"));
    }

    private static Map<String, Object> step(String tool) {
        return Map.of("type", "tool", "tool", tool);
    }
}
