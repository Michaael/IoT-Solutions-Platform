package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces plan-before-execute workflow for the platform admin agent.
 * <p>
 * In planning phases only read-only discovery tools are allowed. Complex tasks in {@link AgentInteractionMode#AUTO}
 * enter planning automatically; {@link AgentInteractionMode#PLAN} always plans first;
 * {@link AgentInteractionMode#ASK} never mutates.
 */
final class AgentPlanGuard {

    private static final Set<String> READ_ONLY_EXACT = Set.of(
            "validate_bundle",
            "dry_run_deploy",
            "run_report"
    );

    private static final String[] COMPLEX_KEYWORDS = {
            "создай проект", "создать проект", "полный проект", "scada", "мнемо", "мимик", "hmi",
            "насосн", "nps", "pump station", "operator ui", "operator hmi", "8 сло", "blueprint",
            "bundle", "приложен", "создай workflow", "create workflow", "настроить workflow",
            "workflow project", "bpmn", "дашборд и", "мониторинг и", "virtual pump",
            "tank farm", "резервуар", "мини-тэц", "pipeline", "настроить платформ",
            "создай насос", "создай scada", "создай дашборд", "create project", "full platform",
            "гидроудар", "hydro impact", "hydro-impact"
    };

    private static final String[] SIMPLE_KEYWORDS = {
            "list ", "list_", "покажи", "show ", "what ", "какие", "список", "describe",
            "get ", "get_", "открой", "open ", "explain", "объясни", "сколько", "where is",
            "где ", "какой отч", "доступн"
    };

    private static final String[] APPROVAL_KEYWORDS = {
            "утверждаю", "утвердить", "подтверждаю", "начинай", "начинай выполнение",
            "approve", "go ahead", "start execution", "execute plan", "proceed", "да, начинай",
            "ок, начинай", "ok, start", "yes, start", "выполняй план", "приступай"
    };

    private AgentPlanGuard() {
    }

    record BlockDecision(String error, String hint) {
        boolean blocked() {
            return error != null && !error.isBlank();
        }
    }

    enum FinishOutcome {
        ALLOW_PLAN,
        ALLOW_EXECUTION,
        BLOCK_NEEDS_PLAN,
        BLOCK_NEEDS_APPROVAL
    }

    static void beginTurn(AgentRunState runState, String userMessage, AgentProfile profile) {
        if (profile == AgentProfile.OPERATOR || runState == null) {
            return;
        }
        if (isApprovalMessage(userMessage, runState.planPhase())) {
            if (runState.planPhase() == AgentPlanPhase.AWAITING_APPROVAL
                    || runState.planPhase() == AgentPlanPhase.PLANNING) {
                runState.approvePlan();
            }
            return;
        }
        if (runState.planPhase() == AgentPlanPhase.AWAITING_APPROVAL) {
            return;
        }
        if (runState.planPhase() == AgentPlanPhase.APPROVED) {
            return;
        }
        AgentInteractionMode mode = runState.interactionMode();
        if (mode == AgentInteractionMode.ASK) {
            runState.setPlanPhase(AgentPlanPhase.PLANNING);
            return;
        }
        if (mode == AgentInteractionMode.EXECUTE) {
            runState.setPlanPhase(AgentPlanPhase.NONE);
            return;
        }
        if (mode == AgentInteractionMode.PLAN || requiresPlanning(userMessage)) {
            runState.setPlanPhase(AgentPlanPhase.PLANNING);
        }
    }

    static Optional<BlockDecision> checkBeforeTool(
            AgentRunState runState,
            String toolName,
            AgentProfile profile
    ) {
        if (profile == AgentProfile.OPERATOR || runState == null) {
            return Optional.empty();
        }
        if (!restrictsMutations(runState)) {
            return Optional.empty();
        }
        if (isReadOnlyTool(toolName)) {
            return Optional.empty();
        }
        String phase = runState.planPhase().storageValue();
        String hint = runState.interactionMode() == AgentInteractionMode.ASK
                ? "Ask mode is read-only. Use list_*/get_*/search_* tools, then finish with an answer."
                : "Planning phase (" + phase + "): use read-only discovery tools, then finish with "
                        + "{\"type\":\"finish\",\"result\":{\"phase\":\"plan\",\"interactive\":true,"
                        + "\"plan\":{...},\"questions\":[...],\"suggestions\":[{\"label\":\"Утвердить и начать\","
                        + "\"message\":\"Утверждаю план, начинай выполнение\",\"primary\":true}]}}.";
        return Optional.of(new BlockDecision(
                "Tool '" + toolName + "' is blocked during planning. Mutations require an approved plan.",
                hint
        ));
    }

    static FinishOutcome evaluateFinish(
            AgentRunState runState,
            Map<String, Object> finishResult,
            List<Map<String, Object>> steps,
            String userMessage,
            AgentProfile profile
    ) {
        if (profile == AgentProfile.OPERATOR || runState == null) {
            return FinishOutcome.ALLOW_EXECUTION;
        }
        if (isPlanFinish(finishResult)) {
            return FinishOutcome.ALLOW_PLAN;
        }
        if (runState.interactionMode() == AgentInteractionMode.ASK) {
            return FinishOutcome.ALLOW_PLAN;
        }
        if (runState.planPhase() == AgentPlanPhase.APPROVED) {
            return FinishOutcome.ALLOW_EXECUTION;
        }
        if (restrictsMutations(runState)) {
            if (looksLikeExecutionResult(finishResult) && !isApprovalMessage(userMessage)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            if (isInteractiveClarification(finishResult)) {
                return FinishOutcome.ALLOW_PLAN;
            }
            if (requiresPlanning(userMessage) && steps.stream().noneMatch(AgentPlanGuard::isDiscoveryStep)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            return FinishOutcome.ALLOW_PLAN;
        }
        if (requiresPlanning(userMessage) && runState.planPhase() == AgentPlanPhase.NONE) {
            return FinishOutcome.BLOCK_NEEDS_PLAN;
        }
        return FinishOutcome.ALLOW_EXECUTION;
    }

    static void capturePlan(AgentRunState runState, Map<String, Object> finishResult) {
        if (runState == null || finishResult == null) {
            return;
        }
        Object planRaw = finishResult.get("plan");
        if (planRaw instanceof Map<?, ?> planMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> incoming = new LinkedHashMap<>((Map<String, Object>) planMap);
            Map<String, Object> merged = mergePlans(runState.storedPlan(), incoming);
            runState.setStoredPlan(merged);
            finishResult.put("plan", merged);
        }
        finishResult.put("phase", "plan");
        finishResult.put("interactive", true);
        runState.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
    }

    /**
     * Extends an existing draft plan with new discovery — never drops prior steps unless the model
     * resubmits a strict superset (same prefix, more steps).
     */
    static Map<String, Object> mergePlans(Map<String, Object> existing, Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return existing == null || existing.isEmpty() ? Map.of() : new LinkedHashMap<>(existing);
        }
        if (existing == null || existing.isEmpty()) {
            return new LinkedHashMap<>(incoming);
        }
        Map<String, Object> merged = new LinkedHashMap<>(existing);

        String existingGoal = stringValue(existing.get("goal"));
        String incomingGoal = stringValue(incoming.get("goal"));
        if (!incomingGoal.isBlank()) {
            if (existingGoal.isBlank() || incomingGoal.length() >= existingGoal.length()) {
                merged.put("goal", incomingGoal);
            }
        }

        if (incoming.containsKey("approach") && incoming.get("approach") != null) {
            merged.put("approach", incoming.get("approach"));
        }

        merged.put("layers", mergeStringLists(existing.get("layers"), incoming.get("layers")));
        merged.put("steps", mergePlanSteps(existing.get("steps"), incoming.get("steps")));
        merged.put("assumptions", mergeStringLists(existing.get("assumptions"), incoming.get("assumptions")));

        return merged;
    }

    static void completeExecution(AgentRunState runState) {
        if (runState == null) {
            return;
        }
        if (runState.planPhase() == AgentPlanPhase.APPROVED) {
            runState.resetPlan();
        }
    }

    static boolean restrictsMutations(AgentRunState runState) {
        if (runState == null) {
            return false;
        }
        if (runState.interactionMode() == AgentInteractionMode.ASK) {
            return true;
        }
        if (runState.interactionMode() == AgentInteractionMode.EXECUTE) {
            return false;
        }
        AgentPlanPhase phase = runState.planPhase();
        return phase == AgentPlanPhase.PLANNING || phase == AgentPlanPhase.AWAITING_APPROVAL;
    }

    static boolean requiresPlanning(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        if (isApprovalMessage(text)) {
            return false;
        }
        for (String keyword : SIMPLE_KEYWORDS) {
            if (text.contains(keyword)) {
                return false;
            }
        }
        if (text.length() < 48 && !containsComplexKeyword(text)) {
            return false;
        }
        return containsComplexKeyword(text);
    }

    static boolean isApprovalMessage(String userMessage) {
        return isApprovalMessage(userMessage, null);
    }

    static boolean isApprovalMessage(String userMessage, AgentPlanPhase phase) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.toLowerCase(Locale.ROOT).trim();
        for (String keyword : APPROVAL_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        if (text.startsWith("утверждаю") || text.startsWith("approve")) {
            return true;
        }
        if (phase == AgentPlanPhase.AWAITING_APPROVAL && text.length() <= 24) {
            return text.equals("да")
                    || text.equals("ok")
                    || text.equals("yes")
                    || text.equals("давай")
                    || text.equals("согласен")
                    || text.equals("согласна")
                    || text.equals("продолжай")
                    || text.equals("выполняй")
                    || text.equals("делай");
        }
        return false;
    }

    static String planningContinuationHint(
            AgentRunState runState,
            List<Map<String, Object>> steps,
            String lastTool
    ) {
        if (!restrictsMutations(runState) || steps == null) {
            return null;
        }
        long discoveryCount = steps.stream().filter(AgentPlanGuard::isDiscoveryStep).count();
        if (!isReadOnlyTool(lastTool) || lastStepPlanBlocked(steps)) {
            return """
                    PLANNING PHASE: mutating tools are blocked until the user approves a plan. \
                    Finish NOW with {"type":"finish","result":{"phase":"plan","interactive":true,"plan":{...},\
                    "questions":[...],"suggestions":[{"label":"Утвердить и начать",\
                    "message":"Утверждаю план, начинай выполнение","primary":true}]}}. \
                    EXTEND the existing draft plan — add new steps from discovery; do NOT rewrite from scratch. \
                    Use exact snake_case tool names from the catalog only — never invent display names as tools. \
                    For workflows after approval: create_object (WORKFLOW), save_workflow_bpmn, run_workflow.""";
        }
        if (discoveryCount >= 2) {
            return """
                    Enough discovery for this turn. Finish with phase=plan (not execution results). \
                    EXTEND the existing draft: keep prior steps and add newly discovered ones. \
                    Do NOT call create_object or other mutations in this turn.""";
        }
        if (discoveryCount >= 1) {
            return """
                    Planning: discovery started — add one more list_*/get_* if needed, then finish with phase=plan. \
                    Extend the draft plan with any new steps; do not replace the whole plan.""";
        }
        return null;
    }

    static boolean isPlanFinish(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        if ("plan".equalsIgnoreCase(String.valueOf(result.get("phase")))) {
            return true;
        }
        return result.containsKey("plan") && result.get("plan") instanceof Map<?, ?>;
    }

    static boolean isReadOnlyTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        if (READ_ONLY_EXACT.contains(normalized)) {
            return true;
        }
        return normalized.startsWith("list_")
                || normalized.startsWith("get_")
                || normalized.startsWith("search_")
                || normalized.startsWith("describe_");
    }

    private static boolean containsComplexKeyword(String text) {
        for (String keyword : COMPLEX_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInteractiveClarification(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        if (Boolean.TRUE.equals(result.get("interactive"))) {
            return true;
        }
        return result.containsKey("suggestions") || result.containsKey("questions");
    }

    private static boolean looksLikeExecutionResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        for (String key : List.of(
                "devicePath", "dashboardPath", "mimicPath", "path", "workflowPath", "reportPath"
        )) {
            Object value = result.get(key);
            if (value instanceof String string && !string.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDiscoveryStep(Map<String, Object> step) {
        if (step == null || !"tool".equals(String.valueOf(step.get("type")))) {
            return false;
        }
        return isReadOnlyTool(String.valueOf(step.get("tool")));
    }

    private static boolean lastStepPlanBlocked(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return false;
        }
        Map<String, Object> last = steps.get(steps.size() - 1);
        if (!"tool".equals(String.valueOf(last.get("type")))) {
            return false;
        }
        Object resultRaw = last.get("result");
        if (!(resultRaw instanceof Map<?, ?> result)) {
            return false;
        }
        if (!"ERROR".equals(String.valueOf(result.get("status")))) {
            return false;
        }
        String error = String.valueOf(result.get("error"));
        return error.contains("blocked during planning");
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> item == null ? "" : String.valueOf(item).trim())
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static List<String> mergeStringLists(Object existingRaw, Object incomingRaw) {
        List<String> existing = toStringList(existingRaw);
        List<String> incoming = toStringList(incomingRaw);
        if (incoming.isEmpty()) {
            return existing;
        }
        if (existing.isEmpty()) {
            return incoming;
        }
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (String item : existing) {
            seen.putIfAbsent(normalizePlanStep(item), item);
        }
        for (String item : incoming) {
            seen.putIfAbsent(normalizePlanStep(item), item);
        }
        return List.copyOf(seen.values());
    }

    private static List<String> mergePlanSteps(Object existingRaw, Object incomingRaw) {
        List<String> existing = toStringList(existingRaw);
        List<String> incoming = toStringList(incomingRaw);
        if (incoming.isEmpty()) {
            return existing;
        }
        if (existing.isEmpty()) {
            return incoming;
        }
        if (startsWithNormalizedSteps(existing, incoming) && incoming.size() >= existing.size()) {
            return incoming;
        }
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (String step : existing) {
            seen.put(normalizePlanStep(step), step);
        }
        for (String step : incoming) {
            seen.putIfAbsent(normalizePlanStep(step), step);
        }
        return List.copyOf(seen.values());
    }

    private static boolean startsWithNormalizedSteps(List<String> prefix, List<String> full) {
        if (full.size() < prefix.size()) {
            return false;
        }
        for (int index = 0; index < prefix.size(); index++) {
            if (!normalizePlanStep(prefix.get(index)).equals(normalizePlanStep(full.get(index)))) {
                return false;
            }
        }
        return true;
    }

    /** Strip leading "1." / "1)" so numbering does not break deduplication. */
    private static String normalizePlanStep(String step) {
        if (step == null) {
            return "";
        }
        String trimmed = step.trim();
        return trimmed.replaceFirst("^\\s*\\d+[.)]\\s*", "").trim().toLowerCase(Locale.ROOT);
    }
}
