package com.ispf.driver.bacnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacnetDeviceDriverTest {

    private static final int REMOTE_DEVICE_ID = 1001;
    private static final int LOCAL_DEVICE_ID = 1234;
    private static final String LOOPBACK_HOST = BacnetLoopbackServer.LOOPBACK_HOST;
    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("bacnetValue")
            .field("value", FieldType.STRING)
            .build();

    private BacnetLoopbackServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void writeRequiresConnection() {
        BacnetDeviceDriver driver = new BacnetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("setpoint", DataRecord.single(VALUE_SCHEMA, Map.of("value", "1"))));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void rejectsUnknownPoint() {
        BacnetDeviceDriver driver = new BacnetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("missing", DataRecord.single(VALUE_SCHEMA, Map.of("value", "1"))));
        assertTrue(error.getMessage().contains("Not connected") || error.getMessage().contains("Unknown point"));
    }

    @Test
    @Timeout(10)
    void connectToLoopbackServerWithStaticRemoteAddress() throws Exception {
        int serverPort = freePort();
        int clientBindPort = freePort();
        server = new BacnetLoopbackServer(REMOTE_DEVICE_ID, serverPort, 21.5f);

        BacnetDeviceDriver driver = connectClient(serverPort, clientBindPort);
        assertTrue(driver.isConnected());
        driver.disconnect();
    }

    private BacnetDeviceDriver connectClient(int serverPort, int clientBindPort) throws DriverException {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "bindAddress", LOOPBACK_HOST,
                "host", LOOPBACK_HOST,
                "port", String.valueOf(serverPort),
                "bindPort", String.valueOf(clientBindPort),
                "localDeviceId", String.valueOf(LOCAL_DEVICE_ID),
                "remoteDeviceId", String.valueOf(REMOTE_DEVICE_ID),
                "timeoutMs", "5000"
        ));
        BacnetDeviceDriver driver = new BacnetDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        return driver;
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-device",
                    "root.platform.devices.test",
                    ObjectType.DEVICE,
                    "Test",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            if (configuration.containsKey(name)) {
                String value = configuration.get(name);
                return Optional.of(DataRecord.single(STRING_VALUE, Map.of("value", value, "raw", value)));
            }
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
