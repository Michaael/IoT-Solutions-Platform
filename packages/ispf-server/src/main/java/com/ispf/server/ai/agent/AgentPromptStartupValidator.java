package com.ispf.server.ai.agent;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fails fast at startup if agent playbooks or system prompt cannot be built.
 */
@Component
class AgentPromptStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(AgentPromptStartupValidator.class);

    private final PlatformAgentToolRegistry toolRegistry;

    AgentPromptStartupValidator(PlatformAgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    void validatePromptAssembly() {
        List<Map<String, Object>> catalog = toolRegistry.toolCatalog();
        String prompt = AgentPromptBuilder.build("root", catalog, "");
        if (prompt.isBlank()) {
            throw new IllegalStateException("Agent system prompt is empty");
        }
        if (prompt.contains("%s")) {
            throw new IllegalStateException("Agent system prompt contains unresolved %s placeholder");
        }
        for (String playbook : List.of(
                AgentPlaybooks.snmpLocalhostMonitoring(),
                AgentPlaybooks.dashboardLayoutEditing(),
                AgentPlaybooks.snmpIfMibExtension()
        )) {
            if (playbook.contains("%s")) {
                throw new IllegalStateException("Agent playbook contains forbidden %s placeholder");
            }
        }
        log.info("Agent prompt validated ({} tools, {} chars)", catalog.size(), prompt.length());
    }
}
