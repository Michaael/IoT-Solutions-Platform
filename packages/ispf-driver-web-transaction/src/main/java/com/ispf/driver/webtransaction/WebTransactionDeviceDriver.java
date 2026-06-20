package com.ispf.driver.webtransaction;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-step HTTP transaction driver using Java HttpClient.
 */
public class WebTransactionDeviceDriver implements DeviceDriver {

    private static final DataSchema RESULT_SCHEMA = DataSchema.builder("webTransactionResult")
            .field("statusCode", FieldType.INTEGER)
            .field("latencyMs", FieldType.LONG)
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "web-transaction",
            "Web Transaction Driver",
            "0.1.0",
            "Executes multi-step HTTP transactions and reports final status and latency",
            "ISPF",
            Map.of(
                    "stepsJson", "[{\"name\":\"step1\",\"method\":\"GET\",\"url\":\"http://127.0.0.1:8080/\"}]",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String stepsJson = "";
    private long timeoutMs = 5000;
    private HttpClient client;
    private final Map<String, List<WebTransactionStep>> points = new ConcurrentHashMap<>();
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
            case "stepsJson" -> stepsJson = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Web transaction client ready");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
    }

    @Override
    public boolean isConnected() {
        return connected && client != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            List<WebTransactionStep> steps = resolveSteps(entry.getValue());
            points.put(entry.getKey(), steps);
            driverObject.updateVariable(entry.getKey(), execute(steps));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Web transaction driver is read-only in v0.1");
    }

    private List<WebTransactionStep> resolveSteps(String pointMapping) {
        if (pointMapping != null && !pointMapping.isBlank()) {
            return WebTransactionSteps.parseMapping(pointMapping);
        }
        if (stepsJson == null || stepsJson.isBlank()) {
            throw new IllegalArgumentException("Web transaction requires stepsJson config or point mapping");
        }
        return WebTransactionSteps.parseMapping(stepsJson);
    }

    private DataRecord execute(List<WebTransactionStep> steps) throws DriverException {
        long start = System.currentTimeMillis();
        int finalStatus = -1;
        String finalBody = "";
        try {
            for (WebTransactionStep step : steps) {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(step.url()))
                        .timeout(Duration.ofMillis(timeoutMs));
                String method = step.method();
                if ("GET".equals(method)) {
                    builder.GET();
                } else if ("HEAD".equals(method)) {
                    builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.ofString(step.body()));
                }
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                finalStatus = response.statusCode();
                finalBody = response.body() == null ? "" : response.body();
            }
            long latencyMs = System.currentTimeMillis() - start;
            return DataRecord.single(RESULT_SCHEMA, Map.of(
                    "statusCode", finalStatus,
                    "latencyMs", latencyMs,
                    "value", finalBody
            ));
        } catch (Exception e) {
            throw new DriverException("Web transaction failed", e);
        }
    }
}
