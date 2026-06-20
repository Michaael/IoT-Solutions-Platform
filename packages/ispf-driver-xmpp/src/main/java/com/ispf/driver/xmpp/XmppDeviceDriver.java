package com.ispf.driver.xmpp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.ping.PingManager;
import org.jxmpp.jid.parts.Resourcepart;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XMPP driver — Smack presence/login and roster metrics.
 */
public class XmppDeviceDriver implements DeviceDriver {

    private static final DataSchema XMPP_SCHEMA = DataSchema.builder("xmppResult")
            .field("value", FieldType.STRING)
            .field("online", FieldType.BOOLEAN)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "xmpp",
            "XMPP Driver",
            "0.1.0",
            "XMPP presence/login and roster count via Smack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5222",
                    "username", "user",
                    "password", "",
                    "domain", "example.com",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5222;
    private String username = "user";
    private String password = "";
    private String domain = "example.com";
    private int timeoutMs = 10_000;
    private AbstractXMPPConnection connection;
    private final Map<String, XmppPoint> points = new ConcurrentHashMap<>();
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
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "domain" -> domain = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                    .setHost(host)
                    .setPort(port)
                    .setXmppDomain(domain)
                    .setConnectTimeout(timeoutMs)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                    .build();
            connection = new XMPPTCPConnection(config);
            connection.connect();
            if (!password.isEmpty()) {
                connection.login(username, password, Resourcepart.from("ispf"));
            }
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "XMPP connected to " + host + ":" + port);
        } catch (Exception e) {
            closeConnection();
            throw new DriverException("XMPP connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeConnection();
    }

    @Override
    public boolean isConnected() {
        return connected && connection != null && connection.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            XmppPoint point = XmppPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), read(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("XMPP driver is read-only in v0.1");
    }

    private DataRecord read(XmppPoint point) throws DriverException {
        try {
            return switch (point.mode()) {
                case PRESENCE -> presence();
                case ROSTER_COUNT -> rosterCount();
            };
        } catch (Exception e) {
            throw new DriverException("XMPP read failed for " + point.mode(), e);
        }
    }

    private DataRecord presence() throws SmackException.NotConnectedException, SmackException.NoResponseException,
            SmackException.NotLoggedInException, InterruptedException {
        PingManager pingManager = PingManager.getInstanceFor(connection);
        boolean online = pingManager.pingMyServer();
        return DataRecord.single(XMPP_SCHEMA, Map.of(
                "value", online ? "online" : "offline",
                "online", online,
                "count", 0
        ));
    }

    private DataRecord rosterCount() {
        Roster roster = Roster.getInstanceFor(connection);
        int count = roster.getEntries().size();
        return DataRecord.single(XMPP_SCHEMA, Map.of(
                "value", String.valueOf(count),
                "online", connection.isAuthenticated(),
                "count", count
        ));
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                if (connection.isConnected()) {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
                // best effort
            }
            connection = null;
        }
    }
}
