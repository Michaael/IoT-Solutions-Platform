package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class ObjectRevisionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void staleIfMatchReturns409() throws Exception {
        String token = loginAdmin();
        String path = "root.platform.devices.demo-sensor-01";

        MvcResult editor = mockMvc.perform(get("/api/v1/objects/by-path/editor")
                        .header("Authorization", "Bearer " + token)
                        .param("path", path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object.revision").exists())
                .andReturn();

        String body = editor.getResponse().getContentAsString();
        long revision = Long.parseLong(body.replaceAll("(?s).*\"revision\"\\s*:\\s*(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .header("If-Match", String.valueOf(revision))
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Demo Sensor 01 (rev test)" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/objects/by-path")
                        .header("Authorization", "Bearer " + token)
                        .header("If-Match", String.valueOf(revision))
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Demo Sensor 01 (stale)" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("REVISION_CONFLICT")));
    }

    private String loginAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
