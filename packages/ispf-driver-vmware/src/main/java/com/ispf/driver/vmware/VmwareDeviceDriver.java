package com.ispf.driver.vmware;

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
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VMware vSphere SOAP API stub — RetrieveServiceContent over HTTPS.
 */
public class VmwareDeviceDriver implements DeviceDriver {

    private static final Pattern XML_VALUE_PATTERN = Pattern.compile(
            "<([\\w:]+)>\\s*([^<]+?)\\s*</\\1>",
            Pattern.CASE_INSENSITIVE
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("vmwareValue")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "vmware",
            "VMware vSphere Driver",
            "0.1.0",
            "Retrieves vSphere SOAP service content and maps properties (e.g. about.version) to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "vcenter.local",
                    "username", "administrator@vsphere.local",
                    "password", "",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000",
                    "useHttp", "false"
            )
    );

    private DriverObject driverObject;
    private String host = "vcenter.local";
    private String username = "administrator@vsphere.local";
    private String password = "";
    private long timeoutMs = 10000;
    private boolean useHttp;
    private HttpClient client;
    private int lastStatusCode = -1;
    private volatile boolean lastConnectOk;
    private final Map<String, String> lastProperties = new ConcurrentHashMap<>();
    private final Map<String, VmwarePoint> points = new ConcurrentHashMap<>();
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
            case "host" -> host = value.trim();
            case "username" -> username = value;
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "useHttp" -> useHttp = Boolean.parseBoolean(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "VMware client ready (host=" + host + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        lastProperties.clear();
        lastConnectOk = false;
        lastStatusCode = -1;
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
        retrieveServiceContent();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            VmwarePoint point = VmwarePoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("VMware driver is read-only in v0.1");
    }

    private void retrieveServiceContent() throws DriverException {
        try {
            String scheme = useHttp ? "http" : "https";
            String endpoint = scheme + "://" + host + "/sdk";
            String envelope = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                      xmlns:vim="urn:vim25">
                      <soapenv:Body>
                        <vim:RetrieveServiceContent>
                          <vim:_this type="ServiceInstance">ServiceInstance</vim:_this>
                        </vim:RetrieveServiceContent>
                      </soapenv:Body>
                    </soapenv:Envelope>
                    """;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope));
            if (username != null && !username.isBlank()) {
                String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                builder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            lastConnectOk = lastStatusCode >= 200 && lastStatusCode < 300;
            lastProperties.clear();
            if (response.body() != null) {
                parseProperties(response.body(), lastProperties);
            }
        } catch (Exception e) {
            lastConnectOk = false;
            throw new DriverException("VMware RetrieveServiceContent failed", e);
        }
    }

    private DataRecord readPoint(VmwarePoint point) {
        if (point.isConnectedPoint()) {
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", String.valueOf(lastConnectOk),
                    "statusCode", lastStatusCode
            ));
        }
        String value = resolveProperty(point.propertyPath());
        return DataRecord.single(VALUE_SCHEMA, Map.of("value", value, "statusCode", lastStatusCode));
    }

    private String resolveProperty(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String direct = lastProperties.get(path);
        if (direct != null) {
            return direct;
        }
        String dottedKey = path.replace('.', '_');
        if (lastProperties.containsKey(dottedKey)) {
            return lastProperties.get(dottedKey);
        }
        for (Map.Entry<String, String> entry : lastProperties.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(path) || entry.getKey().endsWith(path)) {
                return entry.getValue();
            }
        }
        return "";
    }

    static void parseProperties(String xml, Map<String, String> target) {
        Matcher matcher = XML_VALUE_PATTERN.matcher(xml);
        while (matcher.find()) {
            target.put(matcher.group(1), matcher.group(2).trim());
        }
    }
}
