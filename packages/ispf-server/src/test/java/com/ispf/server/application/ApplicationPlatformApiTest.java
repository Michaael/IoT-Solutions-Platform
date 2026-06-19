package com.ispf.server.application;

import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationPlatformApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String APP_ID = "terminal-test";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void ensureTestAppRegistered() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Terminal Test",
                                  "tablePrefix": ""
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void registersApplicationAndMigratesData() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Terminal Test",
                                  "tablePrefix": ""
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID))
                .andExpect(jsonPath("$.schemaName").value("app_terminal_test"));

        mockMvc.perform(post("/api/v1/applications/%s/data/migrate".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "scripts": [
                                    {
                                      "id": "dispatch_order",
                                      "sql": "CREATE TABLE IF NOT EXISTS dispatch_order (id UUID PRIMARY KEY, order_number VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", hasItem("dispatch_order")));

        mockMvc.perform(get("/api/v1/applications/%s/data/status".formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.schemaName").value("app_terminal_test"));
    }

    @Test
    void deploysScriptFunctionAndInvokesViaBff() throws Exception {
        String orderId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.1",
                                  "displayName": "Terminal Test",
                                  "migrations": [
                                    {
                                      "id": "dispatch_order",
                                      "sql": "CREATE TABLE IF NOT EXISTS dispatch_order (id UUID PRIMARY KEY, order_number VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); INSERT INTO dispatch_order (id, order_number, status) VALUES ('%s', 'DO-TEST-01', 'ready');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_ping",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": {
                                          "name": "terminal_ping_input",
                                          "fields": [{"name": "orderId", "type": "STRING"}]
                                        },
                                        "outputSchema": {
                                          "name": "terminal_ping_output",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "status", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectOne\\",\\"var\\":\\"order\\",\\"sql\\":\\"SELECT status FROM dispatch_order WHERE id = ?\\",\\"params\\":[\\"${input.orderId}\\"]},{\\"type\\":\\"failIfNull\\",\\"var\\":\\"order\\",\\"error_code\\":\\"NOT_FOUND\\",\\"error_message\\":\\"missing\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"status\\":\\"${order.status}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(orderId, DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_ping",
                                  "input": {
                                    "schema": {
                                      "name": "terminal_ping_input",
                                      "fields": [{"name": "orderId", "type": "STRING"}]
                                    },
                                    "rows": [{"orderId": "%s"}]
                                  }
                                }
                                """.formatted(DEMO_DEVICE, orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("ready"));
    }

    @Test
    void selectManyReturnsTableRowsOnWire() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.2",
                                  "migrations": [
                                    {
                                      "id": "orders_seed",
                                      "sql": "DELETE FROM dispatch_order; INSERT INTO dispatch_order (id, order_number, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'DO-LIST-01', 'ready'); INSERT INTO dispatch_order (id, order_number, status) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'DO-LIST-02', 'assigned');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_listOrders",
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"orders\\",\\"sql\\":\\"SELECT order_number AS order_number, status AS status FROM dispatch_order ORDER BY order_number\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"rows\\":\\"${orders}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_listOrders",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(2)))
                .andExpect(jsonPath("$.result.rows[0].order_number").value("DO-LIST-01"));
    }

    @Test
    void invokeFunctionPropagatesNestedError() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.3",
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_gate_fail",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": { "name": "in", "fields": [] },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"TANK_NOT_APPROVED\\",\\"error_message\\":\\"blocked\\"}}]}"
                                      }
                                    },
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_startFilling",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": { "name": "in", "fields": [] },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"invoke_function\\",\\"objectPath\\":\\"%s\\",\\"functionName\\":\\"terminal_gate_fail\\",\\"input\\":{}},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(DEMO_DEVICE, DEMO_DEVICE, DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_startFilling",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("TANK_NOT_APPROVED"))
                .andExpect(jsonPath("$.error_message").value("blocked"));
    }

    @Test
    void rejectsInvalidScriptOnDeploy() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/functions/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "bad_script",
                                  "descriptor": {
                                    "inputSchema": { "name": "in", "fields": [] },
                                    "outputSchema": {
                                      "name": "out",
                                      "fields": [
                                        {"name": "error_code", "type": "STRING"},
                                        {"name": "error_message", "type": "STRING"}
                                      ]
                                    }
                                  },
                                  "source": {
                                    "type": "script",
                                    "body": "{\\"steps\\":[{\\"type\\":\\"unknown_step\\"}]}"
                                  }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seedProfileIsIdempotent() throws Exception {
        String terminalApp = "terminal-seed";

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Terminal Seed",
                                  "tablePrefix": "",
                                  "schemaName": "terminal"
                                }
                                """.formatted(terminalApp)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/data/migrate".formatted(terminalApp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "scripts": [
                                    {
                                      "id": "schema",
                                      "sql": "CREATE TABLE IF NOT EXISTS dispatch_shift (id UUID PRIMARY KEY, shift_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, started_at TIMESTAMP); CREATE TABLE IF NOT EXISTS dispatch_order (id UUID PRIMARY KEY, order_number VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, shift_id UUID, tank_path VARCHAR(255)); CREATE TABLE IF NOT EXISTS tank_balance (tank_path VARCHAR(255) PRIMARY KEY, product_code VARCHAR(64), volume_liters BIGINT, quality_status VARCHAR(32));"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/data/seed".formatted(terminalApp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\": \"smoke-p301\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", hasSize(3)));

        mockMvc.perform(post("/api/v1/applications/%s/data/seed".formatted(terminalApp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\": \"smoke-p301\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped", hasSize(3)));
    }

    @Test
    void isolatesApplicationSchemas() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appId": "app-a", "displayName": "A", "tablePrefix": ""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaName").value("app_app_a"));

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appId": "app-b", "displayName": "B", "tablePrefix": ""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaName").value("app_app_b"));

        String migrate = """
                {
                  "version": "1.0.0",
                  "scripts": [
                    {
                      "id": "items",
                      "sql": "CREATE TABLE IF NOT EXISTS items (id UUID PRIMARY KEY, label VARCHAR(64) NOT NULL); INSERT INTO items (id, label) VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'from-app');"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/applications/app-a/data/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(migrate))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/app-b/data/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(migrate))
                .andExpect(status().isOk());
    }
}
