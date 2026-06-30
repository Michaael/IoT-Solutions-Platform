package com.ispf.driver.coap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.config.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoapDeviceDriverTest {

    private CoapServer server;

    @BeforeAll
    static void initCalifornium() {
        Configuration.setStandard(Configuration.createStandardWithoutFile());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server.destroy();
            server = null;
        }
    }

    @Test
    void readsResourceViaLoopbackServer() throws Exception {
        int port = freePort();
        startServer(port, "22.5");

        StubDriverObject driverObject = driverConfig(port);
        CoapDeviceDriver driver = new CoapDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temperature", "/sensor/temp"));

        DataRecord record = driverObject.variables.get("temperature");
        assertEquals(69, record.firstRow().get("statusCode"));
        assertEquals("22.5", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeRequiresConnection() {
        CoapDeviceDriver driver = new CoapDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("temperature", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void startServer(int port, String payload) {
        server = new CoapServer(port);
        CoapResource sensor = new CoapResource("sensor");
        sensor.add(new CoapResource("temp") {
            @Override
            public void handleGET(CoapExchange exchange) {
                exchange.respond(CoAP.ResponseCode.CONTENT, payload);
            }
        });
        server.add(sensor);
        server.start();
    }

    private static StubDriverObject driverConfig(int port) {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "5000"
        ));
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-coap",
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
