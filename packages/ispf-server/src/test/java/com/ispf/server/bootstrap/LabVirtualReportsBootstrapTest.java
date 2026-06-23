package com.ispf.server.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "admin")
class LabVirtualReportsBootstrapTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedsTreeVariablesReportsForLabVirtualDevices() throws Exception {
        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.reports." + LabVirtualReports.SINE_SNAPSHOT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.reports." + LabVirtualReports.SINE_SNAPSHOT));

        mockMvc.perform(get("/api/v1/reports/by-path")
                        .param("path", "root.platform.reports." + LabVirtualReports.ALL_DEVICES_TABLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportType").value("tree-variables"))
                .andExpect(jsonPath("$.devicePathPattern").value(LabVirtualReports.LAB_DEVICE_PATTERN));

        mockMvc.perform(post("/api/v1/reports/by-path/run")
                        .param("path", "root.platform.reports." + LabVirtualReports.SINE_SNAPSHOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportType").value("tree-variables"))
                .andExpect(jsonPath("$.rowCount").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(post("/api/v1/reports/by-path/run")
                        .param("path", "root.platform.reports." + LabVirtualReports.DEVICE_STATUS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.rows[0].online").exists());

        mockMvc.perform(get("/api/v1/reports/by-path/export")
                        .param("path", "root.platform.reports.lab-virtual-sine")
                        .param("format", "html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/html")));

        mockMvc.perform(get("/api/v1/reports/by-path/export")
                        .param("path", "root.platform.reports.lab-virtual-sine")
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertTrue(body.length > 500, "xlsx export should contain table data");
                });
    }
}
