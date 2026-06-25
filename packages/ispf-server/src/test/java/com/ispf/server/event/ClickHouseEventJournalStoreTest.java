package com.ispf.server.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.EventJournalProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickHouseEventJournalStoreTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastQuery = new AtomicReference<>("");
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private final java.util.concurrent.CopyOnWriteArrayList<String> queries = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final EventHistoryRecordCounter recordCounter = new EventHistoryRecordCounter();
    private ClickHouseEventJournalStore store;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            lastQuery.set(query);
            queries.add(query);
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "Ok.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        EventJournalProperties properties = new EventJournalProperties();
        properties.setStore("clickhouse");
        properties.getClickhouse().setUrl("http://localhost:" + port);
        store = new ClickHouseEventJournalStore(properties, recordCounter, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void appendBatchPostsJsonEachRowPayload() {
        EventJournalRecord record = new EventJournalRecord(
                "evt-1",
                "/devices/a",
                "telemetry",
                "INFO",
                "{\"value\":1}",
                Instant.parse("2025-06-25T12:00:00.123Z")
        );

        store.appendBatch(List.of(record));

        assertTrue(lastQuery.get().contains("INSERT+INTO+ispf.event_history"));
        assertTrue(lastBody.get().contains("\"id\":\"evt-1\""));
        assertTrue(lastBody.get().contains("\"object_path\":\"/devices/a\""));
        assertEquals(1L, recordCounter.totalRecords());
    }

    @Test
    void ensureSchemaCreatesDatabaseAndTable() {
        store.ensureSchema();

        assertTrue(queries.stream().anyMatch(q -> q.contains("CREATE+DATABASE")));
        assertTrue(queries.stream().anyMatch(q -> q.contains("CREATE+TABLE")));
    }
}
