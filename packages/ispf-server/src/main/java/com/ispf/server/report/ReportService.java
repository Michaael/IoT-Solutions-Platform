package com.ispf.server.report;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.report.ApplicationReportStore;
import com.ispf.server.object.ObjectManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ReportService {

    public static final String REPORTS_ROOT = "root.platform.reports";

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
    private final ApplicationDataStore dataStore;
    private final ApplicationSchemaSession schemaSession;
    private final ApplicationReportStore reportStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReportService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ApplicationDataStore dataStore,
            ApplicationSchemaSession schemaSession,
            ApplicationReportStore reportStore,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.dataStore = dataStore;
        this.schemaSession = schemaSession;
        this.reportStore = reportStore;
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
                    "SQL reports (REQ-PF-12)",
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
        if (node.getVariable("query").isPresent()) {
            return;
        }
        modelRegistry.findByName("report-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    @Transactional
    public void deploy(
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
        requireApp(appId);
        validateSelectQuery(query);
        ensureReportsCatalog();
        String path = reportPath(reportId);
        ensureReportNode(path, title, description);
        ensureReportStructure(path);
        saveDefinitionInternal(
                path,
                new ReportDefinition(
                        title,
                        appId,
                        query.trim(),
                        parameters != null ? parameters : List.of(),
                        columns != null ? columns : List.of(),
                        defaultParameters != null ? defaultParameters : Map.of(),
                        maxRows != null && maxRows > 0 ? maxRows : 1000,
                        30000
                )
        );
        syncLegacyStore(appId, reportId, title, description, query, parameters, columns, maxRows);
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
        String appId = request.appId() != null ? request.appId() : current.appId();
        requireAppId(appId);
        ReportDefinition definition = new ReportDefinition(
                request.title() != null ? request.title() : current.title(),
                appId,
                request.query().trim(),
                request.parameters() != null ? request.parameters() : current.parameters(),
                request.columns() != null ? request.columns() : current.columns(),
                request.defaultParameters() != null ? request.defaultParameters() : current.defaultParameters(),
                request.maxRows() != null ? request.maxRows() : current.maxRows(),
                request.refreshIntervalMs() != null ? request.refreshIntervalMs() : current.refreshIntervalMs()
        );
        saveDefinitionInternal(path, definition);
        syncLegacyStore(
                definition.appId(),
                reportIdFromPath(path),
                definition.title(),
                node.description(),
                definition.query(),
                definition.parameters(),
                definition.columns(),
                definition.maxRows()
        );
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
        if (!appId.equals(report.appId())) {
            throw new IllegalArgumentException("Report app mismatch: " + reportId);
        }
        return runDefinition(report, parameters);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String path, Map<String, Object> parameters) {
        Map<String, Object> result = run(path, parameters);
        return toCsv(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsvByApp(String appId, String reportId, Map<String, Object> parameters) {
        Map<String, Object> result = runByApp(appId, reportId, parameters);
        return toCsv(result);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByApp(String appId) {
        requireApp(appId);
        ensureReportsCatalog();
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (objectManager.tree().findByPath(REPORTS_ROOT).isPresent()) {
            for (PlatformObject child : objectManager.tree().childrenOf(REPORTS_ROOT)) {
                if (child.type() != ObjectType.REPORT) {
                    continue;
                }
                ReportView view = toView(child.path(), child);
                if (appId.equals(view.appId())) {
                    summaries.add(toSummary(view));
                }
            }
        }
        if (summaries.isEmpty()) {
            return reportStore.listByApp(appId).stream()
                    .map(this::legacyToSummary)
                    .toList();
        }
        return summaries;
    }

    private Map<String, Object> runDefinition(ReportView report, Map<String, Object> parameters) {
        requireApp(report.appId());
        List<Object> paramValues = resolveParameterValues(report.parameters(), parameters);
        String schemaName = schemaName(report.appId());

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
        String schemaName = schemaName(appId);

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

    private void ensureReportNode(String path, String title, String description) {
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
                "report-v1"
        );
    }

    private void saveDefinitionInternal(String path, ReportDefinition definition) {
        setString(path, "title", definition.title());
        setString(path, "appId", definition.appId());
        setString(path, "query", definition.query());
        setString(path, "parameters", serialize(definition.parameters()));
        setString(path, "columns", serialize(definition.columns()));
        setString(path, "defaultParameters", serialize(definition.defaultParameters()));
        setInteger(path, "maxRows", definition.maxRows());
        setInteger(path, "refreshIntervalMs", definition.refreshIntervalMs());
    }

    private ReportView toView(String path, PlatformObject node) {
        return new ReportView(
                path,
                readString(node, "title").orElse(node.displayName()),
                readString(node, "appId").orElse(""),
                readString(node, "query").orElse(""),
                deserializeStringList(readString(node, "parameters").orElse("[]")),
                deserializeReportColumns(readString(node, "columns").orElse("[]")),
                deserializeObjectMap(readString(node, "defaultParameters").orElse("{}")),
                readInteger(node, "maxRows").orElse(1000),
                readInteger(node, "refreshIntervalMs").orElse(30000)
        );
    }

    private void syncLegacyStore(
            String appId,
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows
    ) {
        int rows = maxRows != null && maxRows > 0 ? maxRows : 1000;
        reportStore.upsert(new ApplicationReportStore.DeployedReport(
                UUID.randomUUID(),
                appId,
                reportId,
                title,
                description,
                query.trim(),
                serialize(parameters != null ? parameters : List.of()),
                serialize(columns != null ? columns : List.of()),
                rows
        ));
    }

    private Map<String, Object> toSummary(ReportView view) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportId", reportIdFromPath(view.path()));
        summary.put("path", view.path());
        summary.put("title", view.title());
        summary.put("appId", view.appId());
        summary.put("parameters", view.parameters());
        summary.put("columns", columnMaps(view.columns()));
        summary.put("maxRows", view.maxRows());
        return summary;
    }

    private Map<String, Object> legacyToSummary(ApplicationReportStore.DeployedReport report) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportId", report.reportId());
        summary.put("path", reportPath(report.reportId()));
        summary.put("title", report.title());
        summary.put("description", report.description());
        summary.put("appId", report.appId());
        summary.put("parameters", deserializeStringList(report.parametersJson()));
        summary.put("columns", deserializeColumns(report.columnsJson()));
        summary.put("maxRows", report.maxRows());
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

    private void requireApp(String appId) {
        requireAppId(appId);
        dataStore.findApp(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not registered: " + appId));
    }

    private static void requireAppId(String appId) {
        validateAppId(appId);
    }

    static void validateAppId(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException(
                    "Report appId is required — set deploy-application id (e.g. demo) in report editor"
            );
        }
    }

    private String schemaName(String appId) {
        return dataStore.findApp(appId)
                .map(row -> (String) row.get("schema_name"))
                .orElseThrow();
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
            String appId,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            int maxRows,
            int refreshIntervalMs
    ) {
    }

    public record ReportView(
            String path,
            String title,
            String appId,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            int maxRows,
            int refreshIntervalMs
    ) {
    }

    public record SaveReportDefinitionRequest(
            String title,
            String appId,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Map<String, Object> defaultParameters,
            Integer maxRows,
            Integer refreshIntervalMs
    ) {
    }
}
