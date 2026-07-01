package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates SIF handoffFrame JSON and delivery plan structure before execution.
 */
public final class AgentHandoffFrameValidator {

    private static final Set<String> REQUIRED_HANDOFF_KEYS = Set.of(
            "handoffId", "assignmentType", "domainAdapter", "specBrief", "gapMatrix", "deliveryPhases"
    );

    private AgentHandoffFrameValidator() {
    }

    public record ValidationResult(boolean ok, List<String> errors, List<String> warnings) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult fail(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, errors, warnings != null ? warnings : List.of());
        }
    }

    @SuppressWarnings("unchecked")
    public static ValidationResult validateHandoffFrame(Map<String, Object> frame) {
        if (frame == null || frame.isEmpty()) {
            return ValidationResult.fail(List.of("handoffFrame is missing"), List.of());
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String key : REQUIRED_HANDOFF_KEYS) {
            if (!frame.containsKey(key) || frame.get(key) == null) {
                errors.add("handoffFrame missing required key: " + key);
            }
        }

        Object assignmentType = frame.get("assignmentType");
        if (assignmentType instanceof String s && s.isBlank()) {
            errors.add("assignmentType must not be blank");
        }

        Object gapMatrix = frame.get("gapMatrix");
        if (gapMatrix instanceof List<?> rows) {
            for (int i = 0; i < rows.size(); i++) {
                Object row = rows.get(i);
                if (!(row instanceof Map<?, ?> map)) {
                    errors.add("gapMatrix[" + i + "] must be an object");
                    continue;
                }
                validateGapRow(i, (Map<String, Object>) map, errors, warnings);
            }
        } else if (gapMatrix != null) {
            errors.add("gapMatrix must be an array");
        }

        Object phases = frame.get("deliveryPhases");
        if (phases instanceof List<?> phaseList) {
            if (phaseList.isEmpty()) {
                errors.add("deliveryPhases must contain at least one phase");
            }
            for (int i = 0; i < phaseList.size(); i++) {
                Object phase = phaseList.get(i);
                if (!(phase instanceof Map<?, ?> map)) {
                    errors.add("deliveryPhases[" + i + "] must be an object");
                    continue;
                }
                Map<String, Object> phaseMap = (Map<String, Object>) map;
                if (!phaseMap.containsKey("phaseId") || !phaseMap.containsKey("steps")) {
                    errors.add("deliveryPhases[" + i + "] missing phaseId or steps");
                }
            }
        } else if (phases != null) {
            errors.add("deliveryPhases must be an array");
        }

        return errors.isEmpty()
                ? new ValidationResult(true, List.of(), warnings)
                : ValidationResult.fail(errors, warnings);
    }

    private static void validateGapRow(int index, Map<String, Object> row, List<String> errors, List<String> warnings) {
        requireField(row, "requirementId", index, errors);
        requireField(row, "capabilityId", index, errors);
        requireField(row, "status", index, errors);
        if (!row.containsKey("gapId")) {
            warnings.add("gapMatrix[" + index + "] missing gapId — will be auto-assigned");
        }
        Object blocksDev = row.get("blocksDev");
        if (blocksDev instanceof Boolean b && b) {
            Object gapStatus = row.get("gapStatus");
            if (!"open".equals(String.valueOf(gapStatus))) {
                warnings.add("gapMatrix[" + index + "] blocksDev=true but gapStatus is not open");
            }
        }
    }

    private static void requireField(Map<String, Object> row, String field, int index, List<String> errors) {
        Object value = row.get(field);
        if (value == null || (value instanceof String s && s.isBlank())) {
            errors.add("gapMatrix[" + index + "] missing " + field);
        }
    }

    public static Map<String, Object> minimalHandoffFrame(
            AgentAssignmentType type,
            String specBrief,
            List<Map<String, Object>> gapMatrix,
            List<Map<String, Object>> deliveryPhases
    ) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("handoffId", "handoff-" + System.currentTimeMillis());
        frame.put("assignmentType", type.id());
        frame.put("domainAdapter", type.defaultDomainAdapter());
        frame.put("specBrief", specBrief != null ? specBrief : "");
        frame.put("gapMatrix", gapMatrix != null ? gapMatrix : List.of());
        frame.put("deliveryPhases", deliveryPhases != null ? deliveryPhases : List.of());
        frame.put("pitfallCodes", AgentSpecGapCatalog.pitfallCodesForAssignment(type));
        return frame;
    }

    public static String formatHandoffSummary(Map<String, Object> frame) {
        StringBuilder sb = new StringBuilder();
        sb.append("Handoff: ").append(frame.getOrDefault("handoffId", "?")).append("\n");
        sb.append("Type: ").append(frame.getOrDefault("assignmentType", "?")).append("\n");
        sb.append("Adapter: ").append(frame.getOrDefault("domainAdapter", "?")).append("\n");
        Object brief = frame.get("specBrief");
        if (brief instanceof String s && !s.isBlank()) {
            sb.append("Brief: ").append(s.length() > 200 ? s.substring(0, 200) + "…" : s).append("\n");
        }
        Object gaps = frame.get("gapMatrix");
        if (gaps instanceof List<?> list) {
            long open = list.stream()
                    .filter(row -> row instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("blocksDev")))
                    .count();
            sb.append("Gap rows: ").append(list.size()).append(" (blocking: ").append(open).append(")\n");
        }
        Object phases = frame.get("deliveryPhases");
        if (phases instanceof List<?> list) {
            sb.append("Phases: ").append(list.size()).append("\n");
        }
        return sb.toString().trim();
    }
}
