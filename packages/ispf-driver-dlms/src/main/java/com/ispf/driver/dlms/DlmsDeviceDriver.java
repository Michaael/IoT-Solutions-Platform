package com.ispf.driver.dlms;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import gurux.net.GXNet;
import gurux.net.enums.NetworkType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DLMS/COSEM meter read driver — TCP wrapper transport with Gurux DLMS library.
 * <p>
 * Full authenticated COSEM reads require meter-specific credentials and association;
 * v0.1 reports TCP reachability and parsed OBIS mapping as placeholder values.
 * Point mapping: {@code logicalDevice:obis} e.g. {@code 1:1.0.1.8.0.255}.
 */
public class DlmsDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dlms",
            "DLMS/COSEM Driver",
            "0.1.0",
            "Reads DLMS/COSEM smart meters over TCP (Gurux). v0.1 validates TCP reachability and OBIS mapping; "
                    + "full authenticated reads require meter-specific association settings.",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "4059",
                    "timeoutMs", "10000",
                    "clientAddress", "16",
                    "serverAddress", "1",
                    "pollIntervalMs", "60000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("dlmsValue")
            .field("value", FieldType.STRING)
            .field("obis", FieldType.STRING)
            .field("logicalDevice", FieldType.INTEGER)
            .field("quality", FieldType.STRING)
            .field("tcpConnected", FieldType.BOOLEAN)
            .build();

    private DriverObject driverObject;
    private GXNet media;
    private String host = "127.0.0.1";
    private int port = 4059;
    private int timeoutMs = 10000;
    private final Map<String, DlmsPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("host", value -> host = value);
        readConfig("port", value -> port = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
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
        try {
            media = new GXNet(NetworkType.TCP, host, port);
            media.open();
            connected = media.isOpen();
            if (!connected) {
                throw new DriverException("DLMS TCP connect failed");
            }
            driverObject.log(DriverLogLevel.INFO, "DLMS TCP connected to " + host + ":" + port);
        } catch (Exception e) {
            connected = false;
            media = null;
            throw new DriverException("DLMS connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (media != null) {
            try {
                media.close();
            } catch (Exception ignored) {
                // best effort
            }
            media = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && media != null && media.isOpen();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            DlmsPoint point = DlmsPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("DLMS write not implemented in v0.1");
    }

    private DataRecord readPoint(DlmsPoint point) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", "PLACEHOLDER",
                "obis", point.obis(),
                "logicalDevice", point.logicalDevice(),
                "quality", "TCP_CONNECTED",
                "tcpConnected", isConnected()
        ));
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw == null) {
                raw = record.firstRow().get("value");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
