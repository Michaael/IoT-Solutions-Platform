package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleDependencyDeployApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deployFailsWhenRequiredBundleMissing() throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("displayName", "Dependent App");
        manifest.put("schemaName", "app_dependent_missing");
        manifest.put("requires", List.of(Map.of("appId", "missing-dep-never-deployed", "minVersion", "1.0.0")));

        mockMvc.perform(post("/api/v1/applications/dependent-missing/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(manifest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("missing-dep-never-deployed")));
    }

    @Test
    void deploySucceedsWhenRequiredBundlePresent() throws Exception {
        String warehouse = new ClassPathResource("warehouse-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/warehouse/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehouse))
                .andExpect(status().isOk());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("displayName", "Dependent App");
        manifest.put("schemaName", "app_dependent_ok");
        manifest.put("requires", List.of(Map.of("appId", "warehouse", "minVersion", "1.0.0")));

        mockMvc.perform(post("/api/v1/applications/dependent-ok/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(manifest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    private String asJson(Map<String, Object> manifest) throws Exception {
        return new tools.jackson.databind.ObjectMapper().writeValueAsString(manifest);
    }
}
