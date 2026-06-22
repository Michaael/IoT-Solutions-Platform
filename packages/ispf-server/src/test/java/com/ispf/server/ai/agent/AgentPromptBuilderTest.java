package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptBuilderTest {

    @Test
    void buildsPromptWithoutFormatPlaceholders() {
        String prompt = AgentPromptBuilder.build("root", List.of(
                Map.of("name", "list_objects", "description", "List children")
        ), "### Drivers\n- snmp | SNMP\n");
        assertTrue(prompt.contains("list_objects"));
        assertTrue(prompt.contains("Platform knowledge"));
        assertTrue(prompt.contains("snmp"));
        assertFalse(prompt.contains("%s"));
    }
}
