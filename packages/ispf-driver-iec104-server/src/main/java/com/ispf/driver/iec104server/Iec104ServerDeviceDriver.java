package com.ispf.driver.iec104server;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.Server;
import org.openmuc.j60870.ServerEventListener;
import org.openmuc.j60870.ie.IeScaledValue;
import org.openmuc.j60870.ie.IeSingleCommand;
import org.openmuc.j60870.ie.InformationElement;
import org.openmuc.j60870.ie.InformationObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IEC 60870-5-104 server/slave driver — exposes IOA state written by connected clients.
 * <p>
 * Point mapping: {@code ioa} (information object address).
 */
public class Iec104ServerDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec104-server",
            "IEC 60870-5-104 Server Driver",
            "0.1.0",
            "Hosts an IEC 60870-5-104 slave and exposes last client-written IOA values and connection state",
            "ISPF",
            Map.of(
                    "listenPort", "2404",
                    "commonAddress", "1",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("iec104ServerValue")
            .field("value", FieldType.DOUBLE)
            .field("quality", FieldType.STRING)
            .field("clientConnected", FieldType.BOOLEAN)
            .field("clientOriginatorAddress", FieldType.INTEGER)
            .build();

    private DriverObject driverObject;
    private Server server;
    private int listenPort = 2404;
    private int commonAddress = 1;
    private final Map<String, Iec104ServerPoint> points = new ConcurrentHashMap<>();
    private final Map<Integer, Double> ioaValues = new ConcurrentHashMap<>();
    private final AtomicReference<String> lastClientInfo = new AtomicReference<>("");
    private final AtomicInteger clientOriginatorAddress = new AtomicInteger(-1);
    private volatile boolean clientConnected;
    private volatile boolean listening;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("listenPort", value -> listenPort = Integer.parseInt(value));
        readConfig("commonAddress", value -> commonAddress = Integer.parseInt(value));
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "listenPort" -> listenPort = Integer.parseInt(value.trim());
            case "commonAddress" -> commonAddress = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            server = Server.builder()
                    .setPort(listenPort)
                    .build();
            server.start(serverEventListener);
            listening = true;
            driverObject.log(DriverLogLevel.INFO, "IEC104 server listening on port " + listenPort);
        } catch (IOException e) {
            listening = false;
            server = null;
            throw new DriverException("IEC104 server start failed", e);
        }
    }

    @Override
    public void disconnect() {
        listening = false;
        clientConnected = false;
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Override
    public boolean isConnected() {
        return listening && server != null && !server.isStopped();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Iec104ServerPoint point = Iec104ServerPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        Iec104ServerPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        ioaValues.put(point.ioa(), extractNumeric(value));
        driverObject.updateVariable(pointId, readPoint(point));
    }

    private DataRecord readPoint(Iec104ServerPoint point) {
        double value = ioaValues.getOrDefault(point.ioa(), 0.0);
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value,
                "quality", clientConnected ? "GOOD" : "NOT_CONNECTED",
                "clientConnected", clientConnected,
                "clientOriginatorAddress", clientOriginatorAddress.get()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        Object raw = value.firstRow().get("raw");
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        Object numeric = value.firstRow().get("value");
        if (numeric instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("IEC104 server write requires numeric raw/value field");
    }

    private void handleAsdu(ASdu aSdu) {
        if (aSdu.getCommonAddress() != commonAddress) {
            return;
        }
        for (InformationObject informationObject : aSdu.getInformationObjects()) {
            int ioa = informationObject.getInformationObjectAddress();
            Double parsed = decodeWriteValue(informationObject);
            if (parsed != null) {
                ioaValues.put(ioa, parsed);
            }
        }
    }

    private static Double decodeWriteValue(InformationObject informationObject) {
        InformationElement[][] elements = informationObject.getInformationElements();
        if (elements.length == 0 || elements[0].length == 0) {
            return null;
        }
        InformationElement element = elements[0][0];
        if (element instanceof IeScaledValue scaledValue) {
            return (double) scaledValue.getNormalizedValue();
        }
        if (element instanceof IeSingleCommand singleCommand) {
            return singleCommand.isCommandStateOn() ? 1.0 : 0.0;
        }
        return null;
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

    private final ServerEventListener serverEventListener = new ServerEventListener() {
        @Override
        public ConnectionEventListener connectionIndication(Connection connection) {
            clientConnected = true;
            clientOriginatorAddress.set(connection.getOriginatorAddress());
            lastClientInfo.set(connection.getRemoteInetAddress().getHostAddress());
            driverObject.log(DriverLogLevel.INFO, "IEC104 client connected from " + lastClientInfo.get());
            return connectionEventListener;
        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {
            listening = false;
            driverObject.log(DriverLogLevel.WARNING, "IEC104 server stopped listening");
        }

        @Override
        public void connectionAttemptFailed(IOException e) {
            driverObject.log(DriverLogLevel.DEBUG, "IEC104 connection attempt failed: " + e.getMessage());
        }
    };

    private final ConnectionEventListener connectionEventListener = new ConnectionEventListener() {
        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            handleAsdu(aSdu);
        }

        @Override
        public void connectionClosed(Connection connection, IOException cause) {
            clientConnected = false;
            clientOriginatorAddress.set(-1);
            driverObject.log(DriverLogLevel.INFO, "IEC104 client disconnected");
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {
            clientConnected = !stopped;
        }
    };
}
