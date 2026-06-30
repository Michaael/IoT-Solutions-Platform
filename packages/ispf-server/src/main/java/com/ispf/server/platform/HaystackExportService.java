package com.ispf.server.platform;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.driver.DriverPointMappingParser;
import com.ispf.server.driver.DriverPointMappingParser.Entry;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class HaystackExportService {

    public static final String DEFAULT_ROOT_PATH = "root.platform";
    private static final int FORMAT_VERSION = 1;
    private static final int SEARCH_DEFAULT_LIMIT = 50;
    private static final int SEARCH_MAX_LIMIT = 200;

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public HaystackExportService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportSubtree(String rootPath, boolean includePoints) {
        String normalizedRoot = normalizeRootPath(rootPath);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (!isUnderRoot(node.path(), normalizedRoot)) {
                continue;
            }
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            if (!hasHaystackMetadata(node)) {
                continue;
            }
            rows.add(buildEquipRow(node));
            if (includePoints) {
                Map<String, Entry> pointMappings = parsePointMappings(node);
                for (String variableName : pointVariableNames(node, pointMappings)) {
                    node.getVariable(variableName).ifPresent(variable ->
                            rows.add(buildPointRow(node, variable, pointMappings.get(variableName)))
                    );
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formatVersion", FORMAT_VERSION);
        payload.put("exportedAt", Instant.now().toString());
        payload.put("rootPath", normalizedRoot);
        payload.put("includePoints", includePoints);
        payload.put("rowCount", rows.size());
        payload.put("rows", rows);
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> searchByTags(
            String rootPath,
            List<String> tags,
            String entityKind,
            int limit
    ) {
        String normalizedRoot = normalizeRootPath(rootPath);
        List<String> requiredTags = normalizeTagQuery(tags);
        if (requiredTags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one tag is required.");
        }
        String kindFilter = normalizeEntityKind(entityKind);
        int cappedLimit = Math.max(1, Math.min(limit > 0 ? limit : SEARCH_DEFAULT_LIMIT, SEARCH_MAX_LIMIT));

        List<Map<String, Object>> matches = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (matches.size() >= cappedLimit) {
                break;
            }
            if (!isUnderRoot(node.path(), normalizedRoot)) {
                continue;
            }
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            if (!hasHaystackMetadata(node)) {
                continue;
            }
            Map<String, Boolean> equipTags = parseTags(readString(node, "haystackTags"));
            if (includeEntityKind(kindFilter, "equip")
                    && tagsMatch(equipTags, requiredTags)) {
                matches.add(toSearchMatch(buildEquipRow(node), node.path(), null));
                if (matches.size() >= cappedLimit) {
                    break;
                }
            }
            if (!includeEntityKind(kindFilter, "point")) {
                continue;
            }
            Map<String, Entry> pointMappings = parsePointMappings(node);
            for (String variableName : pointVariableNames(node, pointMappings)) {
                if (matches.size() >= cappedLimit) {
                    break;
                }
                Entry mapping = pointMappings.get(variableName);
                var variableOpt = node.getVariable(variableName);
                if (variableOpt.isEmpty()) {
                    continue;
                }
                List<String> pointTags = mapping != null && !mapping.haystackTags().isEmpty()
                        ? mapping.haystackTags()
                        : List.of("point", "sensor", "his");
                Map<String, Boolean> combinedTags = mergeTags(equipTags, pointTags);
                if (!tagsMatch(combinedTags, requiredTags)) {
                    continue;
                }
                matches.add(toSearchMatch(
                        buildPointRow(node, variableOpt.get(), mapping),
                        node.path(),
                        variableName
                ));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formatVersion", FORMAT_VERSION);
        payload.put("exportedAt", Instant.now().toString());
        payload.put("rootPath", normalizedRoot);
        payload.put("tags", requiredTags);
        payload.put("entityKind", kindFilter.isBlank() ? "all" : kindFilter);
        payload.put("limit", cappedLimit);
        payload.put("count", matches.size());
        payload.put("matches", matches);
        return payload;
    }

    private Map<String, Object> toSearchMatch(Map<String, Object> row, String objectPath, String variableName) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("entityKind", row.get("entityKind"));
        match.put("objectPath", objectPath);
        if (variableName != null) {
            match.put("variableName", variableName);
        }
        Object dis = row.get("dis");
        if (dis != null && !dis.toString().isBlank()) {
            match.put("dis", dis);
        }
        Object unit = row.get("unit");
        if (unit != null && !unit.toString().isBlank()) {
            match.put("unit", unit);
        }
        match.put("tags", row.get("tags"));
        Object haystackRef = row.get("haystackRef");
        if (haystackRef != null && !haystackRef.toString().isBlank()) {
            match.put("haystackRef", haystackRef);
        }
        return match;
    }

    public static List<String> normalizeTagQuery(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            for (String part : tag.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return List.copyOf(normalized);
    }

    static String normalizeEntityKind(String entityKind) {
        if (entityKind == null || entityKind.isBlank() || "all".equalsIgnoreCase(entityKind.trim())) {
            return "";
        }
        String normalized = entityKind.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("equip") && !normalized.equals("point")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "entityKind must be equip, point, or all."
            );
        }
        return normalized;
    }

    static boolean includeEntityKind(String kindFilter, String entityKind) {
        return kindFilter.isBlank() || kindFilter.equals(entityKind);
    }

    static boolean tagsMatch(Map<String, Boolean> tagSet, List<String> requiredTags) {
        if (requiredTags.isEmpty()) {
            return false;
        }
        for (String tag : requiredTags) {
            if (!tagSet.containsKey(tag)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> buildEquipRow(PlatformObject node) {
        Map<String, Object> row = baseRow(node);
        row.put("entityKind", "equip");
        mergeHaystackVariables(row, node);
        return row;
    }

    private Map<String, Object> buildPointRow(PlatformObject node, Variable variable, Entry mapping) {
        Map<String, Object> row = baseRow(node);
        row.put("entityKind", "point");
        row.put("variableName", variable.name());
        row.put("id", node.path() + "." + variable.name());
        if (mapping != null && !mapping.dis().isBlank()) {
            row.put("dis", mapping.dis());
        }
        List<String> pointTags = mapping != null && !mapping.haystackTags().isEmpty()
                ? mapping.haystackTags()
                : List.of("point", "sensor", "his");
        row.put("tags", mergeTags(Map.of(), pointTags));
        row.put("curVal", readNumeric(variable));
        String unit = mapping != null && !mapping.unit().isBlank() ? mapping.unit() : readUnit(variable);
        if (!unit.isBlank()) {
            row.put("unit", unit);
        }
        return row;
    }

    private Map<String, Entry> parsePointMappings(PlatformObject node) {
        return DriverPointMappingParser.parse(readString(node, "driverPointMappingsJson"), objectMapper);
    }

    private static Set<String> pointVariableNames(PlatformObject node, Map<String, Entry> pointMappings) {
        Set<String> names = new LinkedHashSet<>();
        for (Variable variable : node.variables().values()) {
            if (variable.historyEnabled()) {
                names.add(variable.name());
            }
        }
        for (Map.Entry<String, Entry> mapping : pointMappings.entrySet()) {
            if (mapping.getValue().hasHaystackMetadata()) {
                names.add(mapping.getKey());
            }
        }
        return names;
    }

    private static Map<String, Object> baseRow(PlatformObject node) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", node.path());
        row.put("path", node.path());
        row.put("dis", node.displayName());
        return row;
    }

    private void mergeHaystackVariables(Map<String, Object> row, PlatformObject node) {
        row.put("haystackRef", readString(node, "haystackRef"));
        row.put("haystackKind", readString(node, "haystackKind"));
        row.put("tags", parseTags(readString(node, "haystackTags")));
    }

    private static boolean hasHaystackMetadata(PlatformObject node) {
        return node.getVariable("haystackTags").isPresent()
                || node.getVariable("haystackRef").isPresent()
                || node.getVariable("haystackKind").isPresent();
    }

    private static String readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .orElse("");
    }

    private static Double readNumeric(Variable variable) {
        return variable.value()
                .map(DataRecord::firstRow)
                .map(row -> row.get("value"))
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).doubleValue())
                .orElse(null);
    }

    private static String readUnit(Variable variable) {
        return variable.value()
                .map(DataRecord::firstRow)
                .map(row -> row.get("unit"))
                .map(Object::toString)
                .filter(unit -> !unit.isBlank())
                .orElse("");
    }

    private Map<String, Boolean> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return mergeTags(Map.of(), tags);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Map<String, Boolean> mergeTags(Map<String, Boolean> base, List<String> extra) {
        Map<String, Boolean> merged = new LinkedHashMap<>(base);
        for (String tag : extra) {
            if (tag != null && !tag.isBlank()) {
                merged.put(tag.trim(), true);
            }
        }
        return merged;
    }

    static String normalizeRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return DEFAULT_ROOT_PATH;
        }
        String trimmed = rootPath.trim();
        if (!trimmed.startsWith("root.")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rootPath must start with root.");
        }
        return trimmed.replaceAll("\\.+$", "");
    }

    static boolean isUnderRoot(String path, String rootPath) {
        return path.equals(rootPath) || path.startsWith(rootPath + ".");
    }
}
