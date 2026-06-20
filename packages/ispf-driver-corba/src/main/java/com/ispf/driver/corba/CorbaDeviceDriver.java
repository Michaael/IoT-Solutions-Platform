package com.ispf.driver.corba;

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
 * CORBA IIOP connectivity stub.
 * <p>
 * Java CORBA (javax.rmi.CORBA, org.omg.CORBA) was removed in JDK 11+. This driver performs
 * TCP reachability to the IIOP port and optionally records an IOR string for future native-bridge use.
 */
public class CorbaDeviceDriver implements DeviceDriver {

    private static final DataSchema CORBA_SCHEMA = DataSchema.builder("corbaResult")
            .field("connected", FieldType.BOOLEAN)
            .field("value", FieldType.STRING)
            .field("limitation", FieldType.STRING)
            .build();

    private static final String LIMITATION =
            "Java CORBA removed in JDK 11+; v0.1 performs IIOP TCP connect only (no ORB)";

    private static final DriverMetadata METADATA = new DriverMetadata(
            "corba",
            "CORBA IIOP Driver",
            "0.1.0",
            "CORBA IIOP TCP connectivity stub (Java CORBA removed in modern JDK)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2809",
                    "ior", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2809;
    private String ior = "";
    private int timeoutMs = 5000;
    private final Map<String, CorbaPoint> points = new ConcurrentHashMap<>();
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
            case "port" -> port = Integer.parseInt(value.trim());
            case "ior" -> ior = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.WARNING, LIMITATION);
        driverObject.log(DriverLogLevel.INFO, "CORBA stub ready for IIOP " + host + ":" + port);
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
            CorbaPoint point = CorbaPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), check(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("CORBA driver is read-only in v0.1");
    }

    private DataRecord check(CorbaPoint point) {
        boolean reachable = tcpConnect(host, port);
        String value = reachable ? "iiop-open" : "iiop-closed";
        if (!ior.isBlank()) {
            value = value + ";ior-configured";
        }
        return DataRecord.single(CORBA_SCHEMA, Map.of(
                "connected", reachable,
                "value", value,
                "limitation", LIMITATION
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
