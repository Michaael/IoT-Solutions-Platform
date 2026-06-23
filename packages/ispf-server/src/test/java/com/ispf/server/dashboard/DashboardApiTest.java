package com.ispf.server.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoDashboardHasLayoutAndWidgets() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/by-path")
                        .param("path", "root.platform.dashboards.demo-sensor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo Sensor Dashboard"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='temp-value')]").exists())
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='temp-trend')]").exists())
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='temp-trend')].type").value("chart"));
    }

    @Test
    void snmpHostDashboardHasDeviceSelectionLayout() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/by-path")
                        .param("path", "root.platform.dashboards.snmp-host-monitoring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("SNMP Host Monitoring"))
                .andExpect(jsonPath("$.refreshIntervalMs").value(10000))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='btop-proc')]").exists())
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='btop-proc')].selectionKey").value("device"))
                .andExpect(jsonPath("$.layout.theme").value("btop"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='btop-cpu')].type").value("chart"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='btop-net-down')].type").value("chart"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='btop-net-down')].variableName").value("ifInOctetsRate"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='btop-net-down')].unit").value("B/s"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='uptime-chart')]").doesNotExist());
    }

    @Test
    void listsDashboardModel() throws Exception {
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='dashboard-v1')]").exists());
    }

    @Test
    void putLayoutAndTitleRoundTrip() throws Exception {
        String path = "root.platform.dashboards.demo-sensor";
        mockMvc.perform(put("/api/v1/dashboards/by-path/layout")
                        .param("path", path)
                        .contentType("application/json")
                        .content("""
                                {"layoutJson":"{\\"columns\\":12,\\"rowHeight\\":72,\\"widgets\\":[]}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layout.widgets").isArray());

        mockMvc.perform(put("/api/v1/dashboards/by-path/title")
                        .param("path", path)
                        .contentType("application/json")
                        .content("{\"title\":\"Updated Demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Demo"));
    }
}
