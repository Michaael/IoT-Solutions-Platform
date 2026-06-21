package com.ispf.server.api;

import com.ispf.server.platform.update.PlatformUpdateService;
import com.ispf.server.platform.update.PlatformUpdateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformUpdateApiTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class MockUpdateServiceConfig {

        @Bean
        @Primary
        PlatformUpdateService platformUpdateService() {
            PlatformUpdateService service = org.mockito.Mockito.mock(PlatformUpdateService.class);
            PlatformUpdateStatus status = new PlatformUpdateStatus(
                    true,
                    false,
                    "0.1.0-SNAPSHOT",
                    "0.1.1",
                    true,
                    "ISPF 0.1.1",
                    "https://github.com/example/releases/tag/v0.1.1",
                    "Notes",
                    Instant.parse("2026-06-21T00:00:00Z"),
                    Instant.parse("2026-06-21T01:00:00Z"),
                    null,
                    "IDLE",
                    null,
                    null
            );
            when(service.getStatus()).thenReturn(status);
            when(service.refreshCheck()).thenReturn(status);
            return service;
        }
    }

    @Test
    void adminCanReadUpdateStatus() throws Exception {
        mockMvc.perform(get("/api/v1/platform/update/status")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(true))
                .andExpect(jsonPath("$.latestVersion").value("0.1.1"));
    }

    @Test
    void adminCanTriggerCheck() throws Exception {
        mockMvc.perform(post("/api/v1/platform/update/check")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("0.1.0-SNAPSHOT"));
    }
}
