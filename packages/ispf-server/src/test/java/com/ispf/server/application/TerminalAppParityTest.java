package com.ispf.server.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform smoke for terminal app contract — no industry Java, inline script bundles only.
 * Optional CI: {@code ./gradlew :packages:ispf-server:test --tests "*.TerminalAppParityTest"}
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("terminal-parity")
class TerminalAppParityTest {

    private static final String APP_ID = "terminal-parity";
    private static final String WORKBENCH = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void registerApp() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Terminal Parity",
                                  "tablePrefix": ""
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void deployBundleListOrdersAndServeOperatorManifest() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "Terminal Parity",
                                  "migrations": [
                                    {
                                      "id": "parity_schema",
                                      "sql": "CREATE TABLE IF NOT EXISTS dispatch_order (id UUID PRIMARY KEY, order_number VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM dispatch_order; INSERT INTO dispatch_order (id, order_number, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'P-301-01', 'ready');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_dispatchWorkbench_listActiveOrders",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": { "name": "in", "fields": [] },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {
                                              "name": "rows",
                                              "type": "RECORD_LIST",
                                              "nestedSchema": {
                                                "name": "order_row",
                                                "fields": [
                                                  {"name": "order_number", "type": "STRING"},
                                                  {"name": "status", "type": "STRING"}
                                                ]
                                              }
                                            }
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"orders\\",\\"sql\\":\\"SELECT order_number AS order_number, status AS status FROM dispatch_order WHERE status = 'ready'\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"rows\\":\\"${orders}\\"}}]}"
                                      }
                                    }
                                  ],
                                  "operatorManifest": {
                                    "appId": "%s",
                                    "title": "Terminal Parity",
                                    "wireProfile": "anima-operator-v1",
                                    "defaultScreen": "orders",
                                    "screens": [
                                      {
                                        "id": "orders",
                                        "title": "Active orders",
                                        "table": {
                                          "objectPath": "%s",
                                          "functionName": "terminal_dispatchWorkbench_listActiveOrders",
                                          "refreshIntervalMs": 30000
                                        }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(WORKBENCH, APP_ID, WORKBENCH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.snapshot").value("recorded"));

        mockMvc.perform(get("/api/v1/applications/%s/data/status".formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_dispatchWorkbench_listActiveOrders",
                                  "wireProfile": "anima-operator-v1",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(WORKBENCH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result", hasSize(1)))
                .andExpect(jsonPath("$.result[0].order_number").value("P-301-01"));

        mockMvc.perform(get("/api/v1/applications/%s/operator-manifest".formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID))
                .andExpect(jsonPath("$.screens[0].id").value("orders"));
    }

    @Test
    void orderAssignTransitionViaScript() throws Exception {
        String orderId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.1",
                                  "migrations": [
                                    {
                                      "id": "parity_assign",
                                      "sql": "CREATE TABLE IF NOT EXISTS dispatch_order (id UUID PRIMARY KEY, order_number VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM dispatch_order; INSERT INTO dispatch_order (id, order_number, status) VALUES ('%s', 'P-ASSIGN-01', 'ready');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_dispatchOrder_assignTank",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": {
                                          "name": "in",
                                          "fields": [{"name": "orderId", "type": "STRING"}]
                                        },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "status", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"exec\\",\\"sql\\":\\"UPDATE dispatch_order SET status = 'assigned' WHERE id = ?\\",\\"params\\":[\\"${input.orderId}\\"]},{\\"type\\":\\"selectOne\\",\\"var\\":\\"order\\",\\"sql\\":\\"SELECT status FROM dispatch_order WHERE id = ?\\",\\"params\\":[\\"${input.orderId}\\"]},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"status\\":\\"${order.status}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(orderId, WORKBENCH)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_dispatchOrder_assignTank",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{"name": "orderId", "type": "STRING"}]
                                    },
                                    "rows": [{"orderId": "%s"}]
                                  }
                                }
                                """.formatted(WORKBENCH, orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("assigned"));
    }
}
