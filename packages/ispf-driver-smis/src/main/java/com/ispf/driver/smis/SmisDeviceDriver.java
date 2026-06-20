package com.ispf.driver.smis;

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

/**
 * SMI-S driver — CIM-XML over HTTPS placeholder for connect and profile enumeration.
 */
public class SmisDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("smisValue")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "smi-s",
            "SMI-S CIM Driver",
            "0.1.0",
            "Connects to an SMI-S provider over HTTPS and maps CIM class properties to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5989",
                    "username", "admin",
                    "password", "",
                    "namespace", "root/pg",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5989;
    private String username = "admin";
    private String password = "";
    private String namespace = "root/pg";
    private long timeoutMs = 10000;
    private HttpClient client;
    private int lastStatusCode = -1;
    private volatile boolean profilesEnumerated;
    private final Map<String, String> stubProperties = new ConcurrentHashMap<>();
    private final Map<String, SmisPoint> points = new ConcurrentHashMap<>();
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
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "username" -> username = value;
            case "password" -> password = value;
            case "namespace" -> namespace = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "SMI-S client ready (host=" + host + ", namespace=" + namespace + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        profilesEnumerated = false;
        stubProperties.clear();
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
        enumerateProfiles();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            SmisPoint point = SmisPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readProperty(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("SMI-S driver is read-only in v0.1");
    }

    private void enumerateProfiles() throws DriverException {
        try {
            String endpoint = "https://" + host + ":" + port + "/cimom";
            String envelope = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <CIM CIMVERSION="2.0" DTDVERSION="2.0">
                      <MESSAGE ID="1" PROTOCOLVERSION="1.0">
                        <SIMPLEREQ>
                          <IMETHODCALL NAME="EnumerateInstances">
                            <LOCALNAMESPACEPATH>
                              <NAMESPACE NAME="%s"/>
                            </LOCALNAMESPACEPATH>
                            <IPARAMVALUE NAME="ClassName">
                              <CLASSNAME NAME="CIM_RegisteredProfile"/>
                            </IPARAMVALUE>
                          </IMETHODCALL>
                        </SIMPLEREQ>
                      </MESSAGE>
                    </CIM>
                    """.formatted(namespace.replace("root/", "").replace("/", "_"));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope));
            if (username != null && !username.isBlank()) {
                String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                builder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            profilesEnumerated = lastStatusCode >= 200 && lastStatusCode < 300;
            seedStubProperties(response.body());
        } catch (Exception e) {
            profilesEnumerated = false;
            throw new DriverException("SMI-S profile enumeration failed", e);
        }
    }

    private void seedStubProperties(String body) {
        stubProperties.clear();
        stubProperties.put("CIM_RegisteredProfile:RegisteredOrganization", "SNIA");
        stubProperties.put("CIM_RegisteredProfile:ProfileName", "Server Profile");
        stubProperties.put("CIM_RegisteredProfile:connected", String.valueOf(profilesEnumerated));
        if (body != null && !body.isBlank()) {
            stubProperties.put("CIM_RegisteredProfile:rawResponseLength", String.valueOf(body.length()));
        }
    }

    private DataRecord readProperty(SmisPoint point) {
        String key = point.className() + ":" + point.propertyName();
        String value = stubProperties.getOrDefault(key, "");
        if (value.isEmpty()) {
            for (Map.Entry<String, String> entry : stubProperties.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of("value", value, "statusCode", lastStatusCode));
    }
}
