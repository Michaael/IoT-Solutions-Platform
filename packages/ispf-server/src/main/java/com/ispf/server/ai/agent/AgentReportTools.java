package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.bootstrap.LabModelBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.report.ReportService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent tools for tree-first REPORT objects ({@link ReportService#REPORTS_ROOT}).
 */
final class AgentReportTools {

    private AgentReportTools() {
    }

    static List<PlatformAgentTool> all(
            ReportService reportService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return List.of(
                listReportsTool(reportService, objectManager, objectAccessService, tenantScopeService),
                getReportSchemaTool(reportService, objectAccessService, tenantScopeService),
                runReportTool(reportService, objectAccessService, tenantScopeService),
                configureReportTool(reportService, objectManager, objectAccessService, tenantScopeService)
        );
    }

    private static PlatformAgentTool listReportsTool(
            ReportService reportService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_reports";
            }

            @Override
            public String description() {
                return "List REPORT objects under root.platform.reports. "
                        + "Optional args: query (substring filter on path/title).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                String root = ReportService.REPORTS_ROOT;
                if (!tenantScopeService.isPathVisible(root, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + root);
                }
                objectAccessService.requireRead(root, auth);
                reportService.ensureReportsCatalog();
                String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (PlatformObject child : objectManager.tree().childrenOf(root)) {
                    if (child.type() != ObjectType.REPORT) {
                        continue;
                    }
                    if (!tenantScopeService.isPathVisible(child.path(), auth)) {
                        continue;
                    }
                    try {
                        objectAccessService.requireRead(child.path(), auth);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    ReportService.ReportView view = reportService.getReport(child.path());
                    String haystack = (view.path() + " " + view.title() + " " + view.reportType()).toLowerCase(Locale.ROOT);
                    if (!query.isBlank() && !haystack.contains(query)) {
                        continue;
                    }
                    rows.add(reportSummary(view));
                }
                return Map.of("status", "OK", "count", rows.size(), "reports", rows);
            }
        };
    }

    private static PlatformAgentTool getReportSchemaTool(
            ReportService reportService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_report_schema";
            }

            @Override
            public String description() {
                return "Load REPORT definition. Args: path (root.platform.reports.*). "
                        + "Returns columns, parameters, reportType, hasTemplate, exportFormats, yargPlaceholders.";
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
                ReportService.ReportView view = reportService.getReport(path);
                Map<String, Object> report = reportDetail(view);
                return Map.of("status", "OK", "report", report);
            }
        };
    }

    private static PlatformAgentTool runReportTool(
            ReportService reportService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "run_report";
            }

            @Override
            public String description() {
                return "Execute REPORT preview. Args: path, optional parameters (object map). "
                        + "Returns columns, rows, rowCount, truncated.";
            }

            @Override
            @SuppressWarnings("unchecked")
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
                Map<String, Object> parameters = Map.of();
                Object raw = arguments.get("parameters");
                if (raw instanceof Map<?, ?> map) {
                    parameters = (Map<String, Object>) map;
                }
                Map<String, Object> result = reportService.run(path, parameters);
                return Map.of("status", "OK", "path", path, "result", result);
            }
        };
    }

    private static PlatformAgentTool configureReportTool(
            ReportService reportService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_report";
            }

            @Override
            public String description() {
                return "Create or update REPORT under root.platform.reports. "
                        + "Args: path OR reportId, reportType (sql|tree-variables). "
                        + "SQL: title, dataSourcePath, query, parameters[], columns[{field,label}], "
                        + "defaultParameters, maxRows. "
                        + "tree-variables: title, devicePathPattern, variableName, columns[], maxRows. "
                        + "Call get_automation_schema topic=report for playbooks and YARG rules.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                try {
                    String path = stringArg(arguments, "path");
                    String reportId = stringArg(arguments, "reportId");
                    if (path.isBlank() && !reportId.isBlank()) {
                        path = ReportService.reportPath(reportId);
                    }
                    String reportType = stringArg(arguments, "reportType").toLowerCase(Locale.ROOT);
                    boolean treeVariables = "tree-variables".equals(reportType)
                            || "tree_variables".equals(reportType);

                    if (path.isBlank()) {
                        return Map.of("status", "ERROR", "error", "path or reportId is required");
                    }
                    if (!tenantScopeService.isPathVisible(path, auth)) {
                        return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                    }

                    reportService.ensureReportsCatalog();
                    boolean created = objectManager.tree().findByPath(path).isEmpty();
                    if (created) {
                        objectAccessService.requireWrite(ReportService.REPORTS_ROOT, auth);
                        String nodeName = ReportService.reportIdFromPath(path);
                        String title = stringArg(arguments, "title");
                        if (title.isBlank()) {
                            title = nodeName;
                        }
                        String templateId = treeVariables
                                ? LabModelBootstrap.TREE_VARIABLES_REPORT_MODEL
                                : "report-v1";
                        objectManager.create(
                                ReportService.REPORTS_ROOT,
                                nodeName,
                                ObjectType.REPORT,
                                title,
                                optionalString(arguments, "description"),
                                templateId
                        );
                        if (treeVariables) {
                            reportService.ensureTreeVariablesReportStructure(path);
                        } else {
                            reportService.ensureReportStructure(path);
                        }
                    }

                    objectAccessService.requireWrite(path, auth);
                    ReportService.ReportView current = reportService.getReport(path);
                    if (reportType.isBlank()) {
                        treeVariables = LabModelBootstrap.TREE_VARIABLES_REPORT_TYPE.equals(current.reportType())
                                || !stringArg(arguments, "devicePathPattern").isBlank();
                    }

                    if (treeVariables) {
                        String devicePathPattern = optionalString(arguments, "devicePathPattern");
                        String variableName = optionalString(arguments, "variableName");
                        if (devicePathPattern == null && variableName == null) {
                            return Map.of(
                                    "status", "OK",
                                    "path", path,
                                    "created", created,
                                    "report", reportDetail(current),
                                    "hint", "Provide devicePathPattern and variableName to update tree-variables report"
                            );
                        }
                        ReportService.ReportView saved = reportService.saveTreeVariablesDefinition(
                                path,
                                new ReportService.SaveTreeVariablesDefinitionRequest(
                                        optionalString(arguments, "title"),
                                        devicePathPattern != null ? devicePathPattern : current.devicePathPattern(),
                                        variableName != null ? variableName : current.variableName(),
                                        parseColumns(arguments.get("columns"), current.columns()),
                                        intArg(arguments, "maxRows", null),
                                        intArg(arguments, "refreshIntervalMs", null)
                                )
                        );
                        return Map.of(
                                "status", "OK",
                                "path", path,
                                "created", created,
                                "report", reportDetail(saved)
                        );
                    }

                    String query = optionalString(arguments, "query");
                    if (query == null) {
                        return Map.of(
                                "status", "OK",
                                "path", path,
                                "created", created,
                                "report", reportDetail(current),
                                "hint", "Provide query (+ dataSourcePath for new SQL reports) to update SQL report"
                        );
                    }

                    String dataSourcePath = optionalString(arguments, "dataSourcePath");
                    if (dataSourcePath == null) {
                        dataSourcePath = current.dataSourcePath();
                    }
                    ReportService.ReportView saved = reportService.saveDefinition(
                            path,
                            new ReportService.SaveReportDefinitionRequest(
                                    optionalString(arguments, "title"),
                                    dataSourcePath,
                                    optionalString(arguments, "appId"),
                                    query,
                                    parseStringList(arguments.get("parameters"), current.parameters()),
                                    parseColumns(arguments.get("columns"), current.columns()),
                                    parseParametersMap(arguments.get("defaultParameters"), current.defaultParameters()),
                                    intArg(arguments, "maxRows", null),
                                    intArg(arguments, "refreshIntervalMs", null),
                                    optionalString(arguments, "layout")
                            )
                    );
                    return Map.of(
                            "status", "OK",
                            "path", path,
                            "created", created,
                            "report", reportDetail(saved)
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    static Map<String, Object> reportSummary(ReportService.ReportView view) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", view.path());
        row.put("title", view.title());
        row.put("reportType", view.reportType());
        row.put("hasTemplate", view.hasTemplate());
        row.put("templateFormat", view.templateFormat());
        return row;
    }

    static Map<String, Object> reportDetail(ReportService.ReportView view) {
        Map<String, Object> report = new LinkedHashMap<>(reportSummary(view));
        report.put("dataSourcePath", view.dataSourcePath());
        report.put("query", view.query());
        report.put("devicePathPattern", view.devicePathPattern());
        report.put("variableName", view.variableName());
        report.put("parameters", view.parameters());
        report.put("columns", view.columns().stream()
                .map(col -> Map.of("field", col.field(), "label", col.label()))
                .toList());
        report.put("defaultParameters", view.defaultParameters());
        report.put("maxRows", view.maxRows());
        report.put("refreshIntervalMs", view.refreshIntervalMs());
        report.put("exportFormats", exportFormats(view));
        report.put("yargPlaceholders", yargPlaceholders(view));
        report.put("openInUi", "Report Builder → " + view.path());
        return report;
    }

    static List<String> exportFormats(ReportService.ReportView view) {
        List<String> formats = new ArrayList<>();
        formats.add("csv");
        if (view.hasTemplate()) {
            formats.add("pdf");
            formats.add("xlsx");
            formats.add("html");
            String templateFormat = view.templateFormat() != null ? view.templateFormat().toLowerCase(Locale.ROOT) : "";
            if ("xls".equals(templateFormat)) {
                formats.add("xls");
            }
        } else {
            formats.add("xlsx");
            formats.add("html");
        }
        return formats;
    }

    static List<String> yargPlaceholders(ReportService.ReportView view) {
        return view.columns().stream()
                .map(col -> "${" + col.field().toUpperCase(Locale.ROOT) + "}")
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<ReportService.ReportColumn> parseColumns(Object raw, List<ReportService.ReportColumn> fallback) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return fallback;
        }
        List<ReportService.ReportColumn> columns = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String field = stringValue(map.get("field"));
                String label = stringValue(map.get("label"));
                if (!field.isBlank()) {
                    columns.add(new ReportService.ReportColumn(field, label.isBlank() ? field : label));
                }
            }
        }
        return columns.isEmpty() ? fallback : columns;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object raw, List<String> fallback) {
        if (!(raw instanceof List<?> list)) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item).trim());
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseParametersMap(Object raw, Map<String, Object> fallback) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return fallback;
    }

    private static Integer intArg(Map<String, Object> args, String key, Integer defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(text);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        return stringValue(args.get(key));
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
