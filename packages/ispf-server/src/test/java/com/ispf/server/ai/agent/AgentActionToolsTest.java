package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.server.event.EventService;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentActionToolsTest {

    @Mock
    private FunctionService functionService;
    @Mock
    private com.ispf.server.application.function.ApplicationFunctionStore functionStore;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;
    @Mock
    private EventService eventService;
    @Mock
    private ModelRegistry modelRegistry;
    @Mock
    private com.ispf.core.object.ObjectTree objectTree;

    private List<PlatformAgentTool> tools;

    @BeforeEach
    void setUp() {
        tools = AgentActionTools.all(
                functionService,
                functionStore,
                objectManager,
                objectAccessService,
                tenantScopeService,
                eventService,
                modelRegistry,
                new ObjectMapper()
        );
    }

    @Test
    void searchObjectsFiltersByQuery() throws Exception {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.all()).thenReturn(List.of(
                new PlatformObject("1", "root.platform.devices.snmp-localhost", ObjectType.DEVICE, "SNMP localhost", "", "snmp-agent-v1"),
                new PlatformObject("2", "root.platform.devices.demo-sensor-01", ObjectType.DEVICE, "Demo", "", null)
        ));
        when(tenantScopeService.isPathVisible(any(), any())).thenReturn(true);
        when(objectAccessService.canRead(any(), any())).thenReturn(true);

        PlatformAgentTool search = tool("search_objects");
        Map<String, Object> result = search.execute(Map.of("query", "snmp"), new AgentContext("admin", null, null));

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
        assertTrue(result.toString().contains("snmp-localhost"));
    }

    @Test
    void listObjectModelsReturnsTemplates() throws Exception {
        when(modelRegistry.all()).thenReturn(List.of(
                new ModelDefinition(
                        "id-snmp",
                        "snmp-agent-v1",
                        "SNMP agent device",
                        ModelType.INSTANCE,
                        ObjectType.DEVICE,
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        Instant.now(),
                        Instant.now()
                )
        ));

        PlatformAgentTool listModels = tool("list_object_models");
        Map<String, Object> result = listModels.execute(Map.of("query", "snmp"), new AgentContext("admin", null, null));

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void invokeBffRequiresInvokeAcl() throws Exception {
        DataSchema schema = new DataSchema("out", List.of());
        when(functionService.invoke(anyString(), anyString(), nullable(DataRecordPayloadRequest.class)))
                .thenReturn(DataRecord.empty(schema));

        PlatformAgentTool invoke = tool("invoke_bff");
        invoke.execute(Map.of(
                "objectPath", "root.platform.devices.demo-sensor-01",
                "functionName", "mes_listOrders"
        ), new AgentContext("admin", null, null));

        verify(objectAccessService).requireInvoke(eq("root.platform.devices.demo-sensor-01"), any());
    }

    private PlatformAgentTool tool(String name) {
        return tools.stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
    }
}
