package com.ispf.server.ai.agent;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDiscoveryToolsTest {

    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;
    @Mock
    private ApplicationFunctionStore functionStore;
    @Mock
    private ApplicationEventCatalogService eventCatalogService;

    private List<PlatformAgentTool> tools;

    @BeforeEach
    void setUp() {
        tools = AgentDiscoveryTools.all(
                objectManager,
                objectAccessService,
                tenantScopeService,
                functionStore,
                eventCatalogService,
                new ObjectMapper()
        );
    }

    @Test
    void listFunctionsMergesTreeAndDeployed() throws Exception {
        String path = "root.platform.devices.demo-sensor-01";
        PlatformObject node = new PlatformObject(
                "1", path, ObjectType.DEVICE, "Demo", "", null
        );
        node.addFunction(new FunctionDescriptor(
                "ackAlarm",
                "Acknowledge alarm",
                DataSchema.builder("in").field(FieldDefinition.of("alarmId", FieldType.STRING)).build(),
                DataSchema.builder("out").field(FieldDefinition.of("error_code", FieldType.STRING)).build()
        ));
        when(objectManager.require(path)).thenReturn(node);
        when(tenantScopeService.isPathVisible(path, null)).thenReturn(true);
        when(functionStore.listLatestByObjectPath(path)).thenReturn(List.of(
                new ApplicationFunctionHandler.DeployedFunction(
                        UUID.randomUUID(),
                        "mes-reference",
                        path,
                        "mes_listOrders",
                        "1",
                        "script",
                        "{}",
                        "{\"name\":\"in\",\"fields\":[]}",
                        "{\"name\":\"out\",\"fields\":[]}"
                )
        ));

        Map<String, Object> result = tool("list_functions").execute(
                Map.of("objectPath", path),
                new AgentContext("admin", null, null)
        );

        assertEquals("OK", result.get("status"));
        assertEquals(2, result.get("count"));
        assertTrue(result.toString().contains("mes_listOrders"));
        assertTrue(result.toString().contains("ackAlarm"));
    }

    @Test
    void listEventCatalogFiltersByQuery() throws Exception {
        when(eventCatalogService.listEvents("mes-reference")).thenReturn(List.of(
                Map.of("id", "mesRackOverTemp", "roles", List.of("admin")),
                Map.of("id", "mesOrderUpdated", "roles", List.of("operator"))
        ));

        Map<String, Object> result = tool("list_event_catalog").execute(
                Map.of("appId", "mes-reference", "query", "Rack"),
                new AgentContext("admin", null, null)
        );

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void describeVariablesReturnsSchema() throws Exception {
        String path = "root.platform.devices.demo-sensor-01";
        DataSchema schema = DataSchema.builder("temperature")
                .field(FieldDefinition.of("value", FieldType.DOUBLE))
                .build();
        PlatformObject node = new PlatformObject(
                "1", path, ObjectType.DEVICE, "Demo", "", null
        );
        node.addVariable(new Variable("temperature", schema, true, true, null, null));
        when(objectManager.require(path)).thenReturn(node);
        when(tenantScopeService.isPathVisible(path, null)).thenReturn(true);

        Map<String, Object> result = tool("describe_variables").execute(
                Map.of("objectPath", path),
                new AgentContext("admin", null, null)
        );

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
        assertTrue(result.toString().contains("DOUBLE"));
    }

    @Test
    void getEventSchemaReturnsObjectDescriptor() throws Exception {
        String path = "root.platform.devices.demo-sensor-01";
        DataSchema payload = DataSchema.builder("payload")
                .field(FieldDefinition.of("temperature", FieldType.DOUBLE))
                .build();
        PlatformObject node = new PlatformObject(
                "1", path, ObjectType.DEVICE, "Demo", "", null
        );
        node.addEvent(new EventDescriptor("thresholdExceeded", "Threshold", payload, EventLevel.WARNING));
        when(objectManager.require(path)).thenReturn(node);
        when(tenantScopeService.isPathVisible(path, null)).thenReturn(true);

        Map<String, Object> result = tool("get_event_schema").execute(
                Map.of("objectPath", path, "eventName", "thresholdExceeded"),
                new AgentContext("admin", null, null)
        );

        assertEquals("OK", result.get("status"));
        assertEquals("WARNING", result.get("level"));
    }

    private PlatformAgentTool tool(String name) {
        return tools.stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
    }
}
