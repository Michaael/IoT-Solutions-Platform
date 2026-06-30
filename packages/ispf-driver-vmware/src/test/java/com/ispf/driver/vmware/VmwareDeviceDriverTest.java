package com.ispf.driver.vmware;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VmwareDeviceDriverTest {

    private HttpServer server;
    private String hostWithPort;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void retrievesServiceContentViaLoopbackHttp() throws Exception {
        startSoapServer("""
                <returnval>
                  <about>
                    <version>8.0.1</version>
                  </about>
                </returnval>
                """);

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", hostWithPort,
                "useHttp", "true",
                "timeoutMs", "5000"
        ));

        VmwareDeviceDriver driver = new VmwareDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of(
                "version", "version",
                "connected", "connected"
        ));

        DataRecord version = driverObject.variables.get("version");
        assertEquals(200, version.firstRow().get("statusCode"));
        assertEquals("8.0.1", version.firstRow().get("value"));

        DataRecord connected = driverObject.variables.get("connected");
        assertEquals("true", connected.firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        VmwareDeviceDriver driver = new VmwareDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("version", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void startSoapServer(String soapBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sdk", exchange -> respondSoap(exchange, soapBody));
        server.start();
        hostWithPort = "127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respondSoap(HttpExchange exchange, String bodyFragment) throws IOException {
        byte[] body = ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                """ + bodyFragment + """
                  </soapenv:Body>
                </soapenv:Envelope>
                """).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
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
                    "test-vmware",
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
