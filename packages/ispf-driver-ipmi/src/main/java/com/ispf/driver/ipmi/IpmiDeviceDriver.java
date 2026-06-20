package com.ispf.driver.ipmi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.veraxsystems.vxipmi.api.async.ConnectionHandle;
import com.veraxsystems.vxipmi.api.sync.IpmiConnector;
import com.veraxsystems.vxipmi.coding.commands.IpmiVersion;
import com.veraxsystems.vxipmi.coding.commands.chassis.GetChassisStatus;
import com.veraxsystems.vxipmi.coding.commands.chassis.GetChassisStatusResponseData;
import com.veraxsystems.vxipmi.coding.commands.sdr.GetSdr;
import com.veraxsystems.vxipmi.coding.commands.sdr.GetSdrResponseData;
import com.veraxsystems.vxipmi.coding.commands.sdr.ReserveSdrRepository;
import com.veraxsystems.vxipmi.coding.commands.sdr.ReserveSdrRepositoryResponseData;
import com.veraxsystems.vxipmi.coding.commands.sdr.record.CompactSensorRecord;
import com.veraxsystems.vxipmi.coding.commands.sdr.record.FullSensorRecord;
import com.veraxsystems.vxipmi.coding.commands.sdr.record.SensorRecord;
import com.veraxsystems.vxipmi.coding.protocol.AuthenticationType;
import com.veraxsystems.vxipmi.coding.security.CipherSuite;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IPMI LAN driver — vxIPMI sensor/power reads with RMCP+ UDP ping fallback.
 */
public class IpmiDeviceDriver implements DeviceDriver {

    private static final int SDR_END_RECORD_ID = 0xFFFF;

    private static final DataSchema IPMI_SCHEMA = DataSchema.builder("ipmiValue")
            .field("value", FieldType.STRING)
            .field("reachable", FieldType.BOOLEAN)
            .field("powerOn", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ipmi",
            "IPMI LAN Driver",
            "0.1.0",
            "IPMI LAN sensor and chassis power reads via vxIPMI (fr.jrds:vxIPMI)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "623",
                    "username", "",
                    "password", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 623;
    private String username = "";
    private String password = "";
    private int timeoutMs = 5000;
    private final Map<String, IpmiPoint> points = new ConcurrentHashMap<>();
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
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        boolean reachable = RmcpPingClient.ping(host, port, timeoutMs);
        if (!reachable && (username.isBlank() || password.isBlank())) {
            throw new DriverException("IPMI host unreachable at " + host + ":" + port);
        }
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "IPMI ready (" + host + ":" + port + ", reachable=" + reachable + ")");
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
        boolean reachable = RmcpPingClient.ping(host, port, timeoutMs);
        IpmiConnector connector = null;
        ConnectionHandle handle = null;
        CipherSuite cipherSuite = CipherSuite.getEmpty();
        try {
            if (!username.isBlank() && !password.isBlank()) {
                connector = new IpmiConnector(0);
                handle = connector.createConnection(InetAddress.getByName(host), port);
                connector.setTimeout(handle, timeoutMs);
                List<CipherSuite> suites = connector.getAvailableCipherSuites(handle);
                if (!suites.isEmpty()) {
                    cipherSuite = suites.getFirst();
                }
                connector.openSession(handle, username, password, password.getBytes(StandardCharsets.US_ASCII));
            }
            for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
                IpmiPoint point = IpmiPoint.parse(entry.getValue());
                points.put(entry.getKey(), point);
                driverObject.updateVariable(entry.getKey(), readPoint(point, connector, handle, cipherSuite, reachable));
            }
        } catch (Exception e) {
            throw new DriverException("IPMI read failed for " + host + ":" + port, e);
        } finally {
            if (connector != null && handle != null) {
                try {
                    connector.closeSession(handle);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
                try {
                    connector.closeConnection(handle);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
                connector.tearDown();
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("IPMI driver is read-only in v0.1");
    }

    private DataRecord readPoint(IpmiPoint point, IpmiConnector connector, ConnectionHandle handle,
            CipherSuite cipherSuite, boolean reachable) throws Exception {
        if (connector == null || handle == null) {
            return DataRecord.single(IPMI_SCHEMA, Map.of(
                    "value", reachable ? "reachable" : "unreachable",
                    "reachable", reachable,
                    "powerOn", false
            ));
        }
        return switch (point.kind()) {
            case POWER -> readPower(connector, handle, cipherSuite, reachable);
            case SENSOR -> readSensor(connector, handle, cipherSuite, point.sensorName(), reachable);
        };
    }

    private DataRecord readPower(IpmiConnector connector, ConnectionHandle handle, CipherSuite cipherSuite,
            boolean reachable) throws Exception {
        GetChassisStatusResponseData status = (GetChassisStatusResponseData) connector.sendMessage(
                handle, new GetChassisStatus(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus));
        boolean powerOn = status.isPowerOn();
        return DataRecord.single(IPMI_SCHEMA, Map.of(
                "value", powerOn ? "on" : "off",
                "reachable", reachable,
                "powerOn", powerOn
        ));
    }

    private DataRecord readSensor(IpmiConnector connector, ConnectionHandle handle, CipherSuite cipherSuite,
            String sensorName, boolean reachable) throws Exception {
        ReserveSdrRepositoryResponseData reserve = (ReserveSdrRepositoryResponseData) connector.sendMessage(
                handle, new ReserveSdrRepository(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus));
        int reservationId = reserve.getReservationId();
        int recordId = 0;
        while (recordId != SDR_END_RECORD_ID) {
            GetSdrResponseData sdr = (GetSdrResponseData) connector.sendMessage(
                    handle, new GetSdr(IpmiVersion.V20, cipherSuite, AuthenticationType.RMCPPlus,
                            reservationId, recordId));
            byte[] recordData = sdr.getSensorRecordData();
            if (recordData != null && recordData.length > 0) {
                SensorRecord record = SensorRecord.populateSensorRecord(recordData);
                String name = sensorName(record);
                if (name != null && sensorName.equalsIgnoreCase(name)) {
                    return DataRecord.single(IPMI_SCHEMA, Map.of(
                            "value", name,
                            "reachable", reachable,
                            "powerOn", false
                    ));
                }
            }
            recordId = sdr.getNextRecordId();
        }
        return DataRecord.single(IPMI_SCHEMA, Map.of(
                "value", "",
                "reachable", reachable,
                "powerOn", false
        ));
    }

    private static String sensorName(SensorRecord record) {
        if (record instanceof FullSensorRecord full) {
            return full.getName();
        }
        if (record instanceof CompactSensorRecord compact) {
            return compact.getName();
        }
        return null;
    }
}
