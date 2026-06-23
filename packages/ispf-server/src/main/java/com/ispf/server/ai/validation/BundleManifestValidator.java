package com.ispf.server.ai.validation;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleDependencyException;
import com.ispf.server.application.bundle.BundleDependencyVerifier;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.application.script.FunctionScriptValidator;
import com.ispf.server.license.CommercialBundleLicenseVerifier;
import com.ispf.server.license.CommercialLicenseException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BundleManifestValidator {

    private static final Set<String> KNOWN_WIDGET_TYPES = Set.of(
            "value", "indicator", "toggle", "chart", "sparkline", "progress", "gauge", "status-badge",
            "function", "function-form", "pie-chart", "history-table", "variable-editor",
            "svg-widget", "composite-widget", "dashboard-link", "event-feed",
            "object-table", "work-queue", "card-grid", "report",
            "sub-dashboard", "panel", "tab-panel", "map",
            "label", "image", "html-snippet", "object-tree", "breadcrumbs", "timer", "context-list",
            "linear-gauge", "input-form", "drawer-panel", "carousel", "steps-panel",
            "gantt-chart", "network-graph", "spreadsheet", "liquid-gauge", "nav-menu"
    );

    private final FunctionScriptValidator scriptValidator;
    private final BundleDependencyVerifier dependencyVerifier;
    private final CommercialBundleLicenseVerifier licenseVerifier;
    private final ObjectMapper objectMapper;

    public BundleManifestValidator(
            FunctionScriptValidator scriptValidator,
            BundleDependencyVerifier dependencyVerifier,
            CommercialBundleLicenseVerifier licenseVerifier,
            ObjectMapper objectMapper
    ) {
        this.scriptValidator = scriptValidator;
        this.dependencyVerifier = dependencyVerifier;
        this.licenseVerifier = licenseVerifier;
        this.objectMapper = objectMapper;
    }

    public BundleValidationResult validate(String appId, ApplicationBundleDeployService.BundleManifest manifest) {
        return validate(appId, manifest, false);
    }

    public BundleValidationResult dryRun(String appId, ApplicationBundleDeployService.BundleManifest manifest) {
        return validate(appId, manifest, true);
    }

    private BundleValidationResult validate(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            boolean dryRun
    ) {
        BundleValidationResult.Builder builder = BundleValidationResult.builder();

        if (appId == null || appId.isBlank()) {
            builder.addError("appId is required");
        }
        if (manifest.version() == null || manifest.version().isBlank()) {
            builder.addError("manifest.version is required");
        }
        if (manifest.displayName() == null || manifest.displayName().isBlank()) {
            builder.addError("manifest.displayName is required");
        }
        if (manifest.schemaName() == null || manifest.schemaName().isBlank()) {
            builder.addError("manifest.schemaName is required");
        }
        validateMinimumContent(manifest, builder);

        String tablePrefix = manifest.tablePrefix() != null ? manifest.tablePrefix() : "";
        validateMigrations(manifest, tablePrefix, builder);
        validateFunctions(manifest, builder);
        validateEvents(manifest, builder);
        validateDashboards(manifest, builder);
        validateObjects(manifest, builder);
        validateReports(manifest, builder);
        validateDependencies(appId, manifest, builder);
        validateLicense(appId, manifest, builder);

        if (dryRun) {
            collectWouldApply(manifest, builder);
        }

        return builder.build();
    }

    private void validateMinimumContent(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (hasNonEmptyList(manifest.migrations())
                || hasNonEmptyList(manifest.functions())
                || hasNonEmptyList(manifest.dashboards())
                || hasNonEmptyList(manifest.objects())
                || hasNonEmptyList(manifest.workflows())
                || hasNonEmptyList(manifest.models())
                || hasNonEmptyList(manifest.bindings())
                || hasNonEmptyList(manifest.reports())
                || hasNonEmptyList(manifest.alertRules())
                || hasNonEmptyList(manifest.correlators())
                || hasNonEmptyList(manifest.schedules())
                || hasNonEmptyList(manifest.events())
                || hasNonEmptyMap(manifest.operatorUi())
                || hasNonEmptyMap(manifest.operatorManifest())) {
            return;
        }
        builder.addError(
                "manifest must include at least one deployable section "
                        + "(migrations, functions, dashboards, operatorUi, objects, workflows, models, reports, events, ...)"
        );
    }

    private static boolean hasNonEmptyList(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean hasNonEmptyMap(Map<?, ?> values) {
        return values != null && !values.isEmpty();
    }

    private void validateMigrations(
            ApplicationBundleDeployService.BundleManifest manifest,
            String tablePrefix,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.migrations() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleMigration migration : manifest.migrations()) {
            if (migration.id() == null || migration.id().isBlank()) {
                builder.addError("migration: id is required (use {id, sql} objects, not declarative column schemas)");
                continue;
            }
            if (migration.sql() == null || migration.sql().isBlank()) {
                builder.addError("migration " + migration.id() + ": sql is required");
                continue;
            }
            try {
                ApplicationSchemaSupport.validateMigrationSql(migration.sql(), tablePrefix);
            } catch (Exception ex) {
                builder.addError("migration " + migration.id() + ": " + ex.getMessage());
            }
        }
    }

    private void validateFunctions(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.functions() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleFunction function : manifest.functions()) {
            String label = function.functionName() != null ? function.functionName() : function.objectPath();
            if (function.objectPath() == null || function.objectPath().isBlank()) {
                builder.addError("function " + label + ": objectPath is required");
            }
            if (function.functionName() == null || function.functionName().isBlank()) {
                builder.addError("function at " + function.objectPath() + ": functionName is required");
            }
            if (function.source() == null || function.source().body() == null || function.source().body().isBlank()) {
                builder.addError("function " + label + ": source.body is required");
                continue;
            }
            try {
                scriptValidator.validate(function.source().body());
            } catch (Exception ex) {
                builder.addError("function " + label + ": " + ex.getMessage());
            }
        }
    }

    private void validateEvents(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.events() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleEvent event : manifest.events()) {
            if (event.id() == null || event.id().isBlank()) {
                builder.addError("events[]: id is required");
            }
            if (event.payloadSchema() != null) {
                try {
                    objectMapper.writeValueAsString(event.payloadSchema());
                } catch (Exception ex) {
                    builder.addError("event " + event.id() + ": payloadSchema must be JSON-serializable");
                }
            }
        }
    }

    private void validateDashboards(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.dashboards() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleDashboard dashboard : manifest.dashboards()) {
            String path = dashboard.path() != null ? dashboard.path() : "(unknown)";
            if (dashboard.path() == null || dashboard.path().isBlank()) {
                builder.addError("dashboard: path is required");
            }
            if (dashboard.layoutJson() == null || dashboard.layoutJson().isBlank()) {
                builder.addError("dashboard " + path + ": layoutJson is required");
                continue;
            }
            try {
                JsonNode layout = objectMapper.readTree(dashboard.layoutJson());
                validateWidgetTypes(layout, path, builder);
            } catch (Exception ex) {
                builder.addError("dashboard " + path + ": invalid layoutJson — " + ex.getMessage());
            }
        }
    }

    private void validateWidgetTypes(JsonNode layout, String dashboardPath, BundleValidationResult.Builder builder) {
        JsonNode widgets = layout.path("widgets");
        if (!widgets.isArray()) {
            return;
        }
        for (JsonNode widget : widgets) {
            String type = widget.path("type").asText("");
            if (!type.isBlank() && !KNOWN_WIDGET_TYPES.contains(type)) {
                builder.addWarning("dashboard " + dashboardPath + ": unknown widget type '" + type + "'");
            }
        }
    }

    private void validateObjects(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.objects() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleObject object : manifest.objects()) {
            if (object.parentPath() == null || object.parentPath().isBlank()) {
                builder.addError("object " + object.name() + ": parentPath is required");
            }
            if (object.name() == null || object.name().isBlank()) {
                builder.addError("object: name is required");
            }
            if (object.type() == null || object.type().isBlank()) {
                builder.addError("object " + object.name() + ": type is required");
            }
        }
    }

    private void validateReports(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.reports() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleReport report : manifest.reports()) {
            if (report.reportId() == null || report.reportId().isBlank()) {
                builder.addError("report: reportId is required");
            }
            if (report.title() == null || report.title().isBlank()) {
                builder.addError("report " + report.reportId() + ": title is required");
            }
            String reportType = report.reportType() != null ? report.reportType().trim() : "";
            if ("tree-variables".equals(reportType)) {
                if (report.devicePathPattern() == null || report.devicePathPattern().isBlank()) {
                    builder.addError("report " + report.reportId() + ": devicePathPattern is required for tree-variables");
                }
                if (report.variableName() == null || report.variableName().isBlank()) {
                    builder.addError("report " + report.reportId() + ": variableName is required for tree-variables");
                }
            } else if (report.query() == null || report.query().isBlank()) {
                builder.addError("report " + report.reportId() + ": query is required for SQL reports");
            }
            if (report.columns() != null) {
                for (ApplicationBundleDeployService.BundleReportColumn column : report.columns()) {
                    if (column.field() == null || column.field().isBlank()) {
                        builder.addError("report " + report.reportId() + ": column field is required");
                    }
                }
            }
        }
    }

    private void validateDependencies(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.requires() == null || manifest.requires().isEmpty()) {
            return;
        }
        try {
            dependencyVerifier.verify(appId, manifest.requires());
        } catch (BundleDependencyException ex) {
            builder.addError("requires[]: " + ex.getMessage());
        }
    }

    private void validateLicense(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        try {
            licenseVerifier.verifyForValidation(appId, manifest);
        } catch (CommercialLicenseException ex) {
            builder.addError("license: " + ex.getMessage());
        }
    }

    private void collectWouldApply(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        builder.addWouldApply("register");
        if (manifest.migrations() != null && !manifest.migrations().isEmpty()) {
            builder.addWouldApply("migrations");
        }
        if (manifest.functions() != null && !manifest.functions().isEmpty()) {
            builder.addWouldApply("functions");
        }
        if (manifest.bindings() != null && !manifest.bindings().isEmpty()) {
            builder.addWouldApply("bindings");
        }
        if (manifest.reports() != null && !manifest.reports().isEmpty()) {
            builder.addWouldApply("reports");
        }
        if (manifest.objects() != null && !manifest.objects().isEmpty()) {
            builder.addWouldApply("objects");
        }
        if (manifest.dashboards() != null && !manifest.dashboards().isEmpty()) {
            builder.addWouldApply("dashboards");
        }
        if (manifest.workflows() != null && !manifest.workflows().isEmpty()) {
            builder.addWouldApply("workflows");
        }
        if (manifest.models() != null && !manifest.models().isEmpty()) {
            builder.addWouldApply("models");
        }
        if (manifest.alertRules() != null && !manifest.alertRules().isEmpty()) {
            builder.addWouldApply("alertRules");
        }
        if (manifest.correlators() != null && !manifest.correlators().isEmpty()) {
            builder.addWouldApply("correlators");
        }
        if (manifest.schedules() != null && !manifest.schedules().isEmpty()) {
            builder.addWouldApply("schedules");
        }
        if (manifest.events() != null && !manifest.events().isEmpty()) {
            builder.addWouldApply("events");
        }
        if (manifest.operatorUi() != null && !manifest.operatorUi().isEmpty()) {
            builder.addWouldApply("operatorUi");
        }
        if (manifest.operatorManifest() != null && !manifest.operatorManifest().isEmpty()) {
            builder.addWouldApply("operatorManifest");
        }
        builder.addWouldApply("snapshot");
        builder.addWouldApply("objectTreeSync");
    }
}
