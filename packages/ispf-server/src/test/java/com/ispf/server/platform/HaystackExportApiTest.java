package com.ispf.server.platform;

import com.ispf.server.bootstrap.HaystackModelBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HaystackExportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsHaystackGridForLabDemoDevice() throws Exception {
        mockMvc.perform(get("/api/v1/platform/haystack/export")
                        .param("rootPath", "root.platform.devices.lab-userA-01")
                        .param("includePoints", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formatVersion").value(1))
                .andExpect(jsonPath("$.rootPath").value("root.platform.devices.lab-userA-01"))
                .andExpect(jsonPath("$.rowCount").isNumber())
                .andExpect(jsonPath("$.rows[?(@.entityKind == 'equip')].haystackRef")
                        .value(HaystackModelBootstrap.DEMO_HAYSTACK_REF))
                .andExpect(jsonPath("$.rows[?(@.entityKind == 'equip')].tags.equip").value(true))
                .andExpect(jsonPath("$.rows[?(@.variableName == 'sineWave')].entityKind")
                        .value("point"))
                .andExpect(jsonPath("$.rows[?(@.variableName == 'sineWave')].tags.temp")
                        .value(true))
                .andExpect(jsonPath("$.rows[?(@.variableName == 'sineWave')].tags.sensor")
                        .value(true))
                .andExpect(jsonPath("$.rows[?(@.variableName == 'sineWave')].unit")
                        .value("°C"))
                .andExpect(jsonPath("$.rows[?(@.variableName == 'sineWave')].dis")
                        .value("Sine wave"));
    }

    @Test
    void searchesPointsByHaystackTags() throws Exception {
        mockMvc.perform(get("/api/v1/platform/haystack/search")
                        .param("tags", "equip", "point", "temp")
                        .param("rootPath", "root.platform.devices.lab-userA-01")
                        .param("entityKind", "point"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.matches[0].objectPath")
                        .value("root.platform.devices.lab-userA-01"))
                .andExpect(jsonPath("$.matches[0].variableName").value("sineWave"))
                .andExpect(jsonPath("$.matches[0].tags.temp").value(true));
    }
}
