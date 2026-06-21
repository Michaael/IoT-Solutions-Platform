package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleTreeFirstInvokeTest {

    private static final String APP_ID = "tree-first-test";
    private static final String TREE_FUNCTION_PATH =
            "root.platform.applications.tree-first-test.functions.tree_echo";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invokeFunctionViaApplicationTreePathAfterDeploy() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appId":"%s","displayName":"Tree First","tablePrefix":""}
                                """.formatted(APP_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "functions": [
                                    {
                                      "objectPath": "root.platform.devices.demo-sensor-01",
                                      "functionName": "tree_echo",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": { "name": "in", "fields": [] },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "marker", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"tree-path\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "tree_echo",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(TREE_FUNCTION_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.marker").value("tree-path"));
    }
}
