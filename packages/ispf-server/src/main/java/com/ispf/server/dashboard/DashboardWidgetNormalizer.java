package com.ispf.server.dashboard;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ensures widget JSON-embedded fields (columnsJson, fieldsJson, …) are stored as strings in layout,
 * even when the agent passes nested arrays/objects in tool arguments.
 */
public final class DashboardWidgetNormalizer {

    static final Set<String> JSON_STRING_FIELDS = Set.of(
            "columnsJson",
            "variablesJson",
            "fieldsJson",
            "childrenJson",
            "tabsJson",
            "slidesJson",
            "stepsJson",
            "itemsJson",
            "eventNamesJson",
            "contextSelectionJson",
            "contextParamsJson",
            "rowParamsJson",
            "cardParamsJson",
            "inputJson",
            "htmlJson",
            "textJson",
            "stylesJson",
            "demoPreviewJson"
    );

    private DashboardWidgetNormalizer() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeWidget(Map<String, Object> widget, ObjectMapper objectMapper) {
        Map<String, Object> normalized = new LinkedHashMap<>(widget);
        for (String field : JSON_STRING_FIELDS) {
            Object raw = normalized.get(field);
            if (raw == null) {
                continue;
            }
            if (raw instanceof String stringValue) {
                if (!stringValue.isBlank()) {
                    normalized.put(field, stringValue);
                }
                continue;
            }
            try {
                normalized.put(field, objectMapper.writeValueAsString(raw));
            } catch (JacksonException ex) {
                throw new IllegalArgumentException("Invalid widget field " + field, ex);
            }
        }
        return normalized;
    }

    public static String resolveLayoutJson(Object layoutArg, ObjectMapper objectMapper) {
        if (layoutArg == null) {
            return null;
        }
        if (layoutArg instanceof String stringValue) {
            return stringValue.isBlank() ? null : stringValue;
        }
        if (layoutArg instanceof Map<?, ?> map) {
            try {
                return objectMapper.writeValueAsString(normalizeLayoutMap((Map<String, Object>) map, objectMapper));
            } catch (JacksonException ex) {
                throw new IllegalArgumentException("Invalid layout object", ex);
            }
        }
        throw new IllegalArgumentException("layout must be a JSON string or object");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeLayoutMap(Map<String, Object> layout, ObjectMapper objectMapper) {
        Map<String, Object> copy = new LinkedHashMap<>(layout);
        Object widgetsRaw = copy.get("widgets");
        if (widgetsRaw instanceof List<?> list) {
            copy.put(
                    "widgets",
                    list.stream()
                            .filter(Map.class::isInstance)
                            .map(item -> normalizeWidget((Map<String, Object>) item, objectMapper))
                            .toList()
            );
        }
        return copy;
    }
}
