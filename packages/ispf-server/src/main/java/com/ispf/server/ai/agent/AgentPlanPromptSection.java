package com.ispf.server.ai.agent;

import java.util.Map;

/**
 * System-prompt sections for plan-before-execute agent behaviour.
 */
public final class AgentPlanPromptSection {

    private AgentPlanPromptSection() {
    }

    public static String forRunState(AgentRunState runState) {
        if (runState == null) {
            return "";
        }
        return switch (runState.interactionMode()) {
            case ASK -> askMode();
            case PLAN -> planningMode(runState);
            case EXECUTE -> "";
            case AUTO -> autoMode(runState);
        };
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
                pump station, 8-layer blueprint, new application), FIRST run discovery tools and finish with a plan \
                (phase=plan) + questions — do NOT create objects until the user approves. \
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
                    + draft
                    + """
                    
                    Each planning turn: run discovery if needed, then finish with an EXTENDED plan.
                    - Keep all prior steps unless the user explicitly changed scope.
                    - ADD new steps from tool results (paths, profiles, variable names).
                    - You may send only new steps in plan.steps — the server merges with the draft.
                    - Prefer sending the full merged step list when convenient.
                    """;
        }
        return """
                
                ## PLANNING PHASE (active — """
                + phase
                + """
                )
                """
                + draftSection
                + """
                
                You MUST NOT mutate the platform in this phase (no create_object, set_variable, configure_*, \
                save_mimic_diagram, set_dashboard_layout, import_package, etc.).
                
                Allowed: list_*, get_*, search_*, describe_*, validate_bundle, dry_run_deploy, run_report preview.
                
                Tool names: use ONLY exact snake_case names from the tool catalog (e.g. save_workflow_bpmn, run_workflow). \
                NEVER invent display names or Russian labels as tool names.
                
                Workflow:
                1. Run discovery tools — list_objects, list_relative_models, list_virtual_profiles, list_variables as needed.
                2. Finish with a structured plan (NOT execution results). EXTEND the draft — add steps, do not replace the whole plan:
                {"type":"finish","summary":"...","result":{
                  "phase":"plan",
                  "interactive":true,
                  "plan":{"goal":"...","approach":"tree-first","layers":["devices","mimic","dashboard"],"steps":["1. ...","2. ..."],"assumptions":["paths from list_objects"]},
                  "questions":[{"id":"scope","text":"...","options":[{"label":"...","value":"..."}]}],
                  "suggestions":[{"label":"Утвердить и начать","message":"Утверждаю план, начинай выполнение","primary":true}]
                }}
                3. Wait for user approval. When user says «Утверждаю» or clicks the primary suggestion — next turn executes the plan.
                4. If the user picks a stage option or asks to refine — extend the plan with more detail; still no mutations until approval.
                
                Use ONLY paths/modelName/profile from tool results — never invent names from playbooks.
                """;
    }

    private static String approvedExecution(AgentRunState runState) {
        Map<String, Object> plan = runState.storedPlan();
        String planHint = plan == null || plan.isEmpty()
                ? "Execute the approved goal from the prior plan turn."
                : "Approved plan: " + plan;
        return """
                
                ## EXECUTION PHASE (plan approved)
                
                """
                + planHint
                + """
                
                Execute the approved plan step-by-step with tools. Do not restart planning unless the user changes scope.
                Ground truth rules still apply — list_objects before create_object, list_variables before bindings.
                """;
    }

    private static String askMode() {
        return """
                
                ## ASK MODE (read-only)
                
                Answer using discovery tools only. Never mutate the tree. Finish with explanation and optional suggestions.
                """;
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
}
