package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluates recorded tool steps against conformance smoke cases and invariants —
 * same rules as {@link AgentPlatformTurnGuard}, keyed by catalog templates.
 */
final class AgentConformanceEvaluator {

    private AgentConformanceEvaluator() {
    }

    static List<String> verifySmokeCases(List<Map<String, Object>> steps, AgentAssignmentType type) {
        List<String> failures = new ArrayList<>();
        for (AgentConformanceCatalog.SmokeCase smokeCase : AgentConformanceCatalog.defaultSmokeCases(type)) {
            if (!passesSmokeCase(steps, smokeCase)) {
                failures.add(smokeCase.id() + ": " + smokeCase.scenario());
            }
        }
        return failures;
    }

    static List<String> verifyInvariants(List<Map<String, Object>> steps, AgentAssignmentType type) {
        List<String> failures = new ArrayList<>();
        for (AgentConformanceCatalog.Invariant invariant : AgentConformanceCatalog.defaultInvariants(type)) {
            if (!passesInvariant(steps, invariant)) {
                failures.add(invariant.id() + ": " + invariant.rule());
            }
        }
        return failures;
    }

    private static boolean passesSmokeCase(List<Map<String, Object>> steps, AgentConformanceCatalog.SmokeCase smokeCase) {
        String scenario = smokeCase.scenario().toLowerCase(Locale.ROOT);
        if (scenario.contains("list_variables") && scenario.contains("count")) {
            return AgentToolResultMetrics.listVariablesMaxCount(steps) > 0;
        }
        if (scenario.contains("get_mimic_diagram") || scenario.contains("elementcount")) {
            return AgentToolResultMetrics.hasVerifiedMimicDiagram(steps);
        }
        if (scenario.contains("get_dashboard_layout") || scenario.contains("widget")) {
            return AgentToolResultMetrics.hasVerifiedDashboardLayout(steps);
        }
        if (scenario.contains("configure_alert")) {
            return hasSuccessfulTool(steps, "configure_alert");
        }
        if (scenario.contains("validate_bundle")) {
            return hasSuccessfulTool(steps, "validate_bundle");
        }
        if (scenario.contains("dry_run_deploy")) {
            return hasSuccessfulTool(steps, "dry_run_deploy");
        }
        if (scenario.contains("run_workflow") || scenario.contains("fire_event")) {
            return hasSuccessfulTool(steps, "run_workflow") || hasSuccessfulTool(steps, "fire_event");
        }
        if (scenario.contains("discovery tool")) {
            return steps != null && !steps.isEmpty();
        }
        return true;
    }

    private static boolean passesInvariant(List<Map<String, Object>> steps, AgentConformanceCatalog.Invariant invariant) {
        return switch (invariant.errorCode()) {
            case "P_EMPTY_MIMIC" -> !requiresMimic(steps) || AgentToolResultMetrics.hasVerifiedMimicDiagram(steps);
            case "ALERT_MISSING" -> !requiresAlert(steps) || hasSuccessfulTool(steps, "configure_alert");
            default -> true;
        };
    }

    private static boolean requiresMimic(List<Map<String, Object>> steps) {
        return hasSuccessfulTool(steps, "save_mimic_diagram")
                || hasSuccessfulTool(steps, "get_mimic_diagram")
                || hasCreateObjectType(steps, "MIMIC");
    }

    private static boolean requiresAlert(List<Map<String, Object>> steps) {
        return hasSuccessfulTool(steps, "configure_alert") || hasCreateObjectType(steps, "ALERT");
    }

    private static boolean hasSuccessfulTool(List<Map<String, Object>> steps, String expectedTool) {
        if (steps == null) {
            return false;
        }
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))) {
                continue;
            }
            if (!expectedTool.equalsIgnoreCase(String.valueOf(step.get("tool")))) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            if ("OK".equals(String.valueOf(result.get("status")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCreateObjectType(List<Map<String, Object>> steps, String objectType) {
        if (steps == null) {
            return false;
        }
        for (Map<String, Object> step : steps) {
            if (!"tool".equals(String.valueOf(step.get("type")))
                    || !"create_object".equalsIgnoreCase(String.valueOf(step.get("tool")))) {
                continue;
            }
            Map<String, Object> args = stepMap(step, "arguments");
            Map<String, Object> result = stepMap(step, "result");
            if (!"OK".equals(String.valueOf(result.get("status")))) {
                continue;
            }
            if (objectType.equalsIgnoreCase(String.valueOf(args.get("type")))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
