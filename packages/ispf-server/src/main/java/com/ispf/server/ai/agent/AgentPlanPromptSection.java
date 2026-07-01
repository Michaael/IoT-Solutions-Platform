package com.ispf.server.ai.agent;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * System-prompt sections for plan-before-execute agent behaviour.
 */
public final class AgentPlanPromptSection {

    private static final ObjectMapper PLAN_JSON = new ObjectMapper();

    private AgentPlanPromptSection() {
    }

    public static String forRunState(AgentRunState runState) {
        if (runState == null) {
            return "";
        }
        String base = switch (runState.interactionMode()) {
            case ASK -> askMode();
            case PLAN -> planMode(runState);
            case EXECUTE -> executeMode();
            case AUTO -> autoMode(runState);
        };
        if (runState.interactionMode() != AgentInteractionMode.ASK
                && runState.planPhase() == AgentPlanPhase.AWAITING_APPROVAL) {
            base += awaitingApprovalRefinement(runState);
        }
        return base;
    }

    private static String planMode(AgentRunState runState) {
        if (runState.planPhase() == AgentPlanPhase.APPROVED) {
            return approvedExecution(runState);
        }
        if (AgentPlanGuard.restrictsMutations(runState)) {
            return planningMode(runState);
        }
        return """
                
                ## PLAN MODE — plan before platform changes
                
                Before create_object, set_variable, configure_*, save_*, import_*, or other mutations, \
                run discovery and finish with phase=plan + full plan + questions. Wait for user approval.
                Read-only questions (list, show, describe, «какие…») — answer with discovery tools only; \
                no plan panel, no demo scenarios, no mutations.
                """;
    }

    private static String executeMode() {
        return """
                
                ## EXECUTE MODE — act immediately, do not ask first
                
                Execute the user's request with tools right away. Pick reasonable defaults when details are missing.
                Run minimal discovery (list_objects, list_variables) only when required for ground truth, then mutate.
                
                FORBIDDEN in result:
                - phase=plan, plan, questions[]
                - clarifying suggestions before acting («Какой сценарий?», «Утвердить полный план»)
                
                Optional: result.suggestions only AFTER successful execution (open device, open dashboard).
                """;
    }

    private static String autoMode(AgentRunState runState) {
        if (runState.planPhase() == AgentPlanPhase.APPROVED) {
            return approvedExecution(runState);
        }
        if (AgentPlanGuard.restrictsMutations(runState)) {
            return planningMode(runState);
        }
        return """
                
                PLAN-BEFORE-EXECUTE (auto): For complex multi-object tasks (SCADA + devices + dashboards, \
                pump station, 8-layer blueprint, new application), FIRST run discovery tools and finish with a FULL-TZ plan \
                (phase=plan) + questions — do NOT create objects until the user approves. \
                Default scope = complete TZ / 8-layer blueprint — never auto-shrink to MVP unless the user explicitly asks. \
                Simple read-only or single-step tasks may execute immediately.
                """;
    }

    private static String planningMode(AgentRunState runState) {
        String phase = runState.planPhase().storageValue();
        Map<String, Object> draft = runState.storedPlan();
        String draftSection = "";
        if (draft != null && !draft.isEmpty()) {
            draftSection = """
                    
                    ### Current draft plan (EXTEND — do not rewrite from scratch)
                    """
                    + formatPlanJson(draft)
                    + """
                    
                    Each planning turn: run discovery if needed, then finish with an EXTENDED plan.
                    - Keep all prior steps unless the user explicitly changed scope.
                    - ADD new steps from tool results (paths, profiles, variable names).
                    - Include specBrief, gapMatrix, handoffFrame for complex assignments (SIF).
                    """;
        }
        String recipeHint = AgentSpecPlanValidator.hasRecipeDiscovery(List.of())
                ? ""
                : "\nComplex task: include search_platform_recipes or get_automation_schema in discovery before plan finish.\n";
        AgentPhasedPlanIntake.Stage intakeStage = AgentPhasedPlanIntake.resolveStage(runState);
        return """
                
                ## PLANNING PHASE (active — """
                + phase
                + """
                )
                """
                + draftSection
                + recipeHint
                + AgentPhasedPlanIntake.promptSection(intakeStage)
                + AgentAnalyticalIntake.guide()
                + """
                
                You MUST NOT mutate the platform in this phase (no create_object, set_variable, configure_*, \
                save_mimic_diagram, set_dashboard_layout, import_package, etc.).
                
                Allowed: list_*, get_*, search_*, describe_*, validate_bundle, dry_run_deploy, run_report preview.
                
                Phased intake (prevents JSON truncation):
                - Analysis first: specBrief + executiveSummary before deep implementation sections.
                - Each turn: EXTEND draft — ≤2 NEW sections (0 on SYNTHESIS — enrich only).
                - Approval ONLY when analytical completeness gate passes (all 8 core sections substantive).
                """
                + AgentPlanSections.guide()
                + """
                
                Include primary suggestion ONLY when completeness gate passes:
                {"label":"Утвердить полный план","message":"Утверждаю план, начинай выполнение","primary":true}
                """;
    }

