package com.ispf.driver.modemat;

import com.fazecast.jSerialComm.SerialPort;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GSM modem AT command driver — serial or TCP transport (similar to message-stream).
 */
public class ModemAtDeviceDriver implements DeviceDriver {

    private static final DataSchema AT_SCHEMA = DataSchema.builder("modemAt")
            .field("value", FieldType.STRING)
            .field("response", FieldType.STRING)
            .field("success", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "modem-at",
            "GSM Modem AT Driver",
            "0.1.0",
            "Sends AT commands to GSM modems over serial port or TCP",
            "ISPF",
            Map.of(
                    "mode", "tcp",
                    "host", "127.0.0.1",
                    "port", "4001",
                    "serialPort", "COM3",
                    "baudRate", "115200",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String mode = "tcp";
    private String host = "127.0.0.1";
    private int port = 4001;
    private String serialPortName = "COM3";
    private int baudRate = 115200;
    private int timeoutMs = 5000;
    private Socket tcpSocket;
    private SerialPort serialPort;
    private final Map<String, ModemAtPoint> points = new ConcurrentHashMap<>();
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
            case "mode" -> mode = value.trim().toLowerCase(Locale.ROOT);
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "serialPort" -> serialPortName = value.trim();
            case "baudRate" -> baudRate = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            if ("serial".equals(mode)) {
                serialPort = SerialPort.getCommPort(serialPortName);
                serialPort.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
                serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, timeoutMs, 0);
                if (!serialPort.openPort()) {
                    throw new DriverException("Failed to open serial port " + serialPortName);
                }
            } else {
                tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(host, port), timeoutMs);
                tcpSocket.setSoTimeout(timeoutMs);
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "Modem AT ready (" + mode + (isSerial() ? " " + serialPortName : " " + host + ":" + port) + ")");
        } catch (IOException e) {
            throw new DriverException("Modem AT connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException ignored) {
                // best effort
            }
            tcpSocket = null;
        }
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            serialPort = null;
        }
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
            ModemAtPoint point = ModemAtPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), execute(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Modem AT driver is read-only in v0.1");
    }

    private DataRecord execute(ModemAtPoint point) throws DriverException {
        try {
            String response = sendAtCommand(point.wireCommand());
            boolean success = response.toUpperCase(Locale.ROOT).contains("OK");
            String value = extractPayload(response);
            return DataRecord.single(AT_SCHEMA, Map.of(
                    "value", value,
                    "response", response.trim(),
                    "success", success
            ));
        } catch (Exception e) {
            throw new DriverException("AT command failed: " + point.command(), e);
        }
    }

    private String sendAtCommand(String command) throws IOException {
        OutputStream out = isSerial() ? serialPort.getOutputStream() : tcpSocket.getOutputStream();
        InputStream in = isSerial() ? serialPort.getInputStream() : tcpSocket.getInputStream();
        out.write(command.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return readUntilComplete(in);
    }

    private String readUntilComplete(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        long deadline = System.currentTimeMillis() + timeoutMs;
        byte[] buffer = new byte[256];
        while (System.currentTimeMillis() < deadline) {
            int available = in.available();
            if (available > 0) {
                int read = in.read(buffer, 0, Math.min(available, buffer.length));
                if (read > 0) {
                    sb.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
                    String text = sb.toString().toUpperCase(Locale.ROOT);
                    if (text.contains("OK") || text.contains("ERROR")) {
                        return sb.toString();
                    }
                }
            } else if (isSerial()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return sb.toString();
                }
            } else {
                try {
                    int read = in.read(buffer);
                    if (read > 0) {
                        sb.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
                    }
                } catch (IOException e) {
                    if (!sb.isEmpty()) {
                        return sb.toString();
                    }
                    throw e;
                }
            }
        }
        return sb.toString();
    }

    private static String extractPayload(String response) {
        String[] lines = response.split("\\r?\\n");
        StringBuilder payload = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || "OK".equalsIgnoreCase(trimmed) || trimmed.toUpperCase(Locale.ROOT).startsWith("ERROR")) {
                continue;
            }
            if (!payload.isEmpty()) {
                payload.append(' ');
            }
            payload.append(trimmed);
        }
        return payload.toString().trim();
    }

    private boolean isSerial() {
        return "serial".equals(mode);
    }
}
