package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes plan finish payloads for phased multi-turn intake.
 */
public final class AgentPlanFinishNormalizer {

    private AgentPlanFinishNormalizer() {
    }

    @SuppressWarnings("unchecked")
    public static void applyPhasedPolicy(Map<String, Object> finishResult, AgentRunState runState) {
        if (finishResult == null || runState == null) {
            return;
        }
        Map<String, Object> incomingPlan = finishResult.get("plan") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        Map<String, Object> previewMerged = AgentPlanGuard.mergePlans(runState.storedPlan(), incomingPlan);
        AgentAnalyticalIntake.mergeFinishIntakeIntoPlan(previewMerged, finishResult);

        if (!AgentPhasedPlanIntake.readyForApproval(previewMerged, finishResult)) {
            stripPrimaryApprovalSuggestions(finishResult);
        }

        AgentPhasedPlanIntake.Stage stage = AgentPhasedPlanIntake.resolveStage(runState);
        if (stage != AgentPhasedPlanIntake.Stage.SYNTHESIS && stage != AgentPhasedPlanIntake.Stage.FINALIZE) {
            finishResult.remove("handoffFrame");
            finishResult.remove("gapMatrix");
            finishResult.remove("pitfalls");
        }
        // Keep compact specBrief from BOOTSTRAP onward — merged into plan, not stripped from finish.
        if (stage.ordinal() < AgentPhasedPlanIntake.Stage.BOOTSTRAP.ordinal()) {
            finishResult.remove("specBrief");
        }
    }

    @SuppressWarnings("unchecked")
    private static void stripPrimaryApprovalSuggestions(Map<String, Object> finishResult) {
        Object raw = finishResult.get("suggestions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<Object> filtered = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
                if (Boolean.TRUE.equals(copy.get("primary"))) {
                    continue;
                }
                filtered.add(copy);
            }
        }
        if (filtered.isEmpty()) {
            finishResult.remove("suggestions");
        } else {
            finishResult.put("suggestions", filtered);
        }
    }
}
