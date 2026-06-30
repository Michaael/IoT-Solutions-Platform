package com.ispf.server.mimic;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses and normalizes SCADA mimic diagramJson (v2) for agent tools and API.
 */
public final class MimicDiagramJsonSupport {

    public static final String DEFAULT_LAYER_ID = "layer-default";

    private MimicDiagramJsonSupport() {
    }

    public static String normalize(Object raw, ObjectMapper objectMapper) {
        ObjectNode doc = parseToObjectNode(raw, objectMapper);
        return writeDocument(normalizeDocument(doc, objectMapper), objectMapper);
    }

    public static String replaceElements(
            String existingJson,
            Object elements,
            Object connections,
            ObjectMapper objectMapper
    ) {
        return mergeElements(existingJson, elements, connections, false, objectMapper);
    }

    public static String mergeElements(
            String existingJson,
            Object elements,
            Object connections,
            boolean append,
            ObjectMapper objectMapper
    ) {
        ObjectNode doc = parseToObjectNode(existingJson, objectMapper);
        normalizeStructure(doc);
        ArrayNode elementArray = ensureArray(doc, "elements");
        ArrayNode connectionArray = ensureArray(doc, "connections");
        List<JsonNode> incomingElements = toNodeList(elements, objectMapper);
        List<JsonNode> incomingConnections = connections != null
                ? toNodeList(connections, objectMapper)
                : List.of();
        if (!append) {
            elementArray.removeAll();
            connectionArray.removeAll();
        }
        for (JsonNode element : incomingElements) {
            if (element.isObject()) {
                elementArray.add(normalizeElementNode((ObjectNode) element.deepCopy()));
            }
        }
        for (JsonNode connection : incomingConnections) {
            connectionArray.add(connection);
        }
        return writeDocument(normalizeDocument(doc, objectMapper), objectMapper);
    }

