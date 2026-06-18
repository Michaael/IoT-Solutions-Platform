package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.time.Instant;
import java.util.Map;

/**
 * Simulated device driver for demos and integration tests — generates temperature telemetry.
 */
public class VirtualDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "virtual",
            "Virtual Simulator Driver",
            "0.1.0",
            "Generates synthetic telemetry (sine-wave temperature) for stand/demo environments",
            "ISPF",
            Map.of(
                    "baseTemperature", "22.0",
                    "amplitude", "15.0",
                    "periodSec", "60",
                    "pollIntervalMs", "2000"
            )
    );

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private double baseTemperature = 22.0;
    private double amplitude = 15.0;
    private double periodSec = 60.0;
    private final long startedAt = System.currentTimeMillis();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("baseTemperature", value -> baseTemperature = Double.parseDouble(value));
        readConfig("amplitude", value -> amplitude = Double.parseDouble(value));
        readConfig("periodSec", value -> periodSec = Double.parseDouble(value));
    }

    private void applyConfig(String key, String value) {
        switch (key) {
            case "baseTemperature" -> baseTemperature = Double.parseDouble(value);
            case "amplitude" -> amplitude = Double.parseDouble(value);
            case "periodSec" -> periodSec = Double.parseDouble(value);
            default -> { }
        }
    }

    @Override
    public void connect() {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Virtual driver connected");
    }

    @Override
    public void disconnect() {
        connected = false;
        driverObject.log(DriverLogLevel.INFO, "Virtual driver disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
        double elapsedSec = (System.currentTimeMillis() - startedAt) / 1000.0;
        double temperature = baseTemperature + amplitude * Math.sin(2 * Math.PI * elapsedSec / periodSec);

        driverObject.updateVariable(
                "temperature",
                DataRecord.single(TEMPERATURE_SCHEMA, Map.of("value", temperature, "unit", "C"))
        );
        driverObject.updateVariable(
                "status",
                DataRecord.single(STATUS_SCHEMA, Map.of(
                        "online", true,
                        "lastSeen", Instant.now().toString()
                ))
        );
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Virtual driver is read-only");
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("value");
            if (raw == null) {
                raw = record.firstRow().get("raw");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
