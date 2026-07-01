package com.ispf.server.ai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class AgentJsonProtocol {

    public record AgentAction(
            String type,
            String toolName,
            Map<String, Object> arguments,
            String summary,
            Map<String, Object> result
    ) {
    }

    private AgentJsonProtocol() {
    }

    public static AgentAction parse(ObjectMapper objectMapper, String content) throws Exception {
        String normalized = normalizeModelContent(content);
        JsonNode root = readActionRoot(objectMapper, normalized);
        return parseActionNode(objectMapper, root);
    }

    /**
     * Heuristic: model output was likely cut off mid-JSON (max_tokens / length stop).
     */
    public static boolean looksLikeTruncatedContent(String content) {
        String normalized = normalizeModelContent(content);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String trimmed = normalized.trim();
        if (!trimmed.startsWith("{") && !trimmed.contains("{\"type\"")) {
            return false;
        }
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return false;
        }
        if (findJsonObjectEnd(trimmed, start) < 0) {
            return true;
        }
        if (trimmed.endsWith(",") || trimmed.endsWith(":") || trimmed.endsWith("\"")) {
            return true;
        }
        return false;
    }

    /**
     * Best-effort recovery when the model started a finish action but output was cut off mid-JSON.
     */
    @SuppressWarnings("unchecked")
    public static Optional<AgentAction> trySalvageTruncatedFinish(ObjectMapper objectMapper, String content) {
        if (!looksLikeTruncatedContent(content)) {
            return Optional.empty();
        }
        String normalized = normalizeModelContent(content);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.contains("\"type\":\"finish\"") && !lower.contains("\"type\": \"finish\"")) {
            return Optional.empty();
        }
        String summary = extractJsonStringField(normalized, "summary");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", "plan");
        result.put("interactive", true);
        result.put("planPartial", true);

        Map<String, Object> plan = new LinkedHashMap<>();
        String goal = extractNestedJsonStringField(normalized, "plan", "goal");
        if (goal == null) {
            goal = extractJsonStringField(normalized, "goal");
        }
        if (goal != null && !goal.isBlank()) {
            plan.put("goal", goal);
        }
        List<Map<String, Object>> sections = extractCompleteJsonObjectsInArray(objectMapper, normalized, "sections");
        if (!sections.isEmpty()) {
            plan.put("sections", sections);
        }
        if (!plan.isEmpty()) {
            result.put("plan", plan);
        }
        List<Map<String, Object>> questions = extractCompleteJsonObjectsInArray(objectMapper, normalized, "questions");
        if (!questions.isEmpty()) {
            result.put("questions", questions);
        }
        if (summary == null && plan.isEmpty() && questions.isEmpty()) {
            return Optional.empty();
        }
        String effectiveSummary = summary != null && !summary.isBlank()
                ? summary
                : "Частичный план сохранён (ответ модели был обрезан). Продолжите расширение плана на следующем сообщении.";
        return Optional.of(new AgentAction("finish", null, null, effectiveSummary, result));
    }

    private static String extractJsonStringField(String content, String field) {
        return extractNestedJsonStringField(content, null, field);
    }

    private static String extractNestedJsonStringField(String content, String parentField, String field) {
        if (content == null || field == null || field.isBlank()) {
            return null;
        }
        String searchIn = content;
        if (parentField != null && !parentField.isBlank()) {
            int parentIdx = content.indexOf("\"" + parentField + "\"");
            if (parentIdx < 0) {
                return null;
            }
            int brace = content.indexOf('{', parentIdx);
            if (brace < 0) {
                return null;
            }
            searchIn = content.substring(brace);
        }
        int keyIdx = searchIn.indexOf("\"" + field + "\"");
        if (keyIdx < 0) {
            return null;
        }
        int colon = searchIn.indexOf(':', keyIdx + field.length());
        if (colon < 0) {
            return null;
        }
        int quote = searchIn.indexOf('"', colon + 1);
        if (quote < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = quote + 1; i < searchIn.length(); i++) {
            char ch = searchIn.charAt(i);
            if (escaped) {
                sb.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                return sb.toString().trim();
            }
            sb.append(ch);
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractCompleteJsonObjectsInArray(
            ObjectMapper objectMapper,
            String content,
            String arrayField
    ) {
        if (content == null || arrayField == null || arrayField.isBlank()) {
            return List.of();
        }
        int fieldIdx = content.indexOf("\"" + arrayField + "\"");
        if (fieldIdx < 0) {
            return List.of();
        }
        int bracket = content.indexOf('[', fieldIdx);
        if (bracket < 0) {
            return List.of();
        }
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (int i = bracket + 1; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',') {
                continue;
            }
            if (ch == ']') {
                break;
            }
            if (ch != '{') {
                break;
            }
            int end = findJsonObjectEnd(content, i);
            if (end < 0) {
                break;
            }
            String fragment = content.substring(i, end + 1);
            try {
                JsonNode node = objectMapper.readTree(fragment);
                if (node.isObject()) {
                    parsed.add(objectMapper.convertValue(node, Map.class));
                }
            } catch (Exception ignored) {
                break;
            }
            i = end;
        }
        return parsed;
    }

    /**
     * Operator copilot fallback when the model answers in plain language after tool steps.
     */
    public static Optional<AgentAction> tryParsePlainTextFinish(String content) {
        String normalized = normalizeModelContent(content);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        String trimmed = normalized.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return Optional.empty();
        }
        if (trimmed.length() < 16) {
            return Optional.empty();
        }
        if (trimmed.contains("{\"type\"") || trimmed.contains("{\"type\":")) {
            return Optional.empty();
        }
        return Optional.of(new AgentAction("finish", null, null, trimmed, Map.of()));
    }

    private static JsonNode readActionRoot(ObjectMapper objectMapper, String normalized) throws Exception {
        String trimmed = normalized != null ? normalized.trim() : "";
        if (trimmed.startsWith("{")) {
            try {
                JsonNode object = objectMapper.readTree(trimmed);
                if (object.isObject()) {
                    parseActionNode(objectMapper, object);
                    return object;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to array / scan extraction
            }
        }
        if (trimmed.startsWith("[")) {
            JsonNode array = objectMapper.readTree(trimmed);
            if (array.isArray()) {
                for (JsonNode element : array) {
                    if (element.isObject()) {
                        try {
                            parseActionNode(objectMapper, element);
                            return element;
                        } catch (IllegalArgumentException ignored) {
                            // try next array element
                        }
                    }
                }
            }
        }
        String json = extractJsonObject(objectMapper, trimmed);
        return objectMapper.readTree(json);
    }

    private static AgentAction parseActionNode(ObjectMapper objectMapper, JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Agent action must be a JSON object");
        }
        if (node.has("function") && node.get("function").isObject()) {
            JsonNode fn = node.get("function");
            return toolAction(
                    objectMapper,
                    textOrNull(fn, "name"),
                    fn.get("arguments"),
                    fn.get("parameters")
            );
        }
        String type = node.path("type").asString("").trim();
        if ("function".equalsIgnoreCase(type)
                || "tool_call".equalsIgnoreCase(type)
                || "action".equalsIgnoreCase(type)) {
            return toolAction(
                    objectMapper,
                    textOrNull(node, "name", "tool"),
                    node.get("arguments"),
                    node.get("parameters")
            );
        }
        if (type.isEmpty() && (node.has("name") || node.has("tool"))) {
            String toolName = textOrNull(node, "name", "tool");
            if (toolName != null && !toolName.isBlank()) {
                return toolAction(objectMapper, toolName, node.get("arguments"), node.get("parameters"));
            }
        }
        if ("finish".equalsIgnoreCase(type)
                || "done".equalsIgnoreCase(type)
                || "complete".equalsIgnoreCase(type)) {
            return finishAction(objectMapper, node);
        }
        if ("tool".equalsIgnoreCase(type)) {
            return toolAction(
                    objectMapper,
                    textOrNull(node, "name", "tool"),
                    node.get("arguments"),
                    null
            );
        }
        if ("message".equalsIgnoreCase(type) || "text".equalsIgnoreCase(type) || "assistant".equalsIgnoreCase(type)) {
            String embedded = textOrNull(node, "content", "text", "message");
            if (embedded != null && embedded.contains("{")) {
                try {
                    return parse(objectMapper, embedded);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Agent action must be {\"type\":\"tool\",...} or {\"type\":\"finish\",...}");
                }
            }
        }
        throw new IllegalArgumentException("Agent action must be {\"type\":\"tool\",...} or {\"type\":\"finish\",...}");
    }

    private static AgentAction toolAction(
            ObjectMapper objectMapper,
            String toolName,
            JsonNode argumentsNode,
            JsonNode parametersNode
    ) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool action requires name");
        }
        JsonNode argsNode = argumentsNode != null && !argumentsNode.isNull()
                ? argumentsNode
                : parametersNode;
        Map<String, Object> args = argsNode != null && !argsNode.isNull()
                ? objectMapper.convertValue(argsNode, Map.class)
                : Map.of();
        return new AgentAction("tool", toolName, args, null, null);
    }

    private static AgentAction finishAction(ObjectMapper objectMapper, JsonNode node) {
        return new AgentAction(
                "finish",
                null,
                null,
                node.path("summary").asString(null),
                node.isObject() && node.has("result")
                        ? objectMapper.convertValue(node.get("result"), Map.class)
                        : Map.of()
        );
    }

    private static String textOrNull(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field)) {
                String value = node.path(field).asString(null);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static boolean isAgentActionType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "tool", "finish", "function", "tool_call", "action", "done", "complete",
                    "message", "text", "assistant" -> true;
            default -> false;
        };
    }

    private static String normalizeModelContent(String content) {
        String trimmed = content != null ? content.trim() : "";
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int redactedEnd = trimmed.lastIndexOf("</think>");
        if (redactedEnd >= 0) {
            trimmed = trimmed.substring(redactedEnd + "</think>".length()).trim();
        }
        int fenceStart = trimmed.indexOf("```json");
        if (fenceStart >= 0) {
            int bodyStart = fenceStart + "```json".length();
            int fenceEnd = trimmed.indexOf("```", bodyStart);
            if (fenceEnd > bodyStart) {
                return trimmed.substring(bodyStart, fenceEnd).trim();
            }
        }
        int plainFence = trimmed.indexOf("```");
        if (plainFence >= 0) {
            int bodyStart = plainFence + 3;
            int fenceEnd = trimmed.indexOf("```", bodyStart);
            if (fenceEnd > bodyStart) {
                String body = trimmed.substring(bodyStart, fenceEnd).trim();
                if (body.startsWith("{") || body.startsWith("[")) {
                    return body;
                }
            }
        }
        int actionStart = findActionJsonStart(trimmed);
        if (actionStart >= 0) {
            int arrayStart = trimmed.lastIndexOf('[', actionStart);
            if (arrayStart >= 0 && arrayStart < actionStart) {
                return trimmed.substring(arrayStart).trim();
            }
            return trimmed.substring(actionStart).trim();
        }
        return trimmed;
    }

    private static int findActionJsonStart(String content) {
        int best = -1;
        for (String marker : new String[]{
                "{\"type\":\"tool\"",
                "{\"type\": \"tool\"",
                "{\"type\":\"finish\"",
                "{\"type\": \"finish\"",
                "{\"type\":\"function\"",
                "{\"type\": \"function\"",
                "{\"type\":\"tool_call\"",
                "{\"type\": \"tool_call\""
        }) {
            int idx = content.lastIndexOf(marker);
            if (idx > best) {
                best = idx;
            }
        }
        return best;
    }

    private static String extractJsonObject(ObjectMapper objectMapper, String content) throws Exception {
        String trimmed = content != null ? content.trim() : "";
        int anchored = findActionJsonStart(trimmed);
        if (anchored >= 0) {
            String candidate = tryParseActionObject(objectMapper, trimmed, anchored);
            if (candidate != null) {
                return candidate;
            }
        }
        String best = null;
        int bestScore = -1;
        int bestStart = -1;
        for (int start = trimmed.indexOf('{'); start >= 0; start = trimmed.indexOf('{', start + 1)) {
            int end = findJsonObjectEnd(trimmed, start);
            if (end < 0) {
                continue;
            }
            String candidate = trimmed.substring(start, end + 1);
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (!node.isObject() || node.isEmpty()) {
                    continue;
                }
                String type = node.path("type").asString("").trim();
                if (!type.isEmpty() && !isAgentActionType(type)) {
                    continue;
                }
                int score = node.size();
                if (isAgentActionType(type)) {
                    score += 100;
                }
                if (type.isEmpty() && (node.has("name") || node.has("tool"))
                        && (node.has("arguments") || node.has("parameters"))) {
                    score += 90;
                }
                if (node.has("name") || node.has("tool")) {
                    score += 10;
                }
                if (score > bestScore || (score == bestScore && start > bestStart)) {
                    bestScore = score;
                    bestStart = start;
                    best = candidate;
                }
            } catch (Exception ignored) {
                // scan next
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Agent response does not contain a JSON action object");
        }
        return best;
    }

    private static String tryParseActionObject(ObjectMapper objectMapper, String content, int start) {
        int end = findJsonObjectEnd(content, start);
        if (end < 0) {
            return null;
        }
        String candidate = content.substring(start, end + 1);
        try {
            JsonNode node = objectMapper.readTree(candidate);
            if (!node.isObject()) {
                return null;
            }
            String type = node.path("type").asString("").trim();
            if (isAgentActionType(type)
                    || (type.isEmpty() && (node.has("name") || node.has("tool")))) {
                return candidate;
            }
        } catch (Exception ignored) {
            // try scan fallback
        }
        return null;
    }

    private static int findJsonObjectEnd(String content, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
