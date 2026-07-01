package com.ispf.server.ai.api;

import com.ispf.server.ai.agent.AgentAttachmentValidator;
import com.ispf.server.ai.agent.AgentInteractionMode;
import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentSessionStore;
import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.ai.agent.TreeFirstAgentService;
import com.ispf.server.ai.generation.AiBundleGenerationService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleManifestJsonSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final long AGENT_PROGRESS_SSE_TIMEOUT_MS = 2 * 60 * 60 * 1000L;
    private static final long AGENT_PROGRESS_SSE_POLL_MS = 500L;

    private final ScheduledExecutorService progressStreamScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ai-agent-progress-sse");
        thread.setDaemon(true);
        return thread;
    });

    private final AiToolRegistry toolRegistry;
    private final LlmProviderRegistry llmProviderRegistry;
    private final AiBundleGenerationService generationService;
    private final TreeFirstAgentService agentService;
    private final AgentSessionStore agentSessionStore;
    private final PlatformAgentToolRegistry agentToolRegistry;
    private final ObjectMapper objectMapper;
    private final AgentAttachmentValidator attachmentValidator;

    public AiController(
            AiToolRegistry toolRegistry,
            LlmProviderRegistry llmProviderRegistry,
            AiBundleGenerationService generationService,
            TreeFirstAgentService agentService,
            AgentSessionStore agentSessionStore,
            PlatformAgentToolRegistry agentToolRegistry,
            ObjectMapper objectMapper,
            AgentAttachmentValidator attachmentValidator
    ) {
        this.toolRegistry = toolRegistry;
        this.llmProviderRegistry = llmProviderRegistry;
        this.generationService = generationService;
        this.agentService = agentService;
        this.agentSessionStore = agentSessionStore;
        this.agentToolRegistry = agentToolRegistry;
        this.objectMapper = objectMapper;
        this.attachmentValidator = attachmentValidator;
    }

    @GetMapping("/models")
    public Map<String, Object> listModels(Authentication authentication) {
        return toolRegistry.listModels(actor(authentication));
    }

    @GetMapping("/provider")
    public Map<String, Object> providerStatus() {
        return llmProviderRegistry.status();
    }

    @GetMapping("/tools/context-pack")
    public Map<String, Object> contextPackInfo() {
        return toolRegistry.contextPackInfo();
    }

    @PostMapping("/tools/validate-bundle")
    public Map<String, Object> validateBundle(
            Authentication authentication,
            @Valid @RequestBody BundleToolRequest request
    ) {
        return toolRegistry.validateBundle(
                request.appId(),
                parseManifest(request.manifest()),
                actor(authentication)
        );
    }

    @PostMapping("/tools/dry-run-deploy")
    public Map<String, Object> dryRunDeploy(
            Authentication authentication,
            @Valid @RequestBody BundleToolRequest request
    ) {
        return toolRegistry.dryRunDeploy(
                request.appId(),
                parseManifest(request.manifest()),
                actor(authentication)
        );
    }

    @GetMapping("/agent/tools")
    public Map<String, Object> agentTools() {
        return Map.of("tools", agentToolRegistry.toolCatalog());
    }

    @PostMapping("/agent/sessions")
    public Map<String, Object> createAgentSession(
            Authentication authentication,
            @Valid @RequestBody(required = false) CreateAgentSessionRequest request
    ) {
        String rootPath = request != null ? request.rootPath() : null;
        AgentSession session = agentSessionStore.create(actor(authentication), rootPath);
        if (request != null && request.interactionMode() != null && !request.interactionMode().isBlank()) {
            session.runState().setInteractionMode(AgentInteractionMode.fromString(request.interactionMode()));
            agentSessionStore.persistState(session);
        }
        Map<String, Object> summary = new LinkedHashMap<>(session.toSummaryMap());
        summary.put("planState", session.runState().planStateSummary());
        return summary;
    }

    @GetMapping("/agent/sessions/{sessionId}")
    public Map<String, Object> getAgentSession(
            Authentication authentication,
            @PathVariable String sessionId
    ) {
        AgentSession session = agentSessionStore.require(sessionId, actor(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return session.toMap();
    }

    @GetMapping("/agent/sessions/{sessionId}/progress")
    public Map<String, Object> getAgentRunProgress(
            Authentication authentication,
            @PathVariable String sessionId
    ) {
        agentSessionStore.require(sessionId, actor(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return agentService.runProgress(sessionId);
    }

    @GetMapping(value = "/agent/sessions/{sessionId}/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAgentRunProgress(
            Authentication authentication,
            @PathVariable String sessionId
    ) {
        agentSessionStore.require(sessionId, actor(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

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

    @PostMapping("/agent/sessions/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> sendAgentMessage(
            Authentication authentication,
            @PathVariable String sessionId,
            @RequestBody AgentMessageRequest request,
            @RequestParam(name = "async", defaultValue = "false") boolean async
    ) {
        AgentSession session = agentSessionStore.require(sessionId, actor(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (request != null && request.rootPath() != null && !request.rootPath().isBlank()) {
            session.setRootPath(request.rootPath());
        }
        if (request != null && request.interactionMode() != null && !request.interactionMode().isBlank()) {
            session.runState().setInteractionMode(AgentInteractionMode.fromString(request.interactionMode()));
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        boolean hasAttachments = request.attachments() != null && !request.attachments().isEmpty();
        if ((request.message() == null || request.message().isBlank()) && !hasAttachments) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        List<AgentAttachmentValidator.AttachmentInput> attachmentInputs = mapAttachments(request.attachments());
        try {
            if (async) {
                agentService.submitRunTurn(
                        session,
                        request.message() != null ? request.message() : "",
                        attachmentInputs,
                        authentication,
                        actor(authentication),
                        request.interactionMode()
                );
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "status", "ACCEPTED",
                        "sessionId", sessionId,
                        "running", true
                ));
            }
            return ResponseEntity.ok(agentService.runTurn(
                    session,
                    request.message() != null ? request.message() : "",
                    attachmentInputs,
                    authentication,
                    actor(authentication),
                    request.interactionMode()
            ));
        } catch (ResponseStatusException ex) {
            throw ex;
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

    @PostMapping("/agent/sessions/{sessionId}/cancel")
    public Map<String, Object> cancelAgentRun(
            Authentication authentication,
            @PathVariable String sessionId
    ) {
        agentSessionStore.require(sessionId, actor(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return agentService.cancelRun(sessionId);
    }

    @DeleteMapping("/agent/sessions/{sessionId}")
    public Map<String, Object> deleteAgentSession(
            Authentication authentication,
            @PathVariable String sessionId
    ) {
        boolean deleted = agentSessionStore.delete(sessionId, actor(authentication));
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return Map.of("status", "OK", "sessionId", sessionId);
    }

    @PostMapping("/agent/run")
    public Map<String, Object> runAgent(
            Authentication authentication,
            @Valid @RequestBody AgentRunRequest request
    ) {
        try {
            return agentService.run(
                    request.goal(),
                    request.rootPath(),
                    authentication,
                    actor(authentication)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/bundles/generate")
    public Map<String, Object> generateBundle(
            Authentication authentication,
            @Valid @RequestBody GenerateBundleRequest request
    ) {
        try {
            return generationService.generate(
                    request.appId(),
                    request.prompt(),
                    request.baseManifest() != null
                            ? parseManifest(request.baseManifest())
                            : null,
                    actor(authentication)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private ApplicationBundleDeployService.BundleManifest parseManifest(Map<String, Object> raw) {
        try {
            return BundleManifestJsonSupport.parse(objectMapper, raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private static String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "anonymous";
    }

    public record BundleToolRequest(
            @NotBlank String appId,
            @Valid Map<String, Object> manifest
    ) {
    }

    private static List<AgentAttachmentValidator.AttachmentInput> mapAttachments(
            List<AgentAttachmentRequest> attachments
    ) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<AgentAttachmentValidator.AttachmentInput> mapped = new ArrayList<>();
        for (AgentAttachmentRequest attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            mapped.add(new AgentAttachmentValidator.AttachmentInput(
                    attachment.name(),
                    attachment.mimeType(),
                    attachment.contentBase64()
            ));
        }
        return mapped;
    }

    public record GenerateBundleRequest(
            @NotBlank String appId,
            @NotBlank String prompt,
            Map<String, Object> baseManifest
    ) {
    }

    public record AgentRunRequest(
            @NotBlank String goal,
            String rootPath
    ) {
    }

    public record CreateAgentSessionRequest(
            String rootPath,
            String interactionMode
    ) {
    }

    public record AgentAttachmentRequest(
            String name,
            String mimeType,
            String contentBase64
    ) {
    }

    public record AgentMessageRequest(
            String message,
            String rootPath,
            String interactionMode,
            List<AgentAttachmentRequest> attachments
    ) {
    }
}
