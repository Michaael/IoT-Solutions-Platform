package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * Multi-turn phased plan intake — analytical TZ decomposition + incremental sections (avoids JSON truncation).
 */
public final class AgentPhasedPlanIntake {

    public enum Stage {
        DISCOVERY,
        /** specBrief + ground_truth + intent_scope (analysis from implicit phrases). */
        BOOTSTRAP,
        CORE,
        AUTOMATION,
        HMI,
        /** Enrich thin sections + intake artifacts; no new layer skipping. */
        SYNTHESIS,
        /** gapMatrix, handoffFrame, executiveSummary — approval when complete. */
        FINALIZE
    }

    private AgentPhasedPlanIntake() {
    }

    public static Stage resolveStage(AgentRunState runState) {
        return resolveStageFromPlan(runState != null ? runState.storedPlan() : Map.of());
    }

    public static Stage resolveStageAfterMerge(Map<String, Object> mergedPlan) {
        return resolveStageFromPlan(mergedPlan);
    }

    private static Stage resolveStageFromPlan(Map<String, Object> plan) {
        if (plan == null || plan.isEmpty()) {
            return Stage.DISCOVERY;
        }
        int sections = AgentPlanSections.readSections(plan).size();
        if (sections == 0) {
            return Stage.BOOTSTRAP;
        }
        if (sections < 3) {
            return Stage.CORE;
        }
        if (sections < 5) {
            return Stage.AUTOMATION;
        }
        if (sections < 7) {
            return Stage.HMI;
        }
        if (sections < AgentAnalyticalIntake.REQUIRED_SECTION_IDS.size()) {
            return Stage.HMI;
        }
        if (!AgentAnalyticalIntake.readyForApproval(plan, Map.of())) {
            return Stage.SYNTHESIS;
        }
        return Stage.FINALIZE;
    }

    public static boolean readyForApproval(Map<String, Object> plan, Map<String, Object> finishResult) {
        return AgentAnalyticalIntake.readyForApproval(plan, finishResult);
    }

    public static String promptSection(Stage stage) {
        return switch (stage) {
            case DISCOVERY -> """
                    
                    ### Analytical intake — discovery (turn 1)
                    Extract implicit requirements from user text — do NOT jump to device creation.
                    Tools first: get_automation_schema topic=projectBlueprint, search_platform_recipes, list_objects.
                    Optional tiny finish only if user demands plan immediately (see BOOTSTRAP).
                    """;
            case BOOTSTRAP -> """
                    
                    ### Analytical intake — bootstrap (analysis + scope)
                    Finish COMPACT JSON. This turn MUST include:
                    - specBrief: title, businessGoal, entities[], functionalRequirements[] (≥3 with sourcePhrase from user/TZ)
                    - plan.executiveSummary: 3–5 sentences binding TZ to platform delivery
                    - plan.sections[] AT MOST 2: ground_truth, intent_scope
                      Each: summary 2–4 sentences, relatedFrIds[], deliverables[], 2–4 concrete steps
                    - questions[] AT MOST 3 with options[] (user may batch answers)
                    OMIT: gapMatrix, handoffFrame (until SYNTHESIS/FINALIZE). NO approval suggestion yet.
                    """;
            case CORE -> """
                    
                    ### Analytical intake — core layers
                    EXTEND draft. ADD ≤2 sections: model_strategy, source_layer.
                    Tie each summary to FR ids from specBrief. Name every entity (paths, profiles).
                    steps: concrete tools per entity — not «создать все устройства».
                    ≤3 new questions if TBD remains.
                    """;
            case AUTOMATION -> """
                    
                    ### Analytical intake — automation layers
                    EXTEND draft. ADD ≤2 sections: aggregation_layer, alert_layer (+ correlation_layer or N/A summary).
                    Bindings/alerts must reference confirmed variable names from source_layer deliverables.
                    """;
            case HMI -> """
                    
                    ### Analytical intake — operator + validation
                    EXTEND draft. ADD: operator_layer, validation_layer.
                    objectTypesCoverage[] — one row per ObjectType used or N/A with reason.
                    validation_layer: smoke checklist mapped to FR acceptance criteria.
                    """;
            case SYNTHESIS -> """
                    
                    ### Analytical intake — synthesis (enrich, do NOT skip analysis)
                    Do NOT add new layer ids unless missing from required set.
                    ENRICH existing sections: expand summary, steps, deliverables, relatedFrIds.
                    ADD/MERGE: gapMatrix (FR → capability), compact handoffFrame, plan.executiveSummary if thin.
                    Resolve open assumptions from user answers in chat.
                    NO primary approval until analytical completeness gate passes.
                    """;
            case FINALIZE -> """
                    
                    ### Analytical intake — finalize for approval
                    Polish gapMatrix, handoffFrame, conformance.smokeCases.
                    Ensure every section meets quality: summary ≥80 chars, ≥2 steps, deliverables[].
                    NOW include primary «Утвердить полный план» ONLY if all required sections + specBrief complete.
                    summary <= 400 chars; never re-dump entire plan — patch gaps only.
                    """;
        };
    }

    public static String compactFinishNudge(Stage stage) {
        return """
                Truncated JSON — reply with ONLY one valid COMPACT finish object. Stage=%s.
                EXTEND draft; preserve specBrief and prior sections.
                Bootstrap: specBrief + ground_truth + intent_scope.
                Synthesis: enrich thin sections + gapMatrix rows (no prose dump).
                Rules: max 2 NEW sections/turn except SYNTHESIS (0 new sections); max 3 questions/turn.
                """
                .formatted(stage.name());
    }

    public static int maxSectionsThisTurn(Stage stage) {
        return switch (stage) {
            case DISCOVERY -> 0;
            case BOOTSTRAP -> 2;
            case CORE, AUTOMATION, HMI -> 2;
            case SYNTHESIS -> 0;
            case FINALIZE -> 1;
        };
    }

    public static int maxQuestionsThisTurn(Stage stage) {
        return stage == Stage.FINALIZE ? 6 : 3;
    }
}
