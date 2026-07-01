package com.ispf.server.ai.agent;

import com.ispf.plugin.model.ModelCatalogRoots;
import com.ispf.server.automation.AutomationTreeService;

import java.util.List;
import java.util.Map;

/**
 * Platform object-tree paths and create_object conventions for the agent.
 */
public final class AgentObjectTreeGuide {

    private AgentObjectTreeGuide() {
    }

    public static String referenceText() {
        return """
                ### Object tree (platform paths)
                
                | Path | Type folder | create_object type |
                |------|-------------|-------------------|
                | root.platform.devices | Devices | DEVICE |
                | root.platform.dashboards | Dashboards | DASHBOARD (templateId dashboard-v1) |
                | root.platform.workflows | Workflows | WORKFLOW |
                | root.platform.reports | Reports | REPORT |
                | """
                + "| "
                + AutomationTreeService.ALERT_RULES_ROOT
                + " | Alert rules | ALERT |\n"
                + "| "
                + AutomationTreeService.CORRELATORS_ROOT
                + " | Correlators | CORRELATOR |\n"
                + """
                | root.platform.relative-models | Relative model catalog | MODEL definitions; apply via apply_relative_model |
                | root.platform.instance-types | Instance type catalog | (definitions only) |
                | root.platform.absolute-models | Absolute model catalog | (definitions only) |
                | root.platform.instances | Model instances | CUSTOM from instance type |
                
                Legacy `root.platform.models` removed — use typed catalogs above.
                
                ### Object types & key variables
                
                - **DEVICE**: driverId, driverConfigJson, driverPointMappingsJson; poll via configure_driver / driver_control
                - **DASHBOARD**: title, layout (JSON widgets[]), refreshIntervalMs — NOT a variable named widgets
                - **CUSTOM**: hub for refAt bindings, aggregations, clusterError logic
                - **ALERT**: targetObjectPath, watchVariable, conditionExpr (CEL), eventName — tool configure_alert
                - **CORRELATOR**: patternType COUNT|SEQUENCE|EVENT_CHAIN — tool configure_correlator
                - **WORKFLOW**: BPMN in bundle or platform tree
                - **REPORT**: SQL report definition path for report widget
                
                ### Models & templates
                
                - list_object_models — templateId / modelName; rows include modelType (RELATIVE|INSTANCE|ABSOLUTE)
                - list_relative_models — RELATIVE mixins only (virtual-lab-v1, snmp-agent-v1, …)
                - list_instance_types — INSTANCE blueprints for instantiate_instance_type
                - list_absolute_models — ABSOLUTE blueprints for ensure_absolute_instance
                - get_object_model — variables, events, functions of a blueprint
                - instantiate_instance_type — create object from INSTANCE catalog entry
                - apply_relative_model — attach mixin to existing objectPath (preferred over empty DEVICE + manual vars)
                - ensure_absolute_instance — align/create object by ABSOLUTE model contract
                - Model catalogs: """
                + ModelCatalogRoots.RELATIVE
                + ", "
                + ModelCatalogRoots.INSTANCE
                + ", "
                + ModelCatalogRoots.ABSOLUTE
                + """
                
                - create_variable + create_binding_rule for cross-object data (refAt, CEL)
                - describe_variables before set_variable on existing objects
                
                ### Discovery tools (ground truth — use before create/mutate)
                
                Paths, modelName, profile, variableName must come from tool results in the current turn.
                Playbooks and recipes show patterns only; never copy example paths literally.
                
                ### Canonical order (every object type)
                
                1. `list_objects parent=<exact folder>` — e.g. root.platform.workflows (NOT parent=root for deep paths)
                2. `create_object parentPath=<folder> name=… type=…` — use path from step 1
                3. Configure/save on returned path — never before create_object succeeds
                
                | Type | Parent folder | After create_object |
                |------|---------------|---------------------|
                | WORKFLOW | root.platform.workflows | save_workflow_bpmn → update_workflow_status ACTIVE → run_workflow |
                | MIMIC | root.platform.mimics | save_mimic_diagram / add_mimic_elements |
                | DASHBOARD | root.platform.dashboards | set_dashboard_layout or add_dashboard_widget |
                | DEVICE | root.platform.devices | configure_driver, set_variable, list_variables |
                | ALERT | root.platform.alert-rules | configure_alert |
                | REPORT | root.platform.reports | configure_report |
                
                - list_objects parentPath=… — children of folder
                - get_object path=… — single node metadata
                - search_objects query=… — find by name/path fragment
                - list_variables path=… — current variable values for dashboards/widgets
                """;
    }

    public static Map<String, Object> summary() {
        return Map.of(
                "deviceRoot", "root.platform.devices",
                "dashboardRoot", "root.platform.dashboards",
                "alertRulesRoot", AutomationTreeService.ALERT_RULES_ROOT,
                "correlatorsRoot", AutomationTreeService.CORRELATORS_ROOT,
                "modelCatalogs", List.of(
                        ModelCatalogRoots.RELATIVE,
                        ModelCatalogRoots.INSTANCE,
                        ModelCatalogRoots.ABSOLUTE
                ),
                "legacyRemoved", ModelCatalogRoots.LEGACY,
                "discoveryTools", List.of(
                        "list_objects", "get_object", "search_objects",
                        "list_variables", "list_object_models", "describe_variables",
                        "list_instance_types", "instantiate_instance_type", "ensure_absolute_instance"
                )
        );
    }
}
