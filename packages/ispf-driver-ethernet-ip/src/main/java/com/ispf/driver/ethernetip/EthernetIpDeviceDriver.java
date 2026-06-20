package com.ispf.driver.ethernetip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EtherNet/IP placeholder driver — validates TCP/CIP session registration.
 * <p>
 * Full tag read/write requires a native CIP stack (e.g. EIPScanner/Logix SDK); not bundled here.
 * Point mapping: {@code tagPath} (placeholder — returns connection/session status only).
 */
public class EthernetIpDeviceDriver implements DeviceDriver {

    private static final int ENCAP_REGISTER_SESSION = 0x0065;
    private static final int DEFAULT_PORT = 44818;

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ethernet-ip",
            "EtherNet/IP Driver",
            "0.1.0",
            "Placeholder CIP/EtherNet/IP driver: TCP session registration only. "
                    + "Tag read/write requires a native CIP library (not included).",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "44818",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000"
            )
    );

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("ethernetIpStatus")
            .field("connected", FieldType.BOOLEAN)
            .field("sessionHandle", FieldType.LONG)
            .field("tagPath", FieldType.STRING)
            .field("value", FieldType.STRING)
            .field("quality", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private Socket socket;
    private String host = "127.0.0.1";
    private int port = DEFAULT_PORT;
    private int timeoutMs = 5000;
    private long sessionHandle;
    private final Map<String, EthernetIpPoint> points = new ConcurrentHashMap<>();
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
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            sessionHandle = registerSession(socket);
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "EtherNet/IP session registered at " + host + ":" + port + " handle=" + sessionHandle);
        } catch (Exception e) {
            connected = false;
            closeSocket();
            throw new DriverException("EtherNet/IP connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeSocket();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            EthernetIpPoint point = EthernetIpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("EtherNet/IP tag write requires native CIP library (placeholder driver)");
    }

    private DataRecord readPoint(EthernetIpPoint point) {
        return DataRecord.single(STATUS_SCHEMA, Map.of(
                "connected", isConnected(),
                "sessionHandle", sessionHandle,
                "tagPath", point.tagPath(),
                "value", "SESSION_OK",
                "quality", "PLACEHOLDER"
        ));
    }

    private static long registerSession(Socket socket) throws IOException {
        byte[] request = buildRegisterSessionRequest();
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(request);
        out.flush();

        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] header = in.readNBytes(24);
        if (header.length < 24) {
            throw new IOException("Incomplete EtherNet/IP encapsulation header");
        }
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = Short.toUnsignedInt(buffer.getShort(0));
        int status = buffer.getInt(8);
        long handle = Integer.toUnsignedLong(buffer.getInt(4));
        if (command != ENCAP_REGISTER_SESSION || status != 0) {
            throw new IOException("Register Session failed: command=" + command + " status=" + status);
        }
        int length = Short.toUnsignedInt(buffer.getShort(2));
        if (length > 0) {
            in.readNBytes(length);
        }
        return handle;
    }

    private static byte[] buildRegisterSessionRequest() {
        ByteBuffer buffer = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) ENCAP_REGISTER_SESSION);
        buffer.putShort((short) 4);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putLong(0L);
        buffer.putInt(0);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0);
        return buffer.array();
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
            socket = null;
        }
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
