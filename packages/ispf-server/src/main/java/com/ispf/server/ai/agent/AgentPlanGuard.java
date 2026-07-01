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

    private static final String[] MUTATION_INTENT_KEYWORDS = {
            "создай", "создать", "добавь", "добавить", "настрой", "настроить", "сделай", "сделать",
            "построй", "импорт", "import", "deploy", "configure", "create ", "add ", "setup",
            "build ", "install", "удали", "delete ", "remove ", "save_", "save ", "set_",
            "set ", "apply_", "apply ", "запусти workflow", "run workflow", "обнови", "update "
    };

    private static final String[] APPROVAL_KEYWORDS = {
            "утверждаю", "утвердить", "подтверждаю", "начинай", "начинай выполнение",
            "approve", "go ahead", "start execution", "execute plan", "proceed", "да, начинай",
            "ок, начинай", "ok, start", "yes, start", "выполняй план", "приступай",
            "начинаем", "да, начинаем", "yes, begin", "go", "lfg", "вперёд", "вперед"
    };

    private static final String[] APPROVAL_VERBS = {
            "начин", "утверж", "approve", "go", "start", "execute", "proceed", "выполн", "приступ", "давай", "делай"
    };

    private static final String[] SCOPE_CHANGE_KEYWORDS = {
            "измени план", "change plan", "другой scope", "переплан", "replan", "новый план",
            "отмени", "cancel plan", "другая фаза", "change scope", "переделай план"
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
        if (runState != null) {
            runState.setLastUserMessage(userMessage);
        }
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
            runState.setPlanPhase(AgentPlanPhase.NONE);
            return;
        }
        if (mode == AgentInteractionMode.EXECUTE) {
            runState.setPlanPhase(AgentPlanPhase.NONE);
            return;
        }
        if (mode == AgentInteractionMode.PLAN) {
            if (impliesPlatformMutation(userMessage) || requiresPlanning(userMessage)) {
                runState.setPlanPhase(AgentPlanPhase.PLANNING);
            }
            return;
        }
        if (requiresPlanning(userMessage)) {
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
                        + "\"plan\":{...},\"questions\":[...],\"suggestions\":[{\"label\":\"Утвердить полный план\","
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
        if (runState.interactionMode() == AgentInteractionMode.ASK) {
            if (looksLikePlanPayload(finishResult, runState)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            return FinishOutcome.ALLOW_EXECUTION;
        }
        if (runState.interactionMode() == AgentInteractionMode.EXECUTE) {
            if (runState.planPhase() == AgentPlanPhase.APPROVED) {
                return FinishOutcome.ALLOW_EXECUTION;
            }
            if (looksLikePlanPayload(finishResult, runState)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            return FinishOutcome.ALLOW_EXECUTION;
        }
        if (isPlanFinish(finishResult)) {
            if (runState.planPhase() == AgentPlanPhase.APPROVED && !isScopeChangeMessage(userMessage)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            if (runState.isPlanningActive() && !hasStructuredPlanContent(finishResult, runState)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            return FinishOutcome.ALLOW_PLAN;
        }
        if (runState.planPhase() == AgentPlanPhase.APPROVED) {
            return FinishOutcome.ALLOW_EXECUTION;
        }
        if (runState.planPhase() == AgentPlanPhase.AWAITING_APPROVAL) {
            if (looksLikeExecutionResult(finishResult) && !isApprovalMessage(userMessage, runState.planPhase())) {
                return FinishOutcome.BLOCK_NEEDS_APPROVAL;
            }
            if (isPlanFinish(finishResult)) {
                return FinishOutcome.ALLOW_PLAN;
            }
            if (isInteractiveClarification(finishResult)) {
                return hasStructuredPlanContent(finishResult, runState)
                        ? FinishOutcome.ALLOW_PLAN
                        : FinishOutcome.BLOCK_NEEDS_PLAN;
            }
        }
        if (restrictsMutations(runState)) {
            if (looksLikeExecutionResult(finishResult) && !isApprovalMessage(userMessage)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            if (isInteractiveClarification(finishResult)) {
                return hasStructuredPlanContent(finishResult, runState)
                        ? FinishOutcome.ALLOW_PLAN
                        : FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            if (requiresPlanning(userMessage) && steps.stream().noneMatch(AgentPlanGuard::isDiscoveryStep)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            if (runState.isPlanningActive() && !hasStructuredPlanContent(finishResult, runState)) {
                return FinishOutcome.BLOCK_NEEDS_PLAN;
            }
            return FinishOutcome.ALLOW_PLAN;
        }
        if (requiresPlanning(userMessage)
                && runState.interactionMode() != AgentInteractionMode.EXECUTE
                && runState.planPhase() == AgentPlanPhase.NONE) {
            return FinishOutcome.BLOCK_NEEDS_PLAN;
        }
        return FinishOutcome.ALLOW_EXECUTION;
    }

    static void capturePlan(AgentRunState runState, Map<String, Object> finishResult) {
        if (runState == null || finishResult == null) {
            return;
        }
        Map<String, Object> incoming = extractPlanMap(finishResult.get("plan"));
        Map<String, Object> merged = mergePlans(runState.storedPlan(), incoming);
        merged = preserveNonEmptyFields(merged, runState.storedPlan());
        AgentAnalyticalIntake.mergeFinishIntakeIntoPlan(merged, finishResult);
        runState.setStoredPlan(merged);
        finishResult.put("plan", merged);
        AgentAnalyticalIntake.enrichFinishFromPlan(finishResult, merged);
        if (!AgentAnalyticalIntake.readyForApproval(merged, finishResult)) {
            finishResult.put("planCompletenessGaps",
                    AgentAnalyticalIntake.completenessGaps(merged, finishResult, runState.lastUserMessage()));
        } else {
            finishResult.remove("planCompletenessGaps");
        }
        finishResult.put("phase", "plan");
        finishResult.put("interactive", true);
        runState.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
    }

    static boolean shouldCapturePlan(AgentRunState runState, Map<String, Object> finishResult) {
        if (runState == null || finishResult == null) {
            return false;
        }
        if (runState.interactionMode() == AgentInteractionMode.ASK) {
            return false;
        }
        if (isPlanFinish(finishResult) && hasStructuredPlanContent(finishResult, runState)) {
            return true;
        }
        if (!runState.isPlanningActive()) {
            return false;
        }
        if (finishResult.containsKey("plan")
                && finishResult.get("plan") instanceof Map<?, ?>
                && planHasContent(extractPlanMap(finishResult.get("plan")))) {
            return true;
        }
        return !runState.storedPlan().isEmpty() && isInteractiveClarification(finishResult);
    }

    /**
     * True when result.plan (or stored draft) has goal or steps for the plan panel UI.
     */
    static boolean looksLikePlanPayload(Map<String, Object> finishResult, AgentRunState runState) {
        if (finishResult == null) {
            return false;
        }
        if (isPlanFinish(finishResult) || hasStructuredPlanContent(finishResult, runState)) {
            return true;
        }
        Object questions = finishResult.get("questions");
        if (questions instanceof List<?> list && !list.isEmpty()) {
            return true;
        }
        return hasPrimaryApprovalSuggestion(finishResult);
    }

    private static boolean hasPrimaryApprovalSuggestion(Map<String, Object> finishResult) {
        Object raw = finishResult.get("suggestions");
        if (!(raw instanceof List<?> suggestions)) {
            return false;
        }
        for (Object item : suggestions) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            if (Boolean.TRUE.equals(map.get("primary"))) {
                return true;
            }
            Object label = map.get("label");
            if (label != null && isApprovalMessage(String.valueOf(label), AgentPlanPhase.AWAITING_APPROVAL)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasStructuredPlanContent(Map<String, Object> finishResult, AgentRunState runState) {
        if (finishResult != null && planHasContent(extractPlanMap(finishResult.get("plan")))) {
            return true;
        }
        return runState != null && planHasContent(runState.storedPlan());
    }

    private static boolean planHasContent(Map<String, Object> plan) {
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        if (!stringValue(plan.get("goal")).isBlank()) {
            return true;
        }
        if (AgentPlanSections.hasSections(plan)) {
            return true;
        }
        return !toStringList(plan.get("steps")).isEmpty();
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
            Map<String, Object> fresh = new LinkedHashMap<>(incoming);
            fresh.put("steps", mergePlanSteps(List.of(), incoming.get("steps")));
            fresh.put("layers", mergeStringLists(List.of(), incoming.get("layers")));
            fresh.put("assumptions", mergeStringLists(List.of(), incoming.get("assumptions")));
            fresh.put("sections", AgentPlanSections.mergeSections(List.of(), incoming.get("sections")));
            syncFlattenedSteps(fresh);
            return fresh;
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
        merged.put("sections", AgentPlanSections.mergeSections(existing.get("sections"), incoming.get("sections")));
        syncFlattenedSteps(merged);

        return merged;
    }

    /** Keep plan.steps in sync with sectional detail for progress tracking and legacy UI. */
    private static void syncFlattenedSteps(Map<String, Object> plan) {
        if (plan == null || !AgentPlanSections.hasSections(plan)) {
            return;
        }
        List<String> flat = AgentPlanSections.flattenSteps(plan);
        if (!flat.isEmpty()) {
            plan.put("steps", flat);
        }
    }

    /** Never let a merge wipe goal/steps/layers that were already in the draft. */
    private static Map<String, Object> preserveNonEmptyFields(
            Map<String, Object> merged,
            Map<String, Object> existing
    ) {
        if (existing == null || existing.isEmpty()) {
            return merged;
        }
        Map<String, Object> safe = new LinkedHashMap<>(merged);
        if (toStringList(safe.get("steps")).isEmpty() && !toStringList(existing.get("steps")).isEmpty()) {
            safe.put("steps", toStringList(existing.get("steps")));
        }
        if (stringValue(safe.get("goal")).isBlank() && !stringValue(existing.get("goal")).isBlank()) {
            safe.put("goal", existing.get("goal"));
        }
        if (toStringList(safe.get("layers")).isEmpty() && !toStringList(existing.get("layers")).isEmpty()) {
            safe.put("layers", toStringList(existing.get("layers")));
        }
        if (AgentPlanSections.readSections(safe).isEmpty() && AgentPlanSections.hasSections(existing)) {
            safe.put("sections", existing.get("sections"));
            syncFlattenedSteps(safe);
        }
        for (String key : List.of("specBrief", "gapMatrix", "objectTypesCoverage", "executiveSummary", "assumptions")) {
            if (!safe.containsKey(key) || isEmptyValue(safe.get(key))) {
                if (existing.containsKey(key) && !isEmptyValue(existing.get(key))) {
                    safe.put(key, existing.get(key));
                }
            }
        }
        return safe;
    }

    private static boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return String.valueOf(value).isBlank();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPlanMap(Object planRaw) {
        if (planRaw instanceof Map<?, ?> planMap) {
            return new LinkedHashMap<>((Map<String, Object>) planMap);
        }
        return new LinkedHashMap<>();
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

    static boolean impliesPlatformMutation(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        if (isApprovalMessage(text)) {
            return false;
        }
        if (!containsMutationKeyword(text)) {
            return false;
        }
        for (String keyword : SIMPLE_KEYWORDS) {
            if (text.contains(keyword) && !containsComplexKeyword(text)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsMutationKeyword(String text) {
        for (String keyword : MUTATION_INTENT_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
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
        if (phase == AgentPlanPhase.AWAITING_APPROVAL && text.length() <= 40) {
            if (text.equals("да")
                    || text.equals("ok")
                    || text.equals("yes")
                    || text.equals("давай")
                    || text.equals("согласен")
                    || text.equals("согласна")
                    || text.equals("продолжай")
                    || text.equals("выполняй")
                    || text.equals("делай")
                    || text.equals("go")
                    || text.equals("lfg")) {
                return true;
            }
            if ((text.startsWith("да,") || text.startsWith("да ") || text.startsWith("ok,") || text.startsWith("ok "))
                    && containsApprovalVerb(text)) {
                return true;
            }
        }
        return false;
    }

    static boolean isScopeChangeMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        for (String keyword : SCOPE_CHANGE_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    static void markPlanStepCompleted(AgentRunState runState, String toolName) {
        if (runState == null || toolName == null || toolName.isBlank()) {
            return;
        }
        runState.markCompletedPlanStep(toolName.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean containsApprovalVerb(String text) {
        for (String verb : APPROVAL_VERBS) {
            if (text.contains(verb)) {
                return true;
            }
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
                    Finish NOW with {"type":"finish","summary":"1–2 sentences only","result":{"phase":"plan",\
                    "interactive":true,"plan":{"goal":"...","layers":[...],"steps":["1. ...","2. ..."]},\
                    "questions":[...],"suggestions":[{"label":"Утвердить полный план",\
                    "message":"Утверждаю план, начинай выполнение","primary":true}]}}. \
                    Put ALL numbered steps in result.plan.steps — NOT in summary (UI plan panel). \
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
                .map(AgentPlanGuard::stepToString)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String stepToString(Object item) {
        if (item == null) {
            return "";
        }
        if (item instanceof String string) {
            return string.trim();
        }
        if (item instanceof Map<?, ?> map) {
            for (String key : List.of("text", "step", "description", "label", "action", "title")) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value).trim();
                }
            }
        }
        String fallback = String.valueOf(item).trim();
        if (fallback.startsWith("{") && fallback.contains("=")) {
            return "";
        }
        return fallback;
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

    static List<String> mergePlanSteps(Object existingRaw, Object incomingRaw) {
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
