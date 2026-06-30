package com.ispf.driver.opcda;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcDaDeviceDriverTest {

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private int port;

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            executor = null;
        }
        if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
        }
    }

    @Test
    void readsPlaceholderItemWhenProxyIsReachable() throws Exception {
        startAcceptingTcpServer();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "proxyPort", String.valueOf(port),
                "timeoutMs", "5000"
        ));

        OpcDaDeviceDriver driver = new OpcDaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("tag1", "Channel1.Device1.Tag1"));

        DataRecord record = driverObject.variables.get("tag1");
        assertEquals(true, record.firstRow().get("reachable"));
        assertEquals(false, record.firstRow().get("bridgeRequired"));
        assertEquals("placeholder:Channel1.Device1.Tag1", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void statusPointReportsProxyReachability() throws Exception {
        startAcceptingTcpServer();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "proxyPort", String.valueOf(port),
                "timeoutMs", "5000"
        ));

        OpcDaDeviceDriver driver = new OpcDaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("status", "status"));

        DataRecord record = driverObject.variables.get("status");
        assertEquals("proxy-reachable", record.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        OpcDaDeviceDriver driver = new OpcDaDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("tag1", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void startAcceptingTcpServer() throws Exception {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        port = serverSocket.getLocalPort();
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try (var client = serverSocket.accept()) {
                client.getInputStream().readNBytes(1);
            } catch (Exception ignored) {
                // loopback test best effort
            }
        });
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
                    "test-opc-da",
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
