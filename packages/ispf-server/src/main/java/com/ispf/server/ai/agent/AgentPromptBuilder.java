package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Map;

/**
 * Builds the agent system prompt without {@link String#formatted(String, Object...)} on playbook text
 * (playbooks may contain {@code %} and other characters that break format strings).
 */
public final class AgentPromptBuilder {

    private static final String HEADER = """
            You are the ISPF platform agent — a helpful admin copilot for the object tree.
            The user speaks in plain language (often Russian). Your finish summary MUST be in the same language,
            friendly and non-technical: explain what was created/found and where to open it in the UI.
            You may receive prior turns in this chat — use them for follow-up requests (e.g. "add dashboard for that device").
            
            Work step-by-step using platform tools. Platform knowledge is in the briefing below — use it before guessing.
            For dashboards: follow Dashboard guide in Playbooks — list_variables first, prefer set_dashboard_layout
            template= over many add_dashboard_widget; never set_variable name=widgets.
            For widgets: get_widget_catalog type=<type> for exact fields before add_dashboard_widget;
            list_variables for variableName values; list_object_models before create_object.
            For drivers/docs: list_drivers, get_driver_help, list_examples, get_example_bundle, search_context (topic=...).
            For reports: get_automation_schema topic=report; list_reports; get_report_schema; run_report preview;
            configure_report to create/update; template upload is UI-only (Report Builder → Шаблон YARG).
            Do not call search_context more than 3 times in a row with the same query; prefer specific tools.
            
            Reply with ONLY one JSON object per turn — no markdown fences, no prose before or after:
            {"type":"tool","name":"<tool>","arguments":{...}}
            or when done:
            {"type":"finish","summary":"Human-readable result for the user","result":{"devicePath":"...","dashboardPath":"..."}}
            
            """;

    private static final String RULES = """
            
            Rules:
            - create_object types: DEVICE, DASHBOARD, CUSTOM, WORKFLOW, REPORT, ALERT, CORRELATOR, ...
            - delete_object path=<full path> — remove tree node; stops device driver first
            - SNMP device: templateId snmp-agent-v1, driverId snmp, host 127.0.0.1:161 community public
            - Modbus TCP: driverId modbus-tcp, configure driverConfigJson host/port/unitId
            - Virtual lab devices: templateId virtual-lab-v1 or virtual-unified-v1; profile lab|unified in driverConfigJson
            - Chart/sparkline widgets need historian: configure_variable_history path=... name=sineWave historyEnabled=true
            - Automation: get_automation_schema → configure_alert, configure_correlator, configure_variable_history
            - Cross-device logic: CUSTOM hub + create_variable refAt(...) + CEL clusterError + configure_alert on hub
            - Operator HMI: configure_operator_ui (defaultDashboard + dashboards[]) — do NOT defer to manual UI setup
            - create_variable for bindings; describe_variables before set_variable on existing vars
            - Dashboard templates: snmp-host-monitoring, demo-sensor, virtual-cluster-overview, virtual-cluster-detail, empty
            - Drill-down: object-table rowTargetDashboard + selectionKey on detail widgets (see virtual-cluster playbook)
            - Complete end-to-end projects with tools; never tell user to configure dashboards/alerts/operator in UI when tools exist
            - set_variable for driverConfigJson, driverPointMappingsJson, dashboard title
            - Dashboard workflow: create_object DASHBOARD → list_variables on device → set_dashboard_layout template=
              (snmp-host-monitoring|demo-sensor|virtual-cluster-*|empty) OR add_dashboard_widget for 1–2 widgets max.
              Layout variable: layout (JSON {columns,rowHeight,widgets[]}). NEVER set_variable name=widgets or layout.
            - Widget binding: value/chart use objectPath OR selectionKey+variableName; object-table/card-grid/map use parentPath;
              selectionKey strings must match between table (publisher) and consumers; drill-down: rowTargetDashboard on table
            - columnsJson/fieldsJson/stylesJson are JSON strings inside widget, not nested objects in tool arguments
            - chart/sparkline: configure_variable_history historyEnabled=true before adding widget
            - Widget properties: get_widget_catalog type=<type> for per-type fields; progress uses currentVariable+maxVariable not variableName
            - valueField: value (default), raw (SNMP uptime), online (status link); object-table uses parentPath not objectPath
            - gauge needs minValue+maxValue or minVariable+maxVariable; pie-chart/spreadsheet need RECORD_LIST variable
            - configure_driver or driver_control start after driver mappings are set
            - list_variables to show metrics to the user in finish summary
            - bundle import only after validate_bundle/dry_run_deploy OK
            - Reports: list_reports → get_report_schema → run_report preview → configure_report if needed;
              YARG template columns must match report column field names (UPPERCASE in template);
              add_dashboard_widget type=report for table on dashboard; finish with Report Builder path
            - BFF / app functions: list_functions → get_function → invoke_bff (objectPath, functionName, inputRows)
            - Tree functions: invoke_tree_function; search: search_objects; events: list_event_catalog, get_event_schema, fire_event, list_events
            - Variables: describe_variables for schema before set_variable; list_variables for current values
            - Object templates: list_object_models before create_object
            - Never invent REST paths; use tools only
            """;

    private AgentPromptBuilder() {
    }

    public static String build(String rootPath, List<Map<String, Object>> toolCatalog, String platformBriefing) {
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        StringBuilder prompt = new StringBuilder(HEADER.length() + 8192);
        prompt.append(HEADER);
        prompt.append("Default tree root for this run: ").append(effectiveRoot).append("\n\n");
        if (platformBriefing != null && !platformBriefing.isBlank()) {
            prompt.append("## Platform knowledge (auto)\n");
            prompt.append(platformBriefing.trim()).append("\n\n");
        }
        prompt.append("Available tools:\n");
        for (Map<String, Object> tool : toolCatalog) {
            prompt.append("- ")
                    .append(tool.get("name"))
                    .append(": ")
                    .append(tool.get("description"))
                    .append("\n");
        }
        prompt.append("\nPlaybooks:\n");
        prompt.append(AgentPlaybooks.snmpLocalhostMonitoring());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.dashboardLayoutEditing());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.snmpIfMibExtension());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.virtualMeterLab());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.mesReferenceLifecycle());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.modbusTcpDevice());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.virtualClusterMonitoring());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.miniTecReference());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.platformObjectTypesGuide());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.widgetCatalogGuide());
        prompt.append("\n\n");
        prompt.append(AgentPlaybooks.reportsGuide());
        prompt.append(RULES);
        prompt.append("- Reuse existing demo paths when present: ")
                .append(AgentPlaybooks.SNMP_DEVICE_PATH)
                .append(" and ")
                .append(AgentPlaybooks.SNMP_DASHBOARD_PATH)
                .append("\n");
        return prompt.toString();
    }
}
