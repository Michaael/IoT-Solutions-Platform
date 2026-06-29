package com.ispf.server.ai.agent;

import com.ispf.server.dashboard.DashboardService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Canonical dashboard widget reference for the tree-first agent (types, bindings, key fields).
 */
public final class AgentWidgetCatalog {

    public record WidgetDef(
            String type,
            String binding,
            String purpose,
            List<String> required,
            List<String> keyFields
    ) {
    }

    private static final List<WidgetDef> CATALOG = List.of(
            w("value", "object-variable", "Single metric", List.of("variableName"),
                    "objectPath|selectionKey, valueField, unit, decimals"),
            w("toggle", "object-variable", "Writable boolean toggle", List.of("variableName"),
                    "trueLabel, falseLabel"),
            w("indicator", "object-variable", "Status lamp", List.of("variableName"),
                    "trueLabel, falseLabel, trueColor, falseColor"),
            w("chart", "object-variable", "Historian trend", List.of("variableName"),
                    "historyRange, chartType, maxPoints, unit — needs configure_variable_history"),
            w("sparkline", "object-variable", "Compact trend", List.of("variableName"),
                    "historyRange, maxPoints — needs historyEnabled"),
            w("gauge", "object-variable", "Radial gauge", List.of("variableName"),
                    "minValue, maxValue, minVariable, maxVariable, unit"),
            w("linear-gauge", "object-variable", "Horizontal bar gauge", List.of("variableName"),
                    "minValue, maxValue, minVariable, maxVariable"),
            w("liquid-gauge", "object-variable", "Liquid fill gauge", List.of("variableName"),
                    "minValue, maxValue, minVariable, maxVariable"),
            w("progress", "object-variable", "Ratio of two vars on same object", List.of("currentVariable", "maxVariable"),
                    "unit, decimals"),
            w("status-badge", "object-variable", "Status text badge", List.of(),
                    "variableName (default status)"),
            w("pie-chart", "object-variable", "Pie from RECORD_LIST rows", List.of("variableName"),
                    "labelField, decimals"),
            w("history-table", "object-variable", "Last 5 min historian table", List.of("variableName"),
                    "decimals"),
            w("timer", "object-variable", "Countdown or elapsed", List.of(),
                    "mode countdown|elapsed, durationSeconds, variableName"),
            w("spreadsheet", "object-variable", "Formula spreadsheet grid (A1)", List.of(),
                    "sheetConfigJson, persistMode session|variable, valuesVariable, sessionKey, editable"),
            w("gantt-chart", "object-variable", "Gantt from RECORD_LIST", List.of("variableName"),
                    "labelField, startField, endField"),
            w("network-graph", "object-variable", "Nodes/edges RECORD_LIST", List.of(),
                    "nodesVariable, edgesVariable, labelField"),
            w("svg-widget", "object-variable", "SVG with click actions", List.of("svgUrl"),
                    "clickAction function|toggle, functionName, toggleVariable"),
            w("function", "object-only", "Invoke one BFF/tree function", List.of("functionName"),
                    "objectPath|selectionKey, buttonLabel, inputJson"),
            w("function-form", "object-only", "Form → function invoke", List.of("functionName"),
                    "fieldsJson, buttonLabel"),
            w("variable-editor", "object-only", "Inline variable editor", List.of(),
                    "objectPath|selectionKey, variablesJson"),
            w("input-form", "object-only", "Generic input form", List.of(),
                    "fieldsJson, buttonLabel"),
            w("object-table", "parent-catalog", "Children table + selection publisher", List.of("parentPath"),
                    "columnsJson, selectionKey, rowTargetDashboard, rowOpenMode, rowSelectionKey"),
            w("card-grid", "parent-catalog", "Child object cards", List.of("parentPath"),
                    "variablesJson, cardTargetDashboard, cardOpenMode, cardSelectionKey"),
            w("map", "parent-catalog", "Map markers for folder children", List.of("parentPath"),
                    "latVariable, latField, lonField, zoom, tileUrl, rowTargetDashboard"),
            w("object-tree", "parent-catalog", "Subtree navigator", List.of(),
                    "parentPath, maxDepth"),
            w("label", "session", "Static or param text", List.of(),
                    "text, textJson, paramKey"),
            w("breadcrumbs", "session", "Path breadcrumbs", List.of(),
                    "pathKey, separator"),
            w("context-list", "session", "Shows session selection/params", List.of(), ""),
            w("image", "static", "Image URL", List.of(),
                    "imageUrl, alt"),
            w("html-snippet", "static", "Embedded HTML", List.of(),
                    "htmlJson"),
            w("report", "external", "SQL report table", List.of("reportPath"),
                    "parametersJson, contextParamsJson, emptyMessage, showCsv, showPdf, showXlsx, showHtml"),
            w("dashboard-link", "external", "Navigate/modal to dashboard", List.of("targetDashboardPath"),
                    "openMode navigate|modal, buttonLabel, contextSelectionJson"),
            w("sub-dashboard", "external", "Embed dashboard", List.of(),
                    "targetDashboardPath, targetDashboardPathKey, inheritContext"),
            w("event-feed", "external", "Platform events stream", List.of(),
                    "objectPathPrefix, eventNamesJson, payloadFilterExpr, maxItems"),
            w("work-queue", "external", "Operator work queue", List.of(),
                    "operatorId, maxItems"),
            w("composite-widget", "composition", "Nested widgets JSON", List.of(),
                    "childrenJson"),
            w("panel", "composition", "Collapsible panel", List.of(),
                    "childrenJson, collapsible, variant"),
            w("tab-panel", "composition", "Tabbed widgets", List.of(),
                    "tabsJson"),
            w("drawer-panel", "composition", "Side drawer", List.of(),
                    "childrenJson, drawerLabel"),
            w("carousel", "composition", "Slides carousel", List.of(),
                    "slidesJson, autoplayMs"),
            w("steps-panel", "composition", "Wizard steps", List.of(),
                    "stepsJson, activeStepKey"),
            w("nav-menu", "composition", "Navigation menu", List.of(),
                    "itemsJson [{label,dashboardPath}]"),
            w("scada-mimic", "static", "SCADA mimic diagram (symbols + live bindings)", List.of(),
                    "diagramJson, mimicPath, defaultZoom, panEnabled"),
            w("mini-tec-sld", "composition", "Legacy mini-TEC SLD widget (prefer scada-mimic)", List.of(), "")
    );

