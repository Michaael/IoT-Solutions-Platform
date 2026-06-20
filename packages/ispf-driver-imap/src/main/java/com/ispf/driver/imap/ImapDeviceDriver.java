package com.ispf.driver.imap;

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

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IMAP mailbox driver — Jakarta Mail folder statistics and message subjects.
 */
public class ImapDeviceDriver implements DeviceDriver {

    private static final DataSchema MAILBOX_SCHEMA = DataSchema.builder("imapMailbox")
            .field("value", FieldType.STRING)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "imap",
            "IMAP Mailbox Driver",
            "0.1.0",
            "IMAP mailbox message counts, UNSEEN count, and message subjects via Jakarta Mail",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "993",
                    "username", "",
                    "password", "",
                    "folder", "INBOX",
                    "useSsl", "true",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 993;
    private String username = "";
    private String password = "";
    private String folderName = "INBOX";
    private boolean useSsl = true;
    private final Map<String, ImapPoint> points = new ConcurrentHashMap<>();
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
            case "folder" -> folderName = value.trim();
            case "useSsl" -> useSsl = Boolean.parseBoolean(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try (Store store = openStore()) {
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "IMAP connected (" + host + ":" + port + ", folder=" + folderName + ")");
        } catch (Exception e) {
            throw new DriverException("IMAP connect failed for " + host + ":" + port, e);
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
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);
            try {
                for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
                    ImapPoint point = ImapPoint.parse(entry.getValue());
                    points.put(entry.getKey(), point);
                    driverObject.updateVariable(entry.getKey(), readPoint(folder, point));
                }
            } finally {
                folder.close(false);
            }
        } catch (Exception e) {
            throw new DriverException("IMAP read failed", e);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("IMAP driver is read-only in v0.1");
    }

    private Store openStore() throws Exception {
        Properties props = new Properties();
        String protocol = useSsl ? "imaps" : "imap";
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "10000");
        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(host, port, username, password);
        return store;
    }

    private DataRecord readPoint(Folder folder, ImapPoint point) throws Exception {
        return switch (point.kind()) {
            case MESSAGE_COUNT -> DataRecord.single(MAILBOX_SCHEMA, Map.of(
                    "value", String.valueOf(folder.getMessageCount()),
                    "count", folder.getMessageCount()
            ));
            case UNSEEN_COUNT -> DataRecord.single(MAILBOX_SCHEMA, Map.of(
                    "value", String.valueOf(folder.getUnreadMessageCount()),
                    "count", folder.getUnreadMessageCount()
            ));
            case SUBJECT -> readSubject(folder, point.messageNumber());
        };
    }

    private DataRecord readSubject(Folder folder, int messageNumber) throws Exception {
        if (messageNumber < 1 || messageNumber > folder.getMessageCount()) {
            return DataRecord.single(MAILBOX_SCHEMA, Map.of("value", "", "count", 0));
        }
        Message message = folder.getMessage(messageNumber);
        String subject = message.getSubject();
        return DataRecord.single(MAILBOX_SCHEMA, Map.of(
                "value", subject == null ? "" : subject,
                "count", 1
        ));
    }
}
