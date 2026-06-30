package com.ispf.driver.ethernetip;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EthernetIpDeviceDriverTest {

    private static final int ENCAP_REGISTER_SESSION = 0x0065;

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
    void registersSessionAndReadsPlaceholderTagViaLoopback() throws Exception {
        startEncapsulationServer();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "5000"
        ));

        EthernetIpDeviceDriver driver = new EthernetIpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("counter", "Program:MainProgram.Counter"));

        assertTrue(driver.isConnected());
        DataRecord record = driverObject.variables.get("counter");
        assertEquals(true, record.firstRow().get("connected"));
        assertEquals("Program:MainProgram.Counter", record.firstRow().get("tagPath"));
        assertEquals("SESSION_OK", record.firstRow().get("value"));
        assertTrue(((Number) record.firstRow().get("sessionHandle")).longValue() > 0L);
        driver.disconnect();
    }

    @Test
    void writeRequiresNativeCipLibrary() throws Exception {
        startEncapsulationServer();
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "5000"
        ));
        EthernetIpDeviceDriver driver = new EthernetIpDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("counter", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("native CIP"));
        driver.disconnect();
    }

    private void startEncapsulationServer() throws Exception {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        port = serverSocket.getLocalPort();
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try (Socket client = serverSocket.accept()) {
                client.setSoTimeout(5000);
                DataInputStream in = new DataInputStream(client.getInputStream());
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                byte[] header = in.readNBytes(24);
                if (header.length < 24) {
                    return;
                }
                ByteBuffer request = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                int command = Short.toUnsignedInt(request.getShort(0));
                int length = Short.toUnsignedInt(request.getShort(2));
                if (length > 0) {
                    in.readNBytes(length);
                }
                if (command != ENCAP_REGISTER_SESSION) {
                    return;
                }
                ByteBuffer response = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
                response.putShort((short) ENCAP_REGISTER_SESSION);
                response.putShort((short) 4);
                response.putInt(42);
                response.putInt(0);
                response.putLong(0L);
                response.putInt(0);
                response.putShort((short) 1);
                response.putShort((short) 0);
                out.write(response.array());
                out.flush();
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
                    "test-ethernet-ip",
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
