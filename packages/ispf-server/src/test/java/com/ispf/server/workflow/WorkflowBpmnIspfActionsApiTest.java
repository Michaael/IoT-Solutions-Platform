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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowBpmnIspfActionsApiTest {

    private static final String WORKFLOW = "root.platform.workflows.bpmn-ispf-actions";
    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String APP_ID = "bpmn-ispf-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private WorkflowService workflowService;

    @BeforeEach
    void setup() throws Exception {
        if (objectManager.tree().findByPath(WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "bpmn-ispf-actions",
                    ObjectType.WORKFLOW,
                    "BPMN ISPF Actions",
                    "",
                    "workflow-v1"
            );
        }
        workflowService.ensureWorkflowStructure(WORKFLOW);

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appId":"%s","displayName":"BPMN ISPF","tablePrefix":""}
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
                                      "functionName": "wf_read_probe",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": {
                                          "name": "in",
                                          "fields": [{"name": "snap", "type": "STRING"}]
                                        },
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
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\",\\"marker\\":\\"${input.snap}\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """.formatted(DEVICE)))
                .andExpect(status().isOk());
    }

    @Test
    void bpmnFireEventPublishesEvent() throws Exception {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="fire-demo" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="fire" name="Fire"
                                 ispf:action="fire_event"
                                 ispf:objectPath="%s"
                                 ispf:eventName="thresholdExceeded"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="fire"/>
                    <sequenceFlow sourceRef="fire" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(DEVICE);

        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", bpmn))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/events")
                        .param("objectPath", DEVICE)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("thresholdExceeded")));
    }

    @Test
    void bpmnReadVariableAndInvokeFunction() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "threshold",
                                    "fields": [{"name": "value", "type": "DOUBLE"}]
                                  },
                                  "rows": [{"value": 88.0}]
                                }
                                """))
                .andExpect(status().isOk());

        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:ispf="http://ispf.io/bpmn">
                  <process id="read-demo" isExecutable="true">
                    <startEvent id="start"/>
                    <serviceTask id="readTh" name="Read threshold"
                                 ispf:action="read_variable"
                                 ispf:objectPath="%s"
                                 ispf:variable="threshold"
                                 ispf:contextKey="snapThreshold"/>
                    <serviceTask id="callFn" name="Probe"
                                 ispf:action="invoke_function"
                                 ispf:objectPath="%s"
                                 ispf:functionName="wf_read_probe"
                                 ispf:inputMap="snap=${snapThreshold}"/>
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="readTh"/>
                    <sequenceFlow sourceRef="readTh" targetRef="callFn"/>
                    <sequenceFlow sourceRef="callFn" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(DEVICE, DEVICE);

        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", bpmn))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }
}
