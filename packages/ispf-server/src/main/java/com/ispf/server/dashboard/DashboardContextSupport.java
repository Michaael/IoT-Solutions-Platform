package com.ispf.server.dashboard;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parse and merge {@code @dashboardContext} JSON (selection, params, widgets).
 */
public final class DashboardContextSupport {

    public static final String EMPTY_JSON = "{\"selection\":{},\"params\":{},\"widgets\":{}}";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private DashboardContextSupport() {
    }

    public static Map<String, Object> emptyContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("selection", new LinkedHashMap<>());
        context.put("params", new LinkedHashMap<>());
        context.put("widgets", new LinkedHashMap<>());
        return context;
    }

    public static Map<String, Object> parseContextJson(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return emptyContext();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return normalizeContext(parsed);
        } catch (Exception ignored) {
            return emptyContext();
        }
    }

    public static Map<String, Object> normalizeContext(Map<String, Object> raw) {
        Map<String, Object> context = emptyContext();
        if (raw == null) {
            return context;
        }
        if (raw.get("selection") instanceof Map<?, ?> selection) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) selection;
            context.put("selection", new LinkedHashMap<>(typed));
        }
        if (raw.get("params") instanceof Map<?, ?> params) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) params;
            context.put("params", new LinkedHashMap<>(typed));
        }
        if (raw.get("widgets") instanceof Map<?, ?> widgets) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) widgets;
            context.put("widgets", new LinkedHashMap<>(typed));
        }
        if (raw.get("updatedAt") != null) {
            context.put("updatedAt", raw.get("updatedAt"));
        }
        if (raw.get("updatedBy") != null) {
            context.put("updatedBy", raw.get("updatedBy"));
        }
        return context;
    }

    public static String toJson(Map<String, Object> context, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(normalizeContext(context));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize dashboard context", e);
        }
    }

    /**
     * Sets a value at dot path (e.g. {@code params.mode}, {@code widgets.alarm-panel.visible}).
     */
    @SuppressWarnings("unchecked")
    public static void setAtPath(Map<String, Object> context, String dotPath, Object value) {
        if (dotPath == null || dotPath.isBlank()) {
            throw new IllegalArgumentException("Context path is required");
        }
        List<String> segments = List.of(dotPath.split("\\."));
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Invalid context path: " + dotPath);
        }
        Map<String, Object> current = context;
        for (int i = 0; i < segments.size() - 1; i++) {
            String segment = segments.get(i);
            Object next = current.get(segment);
            Map<String, Object> nextMap;
            if (next instanceof Map<?, ?> existing) {
                nextMap = (Map<String, Object>) existing;
            } else {
                nextMap = new LinkedHashMap<>();
                current.put(segment, nextMap);
            }
            current = nextMap;
        }
        current.put(segments.getLast(), value);
    }

    public static boolean contextMapsEqual(Map<String, Object> left, Map<String, Object> right, ObjectMapper objectMapper) {
        return toJson(left, objectMapper).equals(toJson(right, objectMapper));
    }
}
