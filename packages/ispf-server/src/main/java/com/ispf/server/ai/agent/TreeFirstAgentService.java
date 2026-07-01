package com.ispf.server.ai.agent;

import com.ispf.ai.LlmContentPart;
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
import java.util.Optional;

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
    private final AgentAttachmentValidator attachmentValidator;

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
            OperatorAgentResultEnricher operatorResultEnricher,
            AgentAttachmentValidator attachmentValidator
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
        this.attachmentValidator = attachmentValidator;
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
        return runTurn(session, message, authentication, actor, null);
    }

    public Map<String, Object> runTurn(
            AgentSession session,
            String message,
            Authentication authentication,
            String actor,
            String interactionMode
    ) throws Exception {
        return runTurn(session, message, List.of(), authentication, actor, interactionMode);
    }

    public Map<String, Object> runTurn(
            AgentSession session,
            String message,
            List<AgentAttachmentValidator.AttachmentInput> attachments,
            Authentication authentication,
            String actor,
            String interactionMode
    ) throws Exception {
        ensureLlmAvailable();
        AgentAttachmentValidator.PreparedUserMessage prepared =
                attachmentValidator.prepare(message, attachments);
        if (prepared.llmText().isBlank() && prepared.imageParts().isEmpty()) {
            throw new IllegalArgumentException("message is required");
        }
        session.runState().clearPending();

        String userMessage = prepared.displayMessage();
        String llmUserText = prepared.llmText();
        if (interactionMode != null && !interactionMode.isBlank()) {
            session.runState().setInteractionMode(AgentInteractionMode.fromString(interactionMode));
        }
        AgentProfile profile = session.runState().agentProfile();
        AgentPlanGuard.beginTurn(session.runState(), llmUserText, profile);
        OperatorAgentScope operatorScope = resolveOperatorScope(session, profile);
        AgentContext context = new AgentContext(actor, authentication, session.runState(), profile, operatorScope);
        List<Map<String, Object>> steps = new ArrayList<>();
        List<LlmMessage> messages = buildMessagesWithHistory(
                session,
                prepared,
                profile,
                operatorScope
        );

        List<Map<String, Object>> attachmentMetadata = prepared.attachmentMetadata();

        int maxStepsTotal = Math.max(1, aiProperties.getAgentMaxSteps());

        String finishSummary = null;
        Map<String, Object> finishResult = Map.of();
        String finalStatus = AgentTurnStatus.ERROR;

        try (AgentRunCancellationRegistry.RunHandle run = cancellationRegistry.start(
                session.sessionId(),
                userMessage,
                session.runState().planStateSummary()
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
                    Map<String, Object> candidateResult = agentAction.result() != null
                            ? new LinkedHashMap<>(agentAction.result())
                            : new LinkedHashMap<>();
                    if (profile != AgentProfile.OPERATOR) {
                        AgentPlanGuard.FinishOutcome planOutcome = AgentPlanGuard.evaluateFinish(
                                session.runState(),
                                candidateResult,
                                steps,
                                llmUserText,
                                profile
                        );
                        if (planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN
                                || planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_APPROVAL) {
                            Map<String, Object> guardStep = Map.of(
                                    "step", stepNumber,
                                    "type", "guard",
                                    "label", "Требуется план",
                                    "error", planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_APPROVAL
                                            ? "Execution finish blocked: plan not approved yet."
                                            : "Execution finish blocked: produce a plan before mutating the platform.",
                                    "hint", "Run discovery tools, then finish with result.phase=plan, "
                                            + "result.plan, result.questions, result.suggestions "
                                            + "(include primary: «Утвердить и начать»)."
                            );
                            steps.add(guardStep);
                            publishStep(session.sessionId(), guardStep);
                            messages.add(new LlmMessage("assistant", response.content()));
                            messages.add(new LlmMessage(
                                    "user",
                                    "Finish blocked during planning phase. "
                                            + "Do NOT claim objects were created. "
                                            + "Use read-only tools if needed, then finish with phase=plan + questions + suggestions."
                            ));
                            continue;
                        }
                        if (planOutcome == AgentPlanGuard.FinishOutcome.ALLOW_PLAN
                                && AgentPlanGuard.isPlanFinish(candidateResult)) {
                            AgentPlanGuard.capturePlan(session.runState(), candidateResult);
                            syncRunPlanState(session);
                        }
                        if (planOutcome == AgentPlanGuard.FinishOutcome.ALLOW_EXECUTION) {
                            var block = AgentPlatformTurnGuard.checkBeforeFinish(steps, userMessage);
                            if (block.isPresent()) {
                                AgentPlatformTurnGuard.BlockDecision decision = block.get();
                                Map<String, Object> guardStep = Map.of(
                                        "step", stepNumber,
                                        "type", "guard",
                                        "label", "Проверка перед завершением",
                                        "error", decision.error(),
                                        "hint", decision.hint() != null ? decision.hint() : ""
                                );
                                steps.add(guardStep);
                                publishStep(session.sessionId(), guardStep);
                                messages.add(new LlmMessage("assistant", response.content()));
                                messages.add(new LlmMessage(
                                        "user",
                                        "Finish blocked:\n" + decision.error()
                                                + (decision.hint() != null ? "\n\n" + decision.hint() : "")
                                                + "\n\nContinue with tools until devices have telemetry, then finish."
                                ));
                                continue;
                            }
                            AgentPlanGuard.completeExecution(session.runState());
                        }
                    }
                    finishSummary = agentAction.summary();
                    finishResult = candidateResult;
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
                    } else if (!toolRegistry.isKnownTool(toolName)) {
                        toolResult = new LinkedHashMap<>(toolRegistry.unknownToolResult(toolName));
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
                        if (!toolRegistry.isKnownTool(toolName)) {
                            toolResult = new LinkedHashMap<>(toolRegistry.unknownToolResult(toolName));
                            executedTool = toolName;
                        } else {
                        Optional<AgentGroundTruthGuard.BlockDecision> groundBlock =
                                AgentGroundTruthGuard.checkBeforeTool(toolName, toolArgs, steps);
                        if (groundBlock.isPresent() && groundBlock.get().blocked()) {
                            AgentGroundTruthGuard.BlockDecision decision = groundBlock.get();
                            toolResult = new LinkedHashMap<>();
                            toolResult.put("status", "ERROR");
                            toolResult.put("error", decision.error());
                            if (decision.hint() != null) {
                                toolResult.put("hint", decision.hint());
                            }
                            executedTool = toolName;
                        } else {
                            Optional<AgentPlanGuard.BlockDecision> planBlock =
                                    AgentPlanGuard.checkBeforeTool(session.runState(), toolName, profile);
                            if (planBlock.isPresent() && planBlock.get().blocked()) {
                                AgentPlanGuard.BlockDecision decision = planBlock.get();
                                toolResult = new LinkedHashMap<>();
                                toolResult.put("status", "ERROR");
                                toolResult.put("error", decision.error());
                                if (decision.hint() != null) {
                                    toolResult.put("hint", decision.hint());
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
                        }
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
                syncRunPlanState(session);

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
                        : firstNonBlank(
                                AgentPlanGuard.planningContinuationHint(session.runState(), steps, executedTool),
                                AgentLoopGuard.continuationHint(executedTool, steps, maxStepsTotal)
                        );
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

        return persistCompletedTurn(
                session,
                userMessage,
                finishSummary,
                finalStatus,
                steps,
                finishResult,
                attachmentMetadata,
                actor,
                profile
        );
    }

    public Map<String, Object> cancelRun(String sessionId) {
        cancellationRegistry.cancel(sessionId);
        return Map.of("status", "OK", "sessionId", sessionId, "cancelRequested", true);
    }

    public Map<String, Object> runProgress(String sessionId) {
        return cancellationRegistry.progress(sessionId);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private void publishStep(String sessionId, Map<String, Object> step) {
        cancellationRegistry.recordStep(sessionId, step);
    }

    private void syncRunPlanState(AgentSession session) {
        cancellationRegistry.syncPlanState(session.sessionId(), session.runState().planStateSummary());
    }

    private Map<String, Object> persistCompletedTurn(
            AgentSession session,
            String userMessage,
            String finishSummary,
            String finalStatus,
            List<Map<String, Object>> steps,
            Map<String, Object> finishResult,
            List<Map<String, Object>> attachments,
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
        AgentTurn turn = AgentTurn.create(userMessage, finishSummary, finalStatus, steps, finishResult, attachments);
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
                profile,
                attachments
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
            AgentProfile profile,
            List<Map<String, Object>> attachments
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
        if (attachments != null && !attachments.isEmpty()) {
            result.put("attachments", attachments);
        }
        result.put("tools", toolRegistry.toolCatalog(profile));
        result.put("provider", llmProviderRegistry.status());
        result.put("contextPackVersion", contextPackService.contextPackVersion());
        result.put("stepsCompleted", stepsCompleted);
        result.put("maxSteps", maxStepsTotal);
        result.put("running", cancellationRegistry.isRunning(session.sessionId()));
        if (profile != AgentProfile.OPERATOR) {
            result.put("planState", session.runState().planStateSummary());
        }
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
            AgentAttachmentValidator.PreparedUserMessage prepared,
            AgentProfile profile,
            OperatorAgentScope operatorScope
    ) throws Exception {
        String llmUserText = prepared.llmText();
        List<LlmMessage> messages = new ArrayList<>();
        boolean includeStatic = aiProperties.isBriefingEveryTurn() || session.turns().isEmpty();
        String briefing = platformBriefingService.buildBriefing(session.rootPath(), includeStatic);
        String systemPrompt;
        if (profile == AgentProfile.OPERATOR && operatorScope != null) {
            String memorySection = operatorMemoryService.formatPromptSection(
                    operatorScope.appId(),
                    llmUserText
            );
            String knowledgeSection = operatorDocumentService.formatPromptSection(
                    operatorScope.appId(),
                    llmUserText,
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
            systemPrompt += AgentPlanPromptSection.forRunState(session.runState());
            if (prepared.hasImages()) {
                systemPrompt += AgentPlanPromptSection.forImageAttachments();
            }
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
        messages.add(buildCurrentUserMessage(prepared));
        return messages;
    }

    private static LlmMessage buildCurrentUserMessage(AgentAttachmentValidator.PreparedUserMessage prepared) {
        if (prepared.imageParts().isEmpty()) {
            return new LlmMessage("user", prepared.llmText());
        }
        List<LlmContentPart> parts = new ArrayList<>();
        if (prepared.llmText() != null && !prepared.llmText().isBlank()) {
            parts.add(LlmContentPart.text(prepared.llmText()));
        }
        parts.addAll(prepared.imageParts());
        return new LlmMessage("user", prepared.llmText(), List.copyOf(parts));
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
