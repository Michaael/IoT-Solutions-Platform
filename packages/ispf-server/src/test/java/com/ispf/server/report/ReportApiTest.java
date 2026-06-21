package com.ispf.server.report;

import org.junit.jupiter.api.BeforeEach;
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
class ReportApiTest {

    private static final String APP_ID = "platform-test";
    private static final String REPORT_PATH = "root.platform.reports.platform-test-report";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void ensureAppAndSchema() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Platform Test",
                                  "tablePrefix": ""
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/data/migrate".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "scripts": [
                                    {
                                      "id": "platform_item",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void createSaveRunReportByPath() throws Exception {
        mockMvc.perform(post("/api/v1/objects")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.reports",
                                  "name": "platform-test-report",
                                  "type": "REPORT",
                                  "displayName": "Platform Test Report",
                                  "templateId": "report-v1"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/reports/by-path/definition")
                        .header("X-ISPF-Role", "admin")
                        .param("path", REPORT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Platform Test Report",
                                  "appId": "%s",
                                  "query": "SELECT item_code FROM platform_item",
                                  "parameters": [],
                                  "columns": [{ "field": "item_code", "label": "Code" }],
                                  "maxRows": 100,
                                  "refreshIntervalMs": 5000
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(REPORT_PATH))
                .andExpect(jsonPath("$.appId").value(APP_ID));

        mockMvc.perform(get("/api/v1/reports/by-path")
                        .header("X-ISPF-Role", "admin")
                        .param("path", REPORT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("SELECT item_code FROM platform_item"));

        mockMvc.perform(post("/api/v1/reports/by-path/run")
                        .header("X-ISPF-Role", "operator")
                        .param("path", REPORT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").exists());

        mockMvc.perform(get("/api/v1/objects")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem(REPORT_PATH)));
    }
}
