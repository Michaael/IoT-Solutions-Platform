package com.ispf.server.ai.agent;

import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverMaturity;
import com.ispf.server.ai.context.ContextPackSearchService;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.driver.DriverCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListDriversAgentToolTest {

    @Mock
    private ContextPackSearchService contextPackSearchService;
    @Mock
    private DriverCatalog driverCatalog;
    @Mock
    private ApplicationDataStore applicationDataStore;
    @Mock
    private ApplicationBundleSnapshotStore bundleSnapshotStore;

    @Test
    void listDriversReturnsSnmpAndVirtual() throws Exception {
        when(driverCatalog.list()).thenReturn(List.of(
                new DriverMetadata("snmp", "SNMP", "1", "SNMP", "ISPF", Map.of(), DriverMaturity.PRODUCTION),
                new DriverMetadata("virtual", "Virtual", "1", "Simulator", "ISPF", Map.of(), DriverMaturity.PRODUCTION),
                new DriverMetadata("mqtt", "MQTT", "1", "MQTT", "ISPF", Map.of(), DriverMaturity.PRODUCTION)
        ));

        PlatformAgentTool listDrivers = AgentKnowledgeTools.all(
                contextPackSearchService,
                driverCatalog,
                applicationDataStore,
                bundleSnapshotStore
        ).stream().filter(tool -> "list_drivers".equals(tool.name())).findFirst().orElseThrow();

        Map<String, Object> result = listDrivers.execute(Map.of("query", "snmp"), new AgentContext("admin", null, null));

        assertEquals("OK", result.get("status"));
        List<?> drivers = (List<?>) result.get("drivers");
        assertEquals(1, drivers.size());
        assertTrue(result.toString().contains("snmp"));
    }
}
