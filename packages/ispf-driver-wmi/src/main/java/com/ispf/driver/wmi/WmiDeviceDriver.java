package com.ispf.driver.wmi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Windows WMI driver — PowerShell {@code Get-CimInstance} when running on Windows.
 */
public class WmiDeviceDriver implements DeviceDriver {

    private static final DataSchema WMI_SCHEMA = DataSchema.builder("wmiValue")
            .field("value", FieldType.STRING)
            .field("supported", FieldType.BOOLEAN)
            .field("status", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "wmi",
            "Windows WMI Driver",
            "0.1.0",
            "Windows WMI scalar reads via PowerShell Get-CimInstance",
            "ISPF",
            Map.of(
                    "namespace", "root\\cimv2",
                    "query", "SELECT Name FROM Win32_OperatingSystem",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000"
            )
    );

    private DriverObject driverObject;
    private String namespace = "root\\cimv2";
    private String defaultQuery = "SELECT Name FROM Win32_OperatingSystem";
    private int timeoutMs = 10_000;
    private final Map<String, WmiPoint> points = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private final boolean windowsHost = System.getProperty("os.name", "").toLowerCase().contains("windows");

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
            case "namespace" -> namespace = value.trim();
            case "query" -> defaultQuery = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        if (!windowsHost) {
            connected = true;
            driverObject.log(DriverLogLevel.WARNING, "WMI driver unsupported on non-Windows host");
            return;
        }
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "WMI driver ready (namespace=" + namespace + ")");
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
            WmiPoint point = WmiPoint.parse(entry.getValue(), defaultQuery);
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), query(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("WMI driver is read-only");
    }

    private DataRecord query(WmiPoint point) throws DriverException {
        if (!windowsHost) {
            return DataRecord.single(WMI_SCHEMA, Map.of(
                    "value", "",
                    "supported", false,
                    "status", "unsupported: WMI requires Windows host"
            ));
        }
        try {
            String property = point.property() == null ? guessScalarProperty(point.query()) : point.property();
            String command = "Get-CimInstance -Namespace '" + escapePowerShell(namespace)
                    + "' -Query '" + escapePowerShell(point.query()) + "'"
                    + " | Select-Object -First 1 -ExpandProperty " + property;
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new DriverException("WMI query timed out after " + timeoutMs + "ms");
            }
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .reduce((first, second) -> second)
                        .orElse("");
            }
            if (process.exitValue() != 0) {
                return DataRecord.single(WMI_SCHEMA, Map.of(
                        "value", "",
                        "supported", true,
                        "status", "error: " + output
                ));
            }
            return DataRecord.single(WMI_SCHEMA, Map.of(
                    "value", output,
                    "supported", true,
                    "status", "ok"
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("WMI query failed", e);
        }
    }

    private static String guessScalarProperty(String query) {
        String upper = query.toUpperCase();
        int select = upper.indexOf("SELECT ");
        int from = upper.indexOf(" FROM ");
        if (select >= 0 && from > select + 7) {
            String columns = query.substring(select + 7, from).trim();
            if (!columns.contains(",") && !"*".equals(columns)) {
                return columns;
            }
        }
        return "Name";
    }

    private static String escapePowerShell(String value) {
        return value.replace("'", "''");
    }
}
