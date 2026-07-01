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
import com.ispf.server.operator.OperatorAgentMemoryLearner;
import com.ispf.server.operator.OperatorAgentMemoryService;
import com.ispf.server.operator.OperatorAgentResultEnricher;
import com.ispf.server.operator.OperatorAppDocumentService;
import com.ispf.server.operator.OperatorAppUiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Mock
    private OperatorAgentScopeService operatorScopeService;
    @Mock
    private OperatorAgentMemoryService operatorMemoryService;
    @Mock
    private OperatorAgentMemoryLearner operatorMemoryLearner;
    @Mock
    private OperatorAppDocumentService operatorDocumentService;
    @Mock
    private OperatorAppUiService operatorAppUiService;
    @Mock
    private OperatorAgentResultEnricher operatorResultEnricher;
    @Mock
    private AgentAttachmentValidator attachmentValidator;

    private TreeFirstAgentService agentService;
    private AiProperties aiProperties;
    private AgentRunCancellationRegistry cancellationRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        aiProperties.setProvider("openai-compatible");
        aiProperties.setModel("test-model");
        aiProperties.setAgentMaxSteps(96);
        aiProperties.setAgentMaxHistoryTurns(20);
        cancellationRegistry = new AgentRunCancellationRegistry();

        agentService = new TreeFirstAgentService(
                llmProviderRegistry,
                toolRegistry,
                contextPackService,
                platformBriefingService,
                auditService,
                aiProperties,
                objectMapper,
                sessionStore,
                cancellationRegistry,
                operatorScopeService,
                operatorMemoryService,
                operatorMemoryLearner,
                operatorDocumentService,
                operatorAppUiService,
                operatorResultEnricher,
                attachmentValidator
        );

        lenient().when(attachmentValidator.prepare(anyString(), any())).thenAnswer(invocation -> {
            String message = invocation.getArgument(0);
            return new AgentAttachmentValidator.PreparedUserMessage(
                    message,
                    message,
                    List.of(),
                    List.of(),
                    false
            );
        });

        when(llmProviderRegistry.isGenerationAvailable()).thenReturn(true);
        when(toolRegistry.toolCatalog(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(contextPackService.contextPackVersion()).thenReturn("test-pack");
        when(platformBriefingService.buildBriefing(any(), any(Boolean.class))).thenReturn("drivers: snmp, virtual");
        when(llmProviderRegistry.status()).thenReturn(Map.of("providerId", "test"));
        LlmProvider provider = org.mockito.Mockito.mock(LlmProvider.class);
        lenient().when(provider.providerId()).thenReturn("test");
        lenient().when(llmProviderRegistry.activeProvider()).thenReturn(provider);
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

    @Test
    void runsManyToolStepsWithoutPausing() throws Exception {
        when(llmProviderRegistry.complete(any())).thenReturn(new LlmResponse(
                "{\"type\":\"tool\",\"name\":\"list_objects\",\"arguments\":{}}",
                "test-model",
                new LlmUsage(1, 1, 2)
        ));
        when(toolRegistry.execute(anyString(), any(), any())).thenReturn(Map.of("status", "OK"));

        aiProperties.setAgentMaxSteps(8);
        AgentSession session = AgentSession.create("admin", "root");
        var auth = new UsernamePasswordAuthenticationToken("admin", "secret");

        Map<String, Object> result = agentService.runTurn(session, "long task", auth, "admin");
        assertEquals(AgentTurnStatus.ERROR, result.get("status"));
        assertEquals(8, result.get("stepsCompleted"));
        assertEquals(1, session.turns().size());
        assertFalse(session.runState().hasPending());
    }

    @Test
    void exposesLiveProgressWhileRunning() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        when(llmProviderRegistry.complete(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() >= 3) {
                return new LlmResponse(
                        "{\"type\":\"finish\",\"summary\":\"done\",\"result\":{}}",
                        "test-model",
                        new LlmUsage(1, 1, 2)
                );
            }
            return new LlmResponse(
                    "{\"type\":\"tool\",\"name\":\"list_objects\",\"arguments\":{}}",
                    "test-model",
                    new LlmUsage(1, 1, 2)
            );
        });
        when(toolRegistry.execute(anyString(), any(), any())).thenReturn(Map.of("status", "OK"));

        AgentSession session = AgentSession.create("admin", "root");
        var auth = new UsernamePasswordAuthenticationToken("admin", "secret");
        agentService.runTurn(session, "progress task", auth, "admin");

        Map<String, Object> progress = agentService.runProgress(session.sessionId());
        assertFalse(Boolean.TRUE.equals(progress.get("running")));
    }

    @Test
    void cooperativeCancelStopsInFlightRun() throws Exception {
        AgentSession sessionForCancel = AgentSession.create("admin", "root");
        AtomicInteger calls = new AtomicInteger();
        when(llmProviderRegistry.complete(any())).thenAnswer(invocation -> {
            int n = calls.incrementAndGet();
            if (n == 2) {
                agentService.cancelRun(sessionForCancel.sessionId());
            }
            return new LlmResponse(
                    "{\"type\":\"tool\",\"name\":\"list_objects\",\"arguments\":{}}",
                    "test-model",
                    new LlmUsage(1, 1, 2)
            );
        });
        when(toolRegistry.execute(anyString(), any(), any())).thenReturn(Map.of("status", "OK"));

        var auth = new UsernamePasswordAuthenticationToken("admin", "secret");

        Map<String, Object> result = agentService.runTurn(sessionForCancel, "cancel me", auth, "admin");
        assertEquals(AgentTurnStatus.CANCELLED, result.get("status"));
        assertTrue(String.valueOf(result.get("summary")).contains("остановлено"));
        assertEquals(1, sessionForCancel.turns().size());
    }
}
