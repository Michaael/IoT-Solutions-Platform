package com.ispf.driver.graphdb;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph database driver — Neo4j Bolt or Gremlin-over-HTTP scalar queries.
 */
public class GraphDbDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("graphDbValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "graph-db",
            "Graph Database Driver",
            "0.1.0",
            "Runs Cypher (Neo4j Bolt) or Gremlin-over-HTTP queries and maps scalar results to ISPF variables",
            "ISPF",
            Map.of(
                    "uri", "bolt://localhost:7687",
                    "username", "neo4j",
                    "password", "password",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String uri = "bolt://localhost:7687";
    private String username = "neo4j";
    private String password = "password";
    private long timeoutMs = 5000;
    private Driver neo4jDriver;
    private HttpClient httpClient;
    private final Map<String, GraphDbPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null) {
            return;
        }
        switch (key) {
            case "uri" -> uri = value.trim();
            case "username" -> username = value;
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            if (isGremlinHttpUri(uri)) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(timeoutMs))
                        .build();
            } else {
                neo4jDriver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Graph DB ready (uri=" + uri + ")");
        } catch (Exception e) {
            throw new DriverException("Graph DB connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (neo4jDriver != null) {
            neo4jDriver.close();
            neo4jDriver = null;
        }
        httpClient = null;
    }

    @Override
    public boolean isConnected() {
        return connected && (neo4jDriver != null || httpClient != null);
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            GraphDbPoint point = GraphDbPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), execute(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Graph DB driver is read-only in v0.1");
    }

    private DataRecord execute(GraphDbPoint point) throws DriverException {
        if (isGremlinHttpUri(uri)) {
            return executeGremlinHttp(point.cypher());
        }
        return executeCypher(point.cypher());
    }

    private DataRecord executeCypher(String cypher) throws DriverException {
        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher);
            if (!result.hasNext()) {
                return DataRecord.single(VALUE_SCHEMA, Map.of("value", ""));
            }
            Record record = result.next();
            if (record.size() == 0) {
                return DataRecord.single(VALUE_SCHEMA, Map.of("value", ""));
            }
            Object value = record.get(0).asObject();
            return DataRecord.single(VALUE_SCHEMA, Map.of("value", value == null ? "" : String.valueOf(value)));
        } catch (Exception e) {
            throw new DriverException("Cypher query failed", e);
        }
    }

    private DataRecord executeGremlinHttp(String gremlin) throws DriverException {
        try {
            String payload = "{\"gremlin\":\"" + escapeJson(gremlin) + "\"}";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json");
            if (username != null && !username.isBlank()) {
                String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                builder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String body = response.body() == null ? "" : response.body();
            return DataRecord.single(VALUE_SCHEMA, Map.of("value", body));
        } catch (Exception e) {
            throw new DriverException("Gremlin HTTP query failed", e);
        }
    }

    private static boolean isGremlinHttpUri(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
