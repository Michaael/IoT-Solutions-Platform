package com.ispf.driver.odbc;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ODBC driver — JDBC bridge pattern for ODBC data sources via external bridge drivers.
 */
public class OdbcDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("odbcValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "odbc",
            "ODBC JDBC Bridge Driver",
            "0.1.0",
            "Executes a configured SQL query via JDBC (use an ODBC-JDBC bridge driver). "
                    + "Requires an external ODBC-JDBC bridge driver JAR on the classpath.",
            "ISPF",
            Map.of(
                    "jdbcUrl", "jdbc:odbc:DSN_NAME",
                    "username", "",
                    "password", "",
                    "query", "SELECT 1 AS value",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String jdbcUrl = "jdbc:odbc:DSN_NAME";
    private String username = "";
    private String password = "";
    private String query = "SELECT 1 AS value";
    private int timeoutMs = 5000;
    private Connection connection;
    private Map<String, Object> lastRow = Map.of();
    private final Map<String, OdbcPoint> points = new ConcurrentHashMap<>();
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
            case "jdbcUrl" -> jdbcUrl = value.trim();
            case "username" -> username = value;
            case "password" -> password = value;
            case "query" -> query = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            DriverManager.setLoginTimeout(Math.max(1, timeoutMs / 1000));
            if (username == null || username.isBlank()) {
                connection = DriverManager.getConnection(jdbcUrl);
            } else {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
            }
            connection.setReadOnly(true);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "ODBC/JDBC bridge connected to " + jdbcUrl);
        } catch (SQLException e) {
            throw new DriverException("ODBC connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        lastRow = Map.of();
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best effort
            }
            connection = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && connection != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        lastRow = executeQuery();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OdbcPoint point = OdbcPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readColumn(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("ODBC driver is read-only in v0.1");
    }

    private Map<String, Object> executeQuery() throws DriverException {
        String normalized = query.trim();
        if (!normalized.regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new DriverException("Only SELECT queries are allowed");
        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(Math.max(1, timeoutMs / 1000));
            try (ResultSet rs = statement.executeQuery(normalized)) {
                if (!rs.next()) {
                    return Map.of();
                }
                Map<String, Object> row = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String label = meta.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    row.put(label, val == null ? "" : String.valueOf(val));
                }
                return row;
            }
        } catch (SQLException e) {
            throw new DriverException("ODBC query failed", e);
        }
    }

    private DataRecord readColumn(OdbcPoint point) {
        String column = point.columnName();
        Object value = lastRow.get(column);
        if (value == null) {
            for (Map.Entry<String, Object> entry : lastRow.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(column)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of("value", value == null ? "" : String.valueOf(value)));
    }
}
