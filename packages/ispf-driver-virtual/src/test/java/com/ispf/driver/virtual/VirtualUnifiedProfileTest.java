package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualUnifiedProfileTest {

    @Test
    void unifiedProfileProducesAllMajorTypes() throws DriverException {
        Map<String, DataRecord> updates = new LinkedHashMap<>();
        VirtualDeviceDriver driver = new VirtualDeviceDriver();
        DeviceDriver.DriverObject driverObject = stubDriverObject(updates, Map.of("profile", "unified", "periodSec", "10"));

        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of());

        assertTrue(updates.containsKey("temperature"));
        assertTrue(updates.containsKey("coordinates"));
        assertTrue(updates.containsKey("telemetryTable"));
        assertTrue(updates.containsKey("eventLog"));
        assertTrue(updates.containsKey("binarySnapshot"));
        assertTrue(updates.containsKey("sineWave"));
        assertTrue(updates.containsKey("meterLiters"));
        assertTrue(updates.containsKey("deviceHealth"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> telemetryRows = (List<Map<String, Object>>) updates
                .get("telemetryTable")
                .firstRow()
                .get("rows");
        assertTrue(telemetryRows.size() >= 1);

        Object binary = updates.get("binarySnapshot").firstRow().get("data");
        assertInstanceOf(byte[].class, binary);
        assertTrue(((byte[]) binary).length > 0);

        assertEquals(1, updates.get("pollSequence").firstRow().get("value"));
    }

    @Test
    void unifiedProfileIncrementsPollSequence() throws DriverException {
        Map<String, DataRecord> updates = new LinkedHashMap<>();
        VirtualDeviceDriver driver = new VirtualDeviceDriver();
        DeviceDriver.DriverObject driverObject = stubDriverObject(updates, Map.of("profile", "unified"));

        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of());
        driver.readPoints(Map.of());

        assertEquals(2, updates.get("pollSequence").firstRow().get("value"));
    }

    private static DeviceDriver.DriverObject stubDriverObject(
            Map<String, DataRecord> updates,
            Map<String, String> configuration
    ) {
        return new DeviceDriver.DriverObject() {
            @Override
            public PlatformObject deviceObject() {
                return null;
            }

            @Override
            public void updateVariable(String name, DataRecord value) {
                updates.put(name, value);
            }

            @Override
            public Optional<DataRecord> getVariable(String name) {
                return Optional.empty();
            }

            @Override
            public void log(DeviceDriver.DriverLogLevel level, String message) {
            }

            @Override
            public Map<String, String> configuration() {
                return configuration;
            }
        };
    }
}
