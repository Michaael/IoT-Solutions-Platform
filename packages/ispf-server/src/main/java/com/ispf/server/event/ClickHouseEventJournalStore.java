package com.ispf.server.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.EventJournalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * High-throughput append-only event journal backed by ClickHouse MergeTree (HTTP interface).
 */
@Service
@ConditionalOnProperty(name = "ispf.event-journal.store", havingValue = "clickhouse")
public class ClickHouseEventJournalStore implements EventJournalStore {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseEventJournalStore.class);

    private static final DateTimeFormatter CH_DATETIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);

    private static final String SELECT_COLUMNS = """
            id, object_path, event_name, level, payload_json, occurred_at
            """;

    private final EventJournalProperties properties;
    private final EventHistoryRecordCounter recordCounter;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ClickHouseEventJournalStore(
            EventJournalProperties properties,
            EventHistoryRecordCounter recordCounter,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.recordCounter = recordCounter;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(110)
    public void ensureSchema() {
        EventJournalProperties.ClickHouse config = properties.getClickhouse();
        executeStatement("CREATE DATABASE IF NOT EXISTS " + config.getDatabase());
        int retentionDays = properties.getRetentionDays();
        String ttlClause = retentionDays > 0
                ? " TTL occurred_at + INTERVAL " + retentionDays + " DAY"
                : "";
        executeStatement(String.format("""
                CREATE TABLE IF NOT EXISTS %s.%s (
                    id String,
                    object_path String,
                    event_name String,
                    level LowCardinality(String),
                    payload_json String,
                    occurred_at DateTime64(3, 'UTC')
                ) ENGINE = MergeTree()
                PARTITION BY toYYYYMM(occurred_at)
                ORDER BY (object_path, occurred_at, id)%s
                SETTINGS index_granularity = 8192
                """, config.getDatabase(), config.getTable(), ttlClause));
        log.info(
                "ClickHouse event journal ready (database={}, table={}, retentionDays={})",
                config.getDatabase(),
                config.getTable(),
                retentionDays
        );
    }

    @Override
    public void appendBatch(List<EventJournalRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        StringJoiner body = new StringJoiner("\n");
        for (EventJournalRecord record : records) {
            body.add(toJsonLine(record));
        }
        String insertQuery = "INSERT INTO " + qualifiedTable() + " FORMAT JSONEachRow";
        post(insertQuery, body.toString());
        recordCounter.recordPersisted(records.size());
    }

    @Override
    public void appendOne(EventJournalRecord record) {
        appendBatch(List.of(record));
    }

    @Override
    public List<EventJournalRecord> queryRecent(String objectPath, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("limit", String.valueOf(capped));
        String sql;
        if (objectPath == null || objectPath.isBlank()) {
            sql = """
                    SELECT %s FROM %s
                    ORDER BY occurred_at DESC
                    LIMIT {limit:UInt32}
                    FORMAT JSONEachRow
                    """.formatted(SELECT_COLUMNS, qualifiedTable());
        } else {
            params.put("objectPath", objectPath);
            sql = """
                    SELECT %s FROM %s
                    WHERE object_path = {objectPath:String}
                    ORDER BY occurred_at DESC
                    LIMIT {limit:UInt32}
                    FORMAT JSONEachRow
                    """.formatted(SELECT_COLUMNS, qualifiedTable());
        }
        return parseJsonEachRow(postQuery(sql, params));
    }

    @Override
    public Optional<EventJournalRecord> findLatest(String objectPath, String eventName) {
        Map<String, String> params = Map.of(
                "objectPath", objectPath,
                "eventName", eventName
        );
        String sql = """
                SELECT %s FROM %s
                WHERE object_path = {objectPath:String} AND event_name = {eventName:String}
                ORDER BY occurred_at DESC
                LIMIT 1
                FORMAT JSONEachRow
                """.formatted(SELECT_COLUMNS, qualifiedTable());
        List<EventJournalRecord> rows = parseJsonEachRow(postQuery(sql, params));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public long countTotal() {
        String body = postQuery("SELECT count() AS cnt FROM " + qualifiedTable() + " FORMAT JSONEachRow", Map.of());
        if (body.isBlank()) {
            return 0L;
        }
        try {
            JsonNode node = objectMapper.readTree(body.lines().findFirst().orElse("{}"));
            return node.path("cnt").asLong(0L);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse ClickHouse count response", ex);
        }
    }

    @Override
    public void purgeOlderThan(Instant cutoff) {
        // Retention handled by MergeTree TTL.
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return false;
    }

    private List<EventJournalRecord> parseJsonEachRow(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<EventJournalRecord> records = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(line);
                records.add(new EventJournalRecord(
                        node.path("id").asText(),
                        node.path("object_path").asText(),
                        node.path("event_name").asText(),
                        node.path("level").asText(),
                        node.path("payload_json").asText(null),
                        parseInstant(node.path("occurred_at").asText())
                ));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to parse ClickHouse JSONEachRow line: " + line, ex);
            }
        }
        return records;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        if (value.contains("T")) {
            return Instant.parse(value.endsWith("Z") ? value : value + "Z");
        }
        return CH_DATETIME.parse(value, Instant::from);
    }

    private String toJsonLine(EventJournalRecord record) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.id());
            row.put("object_path", record.objectPath());
            row.put("event_name", record.eventName());
            row.put("level", record.level());
            row.put("payload_json", record.payloadJson());
            row.put("occurred_at", CH_DATETIME.format(record.occurredAt()));
            return objectMapper.writeValueAsString(row);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize event journal record", ex);
        }
    }

    private void executeStatement(String sql) {
        post(sql, "");
    }

    private void post(String query, String body) {
        HttpResponse<String> response = send(buildRequest(query, body));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "ClickHouse request failed (" + response.statusCode() + "): " + response.body()
            );
        }
    }

    private String postQuery(String query, Map<String, String> params) {
        HttpResponse<String> response = send(buildRequest(query, "", params));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "ClickHouse query failed (" + response.statusCode() + "): " + response.body()
            );
        }
        return response.body();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ClickHouse request interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("ClickHouse request failed", ex);
        }
    }

    private HttpRequest buildRequest(String query, String body) {
        return buildRequest(query, body, Map.of());
    }

    private HttpRequest buildRequest(String query, String body, Map<String, String> params) {
        EventJournalProperties.ClickHouse config = properties.getClickhouse();
        StringBuilder url = new StringBuilder(config.getUrl());
        if (!config.getUrl().contains("?")) {
            url.append('?');
        } else if (!config.getUrl().endsWith("&") && !config.getUrl().endsWith("?")) {
            url.append('&');
        }
        url.append("database=").append(encode(config.getDatabase()));
        url.append("&query=").append(encode(query));
        for (Map.Entry<String, String> param : params.entrySet()) {
            url.append("&param_").append(encode(param.getKey())).append('=').append(encode(param.getValue()));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            String credentials = config.getUsername() + ":" + (config.getPassword() != null ? config.getPassword() : "");
            builder.header("Authorization", "Basic " + java.util.Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder.build();
    }

    private String qualifiedTable() {
        EventJournalProperties.ClickHouse config = properties.getClickhouse();
        return config.getDatabase() + "." + config.getTable();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
