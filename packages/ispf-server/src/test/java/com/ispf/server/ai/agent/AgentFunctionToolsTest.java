package com.ispf.server.ai.agent;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFunctionToolsTest {

    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;
    @Mock
    private Authentication authentication;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlatformAgentTool tool(String name) {
        return AgentFunctionTools.all(objectManager, objectAccessService, tenantScopeService, objectMapper)
                .stream()
                .filter(t -> name.equals(t.name()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void deployTreeFunctionJavaSource() throws Exception {
        String path = "root.platform.devices.demo";
        when(tenantScopeService.isPathVisible(path, authentication)).thenReturn(true);
        when(objectManager.upsertFunction(eq(path), org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(1));

        Map<String, Object> result = tool("deploy_tree_function").execute(
                Map.of(
                        "path", path,
                        "functionName", "echoFn",
                        "sourceType", "java",
                        "sourceBody", AgentFunctionToolsTest.class.getResource("/") == null
                                ? "public class EchoFn implements com.ispf.core.function.ObjectJavaFunction {}"
                                : "public class EchoFn {}",
                        "outputSchema", Map.of("fields", List.of(Map.of("name", "ok", "type", "BOOLEAN")))
                ),
                new AgentContext("admin", authentication, null)
        );

        assertEquals("OK", result.get("status"));
        ArgumentCaptor<FunctionDescriptor> captor = ArgumentCaptor.forClass(FunctionDescriptor.class);
        verify(objectManager).upsertFunction(eq(path), captor.capture());
        assertEquals("java", captor.getValue().sourceType());
        assertEquals("echoFn", captor.getValue().name());
    }

    @Test
    void getFunctionTemplateJavaTopic() throws Exception {
        Map<String, Object> result = tool("get_function_template").execute(
                Map.of("topic", "java"),
                new AgentContext("admin", authentication, null)
        );
        assertEquals("OK", result.get("status"));
        assertEquals("java", result.get("sourceType"));
        assertTrue(String.valueOf(result.get("exampleSourceBody")).contains("ObjectJavaFunction"));
    }

    @Test
    void normalizeSourceTypeMapsJavascriptToScript() {
        assertEquals("script", AgentFunctionTools.normalizeSourceType("javascript"));
    }
}
