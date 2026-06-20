package com.ispf.driver.opcda;

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
 * OPC DA stub — OPC Classic requires Windows DCOM. When {@code proxyPort} is set, performs TCP
 * reachability to an OPC proxy/bridge; otherwise reports native bridge requirement.
 */
public class OpcDaDeviceDriver implements DeviceDriver {

    private static final DataSchema OPC_DA_SCHEMA = DataSchema.builder("opcDaResult")
            .field("value", FieldType.STRING)
            .field("reachable", FieldType.BOOLEAN)
            .field("bridgeRequired", FieldType.BOOLEAN)
            .build();

    private static final String BRIDGE_NOTE =
            "OPC DA requires Windows DCOM or native OPC bridge; item reads are placeholders in v0.1";

    private static final DriverMetadata METADATA = new DriverMetadata(
            "opc-da",
            "OPC DA Driver",
            "0.1.0",
            "OPC Classic DA stub (Windows DCOM / native bridge required)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "progId", "OPCServer.WinCC.1",
                    "clsid", "",
                    "proxyPort", "0",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private String progId = "OPCServer.WinCC.1";
    private String clsid = "";
    private int proxyPort = 0;
    private int timeoutMs = 5000;
    private final Map<String, OpcDaPoint> points = new ConcurrentHashMap<>();
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
            case "progId" -> progId = value.trim();
            case "clsid" -> clsid = value.trim();
            case "proxyPort" -> proxyPort = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.WARNING, BRIDGE_NOTE);
        driverObject.log(DriverLogLevel.INFO,
                "OPC DA stub ready (host=" + host + ", progId=" + progId + ", proxyPort=" + proxyPort + ")");
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
            OpcDaPoint point = OpcDaPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), read(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("OPC DA driver is read-only in v0.1");
    }

    private DataRecord read(OpcDaPoint point) {
        boolean proxyConfigured = proxyPort > 0;
        boolean reachable = proxyConfigured && tcpConnect(host, proxyPort);
        String value;
        if (point.statusOnly()) {
            value = proxyConfigured
                    ? (reachable ? "proxy-reachable" : "proxy-unreachable")
                    : "dcom-bridge-required";
        } else {
            value = proxyConfigured && reachable
                    ? "placeholder:" + point.itemId()
                    : "unavailable:" + point.itemId();
        }
        return DataRecord.single(OPC_DA_SCHEMA, Map.of(
                "value", value,
                "reachable", reachable,
                "bridgeRequired", !proxyConfigured || !reachable
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