    private static String approvedExecution(AgentRunState runState) {
        Map<String, Object> plan = runState.storedPlan();
        int completed = runState.completedPlanSteps().size();
        int totalSteps = planStepCount(plan);
        String progress = totalSteps > 0
                ? "Completed tools: " + completed + "/" + totalSteps + " plan steps. "
                : "";
        String planHint = plan == null || plan.isEmpty()
                ? "Execute the approved goal from the prior plan turn."
                : progress + "Approved plan:\n" + formatPlanJson(plan);
        return """
                
                ## EXECUTION PHASE (plan approved)
                
                """
                + planHint
                + """
                
                Execute the approved plan step-by-step with tools.
                FORBIDDEN in this phase: phase=plan, questions[], suggestions[] — unless user explicitly changes scope.
                Ground truth rules still apply — list_objects before create_object, list_variables before bindings.
                Never claim success for tool steps that returned ERROR.
                """;
    }

    private static int planStepCount(Map<String, Object> plan) {
        return AgentPlanSections.totalStepCount(plan);
    }

    private static String formatPlanJson(Map<String, Object> plan) {
        try {
            return PLAN_JSON.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
        } catch (Exception ex) {
            return String.valueOf(plan);
        }
    }

    private static String askMode() {
        return """
                
                ## ASK MODE (read-only) — overrides PLAN-BEFORE-EXECUTE
                
                Answer the user's question using discovery tools only (list_*, get_*, search_*, describe_*).
                Never mutate the tree. Do NOT start demo creation or propose build scenarios unless asked.
                
                Finish with a plain answer in summary — FORBIDDEN in result:
                - phase=plan
                - plan / plan.sections / plan.steps
                - questions[]
                - primary approval suggestions («Утвердить полный план», «Да, начинаем»)
                
                Optional: result.suggestions with read-only follow-ups (list, open, describe) — no mutations.
                Example: list_objects parent=root.platform.devices and root.platform.dashboards, then finish with counts and paths.
                """;
    }

    public static String forImageAttachments(AgentRunState runState) {
        if (runState != null && runState.interactionMode() == AgentInteractionMode.ASK) {
            return """
                    
                    ## IMAGE ATTACHMENT (this turn)
                    
                    The user attached one or more images (P&ID, SCADA sketch, dashboard mockup, etc.).
                    Describe visible equipment, layout, labels, and connections from the image.
                    Ask mode: read-only — describe and answer questions; do NOT propose phase=plan or mutations.
                    """;
        }
        return forImageAttachments();
    }

    public static String forImageAttachments() {
        return """
                
                ## IMAGE ATTACHMENT (this turn)
                
                The user attached one or more images (P&ID, SCADA sketch, dashboard mockup, etc.).
                - Describe visible equipment, layout, labels, and connections from the image.
                - Propose a tree-first plan for mimic (save_mimic_diagram) and/or dashboard (set_dashboard_layout) — do NOT invent paths.
                - Run list_objects / list_variables discovery before mutations; finish with phase=plan when scope is complex.
                """;
    }

    private static String awaitingApprovalRefinement(AgentRunState runState) {
        Map<String, Object> draft = runState.storedPlan();
        String draftHint = draft == null || draft.isEmpty() ? "" : "\nCurrent draft:\n" + formatPlanJson(draft) + "\n";
        return """
                
                ## PLAN REFINEMENT (awaiting approval)
                """
                + draftHint
                + """
                If the user asks to extend or change the plan, finish with phase=plan and a FULL plan object.
                User approval triggers: «Да, начинаем», «Утверждаю», «OK, start», or primary suggestion.
                """;
    }
}
