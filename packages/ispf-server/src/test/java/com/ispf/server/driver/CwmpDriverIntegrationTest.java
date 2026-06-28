package com.ispf.server.driver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.ModelApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CwmpDriverIntegrationTest {

    private static final String DEVICE_NAME = "cwmp-driver-it";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ModelApplicationService modelApplicationService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @Autowired
    private MockMvc mockMvc;

    private String devicePath;

    private HttpServer acsServer;
    private String acsUrl;
    private final AtomicInteger informCount = new AtomicInteger();

    @BeforeEach
    void createDevice() {
        devicePath = DriverIntegrationTestSupport.createDevice(
                objectManager,
                modelApplicationService,
                driverRuntimeService,
                DEVICE_NAME
        );
    }

    @BeforeEach
    void startMockAcs() throws IOException {
        informCount.set(0);
        acsServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        acsServer.createContext("/", this::handleAcsRequest);
        acsServer.start();
        acsUrl = "http://127.0.0.1:" + acsServer.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopMockAcs() {
        if (acsServer != null) {
            acsServer.stop(0);
            acsServer = null;
        }
        try {
            mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", devicePath));
        } catch (Exception ignored) {
            // best effort cleanup
        }
        DriverIntegrationTestSupport.deleteDevice(objectManager, driverRuntimeService, devicePath);
    }

    private void handleAcsRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response;
        if (body.contains("cwmp:Inform")) {
            informCount.incrementAndGet();
            response = """
                    <?xml version="1.0"?>
                    <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                      <soap-env:Body>
                        <cwmp:GetParameterValues xmlns:cwmp="urn:dslforum-org:cwmp-1-0">
                          <ParameterNames soap-env:arrayType="xsd:string[1]">
                            <string>Device.DeviceInfo.SoftwareVersion</string>
                          </ParameterNames>
                        </cwmp:GetParameterValues>
                      </soap-env:Body>
                    </soap-env:Envelope>
                    """;
        } else if (body.contains("GetParameterValuesResponse")) {
            response = """
                    <?xml version="1.0"?>
                    <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                      <soap-env:Body>
                        <ParameterList>
                          <Name>Device.DeviceInfo.SoftwareVersion</Name>
                          <Value>ISPF-CWMP-2.0.1</Value>
                        </ParameterList>
                      </soap-env:Body>
                    </soap-env:Envelope>
                    """;
        } else {
            response = "<ok/>";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void cwmpDriverInformAndParameterReadViaRuntime() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", devicePath))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", devicePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "cwmp",
                                  "pollIntervalMs": 60000,
                                  "configuration": {
                                    "acsUrl": "%s",
                                    "deviceId": "TEST-CPE-001",
                                    "timeoutMs": "5000",
                                    "informParameters": "Device.DeviceInfo.SoftwareVersion"
                                  },
                                  "pointMappings": {
                                    "softwareVersion": "Device.DeviceInfo.SoftwareVersion",
                                    "cwmpConnected": "connected"
                                  },
                                  "autoStart": true
                                }
                                """.formatted(acsUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.driverId").value("cwmp"));

        mockMvc.perform(post("/api/v1/drivers/runtime/poll").param("devicePath", devicePath))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", devicePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("softwareVersion")))
                .andExpect(jsonPath("$[*].name", hasItem("cwmpConnected")));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", devicePath)
                        .param("name", "softwareVersion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value("ISPF-CWMP-2.0.1"));

        org.junit.jupiter.api.Assertions.assertTrue(informCount.get() >= 1);
    }
}
