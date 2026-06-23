package com.ispf.server.workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkQueueApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String DEMO_WORKFLOW = "root.platform.workflows.demo-alarm-handler";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void manualRunCompletesViaDefaultGateway() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", DEMO_WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @Test
    void triggerCreatesOperatorTaskInWorkQueue() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
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

        mockMvc.perform(post("/api/v1/workflows/by-path/run")
                        .param("path", DEMO_WORKFLOW)
                        .param("triggerObjectPath", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("WAITING")));

        mockMvc.perform(get("/api/v1/work-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Подтвердите тревогу")))
                .andExpect(jsonPath("$[0].operatorAppId").value("platform"));

        mockMvc.perform(get("/api/v1/work-queue").param("operatorAppId", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Подтвердите тревогу")));

        mockMvc.perform(get("/api/v1/work-queue").param("operatorAppId", "other-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        MvcResult queueResult = mockMvc.perform(get("/api/v1/work-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Подтвердите тревогу")))
                .andReturn();

        JsonNode tasks = objectMapper.readTree(queueResult.getResponse().getContentAsString());
        String taskId = tasks.get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/work-queue/complete")
                        .param("taskId", taskId)
                        .param("operatorId", "operator-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
