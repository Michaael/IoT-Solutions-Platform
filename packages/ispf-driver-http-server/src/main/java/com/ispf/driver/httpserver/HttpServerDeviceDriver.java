package com.ispf.driver.httpserver;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Embedded JDK HttpServer driver — exposes request counters and last request metadata.
 */
public class HttpServerDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("httpServerValue")
            .field("value", FieldType.STRING)
            .field("count", FieldType.LONG)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "http-server",
            "Embedded HTTP Server Driver",
            "0.1.0",
            "Runs an embedded HTTP server and maps request count and last request metadata to ISPF variables",
            "ISPF",
            Map.of(
                    "listenPort", "8090",
                    "contextPath", "/ispf",
                    "pollIntervalMs", "5000"
            )
    );

    private DriverObject driverObject;
    private int listenPort = 8090;
    private String contextPath = "/ispf";
    private HttpServer httpServer;
    private final AtomicLong requestCount = new AtomicLong();
    private volatile String lastPath = "";
    private volatile String lastBody = "";
    private final Map<String, HttpServerPoint> points = new ConcurrentHashMap<>();
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
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "listenPort" -> listenPort = Integer.parseInt(value.trim());
            case "contextPath" -> contextPath = normalizeContextPath(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(listenPort), 0);
            httpServer.createContext(contextPath, new RequestHandler());
            httpServer.start();
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "HTTP server listening on port " + listenPort + " path " + contextPath);
        } catch (IOException e) {
            throw new DriverException("HTTP server start failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && httpServer != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            HttpServerPoint point = HttpServerPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readMetric(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("HTTP server driver is read-only in v0.1");
    }

    private DataRecord readMetric(HttpServerPoint point) {
        return switch (point.metric()) {
            case REQUESTS -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", String.valueOf(requestCount.get()),
                    "count", requestCount.get()
            ));
            case LAST_PATH -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", lastPath,
                    "count", requestCount.get()
            ));
            case LAST_BODY -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", lastBody,
                    "count", requestCount.get()
            ));
        };
    }

    private static String normalizeContextPath(String path) {
        if (path.isEmpty() || "/".equals(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private final class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            lastPath = exchange.getRequestURI().getPath();
            lastBody = readBody(exchange.getRequestBody());
            byte[] response = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }

    private static String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
