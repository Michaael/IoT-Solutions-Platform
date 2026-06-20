package com.ispf.driver.webtransaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses pipe-separated step mappings or JSON step arrays.
 */
public final class WebTransactionSteps {

    private WebTransactionSteps() {
    }

    public static List<WebTransactionStep> parseMapping(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Web transaction mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            return parseJson(trimmed);
        }
        String[] tokens = trimmed.split("\\|");
        List<WebTransactionStep> steps = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                steps.add(WebTransactionStep.parseToken(token.trim()));
            }
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Web transaction mapping has no steps");
        }
        return List.copyOf(steps);
    }

    static List<WebTransactionStep> parseJson(String json) {
        List<WebTransactionStep> steps = new ArrayList<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("stepsJson must be a JSON array");
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        for (String object : splitJsonObjects(body)) {
            steps.add(parseJsonObject(object));
        }
        return List.copyOf(steps);
    }

    private static WebTransactionStep parseJsonObject(String objectText) {
        String name = extractJsonString(objectText, "name");
        String method = extractJsonString(objectText, "method");
        String url = extractJsonString(objectText, "url");
        String body = extractJsonString(objectText, "body");
        if (name == null || method == null || url == null) {
            throw new IllegalArgumentException("stepsJson object requires name, method, url");
        }
        return new WebTransactionStep(name, method.toUpperCase(java.util.Locale.ROOT), url, body == null ? "" : body);
    }

    private static List<String> splitJsonObjects(String arrayBody) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrayBody.length(); i++) {
            char ch = arrayBody.charAt(i);
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(arrayBody.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private static String extractJsonString(String objectText, String field) {
        String pattern = "\"" + field + "\"";
        int idx = objectText.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int colon = objectText.indexOf(':', idx + pattern.length());
        if (colon < 0) {
            return null;
        }
        int quoteStart = objectText.indexOf('"', colon + 1);
        if (quoteStart < 0) {
            return null;
        }
        int quoteEnd = objectText.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return null;
        }
        return objectText.substring(quoteStart + 1, quoteEnd);
    }
}
