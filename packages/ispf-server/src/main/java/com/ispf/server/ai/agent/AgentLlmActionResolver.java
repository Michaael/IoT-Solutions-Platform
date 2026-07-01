package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Calls the LLM and parses a tool/finish action with bounded retries and JSON-only nudges.
 */
final class AgentLlmActionResolver {

    private static final String JSON_NUDGE = """
            Your last reply was not a valid agent action. Reply with ONLY one JSON object, no markdown and no explanation:
            {"type":"tool","name":"<tool>","arguments":{...}}
            or {"type":"finish","summary":"...","result":{...}}
            """;

    private static final String COMPACT_TOOL_NUDGE = """
            Your reply was truncated — invalid JSON. Reply with ONLY one compact JSON tool call. \
            Do NOT embed huge layoutJson or mimic elements inline.
            For dashboards prefer template=:
            {"type":"tool","name":"set_dashboard_layout","arguments":{"path":"<path>","template":"scada-facility-overview"}}
            Or add widgets one at a time via add_dashboard_widget. For mimics use save_mimic_diagram with a small elements array \
            or add_mimic_elements in chunks.
            """;

    private static final String ATTACHMENT_PLAN_NUDGE = """
            User attached a spec/TZ file — do NOT analyze it in prose. Reply with ONLY one JSON object:
            Turn 1: {"type":"tool","name":"get_automation_schema","arguments":{"topic":"projectBlueprint"}}
            Turn 2 (bootstrap): specBrief + plan.sections[ground_truth,intent_scope] + executiveSummary.
            HARD LIMITS: max 2 sections, max 3 questions; gapMatrix/handoffFrame on SYNTHESIS/FINALIZE only.
            """;

    record ResolveContext(
            boolean textAttachment,
            boolean planningTurn,
            boolean executionTurn,
            AgentPhasedPlanIntake.Stage phasedStage
    ) {
        static ResolveContext none() {
            return new ResolveContext(false, false, false, AgentPhasedPlanIntake.Stage.DISCOVERY);
        }
    }

    record ParseAttempt(
            LlmResponse response,
            AgentJsonProtocol.AgentAction action,
            boolean failed,
            String error
    ) {
    }

    private AgentLlmActionResolver() {
    }

    static ParseAttempt resolve(
            ObjectMapper objectMapper,
            LlmProviderRegistry llmProviderRegistry,
            List<LlmMessage> messages,
            Function<List<LlmMessage>, LlmRequest> requestFactory,
            int maxAttempts
    ) throws Exception {
        return resolve(objectMapper, llmProviderRegistry, messages, requestFactory, maxAttempts, false, ResolveContext.none());
    }

    static ParseAttempt resolve(
            ObjectMapper objectMapper,
            LlmProviderRegistry llmProviderRegistry,
            List<LlmMessage> messages,
            Function<List<LlmMessage>, LlmRequest> requestFactory,
            int maxAttempts,
            boolean allowPlainTextFinish
    ) throws Exception {
        return resolve(
                objectMapper,
                llmProviderRegistry,
                messages,
                requestFactory,
                maxAttempts,
                allowPlainTextFinish,
                ResolveContext.none()
        );
    }

    static ParseAttempt resolve(
            ObjectMapper objectMapper,
            LlmProviderRegistry llmProviderRegistry,
            List<LlmMessage> messages,
            Function<List<LlmMessage>, LlmRequest> requestFactory,
            int maxAttempts,
            boolean allowPlainTextFinish,
            ResolveContext context
    ) throws Exception {
        int attempts = Math.max(1, maxAttempts);
        LlmResponse lastResponse = null;
        String lastError = null;
        ResolveContext ctx = context != null ? context : ResolveContext.none();
        for (int attempt = 0; attempt < attempts; attempt++) {
            lastResponse = llmProviderRegistry.complete(requestFactory.apply(messages));
            try {
                AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, lastResponse.content());
                return new ParseAttempt(lastResponse, action, false, null);
            } catch (Exception ex) {
                lastError = ex.getMessage();
                if (attempt < attempts - 1) {
                    messages.add(new LlmMessage("assistant", lastResponse.content()));
                    messages.add(new LlmMessage("user", nudgeForRetry(lastResponse, ctx, attempt)));
                }
            }
        }
        if (allowPlainTextFinish && lastResponse != null) {
            Optional<AgentJsonProtocol.AgentAction> plain = AgentJsonProtocol.tryParsePlainTextFinish(
                    lastResponse.content()
            );
            if (plain.isPresent()) {
                return new ParseAttempt(lastResponse, plain.get(), false, null);
            }
        }
        if (lastResponse != null && AgentJsonProtocol.looksLikeTruncatedContent(lastResponse.content())) {
            Optional<AgentJsonProtocol.AgentAction> salvaged =
                    AgentJsonProtocol.trySalvageTruncatedFinish(objectMapper, lastResponse.content());
            if (salvaged.isPresent()) {
                return new ParseAttempt(lastResponse, salvaged.get(), false, null);
            }
        }
        return new ParseAttempt(lastResponse, null, true, lastError);
    }

    private static String nudgeForRetry(LlmResponse response, ResolveContext context, int attemptIndex) {
        AgentPhasedPlanIntake.Stage stage = context.phasedStage() != null
                ? context.phasedStage()
                : AgentPhasedPlanIntake.Stage.BOOTSTRAP;
        if (response != null && (response.truncatedByLength() || AgentJsonProtocol.looksLikeTruncatedContent(response.content()))) {
            if (context.planningTurn() || context.textAttachment()) {
                return AgentPhasedPlanIntake.compactFinishNudge(stage);
            }
            if (context.executionTurn() || looksLikeHeavyToolPayload(response.content())) {
                return COMPACT_TOOL_NUDGE;
            }
            return AgentPhasedPlanIntake.compactFinishNudge(stage);
        }
        if (context.textAttachment() && attemptIndex == 0) {
            return ATTACHMENT_PLAN_NUDGE;
        }
        if (context.planningTurn()) {
            return AgentPhasedPlanIntake.compactFinishNudge(stage);
        }
        return JSON_NUDGE;
    }

    private static boolean looksLikeHeavyToolPayload(String content) {
        if (content == null) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("layoutjson")
                || lower.contains("set_dashboard_layout")
                || lower.contains("save_mimic_diagram")
                || lower.contains("\"elements\"");
    }
}
