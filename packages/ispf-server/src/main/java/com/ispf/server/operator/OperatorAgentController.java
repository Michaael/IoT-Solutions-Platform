package com.ispf.server.operator;

import com.ispf.server.ai.agent.AgentProfile;
import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentSessionStore;
import com.ispf.server.ai.agent.OperatorAgentScope;
import com.ispf.server.ai.agent.OperatorAgentScopeService;
import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.ai.agent.TreeFirstAgentService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Read-only AI copilot scoped to a single operator application.
 */
@RestController
@RequestMapping("/api/v1/operator-apps/{appId}/agent")
public class OperatorAgentController {

    private static final long AGENT_PROGRESS_SSE_TIMEOUT_MS = 2 * 60 * 60 * 1000L;
    private static final long AGENT_PROGRESS_SSE_POLL_MS = 500L;

    private final ScheduledExecutorService progressStreamScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "operator-agent-progress-sse");
        thread.setDaemon(true);
        return thread;
    });

    private final OperatorAgentScopeService scopeService;
    private final AgentSessionStore sessionStore;
    private final TreeFirstAgentService agentService;
    private final PlatformAgentToolRegistry toolRegistry;
    private final LlmProviderRegistry llmProviderRegistry;
    private final OperatorAgentMemoryService memoryService;
    private final OperatorAppDocumentService documentService;

    public OperatorAgentController(
            OperatorAgentScopeService scopeService,
            AgentSessionStore sessionStore,
            TreeFirstAgentService agentService,
            PlatformAgentToolRegistry toolRegistry,
            LlmProviderRegistry llmProviderRegistry,
            OperatorAgentMemoryService memoryService,
            OperatorAppDocumentService documentService
    ) {
        this.scopeService = scopeService;
        this.sessionStore = sessionStore;
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
        this.llmProviderRegistry = llmProviderRegistry;
        this.memoryService = memoryService;
        this.documentService = documentService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@PathVariable String appId) throws Exception {
        OperatorAgentScope scope = scopeService.resolve(appId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", scope.appId());
        result.put("title", scope.title());
        result.put("pathPrefixes", scope.pathPrefixes());
        result.put("provider", llmProviderRegistry.status());
        result.put("tools", toolRegistry.toolCatalog(AgentProfile.OPERATOR));
        result.put("agentProfile", AgentProfile.OPERATOR.storageValue());
        result.put("memoryCount", memoryService.count(scope.appId()));
        result.put("documentCount", documentService.count(scope.appId()));
        return result;
    }

    @PostMapping("/sessions")
    public Map<String, Object> createSession(
            Authentication authentication,
            @PathVariable String appId
    ) throws Exception {
        OperatorAgentScope scope = scopeService.resolve(appId);
        AgentSession session = sessionStore.createOperator(actor(authentication), scope);
        Map<String, Object> map = new LinkedHashMap<>(session.toSummaryMap());
        map.put("agentProfile", AgentProfile.OPERATOR.storageValue());
        map.put("operatorAppId", scope.appId());
        return map;
    }

    @GetMapping("/sessions/{sessionId}/progress")
    public Map<String, Object> progress(
            Authentication authentication,
            @PathVariable String appId,
            @PathVariable String sessionId
    ) {
        requireOperatorSession(sessionId, appId, actor(authentication));
        return agentService.runProgress(sessionId);
    }

    @GetMapping(value = "/sessions/{sessionId}/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(
            Authentication authentication,
            @PathVariable String appId,
            @PathVariable String sessionId
    ) {
        requireOperatorSession(sessionId, appId, actor(authentication));
        SseEmitter emitter = new SseEmitter(AGENT_PROGRESS_SSE_TIMEOUT_MS);
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> pollTaskRef = new AtomicReference<>();

        Runnable stopPolling = () -> {
            ScheduledFuture<?> task = pollTaskRef.get();
            if (task != null) {
                task.cancel(false);
            }
        };

        ScheduledFuture<?> pollTask = progressStreamScheduler.scheduleAtFixedRate(() -> {
            if (finished.get()) {
                return;
            }
            try {
                Map<String, Object> progress = agentService.runProgress(sessionId);
                emitter.send(SseEmitter.event().name("progress").data(progress, MediaType.APPLICATION_JSON));
                if (!Boolean.TRUE.equals(progress.get("running"))) {
                    finished.set(true);
                    stopPolling.run();
                    emitter.complete();
                }
            } catch (Exception ex) {
                finished.set(true);
                stopPolling.run();
                emitter.completeWithError(ex);
            }
        }, 0, AGENT_PROGRESS_SSE_POLL_MS, TimeUnit.MILLISECONDS);
        pollTaskRef.set(pollTask);

        emitter.onCompletion(() -> {
            finished.set(true);
            stopPolling.run();
        });
        emitter.onTimeout(() -> {
            finished.set(true);
            stopPolling.run();
        });
        emitter.onError(ex -> {
            finished.set(true);
            stopPolling.run();
        });
        return emitter;
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            Authentication authentication,
            @PathVariable String appId,
            @PathVariable String sessionId,
            @RequestBody OperatorAgentMessageRequest request,
            @RequestParam(name = "async", defaultValue = "false") boolean async
    ) {
        AgentSession session = requireOperatorSession(sessionId, appId, actor(authentication));
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        try {
            if (async) {
                agentService.submitOperatorTurn(
                        session,
                        appId,
                        request.message(),
                        authentication,
                        actor(authentication)
                );
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "status", "ACCEPTED",
                        "sessionId", sessionId,
                        "running", true
                ));
            }
            return ResponseEntity.ok(agentService.runOperatorTurn(
                    session,
                    appId,
                    request.message(),
                    authentication,
                    actor(authentication)
            ));
        } catch (IllegalStateException ex) {
            if (async && ex.getMessage() != null && ex.getMessage().contains("already in progress")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    public Map<String, Object> cancel(
            Authentication authentication,
            @PathVariable String appId,
            @PathVariable String sessionId
    ) {
        requireOperatorSession(sessionId, appId, actor(authentication));
        return agentService.cancelRun(sessionId);
    }

    private AgentSession requireOperatorSession(String sessionId, String appId, String actor) {
        AgentSession session = sessionStore.require(sessionId, actor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (session.runState().agentProfile() != AgentProfile.OPERATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not an operator agent session");
        }
        String bound = session.runState().operatorAppId();
        if (bound != null && !bound.equals(appId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session belongs to another operator app");
        }
        return session;
    }

    private static String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "anonymous";
    }

    public record OperatorAgentMessageRequest(@NotBlank String message) {
    }
}
