package com.ispf.driver.opcbridge;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LON/OPC bridge placeholder — TCP connectivity check for external OPC bridge services (#45 backlog).
 */
public class OpcBridgeDeviceDriver implements DeviceDriver {

    private static final DataSchema BRIDGE_SCHEMA = DataSchema.builder("opcBridgeResult")
            .field("connected", FieldType.BOOLEAN)
            .field("value", FieldType.STRING)
            .field("host", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opc-bridge",
            "OPC Bridge Driver",
            "0.1.0",
            "LON/OPC bridge TCP connectivity placeholder",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4841",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 4841;
    private int timeoutMs = 5000;
    private final Map<String, OpcBridgePoint> points = new ConcurrentHashMap<>();
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
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "OPC bridge stub ready for " + host + ":" + port);
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
            OpcBridgePoint point = OpcBridgePoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), check(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("OPC bridge driver is read-only in v0.1");
    }

    private DataRecord check(OpcBridgePoint point) {
        boolean reachable = tcpConnect(host, port);
        return DataRecord.single(BRIDGE_SCHEMA, Map.of(
                "connected", reachable,
                "value", reachable ? "bridge-open" : "bridge-closed",
                "host", host + ":" + port
        ));
    }

    private boolean tcpConnect(String targetHost, int targetPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
