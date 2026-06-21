package com.ispf.server.correlator;

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
class EventCorrelatorApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsSeededDemoCorrelator() throws Exception {
        mockMvc.perform(get("/api/v1/correlators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("thresholdExceeded")));
    }

    @Test
    void correlatorStartsWorkflowOnThresholdEvent() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmAcknowledged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "alarmAcknowledged",
                                    "fields": [{"name": "value", "type": "BOOLEAN"}]
                                  },
                                  "rows": [{"value": false}]
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

        mockMvc.perform(get("/api/v1/work-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Подтвердите тревогу")));
    }
}
