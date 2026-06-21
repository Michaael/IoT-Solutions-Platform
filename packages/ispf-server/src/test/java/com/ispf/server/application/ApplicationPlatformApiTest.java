package com.ispf.server.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    private static final String APP_ID = "platform-test";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void ensureTestAppRegistered() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Platform Test",
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
                                  "displayName": "Platform Test",
                                  "tablePrefix": ""
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID))
                .andExpect(jsonPath("$.schemaName").value("app_platform_test"));

        mockMvc.perform(post("/api/v1/applications/%s/data/migrate".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "scripts": [
                                    {
                                      "id": "platform_item",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", hasItem("platform_item")));

        mockMvc.perform(get("/api/v1/applications/%s/data/status".formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.schemaName").value("app_platform_test"));
    }

    @Test
    void deploysScriptFunctionAndInvokesViaBff() throws Exception {
        String itemId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.1",
                                  "displayName": "Platform Test",
                                  "migrations": [
                                    {
                                      "id": "platform_item",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); INSERT INTO platform_item (id, item_code, status) VALUES ('%s', 'IT-TEST-01', 'ready');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_ping",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": {
                                          "name": "platform_ping_input",
                                          "fields": [{"name": "itemId", "type": "STRING"}]
                                        },
                                        "outputSchema": {
                                          "name": "platform_ping_output",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "status", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectOne\\",\\"var\\":\\"order\\",\\"sql\\":\\"SELECT status FROM platform_item WHERE id = ?\\",\\"params\\":[\\"${input.itemId}\\"]},{\\"type\\":\\"failIfNull\\",\\"var\\":\\"order\\",\\"error_code\\":\\"NOT_FOUND\\",\\"error_message\\":\\"missing\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"status\\":\\"${order.status}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(itemId, DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "platform_ping",
                                  "input": {
                                    "schema": {
                                      "name": "platform_ping_input",
                                      "fields": [{"name": "itemId", "type": "STRING"}]
                                    },
                                    "rows": [{"itemId": "%s"}]
                                  }
                                }
                                """.formatted(DEMO_DEVICE, itemId)))
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
                                      "sql": "DELETE FROM platform_item; INSERT INTO platform_item (id, item_code, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'IT-LIST-01', 'ready'); INSERT INTO platform_item (id, item_code, status) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'IT-LIST-02', 'assigned');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_listItems",
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
                                                  {"name": "item_code", "type": "STRING"},
                                                  {"name": "status", "type": "STRING"}
                                                ]
                                              }
                                            }
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"orders\\",\\"sql\\":\\"SELECT item_code AS item_code, status AS status FROM platform_item ORDER BY item_code\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"rows\\":\\"${orders}\\"}}]}"
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
                                  "functionName": "platform_listItems",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(2)))
                .andExpect(jsonPath("$.result.rows[0].item_code").value("IT-LIST-01"));
    }

    @Test
    void buildRecordAndMapScriptSteps() throws Exception {
        String itemId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.2-map",
                                  "migrations": [
                                    {
                                      "id": "map_seed",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM platform_item; INSERT INTO platform_item (id, item_code, status) VALUES ('%s', 'WH-001', 'ready'); INSERT INTO platform_item (id, item_code, status) VALUES ('%s', 'WH-002', 'assigned');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_mapItems",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": { "name": "in", "fields": [] },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "firstCode", "type": "STRING"},
                                            {
                                              "name": "rows",
                                              "type": "RECORD_LIST",
                                              "nestedSchema": {
                                                "name": "mapped_row",
                                                "fields": [
                                                  {"name": "code", "type": "STRING"},
                                                  {"name": "state", "type": "STRING"}
                                                ]
                                              }
                                            }
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"items\\",\\"sql\\":\\"SELECT item_code, status FROM platform_item ORDER BY item_code\\"},{\\"type\\":\\"selectOne\\",\\"var\\":\\"first\\",\\"sql\\":\\"SELECT item_code FROM platform_item ORDER BY item_code LIMIT 1\\"},{\\"type\\":\\"map\\",\\"var\\":\\"mapped\\",\\"source\\":\\"${items}\\",\\"fields\\":{\\"code\\":\\"${item.item_code}\\",\\"state\\":\\"${item.status}\\"}},{\\"type\\":\\"buildRecord\\",\\"var\\":\\"header\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"firstCode\\":\\"${first.item_code}\\"}},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"${header.error_code}\\",\\"error_message\\":\\"${header.error_message}\\",\\"firstCode\\":\\"${header.firstCode}\\",\\"rows\\":\\"${mapped}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(itemId, UUID.randomUUID(), DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "platform_mapItems",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.firstCode").value("WH-001"))
                .andExpect(jsonPath("$.result.rows", hasSize(2)))
                .andExpect(jsonPath("$.result.rows[0].code").value("WH-001"));
    }

    @Test
    void setVarScriptStepAssignsLiteralAndPath() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "7.0.0-setvar",
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_setvar_demo",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": {
                                          "name": "in",
                                          "fields": [{"name": "label", "type": "STRING"}]
                                        },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "marker", "type": "STRING"},
                                            {"name": "copied", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"setVar\\",\\"var\\":\\"marker\\",\\"value\\":\\"ready\\"},{\\"type\\":\\"setVar\\",\\"var\\":\\"copied\\",\\"value\\":\\"${input.label}\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"${marker}\\",\\"copied\\":\\"${copied}\\"}}]}"
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
                                  "functionName": "platform_setvar_demo",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{"name": "label", "type": "STRING"}]
                                    },
                                    "rows": [{"label": "alpha"}]
                                  }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.marker").value("ready"))
                .andExpect(jsonPath("$.result.copied").value("alpha"));
    }

    @Test
    void whenScriptStepBranchesWithoutSql() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "7.0.1-when-%s",
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_when_demo",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": {
                                          "name": "in",
                                          "fields": [{"name": "mode", "type": "STRING"}]
                                        },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"},
                                            {"name": "branch", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"when\\",\\"var\\":\\"input.mode\\",\\"equals\\":\\"hot\\",\\"then\\":[{\\"type\\":\\"setVar\\",\\"var\\":\\"branch\\",\\"value\\":\\"hot-path\\"}],\\"else\\":[{\\"type\\":\\"setVar\\",\\"var\\":\\"branch\\",\\"value\\":\\"cold-path\\"}]},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"branch\\":\\"${branch}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID(), DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "platform_when_demo",
                                  "input": {
                                    "schema": { "name": "in", "fields": [{"name": "mode", "type": "STRING"}] },
                                    "rows": [{"mode": "hot"}]
                                  }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.branch").value("hot-path"));
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
                                      "functionName": "platform_gate_fail",
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"GATE_BLOCKED\\",\\"error_message\\":\\"blocked\\"}}]}"
                                      }
                                    },
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_startJob",
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"invoke_function\\",\\"objectPath\\":\\"%s\\",\\"functionName\\":\\"platform_gate_fail\\",\\"input\\":{}},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\"}}]}"
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
                                  "functionName": "platform_startJob",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("GATE_BLOCKED"))
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
        String seedApp = "seed-demo";

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Seed Demo",
                                  "tablePrefix": "",
                                  "schemaName": "seed_demo"
                                }
                                """.formatted(seedApp)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/data/migrate".formatted(seedApp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "scripts": [
                                    {
                                      "id": "schema",
                                      "sql": "CREATE TABLE IF NOT EXISTS demo_category (id UUID PRIMARY KEY, category_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); CREATE TABLE IF NOT EXISTS demo_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, category_id UUID); CREATE TABLE IF NOT EXISTS demo_metric (metric_key VARCHAR(64) PRIMARY KEY, metric_value BIGINT, status VARCHAR(32));"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/data/seed".formatted(seedApp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\": \"smoke-demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", hasSize(3)));

        mockMvc.perform(post("/api/v1/applications/%s/data/seed".formatted(seedApp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\": \"smoke-demo\"}"))
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
                                  "displayName": "Platform Test",
                                  "objects": [
                                    {
                                      "parentPath": "root.platform",
                                      "name": "demo-app",
                                      "type": "CUSTOM",
                                      "displayName": "Demo App"
                                    }
                                  ],
                                  "dashboards": [
                                    {
                                      "path": "root.platform.demo-app.ops",
                                      "title": "Ops Board",
                                      "layoutJson": "{\\"columns\\":12,\\"rowHeight\\":72,\\"widgets\\":[]}"
                                    }
                                  ],
                                  "workflows": [
                                    {
                                      "path": "root.platform.demo-app.main-flow",
                                      "bpmnXml": "%s",
                                      "status": "ACTIVE"
                                    }
                                  ]
                                }
                                """.formatted(minimalBpmn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.applied", hasItem("object:demo-app")))
                .andExpect(jsonPath("$.applied", hasItem("dashboard:root.platform.demo-app.ops")))
                .andExpect(jsonPath("$.applied", hasItem("workflow:root.platform.demo-app.main-flow")));

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", "root.platform.demo-app.ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ops Board"));

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", "root.platform.demo-app.main-flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bpmnXml").isNotEmpty());

        mockMvc.perform(get("/api/v1/applications/%s/operator-ui".formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID))
                .andExpect(jsonPath("$.defaultDashboard").value("root.platform.demo-app.ops"))
                .andExpect(jsonPath("$.dashboards", hasSize(1)))
                .andExpect(jsonPath("$.dashboards[0].path").value("root.platform.demo-app.ops"))
                .andExpect(jsonPath("$.dashboards[0].title").value("Ops Board"));
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
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM platform_item; INSERT INTO platform_item (id, item_code, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'IT-WIRE-01', 'ready');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_wire_list",
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
                                                  {"name": "item_code", "type": "STRING"},
                                                  {"name": "status", "type": "STRING"}
                                                ]
                                              }
                                            }
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"orders\\",\\"sql\\":\\"SELECT item_code AS item_code, status AS status FROM platform_item\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"rows\\":\\"${orders}\\"}}]}"
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
                                  "functionName": "platform_wire_list",
                                  "wireProfile": "anima-operator-v1",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.error_message").value(""))
                .andExpect(jsonPath("$.wireProfile").value("anima-operator-v1"))
                .andExpect(jsonPath("$.result", hasSize(1)))
                .andExpect(jsonPath("$.result[0].item_code").value("IT-WIRE-01"))
                .andExpect(jsonPath("$.result_field_labels.item_code").value("Код позиции"));
    }

    @Test
    void wireProfileUsesSchemaDescriptionForFieldLabels() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "3.1.0-labels",
                                  "migrations": [
                                    {
                                      "id": "label_items",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM platform_item; INSERT INTO platform_item (id, item_code, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'LBL-01', 'ready');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "platform_labeled_list",
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
                                                  {"name": "item_code", "type": "STRING", "description": "Артикул"},
                                                  {"name": "status", "type": "STRING", "description": "Статус позиции"}
                                                ]
                                              }
                                            }
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"orders\\",\\"sql\\":\\"SELECT item_code AS item_code, status AS status FROM platform_item\\"},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"rows\\":\\"${orders}\\"}}]}"
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
                                  "functionName": "platform_labeled_list",
                                  "wireProfile": "anima-operator-v1",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result_field_labels.item_code").value("Артикул"))
                .andExpect(jsonPath("$.result_field_labels.status").value("Статус позиции"));
    }

    @Test
    void warehouseReferenceAppUsesMapScriptOnly() throws Exception {
        String whAppId = "warehouse";
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Warehouse Reference App",
                                  "tablePrefix": "",
                                  "schemaName": "app_warehouse"
                                }
                                """.formatted(whAppId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(whAppId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "Warehouse Reference App",
                                  "schemaName": "app_warehouse",
                                  "migrations": [
                                    {
                                      "id": "wh_schema",
                                      "sql": "CREATE TABLE IF NOT EXISTS wh_location (id UUID PRIMARY KEY, location_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM wh_location; INSERT INTO wh_location (id, location_code, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'A-01', 'ready'); INSERT INTO wh_location (id, location_code, status) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'B-02', 'assigned');"
                                    }
                                  ],
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "warehouse_listLocations",
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
                                                "name": "location_row",
                                                "fields": [
                                                  {"name": "code", "type": "STRING", "description": "Код ячейки"},
                                                  {"name": "state", "type": "STRING", "description": "Статус ячейки"}
                                                ]
                                              }
                                            }
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"selectMany\\",\\"var\\":\\"locations\\",\\"sql\\":\\"SELECT location_code, status FROM wh_location ORDER BY location_code\\"},{\\"type\\":\\"map\\",\\"var\\":\\"rows\\",\\"source\\":\\"${locations}\\",\\"fields\\":{\\"code\\":\\"${item.location_code}\\",\\"state\\":\\"${item.status}\\"}},{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"rows\\":\\"${rows}\\"}}]}"
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
                                  "functionName": "warehouse_listLocations",
                                  "wireProfile": "anima-operator-v1",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result", hasSize(2)))
                .andExpect(jsonPath("$.result[0].code").value("A-01"))
                .andExpect(jsonPath("$.result_field_labels.code").value("Код ячейки"))
                .andExpect(jsonPath("$.result_field_labels.state").value("Статус ячейки"));
    }

    @Test
    void cancelWorkflowsScriptStepReturnsCount() throws Exception {
        String workflowPath = "root.platform.demo-app.cancel-target";
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "2.0.2",
                                  "objects": [
                                    {
                                      "parentPath": "root.platform",
                                      "name": "demo-app",
                                      "type": "CUSTOM",
                                      "displayName": "Demo App"
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
                                      "functionName": "platform_cancel_waiting",
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
                                  "functionName": "platform_cancel_waiting",
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
                                  "functionName": "platform_version_probe",
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
                                  "functionName": "platform_version_probe",
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
                        .param("functionName", "platform_version_probe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "platform_version_probe",
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
                                  "functionName": "platform_version_probe",
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
                                  "functionName": "platform_version_probe",
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
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM platform_item; INSERT INTO platform_item (id, item_code, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'KPI-01', 'ready'); INSERT INTO platform_item (id, item_code, status) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'KPI-02', 'ready');"
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
                                  "variable": "readyCount",
                                  "query": "SELECT COUNT(*) AS cnt FROM platform_item WHERE status = 'ready'",
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
                                  "variable": "readyCount"
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "readyCount"))
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
                                      "functionName": "platform_bundle_marker",
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
                                      "functionName": "platform_bundle_marker",
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
                                  "functionName": "platform_bundle_marker",
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
                                  "functionName": "platform_bundle_marker",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(DEMO_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.marker").value("bundle-v1"));
    }

    @Test
    void deploysAndRunsSqlReportWithCsvExport() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "5.0.0",
                                  "migrations": [
                                    {
                                      "id": "report_items",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL); DELETE FROM platform_item; INSERT INTO platform_item (id, item_code, status) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'RPT-01', 'ready'); INSERT INTO platform_item (id, item_code, status) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'RPT-02', 'closed');"
                                    }
                                  ],
                                  "reports": [
                                    {
                                      "reportId": "ready-items",
                                      "title": "Ready items",
                                      "query": "SELECT item_code, status FROM platform_item WHERE status = ?",
                                      "parameters": ["status"],
                                      "columns": [
                                        { "field": "item_code", "label": "Code" },
                                        { "field": "status", "label": "Status" }
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", hasItem("report:ready-items")));

        mockMvc.perform(get("/api/v1/applications/%s/reports".formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value("ready-items"));

        mockMvc.perform(post("/api/v1/applications/%s/reports/ready-items/run".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "parameters": { "status": "ready" } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.rows[0].item_code").value("RPT-01"));

        mockMvc.perform(get("/api/v1/applications/%s/reports/ready-items/export".formatted(APP_ID))
                        .param("status", "ready"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("ready-items.csv")));
    }

    @Test
    void syncsApplicationEntitiesIntoObjectTree() throws Exception {
        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "6.0.0",
                                  "displayName": "Platform Test",
                                  "migrations": [
                                    {
                                      "id": "tree_items",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);"
                                    }
                                  ],
                                  "reports": [
                                    {
                                      "reportId": "tree-report",
                                      "title": "Tree Report",
                                      "query": "SELECT item_code FROM platform_item",
                                      "parameters": [],
                                      "columns": [{ "field": "item_code", "label": "Code" }]
                                    }
                                  ],
                                  "operatorManifest": {
                                    "appId": "%s",
                                    "title": "Platform Test",
                                    "defaultScreen": "main",
                                    "screens": [
                                      { "id": "main", "title": "Main", "report": { "reportId": "tree-report" } }
                                    ]
                                  }
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectTree").value("synced"));

        mockMvc.perform(get("/api/v1/objects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.applications.platform-test")))
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.applications.platform-test.reports.tree-report")))
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.applications.platform-test.migrations.tree_items")))
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.applications.platform-test.screens.main")));
    }
}
