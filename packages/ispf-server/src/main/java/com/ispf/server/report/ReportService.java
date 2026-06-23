package com.ispf.server.report;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.report.ApplicationReportStore;
import com.ispf.server.bootstrap.LabModelBootstrap;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.object.ObjectManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ReportService {

    public static final String REPORTS_ROOT = "root.platform.reports";
    public static final String REPORT_TYPE_TREE_VARIABLES = LabModelBootstrap.TREE_VARIABLES_REPORT_TYPE;

    private static final List<ReportColumn> DEFAULT_TREE_VARIABLE_COLUMNS = List.of(
            new ReportColumn("devicepath", "Device path"),
            new ReportColumn("int", "Int"),
            new ReportColumn("string", "String")
    );

    private static final Pattern FORBIDDEN_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|CALL|EXEC|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ApplicationSchemaSession schemaSession;
    private final ApplicationReportStore reportStore;
    private final ReportTemplateStore templateStore;
    private final DataSourcePathResolver dataSourcePathResolver;
    private final DataSourceObjectService dataSourceObjectService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReportService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ApplicationSchemaSession schemaSession,
            ApplicationReportStore reportStore,
            ReportTemplateStore templateStore,
            DataSourcePathResolver dataSourcePathResolver,
            DataSourceObjectService dataSourceObjectService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.schemaSession = schemaSession;
        this.reportStore = reportStore;
        this.templateStore = templateStore;
        this.dataSourcePathResolver = dataSourcePathResolver;
        this.dataSourceObjectService = dataSourceObjectService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public static String reportPath(String reportId) {
        return REPORTS_ROOT + "." + sanitizeReportNodeName(reportId);
    }

    private static String sanitizeReportNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "node";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "node";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "n_" + sanitized;
        }
        return sanitized;
    }

    public static String reportIdFromPath(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    @Transactional
    public void ensureReportsCatalog() {
        if (objectManager.tree().findByPath(REPORTS_ROOT).isEmpty()) {
            objectManager.create(
                    "root.platform",
                    "reports",
                    ObjectType.REPORTS,
                    "Reports",
                    "SQL reports (tree-first)",
                    null
            );
        } else {
            objectManager.reconcileType(REPORTS_ROOT, ObjectType.REPORTS);
        }
    }

    @Transactional
    public void ensureReportStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        if (node.getVariable("query").isPresent() || node.getVariable("reportType").isPresent()) {
            return;
        }
        modelRegistry.findByName("report-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    @Transactional
    public void ensureTreeVariablesReportStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        if (node.getVariable("reportType").isPresent()) {
            return;
        }
        modelRegistry.findByName(LabModelBootstrap.TREE_VARIABLES_REPORT_MODEL).ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    @Transactional
    public void deploy(
            String dataSourcePath,
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows,
            Map<String, Object> defaultParameters
    ) {
        validateDataSourcePath(dataSourcePath);
        validateSelectQuery(query);
        ensureReportsCatalog();
        String path = reportPath(reportId);
        ensureReportNode(path, title, description, "report-v1");
        ensureReportStructure(path);
        saveDefinitionInternal(
                path,
                new ReportDefinition(
                        title,
                        dataSourcePath,
                        query.trim(),
                        parameters != null ? parameters : List.of(),
                        columns != null ? columns : List.of(),
                        defaultParameters != null ? defaultParameters : Map.of(),
                        maxRows != null && maxRows > 0 ? maxRows : 1000,
                        30000,
                        ""
                )
        );
    }

    @Transactional
    public void deployTreeVariables(
            String reportId,
            String title,
            String description,
            String devicePathPattern,
            String variableName,
            List<ReportColumn> columns,
            Integer maxRows
    ) {
        ensureReportsCatalog();
        String path = reportPath(reportId);
        ensureReportNode(path, title, description, LabModelBootstrap.TREE_VARIABLES_REPORT_MODEL);
        ensureTreeVariablesReportStructure(path);
        saveTreeVariablesDefinitionInternal(
                path,
                title,
                devicePathPattern,
                variableName,
                columns != null && !columns.isEmpty() ? columns : DEFAULT_TREE_VARIABLE_COLUMNS,
                maxRows != null && maxRows > 0 ? maxRows : 1000,
                30000,
                ""
        );
    }
    @Deprecated
    @Transactional
    public void deployLegacyApp(
            String appId,
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows,
            Map<String, Object> defaultParameters
    ) {
        String dataSourcePath = dataSourceObjectService.pathForNodeName(appId);
        dataSourceObjectService.ensureDataSource(appId, appId, inferSchemaForApp(appId), "Legacy app data source");
        deploy(dataSourcePath, reportId, title, description, query, parameters, columns, maxRows, defaultParameters);
    }

    public ReportView getReport(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        return toView(path, node);
    }

    @Transactional
    public ReportView saveDefinition(String path, SaveReportDefinitionRequest request) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        validateSelectQuery(request.query());
        ReportView current = toView(path, node);
        String dataSourcePath = resolveDataSourcePathForSave(request, current);
        validateDataSourcePath(dataSourcePath);
        ReportDefinition definition = new ReportDefinition(
                request.title() != null ? request.title() : current.title(),
                dataSourcePath,
                request.query().trim(),
                request.parameters() != null ? request.parameters() : current.parameters(),
                request.columns() != null ? request.columns() : current.columns(),
                request.defaultParameters() != null ? request.defaultParameters() : current.defaultParameters(),
                request.maxRows() != null ? request.maxRows() : current.maxRows(),
                request.refreshIntervalMs() != null ? request.refreshIntervalMs() : current.refreshIntervalMs(),
                request.layout() != null ? request.layout() : current.layout()
        );
        saveDefinitionInternal(path, definition);
        return getReport(path);
    }

    @Transactional
    public ReportView saveTreeVariablesDefinition(
            String path,
            SaveTreeVariablesDefinitionRequest request
    ) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        ensureTreeVariablesReportStructure(path);
        ReportView current = getReport(path);
        String devicePathPattern = request.devicePathPattern() != null
                ? request.devicePathPattern().trim()
                : "";
        String variableName = request.variableName() != null ? request.variableName().trim() : "";
        if (devicePathPattern.isBlank()) {
            throw new IllegalArgumentException("Report devicePathPattern is required for tree-variables reports");
        }
        if (variableName.isBlank()) {
            throw new IllegalArgumentException("Report variableName is required for tree-variables reports");
        }
        List<ReportColumn> columns = request.columns() != null && !request.columns().isEmpty()
                ? request.columns()
                : current.columns();
        saveTreeVariablesDefinitionInternal(
                path,
                request.title() != null ? request.title() : current.title(),
                devicePathPattern,
                variableName,
                columns,
                request.maxRows() != null ? request.maxRows() : current.maxRows(),
                request.refreshIntervalMs() != null ? request.refreshIntervalMs() : current.refreshIntervalMs(),
                current.layout()
        );
        return getReport(path);
    }

    @Transactional
    public ReportView saveLayout(String path, String layoutJson) {
        getReport(path);
        setString(path, "layout", layoutJson != null ? layoutJson : "");
        return getReport(path);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(String path, Map<String, Object> parameters) {
        ReportView report = getReport(path);
        return runDefinition(report, parameters);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> runByApp(String appId, String reportId, Map<String, Object> parameters) {
        String path = reportPath(reportId);
        if (objectManager.tree().findByPath(path).isEmpty()) {
            return runLegacy(appId, reportId, parameters);
        }
        ReportView report = getReport(path);
        return runDefinition(report, parameters);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return toCsv(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportHtmlTable(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return toHtmlTable(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsxTable(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return toXlsxTable(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsTable(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return toXlsTable(result);
    }

    @Transactional(readOnly = true)
    public boolean hasTemplate(String path) {
        getReport(path);
        return templateStore.exists(path);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsvByApp(String appId, String reportId, Map<String, Object> parameters) {
        Map<String, Object> result = runByApp(appId, reportId, parameters);
        return toCsv(result);
    }

    @Transactional
    public ReportView saveTemplate(String path, String format, byte[] content) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        ReportTemplateStore.validateFormat(format);
        templateStore.save(path, format, content);
        setString(path, "templateFormat", format.trim().toLowerCase());
        return getReport(path);
    }

    @Transactional(readOnly = true)
    public Optional<ReportTemplateStore.StoredTemplate> getTemplate(String path) {
        getReport(path);
        return templateStore.find(path);
    }

    @Transactional
    public ReportView deleteTemplate(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.REPORT) {
            throw new IllegalArgumentException("Not a report object: " + path);
        }
        templateStore.delete(path);
        setString(path, "templateFormat", "");
        return getReport(path);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByDataSource(String dataSourcePath) {
        validateDataSourcePath(dataSourcePath);
        ensureReportsCatalog();
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (objectManager.tree().findByPath(REPORTS_ROOT).isPresent()) {
            for (PlatformObject child : objectManager.tree().childrenOf(REPORTS_ROOT)) {
                if (child.type() != ObjectType.REPORT) {
                    continue;
                }
                ReportView view = toView(child.path(), child);
                if (dataSourcePath.equals(view.dataSourcePath())) {
                    summaries.add(toSummary(view));
                }
            }
        }
        return summaries;
    }

    private Map<String, Object> runDefinition(ReportView report, Map<String, Object> parameters) {
        if (REPORT_TYPE_TREE_VARIABLES.equals(report.reportType())) {
            return runTreeVariablesDefinition(report);
        }

        List<Object> paramValues = resolveParameterValues(report.parameters(), parameters);
        String schemaName = dataSourcePathResolver.resolveSchemaForReport(
                report.dataSourcePath(),
                report.legacyAppId()
        );

        List<Map<String, Object>>[] result = new List[1];
        schemaSession.runInSchema(schemaName, () ->
                result[0] = jdbcTemplate.queryForList(report.query(), paramValues.toArray())
        );

        List<Map<String, Object>> rows = result[0];
        boolean truncated = rows.size() > report.maxRows();
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, report.maxRows()));
        }
        rows = normalizeRowKeys(rows);
        return Map.of(
                "path", report.path(),
                "reportId", reportIdFromPath(report.path()),
                "title", report.title(),
                "columns", columnMaps(report.columns()),
                "rows", rows,
                "rowCount", rows.size(),
                "truncated", truncated
        );
    }

    private Map<String, Object> runLegacy(String appId, String reportId, Map<String, Object> parameters) {
        ApplicationReportStore.DeployedReport report = reportStore.find(appId, reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        List<String> paramNames = deserializeStringList(report.parametersJson());
        List<Object> paramValues = resolveParameterValues(paramNames, parameters);
        String schemaName = dataSourcePathResolver.resolveSchemaForReport(null, appId);

        List<Map<String, Object>>[] result = new List[1];
        schemaSession.runInSchema(schemaName, () ->
                result[0] = jdbcTemplate.queryForList(report.querySql(), paramValues.toArray())
        );

        List<Map<String, Object>> rows = result[0];
        boolean truncated = rows.size() > report.maxRows();
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, report.maxRows()));
        }
        List<Map<String, String>> columns = deserializeColumns(report.columnsJson());
        rows = normalizeRowKeys(rows);
        return Map.of(
                "reportId", report.reportId(),
                "title", report.title(),
                "columns", columns,
                "rows", rows,
                "rowCount", rows.size(),
                "truncated", truncated
        );
    }

    private Map<String, Object> runTreeVariablesDefinition(ReportView report) {
        String pattern = report.devicePathPattern();
        String variableName = report.variableName();
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Report devicePathPattern is required for tree-variables reports");
        }
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("Report variableName is required for tree-variables reports");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            if (!matchesDevicePathPattern(node.path(), pattern)) {
                continue;
            }
            Optional<DataRecord> record = node.getVariable(variableName).flatMap(Variable::value);
            if (record.isPresent()) {
                flattenVariableToRows(node.path(), record.get(), rows);
            }
        }

        boolean truncated = rows.size() > report.maxRows();
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, report.maxRows()));
        }
        rows = normalizeRowKeys(rows);
        return Map.of(
                "path", report.path(),
                "reportId", reportIdFromPath(report.path()),
                "title", report.title(),
                "reportType", REPORT_TYPE_TREE_VARIABLES,
                "columns", columnMaps(report.columns()),
                "rows", rows,
                "rowCount", rows.size(),
                "truncated", truncated
        );
    }

    static boolean matchesDevicePathPattern(String path, String pattern) {
        if (path == null || pattern == null || pattern.isBlank()) {
            return false;
        }
        if (pattern.contains("*")) {
            String regex = "^" + pattern.replace(".", "\\.").replace("*", ".*") + "$";
            return Pattern.compile(regex).matcher(path).matches();
        }
        return path.equals(pattern) || path.startsWith(pattern);
    }

    private static void flattenVariableToRows(
            String devicePath,
            DataRecord record,
            List<Map<String, Object>> rows
    ) {
        Optional<String> listField = record.schema().fields().stream()
                .filter(field -> field.type() == FieldType.RECORD_LIST)
                .map(FieldDefinition::name)
                .findFirst();
        if (listField.isPresent() && record.rowCount() > 0) {
            Object tableRowsObject = record.firstRow().get(listField.get());
            if (tableRowsObject instanceof List<?> tableRows) {
                for (Object rowObject : tableRows) {
                    if (rowObject instanceof Map<?, ?> row) {
                        rows.add(treeVariableRow(devicePath, row));
                    }
                }
                return;
            }
        }
        for (Map<String, Object> row : record.rows()) {
            rows.add(treeVariableRow(devicePath, row));
        }
    }

    private static Map<String, Object> treeVariableRow(String devicePath, Map<?, ?> row) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("devicepath", devicePath);
        for (Map.Entry<?, ?> entry : row.entrySet()) {
            if (entry.getKey() != null) {
                mapped.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return mapped;
    }

    private void ensureReportNode(String path, String title, String description, String templateId) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, title, description != null ? description : "");
            objectManager.reconcileType(path, ObjectType.REPORT);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        String parentPath = path.substring(0, lastDot);
        String name = path.substring(lastDot + 1);
        if (objectManager.tree().findByPath(parentPath).isEmpty()) {
            ensureReportsCatalog();
        }
        objectManager.create(
                parentPath,
                name,
                ObjectType.REPORT,
                title,
                description != null ? description : "",
                templateId
        );
    }

    private void saveTreeVariablesDefinitionInternal(
            String path,
            String title,
            String devicePathPattern,
            String variableName,
            List<ReportColumn> columns,
            int maxRows,
            int refreshIntervalMs,
            String layout
    ) {
        setString(path, "title", title);
        setString(path, "reportType", REPORT_TYPE_TREE_VARIABLES);
        setString(path, "devicePathPattern", devicePathPattern);
        setString(path, "variableName", variableName);
        setString(path, "columns", serialize(columns));
        setString(path, "defaultParameters", "{}");
        setInteger(path, "maxRows", maxRows);
        setInteger(path, "refreshIntervalMs", refreshIntervalMs);
        setString(path, "layout", layout);
    }

    private void saveDefinitionInternal(String path, ReportDefinition definition) {
        setString(path, "title", definition.title());
        setString(path, "dataSourcePath", definition.dataSourcePath());
        setString(path, "query", definition.query());
        setString(path, "parameters", serialize(definition.parameters()));
        setString(path, "columns", serialize(definition.columns()));
        setString(path, "defaultParameters", serialize(definition.defaultParameters()));
        setInteger(path, "maxRows", definition.maxRows());
        setInteger(path, "refreshIntervalMs", definition.refreshIntervalMs());
        setString(path, "layout", definition.layout());
    }

    private ReportView toView(String path, PlatformObject node) {
        String dataSourcePath = readString(node, "dataSourcePath").orElse("");
        String legacyAppId = readString(node, "appId").orElse("");
        if (dataSourcePath.isBlank() && !legacyAppId.isBlank()) {
            dataSourcePath = dataSourceObjectService.pathForNodeName(legacyAppId);
        }
        return new ReportView(
                path,
                readString(node, "title").orElse(node.displayName()),
                dataSourcePath,
                legacyAppId,
                readString(node, "query").orElse(""),
                readString(node, "reportType").orElse(""),
                readString(node, "devicePathPattern").orElse(""),
                readString(node, "variableName").orElse(""),
                deserializeStringList(readString(node, "parameters").orElse("[]")),
                deserializeReportColumns(readString(node, "columns").orElse("[]")),
                deserializeObjectMap(readString(node, "defaultParameters").orElse("{}")),
                readInteger(node, "maxRows").orElse(1000),
                readInteger(node, "refreshIntervalMs").orElse(30000),
                readString(node, "templateFormat").orElse(""),
                readString(node, "layout").orElse(""),
                templateStore.exists(path)
        );
    }

    private String resolveDataSourcePathForSave(SaveReportDefinitionRequest request, ReportView current) {
        if (request.dataSourcePath() != null && !request.dataSourcePath().isBlank()) {
            return request.dataSourcePath();
        }
        if (request.appId() != null && !request.appId().isBlank()) {
            return dataSourceObjectService.pathForNodeName(request.appId());
        }
        if (current.dataSourcePath() != null && !current.dataSourcePath().isBlank()) {
            return current.dataSourcePath();
        }
        if (current.legacyAppId() != null && !current.legacyAppId().isBlank()) {
            return dataSourceObjectService.pathForNodeName(current.legacyAppId());
        }
        throw new IllegalArgumentException("Report dataSourcePath is required");
    }

    private static void validateDataSourcePath(String dataSourcePath) {
        if (dataSourcePath == null || dataSourcePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Report dataSourcePath is required — e.g. root.platform.data-sources.demo"
            );
        }
    }

    private String inferSchemaForApp(String appId) {
        return dataSourcePathResolver.resolveSchemaForReport(
                dataSourceObjectService.pathForNodeName(appId),
                appId
        );
    }

    private Map<String, Object> toSummary(ReportView view) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportId", reportIdFromPath(view.path()));
        summary.put("path", view.path());
        summary.put("title", view.title());
        summary.put("dataSourcePath", view.dataSourcePath());
        summary.put("parameters", view.parameters());
        summary.put("columns", columnMaps(view.columns()));
        summary.put("maxRows", view.maxRows());
        return summary;
    }

    private static List<Map<String, String>> columnMaps(List<ReportColumn> columns) {
        return columns.stream()
                .map(col -> Map.of("field", col.field(), "label", col.label()))
                .toList();
    }

    private byte[] toCsv(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) result.get("columns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");

        StringBuilder csv = new StringBuilder();
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();
        csv.append(columns.stream().map(col -> escapeCsv(col.get("label"))).reduce((a, b) -> a + "," + b).orElse(""));
        csv.append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                Object value = row.get(fields.get(i));
                csv.append(escapeCsv(value == null ? "" : value.toString()));
            }
            csv.append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] toHtmlTable(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) result.get("columns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        String title = String.valueOf(result.getOrDefault("title", "Report"));
        boolean truncated = Boolean.TRUE.equals(result.get("truncated"));
        int rowCount = result.get("rowCount") instanceof Number number ? number.intValue() : rows.size();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>");
        html.append(escapeHtml(title));
        html.append("</title><style>");
        html.append("body{font-family:system-ui,sans-serif;margin:1.5rem;color:#111}");
        html.append("table{border-collapse:collapse;width:100%;font-size:14px}");
        html.append("th,td{border:1px solid #ccc;padding:0.45rem 0.65rem;text-align:left}");
        html.append("th{background:#f3f4f6}.note{color:#666;font-size:13px;margin:0 0 1rem}");
        html.append("</style></head><body><h1>");
        html.append(escapeHtml(title));
        html.append("</h1>");
        if (truncated) {
            html.append("<p class=\"note\">Показаны первые ");
            html.append(rowCount);
            html.append(" строк (truncated).</p>");
        }
        html.append("<table><thead><tr>");
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();
        for (Map<String, String> column : columns) {
            html.append("<th>").append(escapeHtml(column.get("label"))).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            html.append("<tr>");
            for (String field : fields) {
                Object value = row.get(field);
                html.append("<td>").append(escapeHtml(value == null ? "" : value.toString())).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] toXlsxTable(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) result.get("columns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Report");
            Row header = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(columns.get(columnIndex).get("label"));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Map<String, Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < fields.size(); columnIndex++) {
                    Object value = values.get(fields.get(columnIndex));
                    row.createCell(columnIndex).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                sheet.autoSizeColumn(columnIndex);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("XLSX table export failed: " + ex.getMessage(), ex);
        }
    }

    private byte[] toXlsTable(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) result.get("columns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        List<String> fields = columns.stream().map(col -> col.get("field")).toList();

        try (Workbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Report");
            Row header = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(columns.get(columnIndex).get("label"));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Map<String, Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < fields.size(); columnIndex++) {
                    Object value = values.get(fields.get(columnIndex));
                    row.createCell(columnIndex).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                sheet.autoSizeColumn(columnIndex);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("XLS table export failed: " + ex.getMessage(), ex);
        }
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : ""))
        );
    }

    private void setInteger(String path, String variable, int value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(INTEGER_SCHEMA, Map.of("value", value))
        );
    }

    static void validateSelectQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Report query is required");
        }
        String trimmed = query.trim();
        if (!trimmed.regionMatches(true, 0, "SELECT", 0, 6)
                && !trimmed.regionMatches(true, 0, "WITH", 0, 4)) {
            throw new IllegalArgumentException("Report query must start with SELECT or WITH");
        }
        if (FORBIDDEN_SQL.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Report query contains forbidden SQL keyword");
        }
    }

    private static List<Object> resolveParameterValues(
            List<String> paramNames,
            Map<String, Object> parameters
    ) {
        if (paramNames == null || paramNames.isEmpty()) {
            return List.of();
        }
        Map<String, Object> values = parameters != null ? parameters : Map.of();
        List<Object> resolved = new ArrayList<>();
        for (String name : paramNames) {
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException("Missing report parameter: " + name);
            }
            resolved.add(values.get(name));
        }
        return resolved;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize report metadata", ex);
        }
    }

    private List<String> deserializeStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid report parameters JSON", ex);
        }
    }

    private List<Map<String, String>> deserializeColumns(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid report columns JSON", ex);
        }
    }

    private List<ReportColumn> deserializeReportColumns(String json) {
        List<Map<String, String>> raw = deserializeColumns(json);
        return raw.stream()
                .map(col -> new ReportColumn(col.get("field"), col.get("label")))
                .toList();
    }

    private Map<String, Object> deserializeObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid defaultParameters JSON", ex);
        }
    }

    private static List<Map<String, Object>> normalizeRowKeys(List<Map<String, Object>> rows) {
        List<Map<String, Object>> normalized = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                mapped.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            normalized.add(mapped);
        }
        return normalized;
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }

    private static Optional<Integer> readInteger(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                    return Integer.parseInt(String.valueOf(value));
                });
    }

    public record ReportColumn(String field, String label) {
    }

    public record ReportDefinition(
            String title,
            String dataSourcePath,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            int maxRows,
            int refreshIntervalMs,
            String layout
    ) {
    }

    public record ReportView(
            String path,
            String title,
            String dataSourcePath,
            String legacyAppId,
            String query,
            String reportType,
            String devicePathPattern,
            String variableName,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            int maxRows,
            int refreshIntervalMs,
            String templateFormat,
            String layout,
            boolean hasTemplate
    ) {
    }

    public record SaveReportDefinitionRequest(
            String title,
            String dataSourcePath,
            String appId,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            Integer maxRows,
            Integer refreshIntervalMs,
            String layout
    ) {
    }

    public record SaveTreeVariablesDefinitionRequest(
            String title,
            String devicePathPattern,
            String variableName,
            List<ReportColumn> columns,
            Integer maxRows,
            Integer refreshIntervalMs
    ) {
    }
}
