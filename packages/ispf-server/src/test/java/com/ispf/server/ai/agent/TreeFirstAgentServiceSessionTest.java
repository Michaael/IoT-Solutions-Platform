package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmUsage;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.context.PlatformBriefingService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreeFirstAgentServiceSessionTest {

    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private PlatformAgentToolRegistry toolRegistry;
    @Mock
    private ContextPackService contextPackService;
    @Mock
    private PlatformBriefingService platformBriefingService;
    @Mock
    private AiToolAuditService auditService;
    @Mock
    private AgentSessionStore sessionStore;

    private TreeFirstAgentService agentService;
    private AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        aiProperties.setProvider("openai-compatible");
        aiProperties.setModel("test-model");
        aiProperties.setAgentMaxSteps(4);
        aiProperties.setAgentMaxHistoryTurns(20);

        agentService = new TreeFirstAgentService(
                llmProviderRegistry,
                toolRegistry,
                contextPackService,
                platformBriefingService,
                auditService,
                aiProperties,
                objectMapper,
                sessionStore
        );

        when(llmProviderRegistry.isGenerationAvailable()).thenReturn(true);
        when(toolRegistry.toolCatalog()).thenReturn(List.of());
        when(contextPackService.contextPackVersion()).thenReturn("test-pack");
        when(platformBriefingService.buildBriefing(any(), any(Boolean.class))).thenReturn("drivers: snmp, virtual");
        when(llmProviderRegistry.status()).thenReturn(Map.of("providerId", "test"));
        LlmProvider provider = org.mockito.Mockito.mock(LlmProvider.class);
        when(provider.providerId()).thenReturn("test");
        when(llmProviderRegistry.activeProvider()).thenReturn(provider);
    }

    @Test
    void agentRequestDisablesThinkingByDefault() throws Exception {
        when(llmProviderRegistry.complete(any())).thenReturn(new LlmResponse(
                "{\"type\":\"finish\",\"summary\":\"done\",\"result\":{}}",
                "test-model",
                new LlmUsage(1, 1, 2)
        ));
        AgentSession session = AgentSession.create("admin", "root");
        var auth = new UsernamePasswordAuthenticationToken("admin", "secret");
        agentService.runTurn(session, "hello", auth, "admin");

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmProviderRegistry).complete(captor.capture());
        assertEquals(
                false,
                ((Map<?, ?>) captor.getValue().providerOptions().get("chat_template_kwargs")).get("enable_thinking")
        );
    }

    @Test
    void secondTurnReplaysFirstTurnSummaryToLlm() throws Exception {
        when(llmProviderRegistry.complete(any())).thenReturn(new LlmResponse(
                "{\"type\":\"finish\",\"summary\":\"done\",\"result\":{}}",
                "test-model",
                new LlmUsage(1, 1, 2)
        ));

        AgentSession session = AgentSession.create("admin", "root");
        var auth = new UsernamePasswordAuthenticationToken("admin", "secret");

        agentService.runTurn(session, "first task", auth, "admin");
        agentService.runTurn(session, "second task", auth, "admin");

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmProviderRegistry, atLeastOnce()).complete(captor.capture());
        LlmRequest secondTurnRequest = captor.getAllValues().get(captor.getAllValues().size() - 1);
        List<LlmMessage> messages = secondTurnRequest.messages();

        assertTrue(messages.stream().anyMatch(m -> "user".equals(m.role()) && "first task".equals(m.content())));
        assertTrue(messages.stream().anyMatch(m -> "assistant".equals(m.role()) && "done".equals(m.content())));
        assertTrue(messages.stream().anyMatch(m -> "user".equals(m.role()) && "second task".equals(m.content())));
        assertEquals(2, session.turns().size());
    }

    @Test
    void retriesLlmWhenFirstResponseIsNotValidJson() throws Exception {
        when(llmProviderRegistry.complete(any()))
                .thenReturn(new LlmResponse(
                        "Sure, I will list devices for you.",
                        "test-model",
                        new LlmUsage(1, 1, 2)
                ))
                .thenReturn(new LlmResponse(
                        "{\"type\":\"finish\",\"summary\":\"devices listed\",\"result\":{}}",
                        "test-model",
                        new LlmUsage(1, 1, 2)
                ));

        AgentSession session = AgentSession.create("admin", "root");
        var auth = new UsernamePasswordAuthenticationToken("admin", "secret");
        Map<String, Object> result = agentService.runTurn(session, "list devices", auth, "admin");

        assertEquals("OK", result.get("status"));
        verify(llmProviderRegistry, org.mockito.Mockito.times(2)).complete(any());
    }

    @Test
    void returnsStructuredErrorWhenParseNeverSucceeds() throws Exception {
        aiProperties.setAgentParseRetries(2);
        when(llmProviderRegistry.complete(any())).thenReturn(new LlmResponse(
                "I will help you with that task in plain language.",
                "test-model",
                new LlmUsage(1, 1, 2)
        ));

        AgentSession session = AgentSession.create("admin", "root");
        var auth = new UsernamePasswordAuthenticationToken("admin", "secret");
        Map<String, Object> result = agentService.runTurn(session, "do something", auth, "admin");

        assertEquals("ERROR", result.get("status"));
        assertTrue(String.valueOf(result.get("summary")).contains("разобрать ответ модели"));
    }
}
