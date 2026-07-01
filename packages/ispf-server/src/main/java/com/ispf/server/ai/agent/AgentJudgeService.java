package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pre-finish judge phase — readonly conformance verdict (docanima Judge patterns).
 */
public final class AgentJudgeService {

    public enum Verdict {
        APPROVE,
        REWORK,
        BLOCKED,
        GAP_REQUIRED,
        USER_MODERATION_REQUIRED;

        /** Turn must end and wait for the user — no further LLM loop in this session turn. */
        public boolean requiresUserIntervention() {
            return this == GAP_REQUIRED || this == USER_MODERATION_REQUIRED;
        }
    }

    public record JudgeResult(Verdict verdict, String summary, String hint, List<String> issues) {
        boolean blocksFinish() {
            return verdict != Verdict.APPROVE;
        }
    }

    private AgentJudgeService() {
    }

    @SuppressWarnings("unchecked")
    public static JudgeResult evaluate(
            List<Map<String, Object>> steps,
            AgentRunState runState,
            Map<String, Object> finishResult,
            String userMessage
    ) {
        List<String> issues = new ArrayList<>();

        if (hasBlockingErrorSteps(steps)) {
            issues.add("Turn contains ERROR tool steps — fix before finish");
        }

        Map<String, Object> storedPlan = runState != null ? runState.storedPlan() : Map.of();
        Object handoffRaw = finishResult != null ? finishResult.get("handoffFrame") : null;
        if (handoffRaw == null && storedPlan.get("handoffFrame") instanceof Map<?, ?>) {
            handoffRaw = storedPlan.get("handoffFrame");
        }
        if (handoffRaw instanceof Map<?, ?> handoff) {
            List<String> blockingGaps = openBlockingGaps((Map<String, Object>) handoff);
            if (!blockingGaps.isEmpty()) {
                return new JudgeResult(
                        Verdict.GAP_REQUIRED,
                        "Open blocking GAPs: " + String.join(", ", blockingGaps),
                        problemBrief(blockingGaps, issues),
                        issues
                );
            }
        }

        AgentAssignmentType assignmentType = resolveAssignmentType(finishResult, storedPlan, userMessage);
        if (AgentConformanceCatalog.requiresSmokeCases(assignmentType)) {
            Object conformance = storedPlan.get("conformance");
            if (conformance == null && finishResult != null) {
                conformance = finishResult.get("conformance");
            }
            if (!(conformance instanceof Map<?, ?> confMap)
                    || !(confMap.get("smokeCases") instanceof List<?> smoke) || smoke.isEmpty()) {
                issues.add("conformance.smokeCases empty for assignmentType=" + assignmentType.id());
            }
            issues.addAll(AgentConformanceEvaluator.verifyInvariants(steps, assignmentType));
            issues.addAll(AgentConformanceEvaluator.verifySmokeCases(steps, assignmentType));
        }

        if (runState != null && runState.reworkRoundCount() >= 2 && hasBlockingErrorSteps(steps)) {
            return new JudgeResult(
                    Verdict.USER_MODERATION_REQUIRED,
                    "Repeated failures after 2 rework rounds",
                    problemBrief(List.of("USER_GATE"), issues),
                    issues
            );
        }

        if (!issues.isEmpty()) {
            return new JudgeResult(
                    Verdict.REWORK,
                    "Conformance check failed",
                    String.join("\n", issues) + "\n\nNever claim success for steps that returned ERROR.",
                    issues
            );
        }
        return new JudgeResult(Verdict.APPROVE, "Conformance OK", null, List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<String> openBlockingGaps(Map<String, Object> handoff) {
        List<String> open = new ArrayList<>();
        Object gapMatrix = handoff.get("gapMatrix");
        if (!(gapMatrix instanceof List<?> list)) {
            return open;
        }
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            if (Boolean.TRUE.equals(map.get("blocksDev"))) {
                String gapId = String.valueOf(map.getOrDefault("gapId", "?"));
                String status = String.valueOf(map.getOrDefault("gapStatus", "open"));
                if (!"closed".equalsIgnoreCase(status)) {
                    open.add(gapId);
                }
            }
        }
        Object blocking = handoff.get("blockingGaps");
        if (blocking instanceof List<?> blockingList) {
            for (Object item : blockingList) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    open.add(String.valueOf(item));
                }
            }
        }
        return open.stream().distinct().toList();
    }

    private static boolean hasBlockingErrorSteps(List<Map<String, Object>> steps) {
        return AgentTurnToolErrors.hasUnresolvedErrors(steps);
    }

    private static boolean hasErrorSteps(List<Map<String, Object>> steps) {
        return hasBlockingErrorSteps(steps);
    }

    private static AgentAssignmentType resolveAssignmentType(
            Map<String, Object> finishResult,
            Map<String, Object> storedPlan,
            String userMessage
    ) {
        String raw = extractAssignmentType(finishResult);
        if (raw == null) {
            raw = extractAssignmentType(storedPlan);
        }
        if (raw != null) {
            return AgentAssignmentType.fromString(raw);
        }
        return AgentAssignmentClassifier.classify(userMessage).type();
    }

    private static String extractAssignmentType(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object direct = map.get("assignmentType");
        if (direct instanceof String s && !s.isBlank()) {
            return s;
        }
        Object specBrief = map.get("specBrief");
        if (specBrief instanceof Map<?, ?> brief) {
            Object nested = brief.get("assignmentType");
            if (nested instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private static String problemBrief(List<String> gaps, List<String> issues) {
        StringBuilder sb = new StringBuilder("Problem Brief:\n");
        if (!gaps.isEmpty()) {
            sb.append("Blocking GAPs: ").append(String.join(", ", gaps)).append("\n");
            sb.append("User Gate: resolve blocking GAPs (out_of_scope items) or confirm explicit exclusion before execution.\n");
        }
        for (String issue : issues) {
            sb.append("- ").append(issue).append("\n");
        }
        return sb.toString().trim();
    }
}