    public static int countElements(String diagramJson, ObjectMapper objectMapper) {
        try {
            ObjectNode doc = parseToObjectNode(diagramJson, objectMapper);
            JsonNode elements = doc.get("elements");
            return elements != null && elements.isArray() ? elements.size() : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static int countConnections(String diagramJson, ObjectMapper objectMapper) {
        try {
            ObjectNode doc = parseToObjectNode(diagramJson, objectMapper);
            JsonNode connections = doc.get("connections");
            return connections != null && connections.isArray() ? connections.size() : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static ObjectNode parseToObjectNode(Object raw, ObjectMapper objectMapper) {
        if (raw == null) {
            throw new IllegalArgumentException("diagramJson is required");
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isBlank()) {
                throw new IllegalArgumentException("diagramJson is required");
            }
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                return unwrapValueWrapper(toObjectNode(node), objectMapper);
            } catch (JacksonException ex) {
                throw new IllegalArgumentException("Invalid diagramJson string", ex);
            }
        }
        if (raw instanceof Map<?, ?> map) {
            return unwrapValueWrapper(toObjectNode(objectMapper.valueToTree(map)), objectMapper);
        }
        if (raw instanceof JsonNode node) {
            return unwrapValueWrapper(toObjectNode(node), objectMapper);
        }
        throw new IllegalArgumentException("diagramJson must be a JSON string or object");
    }

    private static ObjectNode unwrapValueWrapper(ObjectNode doc, ObjectMapper objectMapper) {
        if (doc.has("value") && doc.size() == 1) {
            JsonNode value = doc.get("value");
            if (value.isObject()) {
                return (ObjectNode) value;
            }
            if (value.isTextual()) {
                String inner = value.asText("").trim();
                if (!inner.isBlank() && inner.startsWith("{")) {
                    try {
                        return toObjectNode(objectMapper.readTree(inner));
                    } catch (JacksonException ex) {
                        throw new IllegalArgumentException("Invalid diagramJson in value wrapper", ex);
                    }
                }
            }
        }
        if (doc.has("value") && doc.get("value").isObject()) {
            return (ObjectNode) doc.get("value");
        }
        return doc;
    }

    private static ObjectNode normalizeDocument(ObjectNode doc, ObjectMapper objectMapper) {
        normalizeStructure(doc);
        ArrayNode elements = ensureArray(doc, "elements");
        ArrayNode normalizedElements = objectMapper.createArrayNode();
        for (JsonNode element : elements) {
            if (element != null && element.isObject()) {
                normalizedElements.add(normalizeElementNode((ObjectNode) element.deepCopy()));
            }
        }
        doc.set("elements", normalizedElements);
        if (!doc.has("connections") || !doc.get("connections").isArray()) {
            doc.set("connections", objectMapper.createArrayNode());
        }
        return doc;
    }

    private static void normalizeStructure(ObjectNode doc) {
        doc.put("version", 2);
        if (!doc.has("width") || !doc.get("width").isNumber()) {
            doc.put("width", 1600);
        }
        if (!doc.has("height") || !doc.get("height").isNumber()) {
            doc.put("height", 900);
        }
        if (!doc.has("background") || !doc.get("background").isTextual()) {
            doc.put("background", "var(--bg)");
        }
        if (!doc.has("grid") || !doc.get("grid").isObject()) {
            ObjectNode grid = doc.objectNode();
            grid.put("size", 1);
            grid.put("snap", false);
            grid.put("visible", false);
            doc.set("grid", grid);
        }
        if (!doc.has("layers") || !doc.get("layers").isArray() || doc.get("layers").isEmpty()) {
            ArrayNode layers = doc.arrayNode();
            ObjectNode layer = doc.objectNode();
            layer.put("id", DEFAULT_LAYER_ID);
            layer.put("name", "Main");
            layer.put("visible", true);
            layers.add(layer);
            doc.set("layers", layers);
        }
        if (!doc.has("elements") || !doc.get("elements").isArray()) {
            doc.set("elements", doc.arrayNode());
        }
        if (!doc.has("connections") || !doc.get("connections").isArray()) {
            doc.set("connections", doc.arrayNode());
        }
    }

    private static ObjectNode normalizeElementNode(ObjectNode element) {
        if (!element.has("id") || !element.get("id").isTextual() || element.get("id").asText("").isBlank()) {
            element.put("id", "el-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (!element.has("symbolId") || !element.get("symbolId").isTextual() || element.get("symbolId").asText("").isBlank()) {
            element.put("symbolId", "label");
        }
        if (!element.has("layerId") || !element.get("layerId").isTextual() || element.get("layerId").asText("").isBlank()) {
            element.put("layerId", DEFAULT_LAYER_ID);
        }
        if (!element.has("x") || !element.get("x").isNumber()) {
            element.put("x", 0);
        }
        if (!element.has("y") || !element.get("y").isNumber()) {
            element.put("y", 0);
        }
        if (!element.has("bindings") || !element.get("bindings").isObject()) {
            element.set("bindings", element.objectNode());
        }
        return element;
    }

    private static ArrayNode ensureArray(ObjectNode doc, String field) {
        JsonNode node = doc.get(field);
        if (node instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = doc.arrayNode();
        doc.set(field, created);
        return created;
    }

    private static List<JsonNode> toNodeList(Object raw, ObjectMapper objectMapper) {
        if (raw == null) {
            return List.of();
        }
        JsonNode node;
        if (raw instanceof String text) {
            try {
                node = objectMapper.readTree(text.trim());
            } catch (JacksonException ex) {
                throw new IllegalArgumentException("Invalid JSON array string for elements/connections", ex);
            }
        } else {
            node = objectMapper.valueToTree(raw);
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("elements and connections must be JSON arrays");
        }
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isObject()) {
                out.add(item);
            }
        }
        return out;
    }

    private static ObjectNode toObjectNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("diagramJson must be a JSON object");
        }
        return (ObjectNode) node;
    }

    private static String writeDocument(ObjectNode doc, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Failed to serialize diagramJson", ex);
        }
    }
}
