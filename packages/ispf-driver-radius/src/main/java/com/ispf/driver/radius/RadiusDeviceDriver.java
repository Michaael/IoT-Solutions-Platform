package com.ispf.driver.radius;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RADIUS authentication probe driver — TinyRadius Access-Request.
 */
public class RadiusDeviceDriver implements DeviceDriver {

    private static final DataSchema AUTH_SCHEMA = DataSchema.builder("radiusAuth")
            .field("value", FieldType.STRING)
            .field("success", FieldType.BOOLEAN)
            .field("responseCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "radius",
            "RADIUS Auth Driver",
            "0.1.0",
            "RADIUS Access-Request authentication probe via TinyRadius",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "1812",
                    "secret", "testing123",
                    "username", "",
                    "password", "",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 1812;
    private String secret = "testing123";
    private String username = "";
    private String password = "";
    private int timeoutMs = 3000;
    private final Map<String, RadiusPoint> points = new ConcurrentHashMap<>();
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
            case "secret" -> secret = value;
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "RADIUS client ready (" + host + ":" + port + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            RadiusPoint point = RadiusPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), authenticate(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("RADIUS driver is read-only");
    }

    private DataRecord authenticate(RadiusPoint point) throws DriverException {
        if (point.kind() != RadiusPoint.Kind.AUTH) {
            throw new DriverException("Unsupported RADIUS point kind: " + point.kind());
        }
        try {
            RadiusClient client = new RadiusClient(host, secret);
            client.setAuthPort(port);
            client.setSocketTimeout(timeoutMs);
            AccessRequest request = new AccessRequest(username, password);
            request.setAuthProtocol(AccessRequest.AUTH_PAP);
            RadiusPacket response = client.authenticate(request);
            boolean success = response != null && response.getPacketType() == RadiusPacket.ACCESS_ACCEPT;
            return DataRecord.single(AUTH_SCHEMA, Map.of(
                    "value", success ? "success" : "fail",
                    "success", success,
                    "responseCode", response == null ? -1 : response.getPacketType()
            ));
        } catch (Exception e) {
            return DataRecord.single(AUTH_SCHEMA, Map.of(
                    "value", "fail",
                    "success", false,
                    "responseCode", -1
            ));
        }
    }
}
