package com.ispf.server.ai.context;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scored search over ContextPack indices (doc chunks, features, examples, drivers).
 */
@Service
public class ContextPackSearchService {

    private static final int MAX_HITS = 5;

    private final ContextPackService contextPackService;

    public ContextPackSearchService(ContextPackService contextPackService) {
        this.contextPackService = contextPackService;
    }

    public Map<String, Object> search(String query, String topic) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            return Map.of("status", "ERROR", "error", "query is required");
        }
        Map<String, Object> pack = contextPackService.loadPack();
        List<Map<String, Object>> hits = new ArrayList<>();
        String normalizedTopic = topic == null || topic.isBlank() ? "all" : topic.trim().toLowerCase(Locale.ROOT);

        if (matchesTopic(normalizedTopic, "drivers", "all")) {
            collectDriverHits(pack, q, hits);
        }
        if (matchesTopic(normalizedTopic, "features", "all")) {
            collectFeatureHits(pack, q, hits);
        }
        if (matchesTopic(normalizedTopic, "examples", "all")) {
            collectExampleHits(pack, q, hits);
        }
        if (matchesTopic(normalizedTopic, "workflows", "dashboards", "all")) {
            collectDocChunkHits(pack, q, hits, normalizedTopic);
        }
        if (hits.isEmpty()) {
            collectLegacyDocHits(pack, q, hits);
        }

        hits.sort(Comparator.comparingInt(row -> -((Number) row.get("score")).intValue()));
        List<Map<String, Object>> top = hits.stream().limit(MAX_HITS).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("query", query);
        result.put("topic", normalizedTopic);
        result.put("contextPackVersion", pack.getOrDefault("contextPackVersion", "unknown"));
        result.put("hits", top);
        result.put("hitCount", top.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> driverHelp(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return Map.of("status", "ERROR", "error", "driverId is required");
        }
        String id = driverId.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> pack = contextPackService.loadPack();
        Object catalog = pack.get("driverCatalog");
        if (catalog instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row && id.equals(String.valueOf(row.get("driverId")).toLowerCase(Locale.ROOT))) {
                    Map<String, Object> result = new LinkedHashMap<>(castMap(row));
                    result.put("status", "OK");
                    return result;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("driverId", driverId);
        result.put("hint", "Use list_drivers and search_context topic=drivers query=" + driverId);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listExampleSummaries() {
        Map<String, Object> pack = contextPackService.loadPack();
        Object summaries = pack.get("exampleSummaries");
        if (summaries instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    result.add(castMap(row));
                }
            }
            return result;
        }
        Object examples = pack.get("examples");
        List<Map<String, Object>> fallback = new ArrayList<>();
        if (examples instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> example) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("appId", example.get("packageId") != null ? example.get("packageId") : example.get("appId"));
                    row.put("version", example.get("version"));
                    row.put("sections", example.get("sections"));
                    row.put("path", example.get("path"));
                    fallback.add(row);
                }
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exampleBundle(String appId, List<String> sections) {
        if (appId == null || appId.isBlank()) {
            return Map.of("status", "ERROR", "error", "appId is required");
        }
        String needle = appId.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> pack = contextPackService.loadPack();
        Object examples = pack.get("examples");
        if (!(examples instanceof List<?> list)) {
            return Map.of("status", "ERROR", "error", "No examples in context pack");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> example)) {
                continue;
            }
            String candidate = String.valueOf(
                    example.get("packageId") != null ? example.get("packageId") : example.get("appId")
            ).toLowerCase(Locale.ROOT);
            String path = String.valueOf(example.get("path") != null ? example.get("path") : "").toLowerCase(Locale.ROOT);
            if (!candidate.equals(needle) && !path.contains(needle)) {
                continue;
            }
            Object manifest = example.get("manifest");
            if (!(manifest instanceof Map<?, ?> manifestMap)) {
                return Map.of("status", "ERROR", "error", "Example manifest missing for " + appId);
            }
            Map<String, Object> subset = new LinkedHashMap<>();
            subset.put("appId", appId);
            if (sections == null || sections.isEmpty()) {
                subset.put("manifest", castMap(manifestMap));
            } else {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (String section : sections) {
                    if (section != null && manifestMap.containsKey(section)) {
                        filtered.put(section, manifestMap.get(section));
                    }
                }
                subset.put("manifest", filtered);
            }
            subset.put("status", "OK");
            return subset;
        }
        return Map.of("status", "ERROR", "error", "Example not found: " + appId);
    }

    @SuppressWarnings("unchecked")
    private void collectDriverHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object catalog = pack.get("driverCatalog");
        if (!(catalog instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            int score = scoreRow(row, q, Set.of("driverId", "name", "description", "keywords"));
            if (score > 0) {
                hits.add(hit("driver", String.valueOf(row.get("driverId")), score, castMap(row)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFeatureHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object index = pack.get("featureIndex");
        if (!(index instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            int score = scoreRow(row, q, Set.of("id", "title", "description", "keywords"));
            if (score > 0) {
                hits.add(hit("feature", String.valueOf(row.get("id")), score, castMap(row)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectExampleHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object summaries = pack.get("exampleSummaries");
        if (summaries instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                int score = scoreRow(row, q, Set.of("appId", "purpose", "keySections", "keywords"));
                if (score > 0) {
                    hits.add(hit("example", String.valueOf(row.get("appId")), score, castMap(row)));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectDocChunkHits(
            Map<String, Object> pack,
            String q,
            List<Map<String, Object>> hits,
            String topic
    ) {
        Object chunks = pack.get("docChunks");
        if (!(chunks instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String chunkTopic = String.valueOf(row.get("topic") != null ? row.get("topic") : "all");
            if (!topic.equals("all") && !chunkTopic.equals(topic) && !chunkTopic.equals("all")) {
                continue;
            }
            int score = scoreRow(row, q, Set.of("id", "title", "keywords", "text"));
            if (score > 0) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("chunkId", row.get("id"));
                payload.put("title", row.get("title"));
                payload.put("topic", row.get("topic"));
                payload.put("snippet", snippet(String.valueOf(row.get("text")), q));
                hits.add(hit("doc", String.valueOf(row.get("id")), score, payload));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectLegacyDocHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object apiSlice = pack.get("apiSlice");
        if (!(apiSlice instanceof Map<?, ?> slice)) {
            return;
        }
        for (Map.Entry<?, ?> entry : slice.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String text = String.valueOf(entry.getValue());
            if (text.toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(hit("doc", key, 10, Map.of(
                        "section", key,
                        "snippet", snippet(text, q)
                )));
            }
        }
    }

    private static int scoreRow(Map<?, ?> row, String query, Set<String> fields) {
        int score = 0;
        for (String field : fields) {
            Object value = row.get(field);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).toLowerCase(Locale.ROOT);
            if (text.equals(query)) {
                score += 30;
            } else if (text.contains(query)) {
                score += field.equals("title") || field.equals("driverId") || field.equals("appId") ? 25 : 15;
            }
            for (String token : query.split("\\s+")) {
                if (token.length() >= 3 && text.contains(token)) {
                    score += 5;
                }
            }
        }
        return score;
    }

    private static Map<String, Object> hit(String kind, String id, int score, Map<String, Object> payload) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("id", id);
        row.put("score", score);
        row.put("payload", payload);
        return row;
    }

    private static String snippet(String text, String query) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int idx = text.toLowerCase(Locale.ROOT).indexOf(query);
        if (idx < 0) {
            return text.length() <= 400 ? text : text.substring(0, 397) + "...";
        }
        int start = Math.max(0, idx - 120);
        int end = Math.min(text.length(), idx + query.length() + 280);
        return text.substring(start, end);
    }

    private static boolean matchesTopic(String topic, String... allowed) {
        for (String candidate : allowed) {
            if (topic.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            target.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return target;
    }
}
