package com.ispf.server.alert;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class AlertRuleApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String INDEX_TEST_DEVICE = "root.platform.devices.alert-index-api-test";
    private static final String INDEX_TEST_RULE = "Alert index API test rule";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @AfterEach
    void cleanupIndexTestArtifacts() throws Exception {
        deleteAlertRuleIfExists(INDEX_TEST_RULE);
        objectManager.tree().findByPath(INDEX_TEST_DEVICE).ifPresent(node -> objectManager.delete(INDEX_TEST_DEVICE));
    }

    @Test
    void listsSeededDemoRule() throws Exception {
        mockMvc.perform(get("/api/v1/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("thresholdExceeded")));
    }

    @Test
    void firesEventViaAlertRuleOnThresholdBreach() throws Exception {
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

        mockMvc.perform(get("/api/v1/events").param("objectPath", DEMO_DEVICE).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("thresholdExceeded")));
    }

    @Test
    void newAlertRuleOnNewDeviceFiresWithoutServerRestart() throws Exception {
        objectManager.tree().findByPath(INDEX_TEST_DEVICE).ifPresent(node -> objectManager.delete(INDEX_TEST_DEVICE));
        PlatformObject device = objectManager.create(
                "root.platform.devices",
                "alert-index-api-test",
                ObjectType.DEVICE,
                "Alert index API test",
                null,
                "mqtt-sensor-v1"
        );
        objectTemplateService.applyTemplate(device.path(), "mqtt-sensor-v1");
        objectManager.persistNodeTree(device.path());

        int baselineRules = readAlertRulesIndexed();

        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "objectPath": "%s",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "self.temperature[\\"value\\"] > 80.0",
                                  "eventName": "thresholdExceeded",
                                  "payloadVariable": "temperature",
                                  "enabled": true,
                                  "edgeTrigger": false
                                }
                                """.formatted(INDEX_TEST_RULE, INDEX_TEST_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(AutomationTreeService.rulePathForName(INDEX_TEST_RULE)));

        assertTrue(readAlertRulesIndexed() > baselineRules);

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", INDEX_TEST_DEVICE)
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
                        .param("path", INDEX_TEST_DEVICE)
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

        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            String body = mockMvc.perform(get("/api/v1/events")
                            .param("objectPath", INDEX_TEST_DEVICE)
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains("thresholdExceeded")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("thresholdExceeded event was not fired within timeout for newly created alert rule");
    }

    @Test
    void rateLimitSecondsSuppressesRepeatedFires() throws Exception {
        String rulePath = "root.platform.alert-rules.temperature-threshold-exceeded";
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", rulePath)
                        .param("name", "rateLimitSeconds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "rateLimitSeconds",
                                    "fields": [{"name": "value", "type": "INTEGER"}]
                                  },
                                  "rows": [{"value": 3600}]
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

        int afterFirstBreach = countThresholdExceededEvents();

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
                                  "rows": [{"value": 30.0, "unit": "C"}]
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

        org.junit.jupiter.api.Assertions.assertEquals(afterFirstBreach, countThresholdExceededEvents());
    }

    private int countThresholdExceededEvents() throws Exception {
        String body = mockMvc.perform(get("/api/v1/events").param("objectPath", DEMO_DEVICE).param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.List<?> matches = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.eventName == 'thresholdExceeded')]");
        return matches.size();
    }

    private int readAlertRulesIndexed() throws Exception {
        String body = mockMvc.perform(get("/api/v1/platform/automation-index/stats"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.alertRulesIndexed");
    }

    private void deleteAlertRuleIfExists(String name) throws Exception {
        String path = AutomationTreeService.rulePathForName(name);
        if (mockMvc.perform(get("/api/v1/alert-rules/by-path").param("path", path))
                .andReturn()
                .getResponse()
                .getStatus() == 200) {
            mockMvc.perform(delete("/api/v1/alert-rules/by-path").param("path", path))
                    .andExpect(status().isOk());
        }
    }
}
