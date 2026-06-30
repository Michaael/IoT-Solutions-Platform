package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.ai.context.ContextPackSearchService;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.ai.validation.BundleValidationResult;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.bundle.BundleManifestJsonSupport;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.dashboard.DashboardWidgetNormalizer;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverCatalog;
import com.ispf.server.event.EventService;
import com.ispf.server.federation.FederationBindService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.operator.OperatorAgentMemoryService;
import com.ispf.server.operator.OperatorAppDocumentService;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.platform.HaystackExportService;
import com.ispf.server.report.ReportService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.workflow.WorkQueueService;
import com.ispf.server.workflow.WorkflowService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
public class PlatformAgentToolRegistry {

    static final Set<String> OPERATOR_TOOLS = Set.of(
            "get_operator_scope",
            "list_objects",
            "get_object",
            "search_objects",
            "search_by_haystack_tags",
            "list_variables",
            "describe_variables",
            "list_functions",
            "get_function",
            "invoke_bff",
            "invoke_tree_function",
            "list_events",
            "get_event_schema",
            "list_event_catalog",
            "list_reports",
            "get_report_schema",
            "run_report",
            "get_dashboard_layout",
            "list_automation",
            "get_automation_schema",
            "get_variable_history",
            "get_variable_trend",
            "list_work_queue",
            "list_app_memory",
            "remember_app_memory",
            "list_app_documents",
            "read_app_document",
            "search_app_documents",
            "get_operator_link"
    );

    private final Map<String, PlatformAgentTool> toolsByName;
    private final ObjectMapper objectMapper;

    public PlatformAgentToolRegistry(
            ContextPackSearchService contextPackSearchService,
            DriverCatalog driverCatalog,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleSnapshotStore bundleSnapshotStore,
            FunctionService functionService,
            ApplicationFunctionStore applicationFunctionStore,
            ApplicationEventCatalogService eventCatalogService,
            EventService eventService,
            ModelRegistry modelRegistry,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectUiIconService objectUiIconService,
            ObjectTemplateService objectTemplateService,
            DashboardService dashboardService,
            ReportService reportService,
            WorkflowService workflowService,
            AutomationTreeService automationTreeService,
            DeviceProvisioningService deviceProvisioningService,
            FederationBindService federationBindService,
            DriverRuntimeService driverRuntimeService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            AiToolRegistry aiToolRegistry,
            ApplicationBundleDeployService bundleDeployService,
            OperatorAppUiService operatorAppUiService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            ObjectMapper objectMapper,
            VariableHistoryService variableHistoryService,
            WorkQueueService workQueueService,
            OperatorAgentMemoryService operatorAgentMemoryService,
            OperatorAppDocumentService operatorAppDocumentService,
            HaystackExportService haystackExportService
    ) {
        this.objectMapper = objectMapper;
        List<PlatformAgentTool> tools = new ArrayList<>();
        tools.addAll(AgentKnowledgeTools.all(
                contextPackSearchService,
                driverCatalog,
                applicationDataStore,
                bundleSnapshotStore
        ));
        tools.addAll(AgentReportTools.all(
                reportService,
                objectManager,
                objectAccessService,
                tenantScopeService
        ));
        tools.addAll(AgentDiscoveryTools.all(
                objectManager,
                objectAccessService,
                tenantScopeService,
                applicationFunctionStore,
                eventCatalogService,
                objectMapper
        ));
        tools.addAll(AgentActionTools.all(
                functionService,
                applicationFunctionStore,
                objectManager,
                objectAccessService,
                tenantScopeService,
                eventService,
                modelRegistry,
                haystackExportService,
                objectMapper
        ));
        tools.addAll(AgentAutomationTools.all(
                automationTreeService,
                operatorAppUiService,
                objectManager,
                objectAccessService,
                tenantScopeService,
                bindingRulesService,
                bindingDependencyIndex,
                bindingRuleEngine,
                objectMapper
        ));
        tools.addAll(List.of(
                listObjectsTool(objectManager, objectAccessService, tenantScopeService, objectUiIconService),
                getObjectTool(objectManager, objectAccessService, tenantScopeService, objectUiIconService),
                listVariablesTool(objectManager, objectAccessService, tenantScopeService),
                setVariableTool(objectManager, objectAccessService, objectMapper),
                configureDriverTool(objectManager, objectAccessService, driverRuntimeService, objectMapper),
                driverControlTool(objectAccessService, driverRuntimeService),
                createObjectTool(
                        objectManager,
                        objectAccessService,
                        objectTemplateService,
                        dashboardService,
                        reportService,
                        workflowService,
                        automationTreeService,
                        deviceProvisioningService,
                        federationBindService,
                        objectUiIconService
                ),
                deleteObjectTool(
                        objectManager,
                        objectAccessService,
                        tenantScopeService,
                        driverRuntimeService,
                        platformUserService,
                        platformRoleService,
                        automationTreeService
                ),
                getDashboardLayoutTool(dashboardService, objectAccessService, tenantScopeService),
                setDashboardLayoutTool(dashboardService, objectAccessService, tenantScopeService, objectMapper),
                addDashboardWidgetTool(dashboardService, objectAccessService, tenantScopeService, objectMapper),
                validateBundleTool(objectMapper, aiToolRegistry),
                dryRunDeployTool(objectMapper, aiToolRegistry),
                importPackageTool(objectMapper, bundleDeployService)
        ));
        tools.addAll(AgentOperatorTools.all(
                variableHistoryService,
                workQueueService,
                objectAccessService,
                tenantScopeService,
                operatorAgentMemoryService,
                operatorAppDocumentService
        ));
        Map<String, PlatformAgentTool> index = new LinkedHashMap<>();
        for (PlatformAgentTool tool : tools) {
            index.put(tool.name(), tool);
        }
        this.toolsByName = Map.copyOf(index);
    }

