package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.report.ReportService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReportToolsTest {

    @Mock
    private ReportService reportService;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectTree objectTree;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;
    @Mock
    private Authentication authentication;
    @Mock
    private PlatformObject reportNode;

    private PlatformAgentTool tool(String name) {
        return AgentReportTools.all(
                reportService, objectManager, objectAccessService, tenantScopeService
        ).stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
    }

    @Test
    void getReportSchemaReturnsDefinitionAndPlaceholders() throws Exception {
        when(tenantScopeService.isPathVisible(eq("root.platform.reports.demo"), any())).thenReturn(true);
        when(reportService.getReport("root.platform.reports.demo")).thenReturn(
                new ReportService.ReportView(
                        "root.platform.reports.demo",
                        "Demo",
                        "root.platform.data-sources.demo",
                        "",
                        "SELECT 1",
                        "",
                        "",
                        "",
                        List.of("status"),
                        List.of(new ReportService.ReportColumn("status", "Status")),
                        Map.of("status", "ready"),
                        1000,
                        30000,
                        "xls",
                        "",
                        true
                )
        );

        Map<String, Object> result = tool("get_report_schema").execute(
                Map.of("path", "root.platform.reports.demo"),
                new AgentContext("admin", authentication, null)
        );

        assertEquals("OK", result.get("status"));
        verify(objectAccessService).requireRead("root.platform.reports.demo", authentication);
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) result.get("report");
        assertEquals("Demo", report.get("title"));
        @SuppressWarnings("unchecked")
        List<String> placeholders = (List<String>) report.get("yargPlaceholders");
        assertTrue(placeholders.contains("${STATUS}"));
        @SuppressWarnings("unchecked")
        List<String> formats = (List<String>) report.get("exportFormats");
        assertTrue(formats.contains("pdf"));
    }

    @Test
    void listReportsReturnsVisibleReports() throws Exception {
        when(tenantScopeService.isPathVisible(eq(ReportService.REPORTS_ROOT), any())).thenReturn(true);
        when(tenantScopeService.isPathVisible(eq("root.platform.reports.demo"), any())).thenReturn(true);
        doNothing().when(reportService).ensureReportsCatalog();
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.childrenOf(ReportService.REPORTS_ROOT)).thenReturn(List.of(reportNode));
        when(reportNode.type()).thenReturn(ObjectType.REPORT);
        when(reportNode.path()).thenReturn("root.platform.reports.demo");
        when(reportService.getReport("root.platform.reports.demo")).thenReturn(
                new ReportService.ReportView(
                        "root.platform.reports.demo",
                        "Demo",
                        "",
                        "",
                        "",
                        "tree-variables",
                        "root.platform.devices.lab-*",
                        "status",
                        List.of(),
                        List.of(new ReportService.ReportColumn("devicepath", "Device path")),
                        Map.of(),
                        500,
                        30000,
                        "",
                        "",
                        false
                )
        );

        Map<String, Object> result = tool("list_reports").execute(Map.of(), new AgentContext("admin", authentication, null));

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void configureReportCreatesTreeVariablesReport() throws Exception {
        String path = "root.platform.reports.lab-status";
        when(tenantScopeService.isPathVisible(eq(path), any())).thenReturn(true);
        doNothing().when(reportService).ensureReportsCatalog();
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(path)).thenReturn(java.util.Optional.empty());
        when(objectManager.create(
                eq(ReportService.REPORTS_ROOT),
                eq("lab-status"),
                eq(ObjectType.REPORT),
                any(),
                any(),
                any()
        )).thenReturn(reportNode);
        doNothing().when(reportService).ensureTreeVariablesReportStructure(path);
        when(reportService.getReport(path)).thenReturn(
                new ReportService.ReportView(
                        path,
                        "Lab status",
                        "",
                        "",
                        "",
                        "tree-variables",
                        "root.platform.devices.lab-*",
                        "status",
                        List.of(),
                        List.of(
                                new ReportService.ReportColumn("devicepath", "Device path"),
                                new ReportService.ReportColumn("online", "Online")
                        ),
                        Map.of(),
                        500,
                        30000,
                        "",
                        "",
                        false
                )
        );
        when(reportService.saveTreeVariablesDefinition(eq(path), any())).thenAnswer(invocation -> {
            ReportService.SaveTreeVariablesDefinitionRequest req = invocation.getArgument(1);
            return new ReportService.ReportView(
                    path,
                    req.title() != null ? req.title() : "Lab status",
                    "",
                    "",
                    "",
                    "tree-variables",
                    req.devicePathPattern(),
                    req.variableName(),
                    List.of(),
                    req.columns(),
                    Map.of(),
                    req.maxRows() != null ? req.maxRows() : 500,
                    req.refreshIntervalMs() != null ? req.refreshIntervalMs() : 30000,
                    "",
                    "",
                    false
            );
        });

        Map<String, Object> result = tool("configure_report").execute(
                Map.of(
                        "reportId", "lab-status",
                        "reportType", "tree-variables",
                        "title", "Lab status",
                        "devicePathPattern", "root.platform.devices.lab-*",
                        "variableName", "status",
                        "columns", List.of(
                                Map.of("field", "devicepath", "label", "Device path"),
                                Map.of("field", "online", "label", "Online")
                        )
                ),
                new AgentContext("admin", authentication, null)
        );

        assertEquals("OK", result.get("status"));
        assertEquals(true, result.get("created"));
        verify(reportService).saveTreeVariablesDefinition(eq(path), any());
    }
}
