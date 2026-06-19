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

    @Test
    void deploysBundleMetadataObjectsDashboardsAndWorkflows() throws Exception {
        String minimalBpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                             targetNamespace="http://ispf.io/bpmn">
                  <process id="Process_1" isExecutable="true">
                    <startEvent id="StartEvent_1"/>
                  </process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
                      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
                        <dc:Bounds x="180" y="100" width="36" height="36"/>
                      </bpmndi:BPMNShape>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </definitions>
                """.replace("\n", "").replace("\"", "\\\"");

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "2.0.0",
                                  "displayName": "Terminal Test",
                                  "objects": [
                                    {
                                      "parentPath": "root.platform",
                                      "name": "terminal-app",
                                      "type": "CUSTOM",
                                      "displayName": "Terminal App"
                                    }
                                  ],
                                  "dashboards": [
                                    {
                                      "path": "root.platform.terminal-app.ops",
                                      "title": "Ops Board",
                                      "layoutJson": "{\\"columns\\":12,\\"rowHeight\\":72,\\"widgets\\":[]}"
                                    }
                                  ],
                                  "workflows": [
                                    {
                                      "path": "root.platform.terminal-app.p301",
                                      "bpmnXml": "%s",
                                      "status": "ACTIVE"
                                    }
                                  ]
                                }
                                """.formatted(minimalBpmn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.applied", hasItem("object:terminal-app")))
                .andExpect(jsonPath("$.applied", hasItem("dashboard:root.platform.terminal-app.ops")))
                .andExpect(jsonPath("$.applied", hasItem("workflow:root.platform.terminal-app.p301")));

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", "root.platform.terminal-app.ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ops Board"));

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", "root.platform.terminal-app.p301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bpmnXml").isNotEmpty());
    }

    @Test
    void animaOperatorWireProfileReturnsTableArray() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "2.0.1",
                                  "migrations": [
                                    {
                                      "id": "orders_wire",
                                      "sql": "CREATE TABLE IF NOT EXISTS dispatch_order (id UUID PRIMARY KEY, order_number VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM dispatch_order; INSERT INTO dispatch_order (id, order_number, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'DO-WIRE-01', 'ready');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_wire_list",
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"orders\\",\\"sql\\":\\"SELECT order_number AS order_number, status AS status FROM dispatch_order\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"rows\\":\\"${orders}\\"}}]}"
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
                                  "functionName": "terminal_wire_list",
                                  "wireProfile": "anima-operator-v1",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.error_message").value(""))
                .andExpect(jsonPath("$.wireProfile").value("anima-operator-v1"))
                .andExpect(jsonPath("$.result", hasSize(1)))
                .andExpect(jsonPath("$.result[0].order_number").value("DO-WIRE-01"))
                .andExpect(jsonPath("$.result_field_labels.order_number").value("order_number"));
    }

    @Test
    void cancelWorkflowsScriptStepReturnsCount() throws Exception {
        String workflowPath = "root.platform.terminal-app.cancel-target";
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "2.0.2",
                                  "objects": [
                                    {
                                      "parentPath": "root.platform",
                                      "name": "terminal-app",
                                      "type": "CUSTOM",
                                      "displayName": "Terminal App"
                                    }
                                  ],
                                  "workflows": [
                                    {
                                      "path": "%s",
                                      "bpmnXml": "<definitions xmlns=\\"http://www.omg.org/spec/BPMN/20100524/MODEL\\"><process id=\\"P\\" isExecutable=\\"true\\"><startEvent id=\\"S\\"/></process></definitions>",
                                      "status": "ACTIVE"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_cancel_waiting",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": { "name": "in", "fields": [] },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "cancelledCount", "type": "INTEGER"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"cancel_workflows\\",\\"var\\":\\"cancelled\\",\\"workflowPath\\":\\"%s\\",\\"statusIn\\":[\\"WAITING\\",\\"RUNNING\\"],\\"reason\\":\\"incident\\",\\"detail\\":{}},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"cancelledCount\\":\\"${cancelled.cancelledCount}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(workflowPath, DEMO_DEVICE, workflowPath)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_cancel_waiting",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.cancelledCount").value(0));
    }

    @Test
    void listsFunctionVersionsAndRollsBack() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/functions/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_version_probe",
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
                                    "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"v1\\"}}]}"
                                  }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/functions/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_version_probe",
                                  "version": "2",
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
                                    "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"v2\\"}}]}"
                                  }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/%s/functions".formatted(APP_ID))
                        .param("objectPath", DEMO_DEVICE)
                        .param("functionName", "terminal_version_probe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_version_probe",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marker").value("v2"));

        mockMvc.perform(post("/api/v1/applications/%s/functions/rollback".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_version_probe",
                                  "version": "1"
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_version_probe",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marker").value("v1"));
    }

    @Test
    void sqlBindingRefreshesDashboardKpiVariable() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/data/migrate".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "3.0.0",
                                  "scripts": [
                                    {
                                      "id": "orders_kpi",
                                      "sql": "CREATE TABLE IF NOT EXISTS dispatch_order (id UUID PRIMARY KEY, order_number VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM dispatch_order; INSERT INTO dispatch_order (id, order_number, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'KPI-01', 'ready'); INSERT INTO dispatch_order (id, order_number, status) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'KPI-02', 'ready');"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/bindings/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "variable": "activeOrders",
                                  "query": "SELECT COUNT(*) AS cnt FROM dispatch_order WHERE status = 'ready'",
                                  "refresh": "on_schedule",
                                  "refreshIntervalMs": 60000,
                                  "valueField": "cnt"
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/bindings/refresh".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "variable": "activeOrders"
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "activeOrders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(2));
    }

    @Test
    void bundleRollbackRedeploysPreviousSnapshot() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "4.0.0",
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_bundle_marker",
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"bundle-v1\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot").value("recorded"));

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "4.0.1",
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "terminal_bundle_marker",
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"bundle-v2\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/%s/deploy/history".formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].version", hasItem("4.0.0")))
                .andExpect(jsonPath("$[*].version", hasItem("4.0.1")));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_bundle_marker",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marker").value("bundle-v2"));

        mockMvc.perform(post("/api/v1/applications/%s/deploy/rollback".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "version": "4.0.0" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolledBackTo").value("4.0.0"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "terminal_bundle_marker",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marker").value("bundle-v1"));
    }
}
