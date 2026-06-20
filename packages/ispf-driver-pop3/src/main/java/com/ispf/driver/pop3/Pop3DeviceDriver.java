package com.ispf.driver.pop3;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * POP3 mailbox driver — Jakarta Mail STAT and RETR message reads.
 */
public class Pop3DeviceDriver implements DeviceDriver {

    private static final DataSchema MAILBOX_SCHEMA = DataSchema.builder("pop3Mailbox")
            .field("value", FieldType.STRING)
            .field("count", FieldType.INTEGER)
            .field("sizeBytes", FieldType.LONG)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "pop3",
            "POP3 Mailbox Driver",
            "0.1.0",
            "POP3 mailbox STAT and RETR reads via Jakarta Mail",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "110",
                    "username", "",
                    "password", "",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 110;
    private String username = "";
    private String password = "";
    private final Map<String, Pop3Point> points = new ConcurrentHashMap<>();
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
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try (Store store = openStore()) {
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "POP3 connected (" + host + ":" + port + ")");
        } catch (Exception e) {
            throw new DriverException("POP3 connect failed for " + host + ":" + port, e);
        }
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
        try (Store store = openStore()) {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            try {
                for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
                    Pop3Point point = Pop3Point.parse(entry.getValue());
                    points.put(entry.getKey(), point);
                    driverObject.updateVariable(entry.getKey(), readPoint(folder, point));
                }
            } finally {
                folder.close(false);
            }
        } catch (Exception e) {
            throw new DriverException("POP3 read failed", e);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("POP3 driver is read-only in v0.1");
    }

    private Store openStore() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "pop3");
        props.put("mail.pop3.connectiontimeout", "10000");
        props.put("mail.pop3.timeout", "10000");
        Session session = Session.getInstance(props);
        Store store = session.getStore("pop3");
        store.connect(host, port, username, password);
        return store;
    }

    private DataRecord readPoint(Folder folder, Pop3Point point) throws Exception {
        return switch (point.kind()) {
            case STAT -> {
                int count = folder.getMessageCount();
                long size = 0;
                for (int i = 1; i <= count; i++) {
                    size += folder.getMessage(i).getSize();
                }
                yield DataRecord.single(MAILBOX_SCHEMA, Map.of(
                        "value", count + " " + size,
                        "count", count,
                        "sizeBytes", size
                ));
            }
            case RETR -> readMessage(folder, point.messageNumber());
        };
    }

    private DataRecord readMessage(Folder folder, int messageNumber) throws Exception {
        if (messageNumber < 1 || messageNumber > folder.getMessageCount()) {
            return DataRecord.single(MAILBOX_SCHEMA, Map.of("value", "", "count", 0, "sizeBytes", 0L));
        }
        Message message = folder.getMessage(messageNumber);
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                message.getInputStream(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }
        return DataRecord.single(MAILBOX_SCHEMA, Map.of(
                "value", body,
                "count", 1,
                "sizeBytes", (long) message.getSize()
        ));
    }
}
