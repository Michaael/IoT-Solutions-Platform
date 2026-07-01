package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared policy: which ERROR tool steps still block finish after later recovery in the same turn.
 */
final class AgentTurnToolErrors {

    private AgentTurnToolErrors() {
    }

    static boolean hasUnresolvedErrors(List<Map<String, Object>> steps) {
        if (steps == null) {
            return false;
        }
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if ("tool".equals(String.valueOf(step.get("type"))) && isUnresolvedError(steps, step, i)) {
                return true;
            }
        }
        return false;
    }

    static List<String> unresolvedErrorSummaries(List<Map<String, Object>> steps) {
        List<String> errors = new ArrayList<>();
        if (steps == null) {
            return errors;
        }
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!"tool".equals(String.valueOf(step.get("type"))) || !isUnresolvedError(steps, step, i)) {
                continue;
            }
            Map<String, Object> result = stepMap(step, "result");
            errors.add(String.valueOf(step.get("tool")) + ": " + result.get("error"));
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static boolean isUnresolvedError(
            List<Map<String, Object>> steps,
            Map<String, Object> step,
            int stepIndex
    ) {
        Object result = step.get("result");
        if (!(result instanceof Map<?, ?> map)) {
            return false;
        }
        if (!"ERROR".equals(String.valueOf(map.get("status")))) {
            return false;
        }
        if (isRecoveredByLaterSuccess(steps, step, stepIndex)) {
            return false;
        }
        String tool = String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
        if ("create_object".equals(tool) || "create_virtual_device".equals(tool)) {
            String error = String.valueOf(map.get("error"));
            if (error.startsWith("Object exists:") || map.get("existingPath") != null) {
                String path = AgentGroundTruthGuard.resolveCanonicalPath(steps, existingPathFromResult(map));
                if (!path.isBlank() && AgentGroundTruthGuard.isObjectPathGrounded(steps, path)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isRecoveredByLaterSuccess(
            List<Map<String, Object>> steps,
            Map<String, Object> errorStep,
            int errorIndex
    ) {
        String targetPath = resolveErrorTargetPath(errorStep);
        if (!targetPath.isBlank()) {
            for (int i = errorIndex + 1; i < steps.size(); i++) {
                Map<String, Object> later = steps.get(i);
                if (!isOkToolStep(later)) {
                    continue;
                }
                String laterPath = resolveStepTargetPath(later);
                if (!laterPath.isBlank() && pathsEqual(targetPath, laterPath)) {
                    return true;
                }
            }
        }
        String tool = String.valueOf(errorStep.get("tool")).toLowerCase(Locale.ROOT);
        for (int i = errorIndex + 1; i < steps.size(); i++) {
            Map<String, Object> later = steps.get(i);
            if (tool.equals(String.valueOf(later.get("tool")).toLowerCase(Locale.ROOT)) && isOkToolStep(later)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static String resolveErrorTargetPath(Map<String, Object> step) {
        String fromArgs = resolveStepTargetPath(step);
        if (!fromArgs.isBlank()) {
            return fromArgs;
        }
        Map<String, Object> result = step.get("result") instanceof Map<?, ?> rawResult
                ? (Map<String, Object>) rawResult
                : Map.of();
        String error = String.valueOf(result.get("error"));
        if (error.startsWith("Object exists:")) {
            return normalizePath(error.substring("Object exists:".length()));
        }
        int turnIdx = error.lastIndexOf(" in this turn: ");
        if (turnIdx >= 0) {
            return normalizePath(error.substring(turnIdx + " in this turn: ".length()));
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String resolveStepTargetPath(Map<String, Object> step) {
        Map<String, Object> args = step.get("arguments") instanceof Map<?, ?> rawArgs
                ? (Map<String, Object>) rawArgs
                : Map.of();
        String path = AgentGroundTruthGuard.resolveObjectPath(args);
        if (!path.isBlank()) {
            return normalizePath(path);
        }
        String tool = String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
        if ("create_object".equals(tool) || "create_virtual_device".equals(tool)) {
            String parent = AgentGroundTruthGuard.resolveParentPath(args);
            String name = stringArg(args, "name");
            if (!parent.isBlank() && !name.isBlank()) {
                return normalizePath(parent + "." + name);
            }
        }
        Map<String, Object> result = step.get("result") instanceof Map<?, ?> rawResult
                ? (Map<String, Object>) rawResult
                : Map.of();
        Object resultPath = result.get("path");
        if (resultPath != null && !String.valueOf(resultPath).isBlank()) {
            return normalizePath(String.valueOf(resultPath));
        }
        return "";
    }

    private static boolean isOkToolStep(Map<String, Object> step) {
        if (!"tool".equals(String.valueOf(step.get("type")))) {
            return false;
        }
        Object result = step.get("result");
        return result instanceof Map<?, ?> map && "OK".equals(String.valueOf(map.get("status")));
    }

    private static String existingPathFromResult(Map<?, ?> map) {
        Object existing = map.get("existingPath");
        if (existing != null && !String.valueOf(existing).isBlank()) {
            return String.valueOf(existing);
        }
        String error = String.valueOf(map.get("error"));
        if (error.startsWith("Object exists:")) {
            return error.substring("Object exists:".length()).trim();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepMap(Map<String, Object> step, String key) {
        return step.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean pathsEqual(String a, String b) {
        return normalizePath(a).equalsIgnoreCase(normalizePath(b));
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replaceAll("\\.+", ".").replaceAll("\\.$", "");
    }
}
