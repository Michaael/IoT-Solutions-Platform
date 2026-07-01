package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates SIF plan finish payloads before capture and approval.
 */
public final class AgentSpecPlanValidator {

    private static final Set<String> DISCOVERY_TOOLS = Set.of(
            "search_platform_recipes",
            "get_automation_schema",
            "search_context"
    );

    private AgentSpecPlanValidator() {
    }

    public record ValidationResult(boolean ok, List<String> errors, List<String> warnings) {
        public static ValidationResult pass(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings != null ? warnings : List.of());
        }

        public static ValidationResult fail(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, errors, warnings != null ? warnings : List.of());
        }
    }

    public static ValidationResult validatePlanFinish(
            Map<String, Object> finishResult,
            String userMessage,
            List<Map<String, Object>> steps,
            AgentRunState runState
    ) {
        if (finishResult == null || !AgentPlanGuard.isPlanFinish(finishResult)) {
            return ValidationResult.pass(List.of());
        }
        AgentAssignmentClassifier.Classification classification = AgentAssignmentClassifier.classify(userMessage);
        if (classification.fastPath()) {
            return ValidationResult.pass(List.of());
        }
        AgentPhasedPlanIntake.Stage stage = runState != null
                ? AgentPhasedPlanIntake.resolveStage(runState)
                : AgentPhasedPlanIntake.Stage.BOOTSTRAP;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Object specBrief = finishResult.get("specBrief");
        if (specBrief == null && classification.type().requiresFullSpecIntake()
                && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
            warnings.add("specBrief missing — include assignmentType, entities, functionalRequirements");
        } else if (specBrief != null && stage.ordinal() < AgentPhasedPlanIntake.Stage.BOOTSTRAP.ordinal()) {
            warnings.add("defer specBrief to BOOTSTRAP stage — discovery first");
        }

        Object gapMatrix = finishResult.get("gapMatrix");
        if (gapMatrix instanceof List<?> rows) {
            if (rows.isEmpty() && classification.type().requiresFullSpecIntake()
                    && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
                warnings.add("gapMatrix empty — map each functional requirement to a capability row");
            }
        } else if (classification.type().requiresFullSpecIntake()
                && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
            warnings.add("gapMatrix missing — required before approval on complex assignments");
        } else if (gapMatrix != null && stage != AgentPhasedPlanIntake.Stage.FINALIZE) {
            warnings.add("defer gapMatrix to FINALIZE stage");
        }

        Object questions = finishResult.get("questions");
        int minQuestions = stage == AgentPhasedPlanIntake.Stage.FINALIZE ? 6 : 3;
        if (questions instanceof List<?> qList) {
            if (qList.size() > AgentPhasedPlanIntake.maxQuestionsThisTurn(stage)
                    && stage.ordinal() < AgentPhasedPlanIntake.Stage.FINALIZE.ordinal()) {
                warnings.add("this turn: max 3 new questions — add more on next planning turns");
            }
            if (qList.size() < minQuestions && classification.type().requiresFullSpecIntake()
                    && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
                warnings.add("questions should have at least 6 items with options[] before approval");
            } else if (qList.size() < 3 && classification.type().requiresFullSpecIntake()
                    && stage.ordinal() >= AgentPhasedPlanIntake.Stage.BOOTSTRAP.ordinal()) {
                warnings.add("questions should have at least 3 items with options[]");
            }
            for (int i = 0; i < qList.size(); i++) {
                Object q = qList.get(i);
                if (q instanceof Map<?, ?> qMap && !(qMap.get("options") instanceof List<?> opts && !opts.isEmpty())) {
                    warnings.add("questions[" + i + "] missing options[] — each question needs clickable choices");
                }
            }
        } else if (classification.type().requiresFullSpecIntake()
                && stage.ordinal() >= AgentPhasedPlanIntake.Stage.BOOTSTRAP.ordinal()) {
            warnings.add("questions missing — ask entities, data source, models before execution");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = finishResult.get("plan") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        Object phases = plan.get("deliveryPhases");
        if (phases == null) {
            phases = finishResult.get("deliveryPhases");
        }
        if (phases instanceof List<?> phaseList) {
            if (phaseList.isEmpty() && classification.type().requiresFullSpecIntake()
                    && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
                warnings.add("deliveryPhases must contain phaseId=full with 8-layer steps for complex assignments");
            } else if (stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
                boolean hasFullPhase = phaseList.stream()
                        .filter(p -> p instanceof Map<?, ?>)
                        .map(p -> String.valueOf(((Map<?, ?>) p).get("phaseId")).toLowerCase(Locale.ROOT))
                        .anyMatch(id -> "full".equals(id) || "production".equals(id));
                if (!hasFullPhase && classification.type().requiresFullSpecIntake()) {
                    warnings.add("deliveryPhases should use phaseId=full (complete TZ) — avoid mvp/phase2 unless user explicitly requested");
                }
            }
        } else if (classification.type().requiresFullSpecIntake()
                && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
            warnings.add("deliveryPhases missing — include single phaseId=full with 8-layer project blueprint steps");
        }

        Object handoff = finishResult.get("handoffFrame");
        if (handoff instanceof Map<?, ?> frameMap) {
            if (stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
                @SuppressWarnings("unchecked")
                AgentHandoffFrameValidator.ValidationResult handoffResult =
                        AgentHandoffFrameValidator.validateHandoffFrame((Map<String, Object>) frameMap);
                errors.addAll(handoffResult.errors());
                warnings.addAll(handoffResult.warnings());
            } else {
                warnings.add("defer handoffFrame to FINALIZE stage");
            }
        } else if (classification.type().requiresFullSpecIntake()
                && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
            warnings.add("handoffFrame recommended — include handoffId, gapMatrix, deliveryPhases, invariants");
        }

        Object pitfalls = finishResult.get("pitfalls");
        if (pitfalls instanceof List<?> pitList && pitList.size() < 3
                && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
            warnings.add("pitfalls should include at least 3 taxonomy codes (P_PATH_GROUND_TRUTH, …)");
        }

        if (AgentAssignmentClassifier.isComplexAssignment(userMessage) && !hasRecipeDiscovery(steps)) {
            warnings.add("Complex task: include search_platform_recipes or get_automation_schema in discovery steps");
        }

        Object assignmentType = finishResult.get("assignmentType");
        if (assignmentType == null && specBrief instanceof Map<?, ?> brief) {
            assignmentType = brief.get("assignmentType");
        }
        if (assignmentType == null && classification.type().requiresFullSpecIntake()
                && stage.ordinal() >= AgentPhasedPlanIntake.Stage.CORE.ordinal()) {
            warnings.add("assignmentType missing — classifier suggests: " + classification.type().id());
        }

        Object stepsRaw = plan.get("steps");
        if (AgentPlanSections.hasSections(plan)) {
            int sectionSteps = AgentPlanSections.totalStepCount(plan);
            if (sectionSteps > 20) {
                warnings.add("plan sections contain " + sectionSteps + " steps — split across planning turns if JSON truncates");
            }
        } else if (stepsRaw instanceof List<?> stepList && stepList.size() > 20) {
            warnings.add("plan.steps has " + stepList.size() + " items — use plan.sections[] for detailed TZ plans");
        } else if (stepsRaw instanceof List<?> stepList && stepList.size() > 15) {
            warnings.add("plan.steps has " + stepList.size() + " items — prefer plan.sections[] with ≥5 sections for complex TZ");
        }

        Object sectionsRaw = plan.get("sections");
        if (sectionsRaw instanceof List<?> sectionList) {
            int maxThisTurn = AgentPhasedPlanIntake.maxSectionsThisTurn(stage);
            if (maxThisTurn > 0 && sectionList.size() > maxThisTurn) {
                warnings.add("this turn: max " + maxThisTurn + " new sections — split across planning turns");
            }
            Map<String, Object> previewMerged = AgentPlanGuard.mergePlans(
                    runState != null ? runState.storedPlan() : Map.of(),
                    plan
            );
            int totalSections = AgentPlanSections.readSections(previewMerged).size();
            if (totalSections < 5 && classification.type().requiresFullSpecIntake()
                    && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
                warnings.add("plan.sections should have at least 5 sections total before approval");
            }
            for (int i = 0; i < sectionList.size(); i++) {
                Object item = sectionList.get(i);
                if (!(item instanceof Map<?, ?> sectionMap)) {
                    warnings.add("plan.sections[" + i + "] must be an object");
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> section = (Map<String, Object>) sectionMap;
                String summary = section.get("summary") != null ? String.valueOf(section.get("summary")).trim() : "";
                if (summary.length() < 40 && classification.type().requiresFullSpecIntake()
                        && stage.ordinal() >= AgentPhasedPlanIntake.Stage.CORE.ordinal()) {
                    warnings.add("plan.sections[" + i + "] summary too short — describe scope, tools, deliverables");
                }
                Object secSteps = section.get("steps");
                if (secSteps instanceof List<?> sl && sl.size() > 3
                        && stage.ordinal() < AgentPhasedPlanIntake.Stage.FINALIZE.ordinal()) {
                    warnings.add("plan.sections[" + i + "] max 3 steps this turn — add more on next turn");
                } else if (secSteps instanceof List<?> sl && sl.size() < 3 && classification.type().requiresFullSpecIntake()
                        && stage == AgentPhasedPlanIntake.Stage.FINALIZE) {
                    warnings.add("plan.sections[" + i + "] needs ≥3 concrete tool steps");
                }
            }
        } else if (classification.type().requiresFullSpecIntake()
                && stage.ordinal() >= AgentPhasedPlanIntake.Stage.BOOTSTRAP.ordinal()) {
            warnings.add("plan.sections missing — add sectional plan incrementally (see AgentPlanSections.guide)");
        }

        Object coverage = plan.get("objectTypesCoverage");
        if (coverage == null) {
            coverage = finishResult.get("objectTypesCoverage");
        }
        if (!(coverage instanceof List<?> covList) || covList.isEmpty()) {
            if (classification.type().requiresFullSpecIntake()
                    && stage.ordinal() >= AgentPhasedPlanIntake.Stage.HMI.ordinal()) {
                warnings.add("objectTypesCoverage missing — map each relevant ObjectType with action/reason");
            }
        }

        if (finishResult.get("specBrief") instanceof Map<?, ?> && estimateJsonSize(finishResult) > 24_000) {
            warnings.add("finish result payload very large — omit specBrief/gapMatrix prose on first plan turn");
        }

        if (estimateJsonSize(finishResult) > 120_000) {
            warnings.add("finish payload very large — risk of LLM truncation; split across turns if parse fails");
        }

        Map<String, Object> previewPlan = AgentPlanGuard.mergePlans(
                runState != null ? runState.storedPlan() : Map.of(),
                plan
        );
        AgentAnalyticalIntake.mergeFinishIntakeIntoPlan(previewPlan, finishResult);
        if (classification.type().requiresFullSpecIntake()
                && stage.ordinal() >= AgentPhasedPlanIntake.Stage.SYNTHESIS.ordinal()) {
            warnings.addAll(AgentAnalyticalIntake.completenessGaps(previewPlan, finishResult));
        }

        return errors.isEmpty()
                ? ValidationResult.pass(warnings)
                : ValidationResult.fail(errors, warnings);
    }

    private static int estimateJsonSize(Map<String, Object> finishResult) {
        return String.valueOf(finishResult).length();
    }

    public static boolean hasRecipeDiscovery(List<Map<String, Object>> steps) {
        if (steps == null) {
            return false;
        }
        return steps.stream()
                .filter(step -> "tool".equals(String.valueOf(step.get("type"))))
                .map(step -> String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT))
                .anyMatch(DISCOVERY_TOOLS::contains);
    }

    public static String formatValidationHint(ValidationResult result) {
        if (result == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!result.errors().isEmpty()) {
            sb.append("Plan validation errors:\n");
            result.errors().forEach(e -> sb.append("- ").append(e).append("\n"));
        }
        if (!result.warnings().isEmpty()) {
            sb.append("Plan validation warnings:\n");
            result.warnings().forEach(w -> sb.append("- ").append(w).append("\n"));
        }
        return sb.toString().trim();
    }
}
