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
 * Simulated device driver for demos and integration tests.
 * Profiles: demo, meter, weighbridge, rack-signals, lab, unified (all types showcase).
 */
public class VirtualDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "virtual",
            "Virtual Simulator Driver",
            "0.3.0",
            "Synthetic telemetry: demo, meter, weighbridge, rack-signals, lab waves, unified multi-type showcase",
            "ISPF",
            Map.ofEntries(
                    Map.entry("profile", "demo"),
                    Map.entry("baseTemperature", "22.0"),
                    Map.entry("amplitude", "15.0"),
                    Map.entry("periodSec", "60"),
                    Map.entry("litersPerSecond", "120"),
                    Map.entry("tareKg", "15000"),
                    Map.entry("density", "0.85"),
                    Map.entry("rackId", "rack-1"),
                    Map.entry("gasConnected", "true"),
                    Map.entry("groundConnected", "true"),
                    Map.entry("pollIntervalMs", "2000"),
                    Map.entry("sineAmplitude", "10.0"),
                    Map.entry("sawtoothAmplitude", "5.0"),
                    Map.entry("triangleAmplitude", "5.0"),
                    Map.entry("baseLatitude", "55.7558"),
                    Map.entry("baseLongitude", "37.6173"),
                    Map.entry("orbitRadiusM", "50"),
                    Map.entry("serialNumber", "VIRT-UNIFIED-001"),
                    Map.entry("firmwareVersion", "1.0.0-unified")
            )
    );

    private static final DataSchema WAVE_SCHEMA = DataSchema.builder("waveReading")
            .field("value", FieldType.DOUBLE)
            .build();

    private static final DataSchema TEMPERATURE_SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    private static final DataSchema METER_SCHEMA = DataSchema.builder("meterReading")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema FLOW_SCHEMA = DataSchema.builder("flowRate")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema FILLING_SCHEMA = DataSchema.builder("filling")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema WEIGHT_SCHEMA = DataSchema.builder("grossWeight")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    private static final DataSchema SIGNAL_SCHEMA = DataSchema.builder("rackSignal")
            .field("value", FieldType.BOOLEAN)
            .build();

    private DriverObject driverObject;
    private String profile = "demo";
    private double baseTemperature = 22.0;
    private double amplitude = 15.0;
    private double sineAmplitude = 10.0;
    private double sawtoothAmplitude = 5.0;
    private double triangleAmplitude = 5.0;
    private double periodSec = 60.0;
    private double litersPerSecond = 120.0;
    private double tareKg = 15_000.0;
    private double density = 0.85;
    private String rackId = "rack-1";
    private boolean gasConnected = true;
    private boolean groundConnected = true;
    private boolean filling = true;
    private double meterLiters;
    private long lastPollAt;
    private final long startedAt = System.currentTimeMillis();
    private final VirtualUnifiedPoll.UnifiedState unifiedState = new VirtualUnifiedPoll.UnifiedState();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        readConfig("profile", value -> profile = value.toLowerCase());
        readConfig("baseTemperature", value -> baseTemperature = Double.parseDouble(value));
        readConfig("amplitude", value -> amplitude = Double.parseDouble(value));
        readConfig("sineAmplitude", value -> sineAmplitude = Double.parseDouble(value));
        readConfig("sawtoothAmplitude", value -> sawtoothAmplitude = Double.parseDouble(value));
        readConfig("triangleAmplitude", value -> triangleAmplitude = Double.parseDouble(value));
        readConfig("periodSec", value -> periodSec = Double.parseDouble(value));
        readConfig("litersPerSecond", value -> litersPerSecond = Double.parseDouble(value));
        readConfig("tareKg", value -> tareKg = Double.parseDouble(value));
        readConfig("density", value -> density = Double.parseDouble(value));
        readConfig("rackId", value -> rackId = value);
        readConfig("gasConnected", value -> gasConnected = Boolean.parseBoolean(value));
        readConfig("groundConnected", value -> groundConnected = Boolean.parseBoolean(value));
        readConfig("filling", value -> filling = Boolean.parseBoolean(value));
        lastPollAt = System.currentTimeMillis();
    }

    private void applyConfig(String key, String value) {
        switch (key) {
            case "profile" -> profile = value.toLowerCase();
            case "baseTemperature" -> baseTemperature = Double.parseDouble(value);
            case "amplitude" -> amplitude = Double.parseDouble(value);
            case "sineAmplitude" -> sineAmplitude = Double.parseDouble(value);
            case "sawtoothAmplitude" -> sawtoothAmplitude = Double.parseDouble(value);
            case "triangleAmplitude" -> triangleAmplitude = Double.parseDouble(value);
            case "periodSec" -> periodSec = Double.parseDouble(value);
            case "litersPerSecond" -> litersPerSecond = Double.parseDouble(value);
            case "tareKg" -> tareKg = Double.parseDouble(value);
            case "density" -> density = Double.parseDouble(value);
            case "rackId" -> rackId = value;
            case "gasConnected" -> gasConnected = Boolean.parseBoolean(value);
            case "groundConnected" -> groundConnected = Boolean.parseBoolean(value);
            case "filling" -> filling = Boolean.parseBoolean(value);
            default -> { }
        }
    }

    @Override
    public void connect() {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Virtual driver connected (profile=" + profile + ")");
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
        switch (profile) {
            case "meter" -> readMeterProfile();
            case "weighbridge" -> readWeighbridgeProfile();
            case "rack-signals" -> readRackSignalsProfile();
            case "lab" -> readLabProfile();
            case "unified" -> readUnifiedProfile();
            default -> readDemoProfile();
        }
    }

    private void readUnifiedProfile() {
        VirtualUnifiedPoll.poll(
                driverObject,
                unifiedState,
                VirtualUnifiedPoll.UnifiedConfig.fromMap(driverObject.configuration())
        );
    }

    private void readDemoProfile() {
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

    private void readMeterProfile() {
        long now = System.currentTimeMillis();
        double deltaSec = lastPollAt > 0 ? (now - lastPollAt) / 1000.0 : 0.0;
        lastPollAt = now;
        if (filling && deltaSec > 0) {
            meterLiters += litersPerSecond * deltaSec;
        }
        driverObject.updateVariable(
                "meterLiters",
                DataRecord.single(METER_SCHEMA, Map.of("value", meterLiters, "unit", "L"))
        );
        driverObject.updateVariable(
                "flowRate",
                DataRecord.single(FLOW_SCHEMA, Map.of(
                        "value", filling ? litersPerSecond : 0.0,
                        "unit", "L/s"
                ))
        );
        driverObject.updateVariable(
                "filling",
                DataRecord.single(FILLING_SCHEMA, Map.of("value", filling))
        );
    }

    private void readWeighbridgeProfile() {
        double actualLiters = readNumericVariable("meterLiters", meterLiters);
        double grossKg = tareKg + actualLiters * density;
        driverObject.updateVariable(
                "grossWeight",
                DataRecord.single(WEIGHT_SCHEMA, Map.of("value", grossKg, "unit", "kg"))
        );
        driverObject.updateVariable(
                "tareKg",
                DataRecord.single(WEIGHT_SCHEMA, Map.of("value", tareKg, "unit", "kg"))
        );
    }

    private void readLabProfile() {
        double effectiveSineAmplitude = sineAmplitude > 0 ? sineAmplitude : amplitude;
        double elapsedSec = (System.currentTimeMillis() - startedAt) / 1000.0;
        double sine = effectiveSineAmplitude * Math.sin(2 * Math.PI * elapsedSec / periodSec);
        double phase = elapsedSec % periodSec;
        double sawtooth = sawtoothAmplitude * (2.0 * (phase / periodSec) - 1.0);
        double phaseNorm = phase / periodSec;
        double triangle = triangleAmplitude * (phaseNorm < 0.5 ? 4.0 * phaseNorm - 1.0 : 3.0 - 4.0 * phaseNorm);
        driverObject.updateVariable(
                "sineWave",
                DataRecord.single(WAVE_SCHEMA, Map.of("value", sine))
        );
        driverObject.updateVariable(
                "sawtoothWave",
                DataRecord.single(WAVE_SCHEMA, Map.of("value", sawtooth))
        );
        driverObject.updateVariable(
                "triangleWave",
                DataRecord.single(WAVE_SCHEMA, Map.of("value", triangle))
        );
        driverObject.updateVariable(
                "status",
                DataRecord.single(STATUS_SCHEMA, Map.of(
                        "online", true,
                        "lastSeen", Instant.now().toString()
                ))
        );
    }

    private void readRackSignalsProfile() {
        boolean gas = gasConnected && !rackId.isBlank();
        boolean ground = groundConnected && !rackId.isBlank();
        driverObject.updateVariable(
                "gasPresent",
                DataRecord.single(SIGNAL_SCHEMA, Map.of("value", gas))
        );
        driverObject.updateVariable(
                "groundConnected",
                DataRecord.single(SIGNAL_SCHEMA, Map.of("value", ground))
        );
    }

    private double readNumericVariable(String name, double fallback) {
        return driverObject.getVariable(name)
                .map(record -> {
                    Object raw = record.firstRow().get("value");
                    if (raw instanceof Number number) {
                        return number.doubleValue();
                    }
                    if (raw != null) {
                        try {
                            return Double.parseDouble(raw.toString());
                        } catch (NumberFormatException ignored) {
                            return fallback;
                        }
                    }
                    return fallback;
                })
                .orElse(fallback);
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
