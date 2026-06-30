package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
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
    private final AgentRunCancellationRegistry cancellationRegistry;
    private final OperatorAgentScopeService operatorScopeService;
    private final OperatorAgentMemoryService operatorMemoryService;
    private final OperatorAgentMemoryLearner operatorMemoryLearner;
    private final OperatorAppDocumentService operatorDocumentService;
    private final OperatorAppUiService operatorAppUiService;
    private final OperatorAgentResultEnricher operatorResultEnricher;

    public TreeFirstAgentService(
            LlmProviderRegistry llmProviderRegistry,
            PlatformAgentToolRegistry toolRegistry,
            ContextPackService contextPackService,
            PlatformBriefingService platformBriefingService,
            AiToolAuditService auditService,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AgentSessionStore sessionStore,
            AgentRunCancellationRegistry cancellationRegistry,
            OperatorAgentScopeService operatorScopeService,
            OperatorAgentMemoryService operatorMemoryService,
            OperatorAgentMemoryLearner operatorMemoryLearner,
            OperatorAppDocumentService operatorDocumentService,
            OperatorAppUiService operatorAppUiService,
            OperatorAgentResultEnricher operatorResultEnricher
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.toolRegistry = toolRegistry;
        this.contextPackService = contextPackService;
        this.platformBriefingService = platformBriefingService;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.sessionStore = sessionStore;
        this.cancellationRegistry = cancellationRegistry;
        this.operatorScopeService = operatorScopeService;
        this.operatorMemoryService = operatorMemoryService;
        this.operatorMemoryLearner = operatorMemoryLearner;
        this.operatorDocumentService = operatorDocumentService;
        this.operatorAppUiService = operatorAppUiService;
        this.operatorResultEnricher = operatorResultEnricher;
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
        session.runState().clearPending();

        String userMessage = message.trim();
        AgentProfile profile = session.runState().agentProfile();
        OperatorAgentScope operatorScope = resolveOperatorScope(session, profile);
        AgentContext context = new AgentContext(actor, authentication, session.runState(), profile, operatorScope);
        List<Map<String, Object>> steps = new ArrayList<>();
        List<LlmMessage> messages = buildMessagesWithHistory(session, userMessage, profile, operatorScope);

        int maxStepsTotal = Math.max(1, aiProperties.getAgentMaxSteps());

        String finishSummary = null;
        Map<String, Object> finishResult = Map.of();
        String finalStatus = AgentTurnStatus.ERROR;

        try (AgentRunCancellationRegistry.RunHandle run = cancellationRegistry.start(
                session.sessionId(),
                userMessage
        )) {
            while (steps.size() < maxStepsTotal) {
                if (cancellationRegistry.isCancelled(session.sessionId())) {
                    finalStatus = AgentTurnStatus.CANCELLED;
                    finishSummary = "Выполнение остановлено пользователем после "
                            + steps.size() + " шаг(ов).";
                    break;
                }

                int stepNumber = steps.size() + 1;

                AgentLlmActionResolver.ParseAttempt parsed = AgentLlmActionResolver.resolve(
                        objectMapper,
                        llmProviderRegistry,
                        messages,
                        this::buildAgentLlmRequest,
                        aiProperties.getAgentParseRetries(),
                        profile == AgentProfile.OPERATOR
                );
                LlmResponse response = parsed.response();
                if (parsed.failed() || parsed.action() == null) {
                    finishSummary = """
                            Не удалось разобрать ответ модели после нескольких попыток. \
                            Попробуйте переформулировать запрос короче или начните новый чат.""";
                    finalStatus = AgentTurnStatus.ERROR;
                    Map<String, Object> errorStep = Map.of(
                            "step", stepNumber,
                            "type", "error",
                            "label", "Ошибка разбора ответа модели",
                            "error", parsed.error() != null ? parsed.error() : "parse failed",
                            "rawPreview", preview(response != null ? response.content() : null)
                    );
                    steps.add(errorStep);
                    publishStep(session.sessionId(), errorStep);
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

                AgentJsonProtocol.AgentAction agentAction = parsed.action();
                if ("finish".equals(agentAction.type())) {
                    finishSummary = agentAction.summary();
                    finishResult = agentAction.result() != null ? agentAction.result() : Map.of();
                    finalStatus = AgentTurnStatus.OK;
                    Map<String, Object> finishStep = Map.of(
                            "step", stepNumber,
                            "type", "finish",
                            "summary", finishSummary != null ? finishSummary : "",
                            "label", AgentStepHumanizer.label("finish", null, null, null, finishSummary),
                            "result", finishResult
                    );
                    steps.add(finishStep);
                    publishStep(session.sessionId(), finishStep);
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

                String toolName = agentAction.toolName();
                Map<String, Object> toolArgs = agentAction.arguments() != null ? agentAction.arguments() : Map.of();
                String executedTool = toolName;
                Map<String, Object> toolResult;
                if (profile == AgentProfile.OPERATOR) {
                    OperatorAgentTurnGuard.BlockDecision block = OperatorAgentTurnGuard.checkBeforeTool(
                            toolName,
                            toolArgs,
                            steps,
                            userMessage,
                            operatorScope
                    );
                    if (block.hasClarification()) {
                        if (applyClarificationFinish(
                                session, steps, stepNumber, userMessage, block.clarification(),
                                actor, response
                        )) {
                            finishSummary = block.clarification().summary();
                            finishResult = block.clarification().result();
                            finalStatus = AgentTurnStatus.OK;
                            break;
                        }
                    }
                    if (block.blocked() && "list_reports".equals(toolName)) {
                        var clarification = OperatorAgentClarificationBuilder.onRepeatListReports(steps, userMessage);
                        if (clarification.isPresent()) {
                            if (applyClarificationFinish(
                                    session, steps, stepNumber, userMessage, clarification.get(),
                                    actor, response
                            )) {
                                finishSummary = clarification.get().summary();
                                finishResult = clarification.get().result();
                                finalStatus = AgentTurnStatus.OK;
                                break;
                            }
                        }
                    }
                    if (block.blocked()) {
                        toolResult = new LinkedHashMap<>();
                        toolResult.put("status", "ERROR");
                        toolResult.put("error", block.error());
                        if (block.hint() != null) {
                            toolResult.put("hint", block.hint());
                        }
                        executedTool = toolName;
                    } else {
                        try {
                            toolResult = toolRegistry.execute(toolName, toolArgs, context);
                        } catch (Exception ex) {
                            toolResult = Map.of("status", "ERROR", "error", ex.getMessage());
                        }
                        if ("list_reports".equals(toolName)) {
                            toolResult = OperatorAgentTurnGuard.enrichListReportsResult(toolResult, userMessage);
                        }
                        executedTool = toolName;
                    }
                } else {
                    try {
                        toolResult = toolRegistry.execute(toolName, toolArgs, context);
                    } catch (Exception ex) {
                        toolResult = Map.of("status", "ERROR", "error", ex.getMessage());
                    }
                    if ("list_reports".equals(toolName)) {
                        toolResult = OperatorAgentTurnGuard.enrichListReportsResult(toolResult, userMessage);
                    }
                }

                Map<String, Object> toolStep = Map.of(
                        "step", stepNumber,
                        "type", "tool",
                        "tool", executedTool,
                        "label", AgentStepHumanizer.label("tool", executedTool, toolArgs, toolResult, null),
                        "arguments", toolArgs,
                        "result", toolResult
                );
                steps.add(toolStep);
                publishStep(session.sessionId(), toolStep);

                if ("list_reports".equals(executedTool)
                        && "OK".equals(String.valueOf(toolResult.get("status")))) {
                    var clarification = OperatorAgentClarificationBuilder.maybeAfterListReports(steps, userMessage);
                    if (clarification.isPresent()) {
                        if (applyClarificationFinish(
                                session, steps, stepNumber + 1, userMessage, clarification.get(),
                                actor, response
                        )) {
                            finishSummary = clarification.get().summary();
                            finishResult = clarification.get().result();
                            finalStatus = AgentTurnStatus.OK;
                            break;
                        }
                    }
                }

                auditService.record(
                        "agent_tool_" + executedTool,
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
                Map<String, Object> llmToolResult = AgentToolResultCompactor.compactForLlm(executedTool, toolResult);
                String toolResultJson = writeJson(llmToolResult);
                if (toolResultJson.length() > 14_000) {
                    toolResultJson = toolResultJson.substring(0, 13_999) + "…";
                }
                String continuation = profile == AgentProfile.OPERATOR
                        ? OperatorAgentTurnGuard.continuationHint(executedTool, steps, maxStepsTotal, userMessage)
                        : AgentLoopGuard.continuationHint(executedTool, steps, maxStepsTotal);
                messages.add(new LlmMessage(
                        "user",
                        "Tool result for " + executedTool + ":\n" + toolResultJson
                                + "\n\n" + continuation
                ));
            }
        }

        if (finishSummary == null && !AgentTurnStatus.CANCELLED.equals(finalStatus)) {
            finalStatus = AgentTurnStatus.ERROR;
            finishSummary = "Достигнут лимит " + maxStepsTotal
                    + " шагов без завершения задачи.";
        }

        return persistCompletedTurn(session, userMessage, finishSummary, finalStatus, steps, finishResult, actor, profile);
    }

    public Map<String, Object> cancelRun(String sessionId) {
        cancellationRegistry.cancel(sessionId);
        return Map.of("status", "OK", "sessionId", sessionId, "cancelRequested", true);
    }

    public Map<String, Object> runProgress(String sessionId) {
        return cancellationRegistry.progress(sessionId);
    }

    private void publishStep(String sessionId, Map<String, Object> step) {
        cancellationRegistry.recordStep(sessionId, step);
    }

    private Map<String, Object> persistCompletedTurn(
            AgentSession session,
            String userMessage,
            String finishSummary,
            String finalStatus,
            List<Map<String, Object>> steps,
            Map<String, Object> finishResult,
            String actor,
            AgentProfile profile
    ) {
        if (profile == AgentProfile.OPERATOR
                && session.runState().operatorAppId() != null
                && AgentTurnStatus.OK.equals(finalStatus)) {
            try {
                OperatorAgentScope scope = operatorScopeService.resolve(session.runState().operatorAppId());
                finishResult = operatorResultEnricher.enrich(
                        session.runState().operatorAppId(),
                        scope,
                        steps,
                        finishResult
                );
            } catch (Exception ignored) {
                // keep original finish result
            }
        }
        AgentTurn turn = AgentTurn.create(userMessage, finishSummary, finalStatus, steps, finishResult);
        session.addTurn(turn);
        sessionStore.persistAfterTurn(session, turn);
        if (profile == AgentProfile.OPERATOR
                && session.runState().operatorAppId() != null
                && AgentTurnStatus.OK.equals(finalStatus)) {
            operatorMemoryLearner.learnFromTurn(
                    session.runState().operatorAppId(),
                    userMessage,
                    finishSummary,
                    actor,
                    turn.turnId()
            );
        }
        return buildResponse(
                session,
                userMessage,
                finalStatus,
                finishSummary,
                steps,
                finishResult,
                turn.turnId(),
                steps.size(),
                aiProperties.getAgentMaxSteps(),
                profile
        );
    }

    public Map<String, Object> runOperatorTurn(
            AgentSession session,
            String operatorAppId,
            String message,
            Authentication authentication,
            String actor
    ) throws Exception {
        if (operatorAppId == null || operatorAppId.isBlank()) {
            throw new IllegalArgumentException("operatorAppId is required");
        }
        String appId = operatorAppId.trim();
        if (session.runState().agentProfile() != AgentProfile.OPERATOR) {
            throw new IllegalStateException("Session is not an operator agent session");
        }
        String boundApp = session.runState().operatorAppId();
        if (boundApp != null && !boundApp.equals(appId)) {
            throw new IllegalArgumentException("Session belongs to operator app: " + boundApp);
        }
        OperatorAgentScope scope = operatorScopeService.resolve(appId);
        session.runState().setOperatorAppId(appId);
        session.setRootPath(scope.briefingRoot());
        return runTurn(session, message, authentication, actor);
    }

    private OperatorAgentScope resolveOperatorScope(AgentSession session, AgentProfile profile) throws Exception {
        if (profile != AgentProfile.OPERATOR) {
            return null;
        }
        String appId = session.runState().operatorAppId();
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Operator session missing operatorAppId");
        }
        OperatorAgentScope scope = operatorScopeService.resolve(appId);
        session.setRootPath(scope.briefingRoot());
        return scope;
    }

    private Map<String, Object> buildResponse(
            AgentSession session,
            String userMessage,
            String finalStatus,
            String finishSummary,
            List<Map<String, Object>> steps,
            Map<String, Object> finishResult,
            String turnId,
            int stepsCompleted,
            int maxStepsTotal,
            AgentProfile profile
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", finalStatus);
        result.put("sessionId", session.sessionId());
        result.put("turnId", turnId);
        result.put("title", session.title());
        result.put("message", userMessage);
        result.put("rootPath", session.rootPath());
        result.put("steps", steps);
        result.put("summary", finishSummary);
        result.put("result", finishResult);
        result.put("tools", toolRegistry.toolCatalog(profile));
        result.put("provider", llmProviderRegistry.status());
        result.put("contextPackVersion", contextPackService.contextPackVersion());
        result.put("stepsCompleted", stepsCompleted);
        result.put("maxSteps", maxStepsTotal);
        result.put("running", cancellationRegistry.isRunning(session.sessionId()));
        if (profile == AgentProfile.OPERATOR) {
            result.put("agentProfile", profile.storageValue());
            result.put("operatorAppId", session.runState().operatorAppId());
        }
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

    private List<LlmMessage> buildMessagesWithHistory(
            AgentSession session,
            String userMessage,
            AgentProfile profile,
            OperatorAgentScope operatorScope
    ) throws Exception {
        List<LlmMessage> messages = new ArrayList<>();
        boolean includeStatic = aiProperties.isBriefingEveryTurn() || session.turns().isEmpty();
        String briefing = platformBriefingService.buildBriefing(session.rootPath(), includeStatic);
        String systemPrompt;
        if (profile == AgentProfile.OPERATOR && operatorScope != null) {
            String memorySection = operatorMemoryService.formatPromptSection(
                    operatorScope.appId(),
                    userMessage
            );
            String knowledgeSection = operatorDocumentService.formatPromptSection(
                    operatorScope.appId(),
                    userMessage,
                    operatorAppUiService.getAgentInstructions(operatorScope.appId())
            );
            systemPrompt = AgentOperatorPromptBuilder.build(
                    operatorScope,
                    toolRegistry.toolCatalog(profile),
                    briefing,
                    memorySection,
                    knowledgeSection
            );
        } else {
            systemPrompt = AgentPromptBuilder.build(
                    session.rootPath(),
                    toolRegistry.toolCatalog(profile),
                    briefing
            );
        }
        messages.add(new LlmMessage("system", systemPrompt));

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

    private boolean applyClarificationFinish(
            AgentSession session,
            List<Map<String, Object>> steps,
            int stepNumber,
            String userMessage,
            OperatorAgentClarificationBuilder.ClarificationFinish clarification,
            String actor,
            LlmResponse response
    ) {
        Map<String, Object> finishStep = Map.of(
                "step", stepNumber,
                "type", "finish",
                "summary", clarification.summary(),
                "label", AgentStepHumanizer.label("finish", null, null, null, clarification.summary()),
                "result", clarification.result(),
                "clarification", true
        );
        steps.add(finishStep);
        publishStep(session.sessionId(), finishStep);
        auditService.record(
                "agent_finish_clarify",
                session.sessionId(),
                actor,
                userMessage,
                AgentTurnStatus.OK,
                llmProviderRegistry.activeProvider().providerId(),
                response != null ? response.model() : null,
                contextPackService.contextPackVersion(),
                List.of()
        );
        return true;
    }
}
