package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class OperatorAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "operator")
    void operatorCanReadWorkQueue() throws Exception {
        mockMvc.perform(get("/api/v1/work-queue"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "operator")
    void operatorCannotCreateAlertRules() throws Exception {
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "test",
                                  "objectPath": "root",
                                  "watchVariable": "x",
                                  "conditionExpr": "true",
                                  "eventName": "test",
                                  "enabled": true,
                                  "edgeTrigger": false
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "admin")
    void adminCanCreateAlertRules() throws Exception {
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "rbac-test-rule",
                                  "objectPath": "root.platform.devices.demo-sensor-01",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "true",
                                  "eventName": "thresholdExceeded",
                                  "enabled": false,
                                  "edgeTrigger": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("rbac-test-rule"));
    }

    @Test
    void localRoleHeaderGrantsOperatorAccess() throws Exception {
        mockMvc.perform(get("/api/v1/work-queue").header("X-ISPF-Role", "operator"))
                .andExpect(status().isOk());
    }

    @Test
    void localRoleHeaderBlocksOperatorFromCorrelatorsWrite() throws Exception {
        mockMvc.perform(post("/api/v1/correlators")
                        .header("X-ISPF-Role", "operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "blocked",
                                  "eventName": "x",
                                  "windowSeconds": 60,
                                  "minOccurrences": 1,
                                  "cooldownSeconds": 0,
                                  "actionType": "RUN_WORKFLOW",
                                  "actionTarget": "root.platform.workflows.demo-alarm-handler",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "operator")
    void operatorCanPutWritableVariableOnOpenObject() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", "root.platform.devices.demo-sensor-01")
                        .param("name", "temperature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "doubleValue",
                                    "fields": [{"name": "value", "type": "DOUBLE"}]
                                  },
                                  "rows": [{"value": 21.5}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("temperature"));
    }

    @Test
    @WithMockUser(roles = "operator")
    void operatorCannotPutVariableOnAclProtectedLabDevice() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", "root.platform.devices.lab-userA-01")
                        .param("name", "sheetValues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "sheetValues",
                                    "fields": [{
                                      "name": "rows",
                                      "type": "RECORD_LIST",
                                      "nestedSchema": {
                                        "name": "sheetCellRow",
                                        "fields": [
                                          {"name": "cell", "type": "STRING"},
                                          {"name": "value", "type": "STRING"}
                                        ]
                                      }
                                    }]
                                  },
                                  "rows": [{"rows": [{"cell": "A1", "value": "1"}]}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
