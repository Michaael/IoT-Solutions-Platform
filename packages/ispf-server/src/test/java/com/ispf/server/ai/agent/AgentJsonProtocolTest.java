package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentJsonProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesToolActionFromMarkdownWrappedJson() throws Exception {
        String content = """
                Here is my plan:
                ```json
                {"type":"tool","name":"list_objects","arguments":{"parent":"root.platform.devices"}}
                ```
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("list_objects", action.toolName());
        assertEquals("root.platform.devices", action.arguments().get("parent"));
    }

    @Test
    void parsesFinishAction() throws Exception {
        String content = """
                {"type":"finish","summary":"Created device lab-sensor-1","result":{"path":"root.platform.devices.lab-sensor-1"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
        assertEquals("Created device lab-sensor-1", action.summary());
        assertEquals("root.platform.devices.lab-sensor-1", action.result().get("path"));
        assertNull(action.toolName());
    }

    @Test
    void acceptsToolAliasField() throws Exception {
        String content = "{\"type\":\"tool\",\"tool\":\"search_context\",\"arguments\":{\"query\":\"DEVICE template\"}}";
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("search_context", action.toolName());
        assertEquals("DEVICE template", action.arguments().get("query"));
    }

    @Test
    void parsesJsonAfterRedactedThinkingBlock() throws Exception {
        String content = """
                Long reasoning about list_objects...
                </think>

                {"type":"tool","name":"list_objects","arguments":{"parent":"root.platform.devices"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("list_objects", action.toolName());
    }

    @Test
    void ignoresNestedObjectTypeInToolArguments() throws Exception {
        String content = """
                Let me create a dashboard.
                {"type":"tool","name":"create_object","arguments":{"parentPath":"root.platform.dashboards","name":"dash","type":"DASHBOARD","templateId":"dashboard-v1"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("create_object", action.toolName());
        assertEquals("DASHBOARD", action.arguments().get("type"));
    }

    @Test
    void parsesJsonEmbeddedInProseAfterThinking() throws Exception {
        String content = """
                I will list devices now.
                </think>

                Here are the devices... Actually:
                {"type":"tool","name":"list_objects","arguments":{"parent":"root.platform.devices","lite":true}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_objects", action.toolName());
    }

    @Test
    void parsesFunctionStyleToolCall() throws Exception {
        String content = """
                {"type":"function","name":"add_dashboard_widget","parameters":{"path":"root.platform.dashboards.snmp-host-monitoring","widget":{"id":"if-speed"}}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("tool", action.type());
        assertEquals("add_dashboard_widget", action.toolName());
        assertEquals("root.platform.dashboards.snmp-host-monitoring", action.arguments().get("path"));
    }

    @Test
    void parsesToolWithoutTypeField() throws Exception {
        String content = """
                {"name":"list_variables","arguments":{"path":"root.platform.devices.snmp-localhost"}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_variables", action.toolName());
    }

    @Test
    void parsesNestedFunctionObject() throws Exception {
        String content = """
                {"type":"tool","function":{"name":"set_variable","arguments":{"path":"x","name":"layout","value":"{}"}}}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("set_variable", action.toolName());
    }

    @Test
    void parsesActionFromJsonArray() throws Exception {
        String content = """
                [{"type":"tool","name":"get_dashboard_layout","arguments":{"path":"root.platform.dashboards.snmp-host-monitoring"}}]
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("get_dashboard_layout", action.toolName());
    }

    @Test
    void parsesActionEmbeddedInMessageWrapper() throws Exception {
        String content = """
                {"type":"message","content":"{\\"type\\":\\"tool\\",\\"name\\":\\"list_variables\\",\\"arguments\\":{\\"path\\":\\"root\\"}}"}
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("list_variables", action.toolName());
    }

    @Test
    void parsesPlainCodeFence() throws Exception {
        String content = """
                ```
                {"type":"finish","summary":"done","result":{}}
                ```
                """;
        AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, content);
        assertEquals("finish", action.type());
    }

    @Test
    void acceptsPlainTextFinishFallback() {
        var action = AgentJsonProtocol.tryParsePlainTextFinish(
                "Сменный отчёт: средняя температура 72.4°C, максимум 81°C за последние 8 часов."
        );
        assertTrue(action.isPresent());
        assertEquals("finish", action.get().type());
        assertTrue(action.get().summary().contains("72.4"));
    }

    @Test
    void salvagesTruncatedPlanFinish() {
        String truncated = """
                {"type":"finish","summary":"План НС","result":{"phase":"plan","interactive":true,"plan":{"goal":"Насосная станция","sections":[{"id":"ground_truth","title":"1. Discovery","summary":"Обнаружение","steps":["list_objects parent=root.platform.devices","get_automation_schema topic=projectBlueprint"]},{"id":"intent_scope","title":"2. Scope","summary":"Цель","steps":["FR mapping"
                """;
        var salvaged = AgentJsonProtocol.trySalvageTruncatedFinish(objectMapper, truncated);
        assertTrue(salvaged.isPresent());
        assertEquals("finish", salvaged.get().type());
        assertEquals("План НС", salvaged.get().summary());
        assertTrue(salvaged.get().result().containsKey("plan"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) salvaged.get().result().get("plan");
        assertEquals("Насосная станция", plan.get("goal"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) plan.get("sections");
        assertThat(sections).hasSize(1);
        assertEquals("ground_truth", sections.getFirst().get("id"));
    }

    @Test
    void detectsTruncatedJsonContent() {
        String truncated = """
                {"type":"finish","summary":"Plan","result":{"phase":"plan","plan":{"goal":"MVP","steps":["1. a","2. b
                """;
        assertTrue(AgentJsonProtocol.looksLikeTruncatedContent(truncated));
    }

    @Test
    void completeJsonIsNotMarkedTruncated() throws Exception {
        String content = """
                {"type":"finish","summary":"OK","result":{"phase":"plan","plan":{"goal":"MVP","steps":["1. list_objects"]}}}
                """;
        assertFalse(AgentJsonProtocol.looksLikeTruncatedContent(content));
        AgentJsonProtocol.parse(objectMapper, content);
    }
}
