package com.ispf.server.automation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier 1.6 acceptance: N events within window → correlator → workflow → user task.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EscalationChainAcceptanceTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void nEventsInWindowStartWorkflowWithUserTask() throws Exception {
        mockMvc.perform(get("/api/v1/correlators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Recurring threshold escalation")));

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

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/events/fire")
                            .param("objectPath", DEMO_DEVICE)
                            .param("eventName", "thresholdExceeded"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/work-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Подтвердите тревогу")));
    }
}
