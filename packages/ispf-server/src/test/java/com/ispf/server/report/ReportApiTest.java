package com.ispf.server.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiTest {

    private static final String PACKAGE_ID = "platform-test";
    private static final String DATA_SOURCE_PATH = "root.platform.data-sources.platform-test";
    private static final String REPORT_PATH = "root.platform.reports.platform-test-report";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void ensureDataSourceAndSchema() throws Exception {
        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", PACKAGE_ID)
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "Platform Test",
                                  "schemaName": "app_platform_test",
                                  "migrations": [
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
                                  "dataSourcePath": "%s",
                                  "query": "SELECT item_code FROM platform_item",
                                  "parameters": [],
                                  "columns": [{ "field": "item_code", "label": "Code" }],
                                  "maxRows": 100,
                                  "refreshIntervalMs": 5000
                                }
                                """.formatted(DATA_SOURCE_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(REPORT_PATH))
                .andExpect(jsonPath("$.dataSourcePath").value(DATA_SOURCE_PATH));

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

    @Test
    void yargExportRequiresTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/objects")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.reports",
                                  "name": "yarg-export-test",
                                  "type": "REPORT",
                                  "displayName": "YARG Export Test",
                                  "templateId": "report-v1"
                                }
                                """))
                .andExpect(status().isOk());

        String path = "root.platform.reports.yarg-export-test";
        mockMvc.perform(put("/api/v1/reports/by-path/definition")
                        .header("X-ISPF-Role", "admin")
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "YARG Export Test",
                                  "dataSourcePath": "%s",
                                  "query": "SELECT item_code FROM platform_item",
                                  "parameters": [],
                                  "columns": [{ "field": "item_code", "label": "Code" }],
                                  "maxRows": 100
                                }
                                """.formatted(DATA_SOURCE_PATH)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/by-path/export")
                        .header("X-ISPF-Role", "operator")
                        .param("path", path)
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("spreadsheetml")));

        mockMvc.perform(get("/api/v1/reports/by-path/export")
                        .header("X-ISPF-Role", "operator")
                        .param("path", path)
                        .param("format", "pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadTemplateAndExportXlsx() throws Exception {
        mockMvc.perform(post("/api/v1/objects")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.reports",
                                  "name": "yarg-template-test",
                                  "type": "REPORT",
                                  "displayName": "YARG Template Test",
                                  "templateId": "report-v1"
                                }
                                """))
                .andExpect(status().isOk());

        String path = "root.platform.reports.yarg-template-test";
        mockMvc.perform(put("/api/v1/reports/by-path/definition")
                        .header("X-ISPF-Role", "admin")
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "YARG Template Test",
                                  "dataSourcePath": "%s",
                                  "query": "SELECT item_code FROM platform_item",
                                  "parameters": [],
                                  "columns": [{ "field": "item_code", "label": "Code" }],
                                  "maxRows": 100
                                }
                                """.formatted(DATA_SOURCE_PATH)))
                .andExpect(status().isOk());

        byte[] template = ReportYargTemplateTestHelper.smokeTestTemplate();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.xls",
                "application/vnd.ms-excel",
                template
        );

        mockMvc.perform(multipart("/api/v1/reports/by-path/template")
                        .file(file)
                        .param("path", path)
                        .param("format", "xls")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTemplate").value(true))
                .andExpect(jsonPath("$.templateFormat").value("xls"));

        mockMvc.perform(get("/api/v1/reports/by-path/template")
                        .header("X-ISPF-Role", "admin")
                        .param("path", path))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));

        mockMvc.perform(get("/api/v1/reports/by-path/export")
                        .header("X-ISPF-Role", "operator")
                        .param("path", path)
                        .param("format", "xls"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.ms-excel"))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));

        mockMvc.perform(delete("/api/v1/reports/by-path/template")
                        .header("X-ISPF-Role", "admin")
                        .param("path", path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTemplate").value(false));
    }
}