    public List<Map<String, Object>> toolCatalog() {
        return toolCatalog(AgentProfile.ADMIN);
    }

    public List<Map<String, Object>> toolCatalog(AgentProfile profile) {
        return toolsByName.values().stream()
                .filter(tool -> profile != AgentProfile.OPERATOR || OPERATOR_TOOLS.contains(tool.name()))
                .map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description()
                ))
                .toList();
    }

    public Map<String, Object> execute(String toolName, Map<String, Object> arguments, AgentContext context)
            throws Exception {
        if (context.isOperator() && !OPERATOR_TOOLS.contains(toolName)) {
            return Map.of(
                    "status", "ERROR",
                    "error", "Tool not allowed in operator mode: " + toolName
            );
        }
        if (context.isOperator()) {
            try {
                AgentScopeGuard.enforceOperatorScope(toolName, arguments != null ? arguments : Map.of(), context.operatorScope());
            } catch (IllegalArgumentException ex) {
                return Map.of("status", "ERROR", "error", ex.getMessage());
            }
        }
        PlatformAgentTool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return tool.execute(arguments != null ? arguments : Map.of(), context);
    }

    private static PlatformAgentTool listObjectsTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectUiIconService objectUiIconService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_objects";
            }

            @Override
            public String description() {
                return "List child objects under a parent path. Args: parent (default root), lite (bool, default true).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String parent = stringArg(arguments, "parent");
                if (parent.isBlank()) {
                    parent = "root";
                }
                boolean lite = boolArg(arguments, "lite", true);
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(parent, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + parent);
                }
                objectAccessService.requireRead(parent, auth);
                Function<PlatformObject, ObjectDto> mapper = lite
                        ? node -> ObjectDto.fromLite(node, objectUiIconService.readIconId(node).orElse(null))
                        : node -> ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null));
                List<ObjectDto> children = objectManager.tree().childrenOf(parent).stream()
                        .filter(node -> tenantScopeService.isPathVisible(node.path(), auth))
                        .filter(node -> objectAccessService.canRead(node.path(), auth))
                        .map(mapper)
                        .toList();
                return Map.of("status", "OK", "parent", parent, "count", children.size(), "objects", children);
            }
        };
    }

    private static PlatformAgentTool getObjectTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectUiIconService objectUiIconService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_object";
            }

            @Override
            public String description() {
                return "Get one object by path. Args: path (required).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireRead(path, auth);
                PlatformObject node = objectManager.require(path);
                ObjectDto dto = ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null));
                return Map.of("status", "OK", "object", dto);
            }
        };
    }

    private static PlatformAgentTool createObjectTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            ObjectTemplateService objectTemplateService,
            DashboardService dashboardService,
            ReportService reportService,
            WorkflowService workflowService,
            AutomationTreeService automationTreeService,
            DeviceProvisioningService deviceProvisioningService,
            FederationBindService federationBindService,
            ObjectUiIconService objectUiIconService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_object";
            }

            @Override
            public String description() {
                return "Create object tree node. Args: parentPath, name, type (DEVICE|DASHBOARD|CUSTOM|...), "
                        + "displayName, description?, templateId?, driverId?, driverPollIntervalMs?, autoStartDriver?.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String parentPath = stringArg(arguments, "parentPath");
                String name = stringArg(arguments, "name");
                String typeRaw = stringArg(arguments, "type");
                String displayName = stringArg(arguments, "displayName");
                if (parentPath.isBlank() || name.isBlank() || typeRaw.isBlank() || displayName.isBlank()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "parentPath, name, type, displayName are required"
                    );
                }
                ObjectType type = ObjectType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
                var auth = context.authentication();
                objectAccessService.requireWrite(parentPath, auth);
                federationBindService.assertParentAllowsChildren(parentPath);
                String fullPath = objectManager.tree().resolveChildPath(parentPath, name);
                if (objectManager.tree().findByPath(fullPath).isPresent()) {
                    return Map.of("status", "ERROR", "error", "Object exists: " + fullPath);
                }
                String templateId = optionalString(arguments, "templateId");
                String description = optionalString(arguments, "description");
                PlatformObject node = objectManager.create(
                        parentPath,
                        name,
                        type,
                        displayName,
                        description,
                        templateId
                );
                objectTemplateService.applyTemplate(node.path(), templateId);
                if (type == ObjectType.DASHBOARD) {
                    dashboardService.ensureDashboardStructure(node.path());
                }
                if (type == ObjectType.REPORT) {
                    reportService.ensureReportStructure(node.path());
                }
                if (type == ObjectType.WORKFLOW) {
                    workflowService.ensureWorkflowStructure(node.path());
                }
                if (type == ObjectType.ALERT) {
                    automationTreeService.ensureAlertRuleStructure(node.path());
                }
                if (type == ObjectType.CORRELATOR) {
                    automationTreeService.ensureCorrelatorStructure(node.path());
                }
                String driverId = optionalString(arguments, "driverId");
                if (type == ObjectType.DEVICE && driverId != null && !driverId.isBlank()) {
                    deviceProvisioningService.provisionDriver(
                            node.path(),
                            driverId,
                            intArg(arguments, "driverPollIntervalMs", 5000),
                            boolArg(arguments, "autoStartDriver", true)
                    );
                    objectManager.persistNodeTree(node.path());
                }
                PlatformObject saved = objectManager.require(node.path());
                ObjectDto created = ObjectDto.from(saved, objectUiIconService.readIconId(saved).orElse(null));
                return Map.of("status", "OK", "path", node.path(), "object", created);
            }
        };
    }

    private static PlatformAgentTool deleteObjectTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            DriverRuntimeService driverRuntimeService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            AutomationTreeService automationTreeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "delete_object";
            }

            @Override
            public String description() {
                return "Delete object tree node by path (and descendants). Args: path (required). "
                        + "Stops device driver if running. Cannot delete root.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                if ("root".equals(path)) {
                    return Map.of("status", "ERROR", "error", "Cannot delete root object");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    if (objectManager.tree().findByPath(path).isEmpty()) {
                        return Map.of("status", "ERROR", "error", "Object not found: " + path);
                    }
                    if (platformUserService.isSecurityUserPath(path)) {
                        platformUserService.deleteUser(platformUserService.usernameFromPath(path));
                        return Map.of("status", "OK", "path", path, "deleted", true);
                    }
                    if (platformRoleService.isSecurityRolePath(path)) {
                        platformRoleService.deleteRole(platformRoleService.roleNameFromPath(path));
                        return Map.of("status", "OK", "path", path, "deleted", true);
                    }
                    if (objectManager.tree().findByPath(path)
                            .filter(node -> node.type() == ObjectType.CORRELATOR)
                            .isPresent()) {
                        automationTreeService.deleteCorrelator(path);
                        return Map.of("status", "OK", "path", path, "deleted", true);
                    }
                    objectManager.tree().findByPath(path)
                            .filter(node -> node.type() == ObjectType.DEVICE)
                            .ifPresent(node -> driverRuntimeService.stopIfRunning(path));
                    objectManager.delete(path);
                    return Map.of("status", "OK", "path", path, "deleted", true);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool getDashboardLayoutTool(
            DashboardService dashboardService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_dashboard_layout";
            }

            @Override
            public String description() {
                return "Read dashboard layout JSON. Args: path (dashboard object path) OR template "
                        + "(snmp-host-monitoring | demo-sensor | virtual-cluster-overview | virtual-cluster-detail | empty). "
                        + "Call before editing. Returns layoutJson for set_dashboard_layout. "
                        + "Prefer template= over hand-building full layout.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String template = optionalString(arguments, "template");
                try {
                    String layoutJson;
                    String source;
                    if (template != null) {
                        layoutJson = dashboardService.resolveTemplateLayout(template);
                        source = "template:" + template;
                    } else if (!path.isBlank()) {
                        var auth = context.authentication();
                        if (!tenantScopeService.isPathVisible(path, auth)) {
                            return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                        }
                        objectAccessService.requireRead(path, auth);
                        DashboardService.DashboardView view = dashboardService.getDashboard(path);
                        layoutJson = view.layoutJson();
                        source = "path:" + path;
                    } else {
                        return Map.of("status", "ERROR", "error", "path or template is required");
                    }
                    return Map.of(
                            "status", "OK",
                            "source", source,
                            "layoutJson", layoutJson,
                            "templates", DashboardService.layoutTemplateNames(),
                            "hint", "Widgets are inside layout JSON only. Use get_widget_catalog for all types, "
                                    + "set_dashboard_layout or add_dashboard_widget to edit."
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool setDashboardLayoutTool(
            DashboardService dashboardService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "set_dashboard_layout";
            }

            @Override
            public String description() {
                return "Replace dashboard layout variable. Args: path (required), layoutJson (full JSON string) "
                        + "OR template (snmp-host-monitoring | demo-sensor | virtual-cluster-overview | "
                        + "virtual-cluster-detail | empty). Prefer template= for standard screens. "
                        + "Requires DASHBOARD object at path. Never use set_variable for layout.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    String template = optionalString(arguments, "template");
                    DashboardService.DashboardView view;
                    if (template != null) {
                        view = dashboardService.applyTemplateLayout(path, template);
                    } else {
                        String layoutJson = optionalString(arguments, "layoutJson");
                        if (layoutJson == null) {
                            layoutJson = optionalString(arguments, "layout");
                        }
                        if (layoutJson == null) {
                            Object layoutObject = arguments.get("layout");
                            if (layoutObject == null) {
                                layoutObject = arguments.get("layoutJson");
                            }
                            if (layoutObject != null && !(layoutObject instanceof String)) {
                                layoutJson = DashboardWidgetNormalizer.resolveLayoutJson(layoutObject, objectMapper);
                            }
                        }
                        if (layoutJson == null || layoutJson.isBlank()) {
                            return Map.of(
                                    "status", "ERROR",
                                    "error",
                                    "layoutJson or template is required"
                            );
                        }
                        view = dashboardService.saveLayout(path, layoutJson);
                    }
                    return Map.of(
                            "status", "OK",
                            "path", view.path(),
                            "title", view.title(),
                            "widgetCount", widgetCount(objectMapper, view.layoutJson())
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool addDashboardWidgetTool(
            DashboardService dashboardService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "add_dashboard_widget";
            }

            @Override
            public String description() {
                return "Append or replace one widget in layout.widgets[] (by id). Args: path (required), "
                        + "widget (id, type, title, x, y, w, h, variableName, valueField, objectPath OR selectionKey). "
                        + "For 3+ widgets use set_dashboard_layout template= instead of many calls. "
                        + "list_variables on device first; columnsJson must be a JSON string.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                Object widgetRaw = arguments.get("widget");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                if (!(widgetRaw instanceof Map<?, ?> widgetMap) || widgetMap.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "widget object is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    DashboardService.DashboardView view = dashboardService.addWidget(
                            path,
                            (Map<String, Object>) widgetMap
                    );
                    return Map.of(
                            "status", "OK",
                            "path", view.path(),
                            "widgetCount", widgetCount(objectMapper, view.layoutJson())
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static int widgetCount(ObjectMapper objectMapper, String layoutJson) {
        try {
            tools.jackson.databind.JsonNode root = objectMapper.readTree(layoutJson);
            return root.path("widgets").isArray() ? root.path("widgets").size() : 0;
        } catch (Exception ex) {
            return -1;
        }
    }

    private static PlatformAgentTool listVariablesTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_variables";
            }

            @Override
            public String description() {
                return "List variables on an object with current values (preview). Args: path (required).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireRead(path, auth);
                PlatformObject node = objectManager.require(path);
                List<Map<String, Object>> variables = node.variables().values().stream()
                        .map(PlatformAgentToolRegistry::variablePreview)
                        .toList();
                return Map.of("status", "OK", "path", path, "count", variables.size(), "variables", variables);
            }
        };
    }

    private static Map<String, Object> variablePreview(Variable variable) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("name", variable.name());
        preview.put("writable", variable.writable());
        variable.value().ifPresent(record -> {
            if (!record.rows().isEmpty()) {
                preview.put("value", record.firstRow());
            }
        });
        return preview;
    }

    private static PlatformAgentTool setVariableTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "set_variable";
            }

            @Override
            public String description() {
                return "Set variable value on object. Args: path, name, value (string or object map for DataRecord fields).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                if (path.isBlank() || name.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and name are required");
                }
                var auth = context.authentication();
                objectAccessService.requireWrite(path, auth);
                PlatformObject node = objectManager.require(path);
                Variable existing = node.getVariable(name)
                        .orElseThrow(() -> new IllegalArgumentException("Variable not found: " + name));
                if (!existing.writable()) {
                    return Map.of("status", "ERROR", "error", "Variable is not writable: " + name);
                }
                Object rawValue = arguments.containsKey("value")
                        ? arguments.get("value")
                        : arguments.get("valueJson");
                if (rawValue == null) {
                    return Map.of("status", "ERROR", "error", "value or valueJson is required");
                }
                try {
                    DataRecord record = toDataRecord(objectMapper, existing, rawValue);
                    Variable updated = objectManager.setVariableValue(path, name, record);
                    return Map.of(
                            "status", "OK",
                            "path", path,
                            "name", name,
                            "variable", VariableDto.from(updated)
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static DataRecord toDataRecord(ObjectMapper objectMapper, Variable existing, Object rawValue)
            throws Exception {
        if (rawValue instanceof Map<?, ?> map) {
            return DataRecord.single(existing.schema(), (Map<String, Object>) map);
        }
        if (rawValue instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    Map<String, Object> parsed = objectMapper.readValue(trimmed, Map.class);
                    return DataRecord.single(existing.schema(), parsed);
                } catch (Exception ignored) {
                    // fall through to scalar wrap
                }
            }
            return DataRecord.single(existing.schema(), Map.of("value", text));
        }
        return DataRecord.single(existing.schema(), Map.of("value", rawValue));
    }

    private static PlatformAgentTool configureDriverTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            DriverRuntimeService driverRuntimeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_driver";
            }

            @Override
            public String description() {
                return "Configure device driver binding. Args: devicePath, driverId (e.g. snmp), "
                        + "configuration (map or JSON string), pointMappings (map or JSON string), "
                        + "pollIntervalMs?, autoStart? (default true).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String devicePath = stringArg(arguments, "devicePath");
                if (devicePath.isBlank()) {
                    return Map.of("status", "ERROR", "error", "devicePath is required");
                }
                var auth = context.authentication();
                objectAccessService.requireWrite(devicePath, auth);
                try {
                    objectManager.require(devicePath);
                    Map<String, String> configuration = readStringMap(objectMapper, arguments.get("configuration"));
                    Map<String, String> pointMappings = readStringMap(objectMapper, arguments.get("pointMappings"));
                    DriverBinding binding = DriverBinding.of(
                            optionalString(arguments, "driverId") != null
                                    ? stringArg(arguments, "driverId")
                                    : DriverBinding.DEFAULT_DRIVER_ID,
                            intArg(arguments, "pollIntervalMs", 5000),
                            configuration != null ? configuration : Map.of(),
                            pointMappings != null ? pointMappings : Map.of()
                    );
                    driverRuntimeService.configure(devicePath, binding);
                    if (boolArg(arguments, "autoStart", true)) {
                        var status = driverRuntimeService.start(devicePath);
                        return driverStatusMap("OK", status);
                    }
                    return driverRuntimeService.status(devicePath)
                            .map(status -> driverStatusMap("OK", status))
                            .orElse(Map.of("status", "OK", "devicePath", devicePath));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool driverControlTool(
            ObjectAccessService objectAccessService,
            DriverRuntimeService driverRuntimeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "driver_control";
            }

            @Override
            public String description() {
                return "Start/stop/poll/status device driver. Args: devicePath, action (status|start|stop|poll).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String devicePath = stringArg(arguments, "devicePath");
                String action = stringArg(arguments, "action");
                if (devicePath.isBlank() || action.isBlank()) {
                    return Map.of("status", "ERROR", "error", "devicePath and action are required");
                }
                var auth = context.authentication();
                objectAccessService.requireWrite(devicePath, auth);
                try {
                    return switch (action.toLowerCase(Locale.ROOT)) {
                        case "start" -> driverStatusMap("OK", driverRuntimeService.start(devicePath));
                        case "stop" -> driverStatusMap("OK", driverRuntimeService.stop(devicePath));
                        case "poll" -> driverStatusMap("OK", driverRuntimeService.pollNow(devicePath));
                        case "status" -> driverRuntimeService.status(devicePath)
                                .map(status -> driverStatusMap("OK", status))
                                .orElse(Map.of("status", "ERROR", "error", "No driver binding for " + devicePath));
                        default -> Map.of("status", "ERROR", "error", "Unknown action: " + action);
                    };
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Map<String, Object> driverStatusMap(
            String status,
            DriverRuntimeService.DriverRuntimeStatus runtimeStatus
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("devicePath", runtimeStatus.devicePath());
        result.put("driverId", runtimeStatus.driverId());
        result.put("driverStatus", runtimeStatus.status());
        result.put("connected", runtimeStatus.connected());
        result.put("pollIntervalMs", runtimeStatus.pollIntervalMs());
        result.put("lastError", runtimeStatus.lastError());
        result.put("telemetryPublishMode", runtimeStatus.telemetryPublishMode());
        result.put("telemetryCoalesceMs", runtimeStatus.telemetryCoalesceMs());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readStringMap(ObjectMapper objectMapper, Object raw) throws Exception {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            return out;
        }
        if (raw instanceof String text && !text.isBlank()) {
            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                out.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return out;
        }
        throw new IllegalArgumentException("Expected map or JSON string");
    }

    private static PlatformAgentTool validateBundleTool(ObjectMapper objectMapper, AiToolRegistry aiToolRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "validate_bundle";
            }

            @Override
            public String description() {
                return "Semantic validation of bundle manifest (no DB writes). Args: appId, manifest (object).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                Object manifestRaw = arguments.get("manifest");
                if (appId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return Map.of("status", "ERROR", "error", "appId and manifest object are required");
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                Map<String, Object> result = aiToolRegistry.validateBundle(appId, parsed, context.actor());
                if (BundleValidationResult.OK.equals(result.get("status"))) {
                    context.runState().markBundleValidated(appId);
                }
                return result;
            }
        };
    }

    private static PlatformAgentTool dryRunDeployTool(ObjectMapper objectMapper, AiToolRegistry aiToolRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "dry_run_deploy";
            }

            @Override
            public String description() {
                return "Validate bundle and list wouldApply sections. Args: appId, manifest (object).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                Object manifestRaw = arguments.get("manifest");
                if (appId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return Map.of("status", "ERROR", "error", "appId and manifest object are required");
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                Map<String, Object> result = aiToolRegistry.dryRunDeploy(appId, parsed, context.actor());
                if (BundleValidationResult.OK.equals(result.get("status"))) {
                    context.runState().markBundleValidated(appId);
                }
                return result;
            }
        };
    }

    private static PlatformAgentTool importPackageTool(
            ObjectMapper objectMapper,
            ApplicationBundleDeployService bundleDeployService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "import_package";
            }

            @Override
            public String description() {
                return "Deploy bundle to platform (mutates DB). Args: packageId, manifest. "
                        + "Requires prior validate_bundle or dry_run_deploy OK for same packageId in this run.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String packageId = stringArg(arguments, "packageId");
                if (packageId.isBlank()) {
                    packageId = stringArg(arguments, "appId");
                }
                Object manifestRaw = arguments.get("manifest");
                if (packageId.isBlank() || !(manifestRaw instanceof Map<?, ?> manifestMap)) {
                    return Map.of("status", "ERROR", "error", "packageId and manifest object are required");
                }
                if (!context.runState().isBundleValidated(packageId)) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "Run validate_bundle or dry_run_deploy with status OK before import_package"
                    );
                }
                var parsed = BundleManifestJsonSupport.parse(objectMapper, (Map<String, Object>) manifestMap);
                try {
                    Map<String, Object> deployed = bundleDeployService.deploy(packageId, parsed);
                    deployed.put("status", "OK");
                    return deployed;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