    private AgentWidgetCatalog() {
    }

    private static WidgetDef w(
            String type,
            String binding,
            String purpose,
            List<String> required,
            String keyFieldsCsv
    ) {
        List<String> keyFields = keyFieldsCsv == null || keyFieldsCsv.isBlank()
                ? List.of()
                : List.of(keyFieldsCsv.split(",\\s*"));
        return new WidgetDef(type, binding, purpose, required, keyFields);
    }

    public static List<WidgetDef> all() {
        return CATALOG;
    }

    public static Map<String, Object> catalogResponse(String typeFilter, String bindingFilter) {
        String typeQ = typeFilter == null ? "" : typeFilter.trim().toLowerCase(Locale.ROOT);
        String bindQ = bindingFilter == null ? "" : bindingFilter.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> widgets = new ArrayList<>();
        for (WidgetDef def : CATALOG) {
            if (!typeQ.isEmpty() && !def.type().equalsIgnoreCase(typeQ)) {
                continue;
            }
            if (!bindQ.isEmpty() && !def.binding().equalsIgnoreCase(bindQ)) {
                continue;
            }
            widgets.add(toMap(def));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("count", widgets.size());
        result.put("widgets", widgets);
        result.put("fieldSemantics", AgentWidgetPropertiesGuide.fieldSemantics());
        result.put("bindings", bindingGuide());
        result.put("commonFields", List.of(
                "id", "type", "title", "x", "y", "w", "h",
                "objectPath", "selectionKey", "contextPathKey", "modelHintPath",
                "variableName", "valueField", "paramKey", "stylesJson"
        ));
        result.put("layoutVariable", "layout");
        result.put("layoutShape", Map.of(
                "columns", 12,
                "rowHeight", 72,
                "widgets", "array of widget objects"
        ));
        result.put("templates", DashboardService.layoutTemplateNames());
        result.put("selectionPattern",
                "object-table parentPath publishes selectionKey; consumers with same selectionKey read selected path");
        result.put("workflow", AgentDashboardGuide.summary().get("workflow"));
        result.put("antiPatterns", AgentDashboardGuide.summary().get("antiPatterns"));
        if (!typeQ.isEmpty() && widgets.size() == 1) {
            result.put("propertySpec", AgentWidgetPropertiesGuide.propertiesForType(typeQ));
        }
        if (typeQ.isEmpty() && bindQ.isEmpty()) {
            result.put("propertyGuideHint",
                    "Call get_widget_catalog type=<widgetType> for full field list per type");
        }
        result.put("tools", List.of(
                "get_widget_catalog",
                "get_automation_schema topic=dashboard",
                "get_dashboard_layout",
                "set_dashboard_layout",
                "add_dashboard_widget"
        ));
        return result;
    }

    public static Map<String, Object> catalogSummary() {
        Map<String, List<String>> byBinding = CATALOG.stream()
                .collect(Collectors.groupingBy(
                        WidgetDef::binding,
                        LinkedHashMap::new,
                        Collectors.mapping(WidgetDef::type, Collectors.toList())
                ));
        return Map.of(
                "widgetCount", CATALOG.size(),
                "byBinding", byBinding,
                "templates", DashboardService.layoutTemplateNames(),
                "layoutVariable", "layout"
        );
    }

    public static String referenceText() {
        StringBuilder sb = new StringBuilder();
        sb.append("### Widget catalog (").append(CATALOG.size()).append(" types)\n");
        sb.append("Layout: DASHBOARD variable `layout` JSON {columns, rowHeight, widgets[]}.\n");
        sb.append("Never set_variable name=widgets. Grid: x,y,w,h (12 columns).\n\n");
        sb.append("#### Data bindings\n");
        for (Map.Entry<String, String> entry : bindingGuide().entrySet()) {
            sb.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append('\n');
        }
        sb.append("\n#### Types by binding\n");
        String currentBinding = "";
        for (WidgetDef def : CATALOG) {
            if (!def.binding().equals(currentBinding)) {
                currentBinding = def.binding();
                sb.append("\n**").append(currentBinding).append("**\n");
            }
            sb.append("- `").append(def.type()).append("`: ").append(def.purpose());
            if (!def.required().isEmpty()) {
                sb.append(" [req: ").append(String.join(", ", def.required())).append(']');
            }
            if (!def.keyFields().isEmpty()) {
                sb.append(" — ").append(String.join("; ", def.keyFields()));
            }
            sb.append('\n');
        }
        sb.append("\n#### Drill-down & navigation\n");
        sb.append("- object-table/card-grid/map: parentPath lists children; selectionKey publishes click path\n");
        sb.append("- rowTargetDashboard + rowOpenMode on object-table/map; cardTargetDashboard on card-grid\n");
        sb.append("- dashboard-link: targetDashboardPath + openMode navigate|modal\n");
        sb.append("- sub-dashboard: embed targetDashboardPath; inheritContext passes session\n");
        sb.append("- chart/sparkline/history-table: configure_variable_history historyEnabled=true first\n");
        sb.append("\n#### Templates (set_dashboard_layout template=...)\n");
        for (String template : DashboardService.layoutTemplateNames()) {
            sb.append("- ").append(template).append('\n');
        }
        sb.append('\n');
        sb.append(AgentWidgetPropertiesGuide.referenceText());
        return sb.toString();
    }

    private static Map<String, String> bindingGuide() {
        Map<String, String> guide = new LinkedHashMap<>();
        guide.put("object-variable",
                "objectPath OR selectionKey + variableName + valueField (default value); reads DEVICE/CUSTOM vars");
        guide.put("object-only",
                "objectPath OR selectionKey; functionName/fieldsJson on widget — no variableName binding");
        guide.put("parent-catalog",
                "parentPath → GET /objects?parent=…; optional selectionKey for click → session");
        guide.put("session", "paramKey/pathKey from dashboard session.params; label/breadcrumbs/context-list");
        guide.put("static", "imageUrl, htmlJson, text — no live object binding");
        guide.put("external", "reportPath, targetDashboardPath, event feed, work-queue paths");
        guide.put("composition", "childrenJson, tabsJson, slidesJson, itemsJson — nested UI");
        return guide;
    }

    private static Map<String, Object> toMap(WidgetDef def) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", def.type());
        row.put("binding", def.binding());
        row.put("purpose", def.purpose());
        row.put("required", def.required());
        row.put("keyFields", def.keyFields());
        Map<String, Object> spec = AgentWidgetPropertiesGuide.propertiesForType(def.type());
        if (!spec.isEmpty() && !spec.containsKey("error")) {
            row.put("fields", spec.get("fields"));
            if (spec.containsKey("notes")) {
                row.put("notes", spec.get("notes"));
            }
        }
        return row;
    }
}
