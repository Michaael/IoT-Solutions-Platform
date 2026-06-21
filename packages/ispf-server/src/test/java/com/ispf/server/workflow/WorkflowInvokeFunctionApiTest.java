package com.ispf.server.workflow;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowInvokeFunctionApiTest {

    private static final String WORKFLOW = "root.platform.workflows.app-function-demo";
    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String APP_ID = "wf-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private WorkflowService workflowService;

    @BeforeEach
    void setupWorkflowAndFunction() throws Exception {
        if (objectManager.tree().findByPath(WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "app-function-demo",
                    ObjectType.WORKFLOW,
                    "App Function Demo",
                    "",
                    "workflow-v1"
            );
            workflowService.ensureWorkflowStructure(WORKFLOW);
        }

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appId":"%s","displayName":"WF Test","tablePrefix":""}
                                """.formatted(APP_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(APP_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "functions": [
                                    {
                                      "objectPath": "%s",
                                      "functionName": "wf_ping",
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"from-bpmn\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(DEVICE)))
                .andExpect(status().isOk());

        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="invoke-demo" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="callFn" name="Call app fn"
                                 ispf:action="invoke_function"
                                 ispf:objectPath="%s"
                                 ispf:functionName="wf_ping"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="callFn"/>
                    <sequenceFlow sourceRef="callFn" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(DEVICE);

        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", bpmn))))
                .andExpect(status().isOk());
    }

    @Test
    void bpmnInvokeFunctionCallsDeployedAppFunction() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }
}
