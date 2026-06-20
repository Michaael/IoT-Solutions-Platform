package com.ispf.driver.dhcp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DHCP discover probe driver — UDP broadcast DISCOVER for server IP and lease status.
 */
public class DhcpDeviceDriver implements DeviceDriver {

    private static final DataSchema PROBE_SCHEMA = DataSchema.builder("dhcpProbe")
            .field("value", FieldType.STRING)
            .field("leased", FieldType.BOOLEAN)
            .field("leaseSeconds", FieldType.LONG)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "dhcp",
            "DHCP Discover Driver",
            "0.1.0",
            "UDP DHCP DISCOVER probe for server IP and lease status",
            "ISPF",
            Map.of(
                    "interfaceName", "",
                    "bindAddress", "0.0.0.0",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String interfaceName = "";
    private String bindAddress = "0.0.0.0";
    private int timeoutMs = 3000;
    private final Map<String, DhcpPoint> points = new ConcurrentHashMap<>();
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
            case "interfaceName" -> interfaceName = value.trim();
            case "bindAddress" -> bindAddress = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "DHCP probe ready (bind=" + bindAddress + ", timeoutMs=" + timeoutMs + ")");
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
        try {
            DhcpDiscoverClient.DhcpProbeResult result =
                    DhcpDiscoverClient.probe(interfaceName, bindAddress, timeoutMs);
            for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
                DhcpPoint point = DhcpPoint.parse(entry.getValue());
                points.put(entry.getKey(), point);
                driverObject.updateVariable(entry.getKey(), toRecord(point, result));
            }
        } catch (Exception e) {
            throw new DriverException("DHCP probe failed", e);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("DHCP driver is read-only");
    }

    private static DataRecord toRecord(DhcpPoint point, DhcpDiscoverClient.DhcpProbeResult result) {
        return switch (point.kind()) {
            case SERVER_IP -> DataRecord.single(PROBE_SCHEMA, Map.of(
                    "value", result.serverIp() == null ? "" : result.serverIp(),
                    "leased", result.leased(),
                    "leaseSeconds", result.leaseSeconds()
            ));
            case LEASE -> DataRecord.single(PROBE_SCHEMA, Map.of(
                    "value", result.leased() ? "obtained" : "none",
                    "leased", result.leased(),
                    "leaseSeconds", result.leaseSeconds()
            ));
        };
    }
}
