package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Single {@code profile=unified} poll — synthetic telemetry covering all common ISPF field types.
 */
final class VirtualUnifiedPoll {

    static final DataSchema STATUS_SCHEMA = DataSchema.builder("deviceStatus")
            .field("online", FieldType.BOOLEAN)
            .field("lastSeen", FieldType.STRING)
            .build();

    static final DataSchema MEASUREMENT_SCHEMA = DataSchema.builder("measurement")
            .field("value", FieldType.DOUBLE)
            .field("unit", FieldType.STRING)
            .build();

    static final DataSchema WAVE_SCHEMA = DataSchema.builder("waveReading")
            .field("value", FieldType.DOUBLE)
            .build();

    static final DataSchema BOOL_SCHEMA = DataSchema.builder("boolValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    static final DataSchema INT_SCHEMA = DataSchema.builder("intValue")
            .field("value", FieldType.INTEGER)
            .build();

    static final DataSchema LONG_SCHEMA = DataSchema.builder("longValue")
            .field("value", FieldType.LONG)
            .build();

    static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    static final DataSchema DATETIME_SCHEMA = DataSchema.builder("dateTimeValue")
            .field("value", FieldType.DATETIME)
            .build();

    static final DataSchema COORDINATES_SCHEMA = DataSchema.builder("geoCoordinates")
            .field("latitude", FieldType.DOUBLE)
            .field("longitude", FieldType.DOUBLE)
            .field("altitude", FieldType.DOUBLE)
            .field("accuracy", FieldType.DOUBLE)
            .build();

    static final DataSchema HEALTH_SCHEMA = DataSchema.builder("deviceHealth")
            .field("cpuPct", FieldType.DOUBLE)
            .field("memoryPct", FieldType.DOUBLE)
            .field("diskPct", FieldType.DOUBLE)
            .build();

    static final DataSchema TELEMETRY_ROW_SCHEMA = DataSchema.builder("telemetryRow")
            .field("seq", FieldType.INTEGER)
            .field("metric", FieldType.STRING)
            .field("value", FieldType.DOUBLE)
            .field("quality", FieldType.STRING)
            .build();

    static final DataSchema EVENT_LOG_ROW_SCHEMA = DataSchema.builder("eventLogRow")
            .field("int", FieldType.INTEGER)
            .field("string", FieldType.STRING)
            .build();

    static final DataSchema TELEMETRY_TABLE_SCHEMA = DataSchema.builder("telemetryTable")
            .field(new FieldDefinition("rows", FieldType.RECORD_LIST, "", true, TELEMETRY_ROW_SCHEMA))
            .build();

    static final DataSchema EVENT_LOG_TABLE_SCHEMA = DataSchema.builder("eventLogTable")
            .field(new FieldDefinition("rows", FieldType.RECORD_LIST, "", true, EVENT_LOG_ROW_SCHEMA))
            .build();

    static final DataSchema BINARY_SNAPSHOT_SCHEMA = DataSchema.builder("binarySnapshot")
            .field("data", FieldType.BINARY)
            .field("sizeBytes", FieldType.INTEGER)
            .field("checksum", FieldType.STRING)
            .field("mimeType", FieldType.STRING)
            .build();

    private static final int TELEMETRY_TABLE_CAP = 12;
    private static final int EVENT_LOG_CAP = 8;

    private VirtualUnifiedPoll() {
    }

    static void poll(DeviceDriver.DriverObject driverObject, UnifiedState state, UnifiedConfig config) {
        long now = System.currentTimeMillis();
        double elapsedSec = (now - state.startedAt) / 1000.0;
        double phase = elapsedSec % config.periodSec();
        double phaseNorm = phase / config.periodSec();

        state.pollSequence++;
        state.eventCounter++;

        double sine = config.sineAmplitude() * Math.sin(2 * Math.PI * elapsedSec / config.periodSec());
        double sawtooth = config.sawtoothAmplitude() * (2.0 * phaseNorm - 1.0);
        double triangle = config.triangleAmplitude() * (
                phaseNorm < 0.5 ? 4.0 * phaseNorm - 1.0 : 3.0 - 4.0 * phaseNorm
        );
        double temperature = config.baseTemperature()
                + config.amplitude() * Math.sin(2 * Math.PI * elapsedSec / config.periodSec());
        double pressure = 101.3 + 2.5 * Math.cos(2 * Math.PI * elapsedSec / 45.0);
        double humidity = 45.0 + 15.0 * Math.sin(2 * Math.PI * elapsedSec / 120.0);

        double deltaSec = state.lastPollAt > 0 ? (now - state.lastPollAt) / 1000.0 : 0.0;
        state.lastPollAt = now;
        if (config.filling() && deltaSec > 0) {
            state.meterLiters += config.litersPerSecond() * deltaSec;
        }

        double orbitAngle = 2 * Math.PI * elapsedSec / 300.0;
        double lat = config.baseLatitude() + (config.orbitRadiusM() / 111_000.0) * Math.cos(orbitAngle);
        double lon = config.baseLongitude()
                + (config.orbitRadiusM() / (111_000.0 * Math.cos(Math.toRadians(config.baseLatitude()))))
                * Math.sin(orbitAngle);
        double altitude = 150.0 + 5.0 * Math.sin(orbitAngle * 2);

        appendTelemetryRow(state, state.pollSequence, "temperature", temperature, "GOOD");
        appendTelemetryRow(state, state.pollSequence, "sineWave", sine, "GOOD");
        appendTelemetryRow(state, state.pollSequence, "pressure", pressure, "GOOD");
        appendEventLogRow(state, state.pollSequence, "poll-" + state.pollSequence);

        byte[] binaryPayload = buildBinaryPayload(state.pollSequence, temperature, sine);
        CRC32 crc = new CRC32();
        crc.update(binaryPayload);

        driverObject.updateVariable("status", DataRecord.single(STATUS_SCHEMA, Map.of(
                "online", true,
                "lastSeen", Instant.now().toString()
        )));
        driverObject.updateVariable("temperature", DataRecord.single(MEASUREMENT_SCHEMA, Map.of(
                "value", temperature, "unit", "C"
        )));
        driverObject.updateVariable("pressure", DataRecord.single(MEASUREMENT_SCHEMA, Map.of(
                "value", pressure, "unit", "kPa"
        )));
        driverObject.updateVariable("humidity", DataRecord.single(MEASUREMENT_SCHEMA, Map.of(
                "value", humidity, "unit", "%"
        )));
        driverObject.updateVariable("sineWave", DataRecord.single(WAVE_SCHEMA, Map.of("value", sine)));
        driverObject.updateVariable("sawtoothWave", DataRecord.single(WAVE_SCHEMA, Map.of("value", sawtooth)));
        driverObject.updateVariable("triangleWave", DataRecord.single(WAVE_SCHEMA, Map.of("value", triangle)));
        driverObject.updateVariable("pollSequence", DataRecord.single(INT_SCHEMA, Map.of("value", state.pollSequence)));
        driverObject.updateVariable("eventCounter", DataRecord.single(LONG_SCHEMA, Map.of("value", state.eventCounter)));
        driverObject.updateVariable("deviceActive", DataRecord.single(BOOL_SCHEMA, Map.of("value", true)));
        driverObject.updateVariable("coordinates", DataRecord.single(COORDINATES_SCHEMA, Map.of(
                "latitude", lat,
                "longitude", lon,
                "altitude", altitude,
                "accuracy", 3.5
        )));
        driverObject.updateVariable("locationTag", DataRecord.single(STRING_SCHEMA, Map.of(
                "value", "orbit:%.5f,%.5f".formatted(lat, lon)
        )));
        driverObject.updateVariable("serialNumber", DataRecord.single(STRING_SCHEMA, Map.of(
                "value", config.serialNumber()
        )));
        driverObject.updateVariable("firmwareVersion", DataRecord.single(STRING_SCHEMA, Map.of(
                "value", config.firmwareVersion()
        )));
        driverObject.updateVariable("lastMaintenance", DataRecord.single(DATETIME_SCHEMA, Map.of(
                "value", Instant.parse(config.lastMaintenanceIso())
        )));
        driverObject.updateVariable("meterLiters", DataRecord.single(MEASUREMENT_SCHEMA, Map.of(
                "value", state.meterLiters, "unit", "L"
        )));
        driverObject.updateVariable("flowRate", DataRecord.single(MEASUREMENT_SCHEMA, Map.of(
                "value", config.filling() ? config.litersPerSecond() : 0.0,
                "unit", "L/s"
        )));
        driverObject.updateVariable("filling", DataRecord.single(BOOL_SCHEMA, Map.of("value", config.filling())));
        driverObject.updateVariable("grossWeight", DataRecord.single(MEASUREMENT_SCHEMA, Map.of(
                "value", config.tareKg() + state.meterLiters * config.density(),
                "unit", "kg"
        )));
        driverObject.updateVariable("gasPresent", DataRecord.single(BOOL_SCHEMA, Map.of(
                "value", config.gasConnected() && !config.rackId().isBlank()
        )));
        driverObject.updateVariable("groundConnected", DataRecord.single(BOOL_SCHEMA, Map.of(
                "value", config.groundConnected() && !config.rackId().isBlank()
        )));
        driverObject.updateVariable("deviceHealth", DataRecord.single(HEALTH_SCHEMA, Map.of(
                "cpuPct", 20.0 + 15.0 * Math.abs(Math.sin(elapsedSec / 17.0)),
                "memoryPct", 40.0 + 10.0 * Math.abs(Math.cos(elapsedSec / 23.0)),
                "diskPct", 55.0 + 5.0 * Math.sin(elapsedSec / 41.0)
        )));
        driverObject.updateVariable("telemetryTable", DataRecord.single(TELEMETRY_TABLE_SCHEMA, Map.of(
                "rows", List.copyOf(state.telemetryRows)
        )));
        driverObject.updateVariable("eventLog", DataRecord.single(EVENT_LOG_TABLE_SCHEMA, Map.of(
                "rows", List.copyOf(state.eventLogRows)
        )));
        driverObject.updateVariable("binarySnapshot", DataRecord.single(BINARY_SNAPSHOT_SCHEMA, Map.of(
                "data", binaryPayload,
                "sizeBytes", binaryPayload.length,
                "checksum", "CRC32:%08X".formatted(crc.getValue()),
                "mimeType", "application/octet-stream"
        )));
    }

    private static byte[] buildBinaryPayload(int sequence, double temperature, double sine) {
        String header = "ISPF-UNIFIED-v1 seq=%d temp=%.2f sine=%.4f".formatted(sequence, temperature, sine);
        return header.getBytes(StandardCharsets.UTF_8);
    }

    private static void appendTelemetryRow(
            UnifiedState state,
            int seq,
            String metric,
            double value,
            String quality
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", seq);
        row.put("metric", metric);
        row.put("value", value);
        row.put("quality", quality);
        state.telemetryRows.add(row);
        while (state.telemetryRows.size() > TELEMETRY_TABLE_CAP) {
            state.telemetryRows.removeFirst();
        }
    }

    private static void appendEventLogRow(UnifiedState state, int seq, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("int", seq);
        row.put("string", message);
        state.eventLogRows.add(row);
        while (state.eventLogRows.size() > EVENT_LOG_CAP) {
            state.eventLogRows.removeFirst();
        }
    }

    record UnifiedConfig(
            double baseTemperature,
            double amplitude,
            double sineAmplitude,
            double sawtoothAmplitude,
            double triangleAmplitude,
            double periodSec,
            double litersPerSecond,
            boolean filling,
            double tareKg,
            double density,
            String rackId,
            boolean gasConnected,
            boolean groundConnected,
            double baseLatitude,
            double baseLongitude,
            double orbitRadiusM,
            String serialNumber,
            String firmwareVersion,
            String lastMaintenanceIso
    ) {
        static UnifiedConfig fromMap(Map<String, String> configuration) {
            return new UnifiedConfig(
                    parseDouble(configuration, "baseTemperature", 22.0),
                    parseDouble(configuration, "amplitude", 15.0),
                    parseDouble(configuration, "sineAmplitude", 10.0),
                    parseDouble(configuration, "sawtoothAmplitude", 5.0),
                    parseDouble(configuration, "triangleAmplitude", 5.0),
                    parseDouble(configuration, "periodSec", 60.0),
                    parseDouble(configuration, "litersPerSecond", 120.0),
                    parseBoolean(configuration, "filling", true),
                    parseDouble(configuration, "tareKg", 15_000.0),
                    parseDouble(configuration, "density", 0.85),
                    configuration.getOrDefault("rackId", "rack-1"),
                    parseBoolean(configuration, "gasConnected", true),
                    parseBoolean(configuration, "groundConnected", true),
                    parseDouble(configuration, "baseLatitude", 55.7558),
                    parseDouble(configuration, "baseLongitude", 37.6173),
                    parseDouble(configuration, "orbitRadiusM", 50.0),
                    configuration.getOrDefault("serialNumber", "VIRT-UNIFIED-001"),
                    configuration.getOrDefault("firmwareVersion", "1.0.0-unified"),
                    configuration.getOrDefault("lastMaintenanceIso", "2026-01-15T08:00:00Z")
            );
        }

        private static double parseDouble(Map<String, String> configuration, String key, double defaultValue) {
            String raw = configuration.get(key);
            if (raw == null || raw.isBlank()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }

        private static boolean parseBoolean(Map<String, String> configuration, String key, boolean defaultValue) {
            String raw = configuration.get(key);
            if (raw == null || raw.isBlank()) {
                return defaultValue;
            }
            return Boolean.parseBoolean(raw);
        }
    }

    static final class UnifiedState {
        final long startedAt = System.currentTimeMillis();
        int pollSequence;
        long eventCounter;
        double meterLiters;
        long lastPollAt;
        final List<Map<String, Object>> telemetryRows = new ArrayList<>();
        final List<Map<String, Object>> eventLogRows = new ArrayList<>();
    }
}
