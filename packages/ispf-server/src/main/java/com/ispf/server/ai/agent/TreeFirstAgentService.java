package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.context.PlatformBriefingService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.validation.BundleValidationResult;
import com.ispf.server.config.AiProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TreeFirstAgentService {

    private static final int HISTORY_SUMMARY_MAX_LEN = 800;

    private final LlmProviderRegistry llmProviderRegistry;
    private final PlatformAgentToolRegistry toolRegistry;
    private final ContextPackService contextPackService;
    private final PlatformBriefingService platformBriefingService;
    private final AiToolAuditService auditService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AgentSessionStore sessionStore;

    public TreeFirstAgentService(
            LlmProviderRegistry llmProviderRegistry,
            PlatformAgentToolRegistry toolRegistry,
            ContextPackService contextPackService,
            PlatformBriefingService platformBriefingService,
            AiToolAuditService auditService,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AgentSessionStore sessionStore
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.toolRegistry = toolRegistry;
        this.contextPackService = contextPackService;
        this.platformBriefingService = platformBriefingService;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.sessionStore = sessionStore;
    }

    public Map<String, Object> run(String goal, String rootPath, Authentication authentication, String actor)
            throws Exception {
        AgentSession session = AgentSession.create(actor, rootPath);
        return runTurn(session, goal, authentication, actor);
    }

    public Map<String, Object> runTurn(
            AgentSession session,
            String message,
            Authentication authentication,
            String actor
    ) throws Exception {
        ensureLlmAvailable();

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }

        String userMessage = message.trim();
        AgentContext context = new AgentContext(actor, authentication, session.runState());
        List<Map<String, Object>> steps = new ArrayList<>();
        List<LlmMessage> messages = buildMessagesWithHistory(session, userMessage);

        String finishSummary = null;
        Map<String, Object> finishResult = Map.of();
        String finalStatus = BundleValidationResult.ERROR;
        int maxSteps = Math.max(1, aiProperties.getAgentMaxSteps());

        for (int step = 1; step <= maxSteps; step++) {
            AgentLlmActionResolver.ParseAttempt parsed = AgentLlmActionResolver.resolve(
                    objectMapper,
                    llmProviderRegistry,
                    messages,
                    this::buildAgentLlmRequest,
                    aiProperties.getAgentParseRetries()
            );
            LlmResponse response = parsed.response();
            if (parsed.failed() || parsed.action() == null) {
                finishSummary = """
                        Не удалось разобрать ответ модели после нескольких попыток. \
                        Попробуйте переформулировать запрос короче или начните новый чат.""";
                finalStatus = BundleValidationResult.ERROR;
                steps.add(Map.of(
                        "step", step,
                        "type", "error",
                        "label", "Ошибка разбора ответа модели",
                        "error", parsed.error() != null ? parsed.error() : "parse failed",
                        "rawPreview", preview(response != null ? response.content() : null)
                ));
                auditService.record(
                        "agent_parse_error",
                        session.sessionId(),
                        actor,
                        userMessage,
                        finalStatus,
                        llmProviderRegistry.activeProvider().providerId(),
                        response != null ? response.model() : null,
                        contextPackService.contextPackVersion(),
                        List.of(parsed.error() != null ? parsed.error() : "parse failed")
                );
                break;
            }

            AgentJsonProtocol.AgentAction action = parsed.action();
            if ("finish".equals(action.type())) {
                finishSummary = action.summary();
                finishResult = action.result() != null ? action.result() : Map.of();
                finalStatus = BundleValidationResult.OK;
                steps.add(Map.of(
                        "step", step,
                        "type", "finish",
                        "summary", finishSummary != null ? finishSummary : "",
                        "label", AgentStepHumanizer.label("finish", null, null, null, finishSummary),
                        "result", finishResult
                ));
                auditService.record(
                        "agent_finish",
                        session.sessionId(),
                        actor,
                        userMessage,
                        finalStatus,
                        llmProviderRegistry.activeProvider().providerId(),
                        response.model(),
                        contextPackService.contextPackVersion(),
                        List.of()
                );
                break;
            }

            String toolName = action.toolName();
            Map<String, Object> toolArgs = action.arguments() != null ? action.arguments() : Map.of();
            Map<String, Object> toolResult;
            try {
                toolResult = toolRegistry.execute(toolName, toolArgs, context);
            } catch (Exception ex) {
                toolResult = Map.of("status", "ERROR", "error", ex.getMessage());
            }

            steps.add(Map.of(
                    "step", step,
                    "type", "tool",
                    "tool", toolName,
                    "label", AgentStepHumanizer.label("tool", toolName, toolArgs, toolResult, null),
                    "arguments", toolArgs,
                    "result", toolResult
            ));

            auditService.record(
                    "agent_tool_" + toolName,
                    session.sessionId(),
                    actor,
                    writeJson(toolArgs),
                    String.valueOf(toolResult.getOrDefault("status", "UNKNOWN")),
                    llmProviderRegistry.activeProvider().providerId(),
                    response.model(),
                    contextPackService.contextPackVersion(),
                    toolResult.containsKey("errors") ? (List<String>) toolResult.get("errors") : List.of()
            );

            messages.add(new LlmMessage("assistant", response.content()));
            messages.add(new LlmMessage(
                    "user",
                    "Tool result for " + toolName + ":\n" + writeJson(toolResult)
                            + "\n\n" + AgentLoopGuard.continuationHint(toolName, steps, maxSteps)
            ));
        }

        if (finishSummary == null) {
            finalStatus = BundleValidationResult.ERROR;
            finishSummary = "Agent stopped after " + maxSteps + " steps without finish action";
        }

        AgentTurn turn = AgentTurn.create(userMessage, finishSummary, finalStatus, steps, finishResult);
        session.addTurn(turn);
        sessionStore.persistAfterTurn(session, turn);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", finalStatus);
        result.put("sessionId", session.sessionId());
        result.put("turnId", turn.turnId());
        result.put("title", session.title());
        result.put("message", userMessage);
        result.put("rootPath", session.rootPath());
        result.put("steps", steps);
        result.put("summary", finishSummary);
        result.put("result", finishResult);
        result.put("tools", toolRegistry.toolCatalog());
        result.put("provider", llmProviderRegistry.status());
        result.put("contextPackVersion", contextPackService.contextPackVersion());
        return result;
    }

    private LlmRequest buildAgentLlmRequest(List<LlmMessage> messages) {
        Map<String, Object> providerOptions = aiProperties.isAgentDisableThinking()
                ? Map.of("chat_template_kwargs", Map.of("enable_thinking", false))
                : Map.of();
        return new LlmRequest(
                aiProperties.getModel(),
                messages,
                aiProperties.getMaxTokens(),
                aiProperties.getTemperature(),
                providerOptions
        );
    }

    private List<LlmMessage> buildMessagesWithHistory(AgentSession session, String userMessage) {
        List<LlmMessage> messages = new ArrayList<>();
        boolean includeStatic = aiProperties.isBriefingEveryTurn() || session.turns().isEmpty();
        String briefing = platformBriefingService.buildBriefing(session.rootPath(), includeStatic);
        messages.add(new LlmMessage("system", AgentPromptBuilder.build(
                session.rootPath(),
                toolRegistry.toolCatalog(),
                briefing
        )));

        List<AgentTurn> history = session.turns();
        int maxTurns = Math.max(1, aiProperties.getAgentMaxHistoryTurns());
        int start = Math.max(0, history.size() - maxTurns);
        for (int i = start; i < history.size(); i++) {
            AgentTurn turn = history.get(i);
            messages.add(new LlmMessage("user", turn.userMessage()));
            messages.add(new LlmMessage("assistant", truncateForHistory(turn.assistantSummary())));
        }
        messages.add(new LlmMessage("user", userMessage));
        return messages;
    }

    private void ensureLlmAvailable() {
        if (!llmProviderRegistry.isGenerationAvailable()) {
            throw new IllegalStateException(
                    "LLM provider is not configured. Set ispf.ai.provider and base-url/model."
            );
        }
    }

    private static String truncateForHistory(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        String trimmed = summary.trim();
        if (trimmed.length() <= HISTORY_SUMMARY_MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, HISTORY_SUMMARY_MAX_LEN - 1) + "…";
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 237) + "...";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
