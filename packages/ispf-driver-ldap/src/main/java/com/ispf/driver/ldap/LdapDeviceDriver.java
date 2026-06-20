package com.ispf.driver.ldap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import javax.net.ssl.SSLContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LDAP directory driver — UnboundID LDAP SDK search and attribute reads.
 */
public class LdapDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ldapValue")
            .field("value", FieldType.STRING)
            .field("count", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ldap",
            "LDAP Directory Driver",
            "0.1.0",
            "LDAP search filters and attribute reads via UnboundID LDAP SDK",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "389",
                    "bindDn", "",
                    "password", "",
                    "useSsl", "false",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 389;
    private String bindDn = "";
    private String password = "";
    private boolean useSsl;
    private final Map<String, LdapPoint> points = new ConcurrentHashMap<>();
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
            case "bindDn" -> bindDn = value.trim();
            case "password" -> password = value;
            case "useSsl" -> useSsl = Boolean.parseBoolean(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try (LDAPConnection probe = openConnection()) {
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "LDAP connected (" + (useSsl ? "ldaps" : "ldap") + "://" + host + ":" + port + ")");
        } catch (LDAPException e) {
            throw new DriverException("LDAP connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        driverObject.log(DriverLogLevel.INFO, "LDAP disconnected");
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
        try (LDAPConnection connection = openConnection()) {
            for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
                LdapPoint point = LdapPoint.parse(entry.getValue());
                points.put(entry.getKey(), point);
                driverObject.updateVariable(entry.getKey(), readPoint(connection, point));
            }
        } catch (LDAPException e) {
            throw new DriverException("LDAP read failed", e);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("LDAP driver is read-only in v0.1");
    }

    private LDAPConnection openConnection() throws LDAPException {
        LDAPConnectionOptions options = new LDAPConnectionOptions();
        options.setConnectTimeoutMillis(10_000);
        if (useSsl) {
            try {
                return new LDAPConnection(SSLContext.getDefault().getSocketFactory(), options, host, port,
                        bindDn.isBlank() ? null : bindDn, password.isBlank() ? null : password);
            } catch (Exception e) {
                throw new LDAPException(com.unboundid.ldap.sdk.ResultCode.CONNECT_ERROR, e.getMessage(), e);
            }
        }
        return new LDAPConnection(options, host, port, bindDn.isBlank() ? null : bindDn,
                password.isBlank() ? null : password);
    }

    private DataRecord readPoint(LDAPConnection connection, LdapPoint point) throws LDAPException {
        if (point.kind() == LdapPoint.Kind.FILTER_COUNT) {
            SearchResult result = connection.search(new SearchRequest("", SearchScope.SUB, point.filter()));
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", String.valueOf(result.getEntryCount()),
                    "count", result.getEntryCount()
            ));
        }
        SearchResult result = connection.search(new SearchRequest("", SearchScope.SUB, point.filter(), point.attribute()));
        if (result.getEntryCount() == 0) {
            return DataRecord.single(VALUE_SCHEMA, Map.of("value", "", "count", 0));
        }
        SearchResultEntry entry = result.getSearchEntries().getFirst();
        String value = entry.getAttributeValue(point.attribute());
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", value == null ? "" : value,
                "count", result.getEntryCount()
        ));
    }
}
