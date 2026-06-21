package com.ispf.server.mes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesPlatformApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsVirtualDriver() throws Exception {
        mockMvc.perform(get("/api/v1/drivers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='virtual')]").exists())
                .andExpect(jsonPath("$[?(@.id=='virtual')].maturity").value("PRODUCTION"))
                .andExpect(jsonPath("$[?(@.id=='snmp')]").exists())
                .andExpect(jsonPath("$[?(@.id=='dnp3')].maturity").value("STUB"))
                .andExpect(jsonPath("$[?(@.id=='cwmp')].maturity").value("BETA"))
                .andExpect(jsonPath("$[?(@.id=='dlms')].maturity").value("BETA"));
    }

    @Test
    void autoStartsVirtualDriverOnDemoDevice() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/start").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/drivers/runtime/status").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("virtual"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void invokesAcknowledgeAlarmFunction() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", DEMO_DEVICE)
                        .param("name", "acknowledgeAlarm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmAcknowledged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(true));
    }

    @Test
    void firesThresholdExceededEventWhenAlarmBecomesActive() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
                        .param("name", "temperature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "temperature",
                                    "fields": [
                                      {"name": "value", "type": "DOUBLE"},
                                      {"name": "unit", "type": "STRING"}
                                    ]
                                  },
                                  "rows": [{"value": 20.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
                        .param("name", "threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "threshold",
                                    "fields": [{"name": "value", "type": "DOUBLE"}]
                                  },
                                  "rows": [{"value": 80.0}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
                        .param("name", "temperature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "temperature",
                                    "fields": [
                                      {"name": "value", "type": "DOUBLE"},
                                      {"name": "unit", "type": "STRING"}
                                    ]
                                  },
                                  "rows": [{"value": 95.0, "unit": "C"}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmActive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(true));

        mockMvc.perform(get("/api/v1/events").param("objectPath", DEMO_DEVICE).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("thresholdExceeded")));
    }

    @Test
    void restartsDriverRuntime() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));

        mockMvc.perform(post("/api/v1/drivers/runtime/start").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void meterSimulatorProfileIncreasesLitersWhileFilling() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", DEMO_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "virtual",
                                  "pollIntervalMs": 500,
                                  "configuration": {
                                    "profile": "meter",
                                    "litersPerSecond": "200",
                                    "filling": "true"
                                  },
                                  "autoStart": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        Thread.sleep(2000);

        mockMvc.perform(get("/api/v1/drivers/runtime/status").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("meterLiters")))
                .andExpect(jsonPath("$[*].name", hasItem("flowRate")))
                .andExpect(jsonPath("$[*].name", hasItem("filling")));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "flowRate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(200.0));
    }
}
