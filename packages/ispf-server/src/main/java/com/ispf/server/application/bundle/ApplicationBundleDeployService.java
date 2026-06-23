package com.ispf.server.application.bundle;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelBindingRule;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelException;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.core.model.DataSchema;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.api.ApplicationController;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.report.ApplicationReportService;
import com.ispf.server.application.schedule.PlatformSchedulerService;
import com.ispf.server.application.tree.ApplicationObjectTreeService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.report.ReportService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.operator.OperatorAppObjectTreeService;
import com.ispf.server.operator.OperatorAppUiStore;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.license.CommercialBundleLicenseVerifier;
import com.ispf.server.plugin.model.ModelPersistenceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ApplicationBundleDeployService {

    private final ApplicationDataService dataService;
    private final ApplicationFunctionStore functionStore;
    private final ApplicationBundleMetadataService metadataService;
    private final PlatformSchedulerService schedulerService;
    private final ApplicationSqlBindingService sqlBindingService;
    private final ApplicationReportService reportService;
    private final ApplicationObjectTreeService objectTreeService;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;
    private final ModelPersistenceService modelPersistence;
    private final ObjectMapper objectMapper;
    private final DataSourceObjectService dataSourceObjectService;
    private final ScheduleObjectService scheduleObjectService;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final MigrationObjectService migrationObjectService;
    private final ObjectManager objectManager;
    private final AutomationTreeService automationTreeService;
    private final OperatorAppUiStore operatorAppUiStore;
    private final OperatorAppObjectTreeService operatorAppObjectTreeService;
    private final CommercialBundleLicenseVerifier licenseVerifier;
    private final BundleDependencyVerifier dependencyVerifier;
    private final ApplicationEventCatalogService eventCatalogService;

    public ApplicationBundleDeployService(
            ApplicationDataService dataService,
            ApplicationFunctionStore functionStore,
            ApplicationBundleMetadataService metadataService,
            PlatformSchedulerService schedulerService,
            ApplicationSqlBindingService sqlBindingService,
            ApplicationReportService reportService,
            ApplicationObjectTreeService objectTreeService,
            ApplicationBundleSnapshotStore snapshotStore,
            ModelEngine modelEngine,
            ModelRegistry modelRegistry,
            ModelPersistenceService modelPersistence,
            ObjectMapper objectMapper,
            DataSourceObjectService dataSourceObjectService,
            ScheduleObjectService scheduleObjectService,
            SqlBindingObjectService sqlBindingObjectService,
            MigrationObjectService migrationObjectService,
            ObjectManager objectManager,
            AutomationTreeService automationTreeService,
            OperatorAppUiStore operatorAppUiStore,
            OperatorAppObjectTreeService operatorAppObjectTreeService,
            CommercialBundleLicenseVerifier licenseVerifier,
            BundleDependencyVerifier dependencyVerifier,
            ApplicationEventCatalogService eventCatalogService
    ) {
        this.dataService = dataService;
        this.functionStore = functionStore;
        this.metadataService = metadataService;
        this.schedulerService = schedulerService;
        this.sqlBindingService = sqlBindingService;
        this.reportService = reportService;
        this.objectTreeService = objectTreeService;
        this.snapshotStore = snapshotStore;
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
        this.modelPersistence = modelPersistence;
        this.objectMapper = objectMapper;
        this.dataSourceObjectService = dataSourceObjectService;
        this.scheduleObjectService = scheduleObjectService;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.migrationObjectService = migrationObjectService;
        this.objectManager = objectManager;
        this.automationTreeService = automationTreeService;
        this.operatorAppUiStore = operatorAppUiStore;
        this.operatorAppObjectTreeService = operatorAppObjectTreeService;
        this.licenseVerifier = licenseVerifier;
        this.dependencyVerifier = dependencyVerifier;
        this.eventCatalogService = eventCatalogService;
    }

    public Map<String, Object> deploy(String appId, BundleManifest manifest) {
        licenseVerifier.verifyOrWarn(appId, manifest);
        dependencyVerifier.verify(appId, manifest.requires());
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            String displayName = manifest.displayName() != null && !manifest.displayName().isBlank()
                    ? manifest.displayName()
                    : appId;
            dataService.register(
                    appId,
                    displayName,
                    manifest.tablePrefix(),
                    manifest.schemaName()
            );
            applied.add("register");
        } catch (Exception ex) {
            errors.add("register: " + ex.getMessage());
        }

        String dataSourcePath = dataSourceObjectService.pathForNodeName(appId);
        try {
            String schema = resolvePackageSchemaName(appId, manifest);
            dataSourceObjectService.ensureDataSource(
                    appId,
                    manifest.displayName() != null ? manifest.displayName() : appId,
                    schema,
                    "Package import data source"
            );
            applied.add("dataSource:" + dataSourcePath);
        } catch (Exception ex) {
            errors.add("dataSource: " + ex.getMessage());
        }

        if (manifest.models() != null) {
            for (BundleModel model : manifest.models()) {
                try {
                    deployModel(model);
                    applied.add("model:" + model.name());
                } catch (Exception ex) {
                    errors.add("model:" + model.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.objects() != null) {
            for (BundleObject object : manifest.objects()) {
                try {
                    ApplicationBundleMetadataService.DeployOutcome outcome = metadataService.deployObject(object);
                    if (outcome == ApplicationBundleMetadataService.DeployOutcome.APPLIED
                            || outcome == ApplicationBundleMetadataService.DeployOutcome.UPDATED) {
                        applied.add("object:" + object.name());
                    } else {
                        skipped.add("object:" + object.name());
                    }
                } catch (Exception ex) {
                    errors.add("object:" + object.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.dashboards() != null) {
            for (BundleDashboard dashboard : manifest.dashboards()) {
                try {
                    metadataService.deployDashboard(dashboard);
                    applied.add("dashboard:" + dashboard.path());
                } catch (Exception ex) {
                    errors.add("dashboard:" + dashboard.path() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.workflows() != null) {
            for (BundleWorkflow workflow : manifest.workflows()) {
                try {
                    metadataService.deployWorkflow(workflow);
                    applied.add("workflow:" + workflow.path());
                } catch (Exception ex) {
                    errors.add("workflow:" + workflow.path() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.migrations() != null && !manifest.migrations().isEmpty()) {
            try {
                for (BundleMigration script : manifest.migrations()) {
                    migrationObjectService.upsert(new MigrationObjectService.MigrationDefinition(
                            "",
                            script.id(),
                            manifest.version(),
                            dataSourcePath,
                            script.sql()
                    ));
                }
                List<String> appliedMigrations = migrationObjectService.applyPending(manifest.version());
                applied.addAll(appliedMigrations.stream().map(id -> "migration:" + id).toList());
            } catch (Exception ex) {
                errors.add("migrations: " + ex.getMessage());
            }
        }

        if (manifest.functions() != null) {
            for (BundleFunction function : manifest.functions()) {
                try {
                    deployFunction(appId, dataSourcePath, function);
                    applied.add("function:" + function.functionName());
                } catch (Exception ex) {
                    errors.add("function:" + function.functionName() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.bindings() != null) {
            for (BundleSqlBinding binding : manifest.bindings()) {
                try {
                    String bindingId = binding.objectPath().replace('.', '-') + "-" + binding.variable();
                    sqlBindingObjectService.upsert(new SqlBindingObjectService.BindingDefinition(
                            "",
                            bindingId,
                            binding.objectPath(),
                            binding.variable(),
                            dataSourcePath,
                            binding.query(),
                            binding.valueField(),
                            binding.refresh() != null ? binding.refresh() : "manual",
                            binding.refreshIntervalMs() != null ? binding.refreshIntervalMs() : 30_000L,
                            binding.triggerObjectPath() != null ? binding.triggerObjectPath() : "",
                            binding.triggerFunctionName() != null ? binding.triggerFunctionName() : "",
                            binding.enabled() == null || binding.enabled(),
                            null
                    ));
                    applied.add("binding:" + binding.variable());
                } catch (Exception ex) {
                    errors.add("binding:" + binding.variable() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.reports() != null) {
            for (BundleReport report : manifest.reports()) {
                try {
                    reportService.deploy(appId, new ApplicationReportService.DeployReportRequest(
                            report.reportId(),
                            report.title(),
                            report.description(),
                            report.reportType(),
                            report.devicePathPattern(),
                            report.variableName(),
                            report.query(),
                            report.parameters(),
                            report.columns() == null
                                    ? List.of()
                                    : report.columns().stream()
                                            .map(col -> new ApplicationReportService.ReportColumn(col.field(), col.label()))
                                            .toList(),
                            report.maxRows()
                    ));
                    applied.add("report:" + report.reportId());
                } catch (Exception ex) {
                    errors.add("report:" + report.reportId() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.alertRules() != null) {
            for (BundleAlertRule rule : manifest.alertRules()) {
                try {
                    deployAlertRule(rule);
                    applied.add("alertRule:" + rule.name());
                } catch (Exception ex) {
                    errors.add("alertRule:" + rule.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.correlators() != null) {
            for (BundleCorrelator correlator : manifest.correlators()) {
                try {
                    deployCorrelator(correlator);
                    applied.add("correlator:" + correlator.name());
                } catch (Exception ex) {
                    errors.add("correlator:" + correlator.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.schedules() != null) {
            for (BundleSchedule schedule : manifest.schedules()) {
                try {
                    String actionJson = objectMapper.writeValueAsString(schedule.action());
                    scheduleObjectService.upsert(new ScheduleObjectService.ScheduleDefinition(
                            "",
                            schedule.scheduleId(),
                            schedule.enabled(),
                            schedule.intervalMs(),
                            schedule.actionType(),
                            actionJson,
                            null,
                            null
                    ));
                    applied.add("schedule:" + schedule.scheduleId());
                } catch (Exception ex) {
                    errors.add("schedule:" + schedule.scheduleId() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.events() != null) {
            try {
                eventCatalogService.replaceFromBundle(appId, manifest.events().stream()
                        .map(event -> new ApplicationEventCatalogService.BundleEventDefinition(
                                event.id(),
                                event.roles(),
                                event.payloadSchema()
                        ))
                        .toList());
                applied.add("events:" + manifest.events().size());
            } catch (Exception ex) {
                errors.add("events: " + ex.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("appId", appId);
        response.put("version", manifest.version());
        response.put("status", errors.isEmpty() ? "OK" : "PARTIAL");
        response.put("applied", applied);
        response.put("skipped", skipped);
        response.put("errors", errors);

        try {
            String manifestJson = objectMapper.writeValueAsString(manifest);
            String operatorManifestJson = manifest.operatorManifest() != null
                    ? objectMapper.writeValueAsString(manifest.operatorManifest())
                    : null;
            snapshotStore.recordDeployment(appId, manifest.version(), manifestJson, operatorManifestJson);
            response.put("snapshot", "recorded");
        } catch (Exception ex) {
            errors.add("snapshot: " + ex.getMessage());
            response.put("status", "PARTIAL");
            response.put("errors", errors);
        }

        response.put("dataSourcePath", dataSourcePath);
        response.put("objectTree", "tree-first");
        String applicationPath = applicationTreePath(appId);
        response.put("applicationPath", applicationPath);

        try {
            objectTreeService.syncApplication(appId);
            applied.add("applicationTree:" + applicationPath);
            syncOperatorAppUi(appId, manifest);
            applied.add("operatorApp:" + operatorAppTreePath(appId));
            response.put("applied", applied);
        } catch (Exception ex) {
            errors.add("applicationSync: " + ex.getMessage());
            response.put("status", errors.isEmpty() ? "OK" : "PARTIAL");
            response.put("errors", errors);
            response.put("applied", applied);
        }

        return response;
    }

    private void syncOperatorAppUi(String appId, BundleManifest manifest) throws Exception {
        if (!hasOperatorUiManifest(manifest)) {
            return;
        }
        Map<String, Object> ui = manifest.operatorUi() != null && !manifest.operatorUi().isEmpty()
                ? manifest.operatorUi()
                : buildOperatorUiFromBundle(appId, manifest);
        String title = ui.get("title") != null ? String.valueOf(ui.get("title")) : appId;
        String defaultDashboard = ui.get("defaultDashboard") != null
                ? String.valueOf(ui.get("defaultDashboard"))
                : "";
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dashboardEntries = (List<Map<String, Object>>) ui.get("dashboards");
        if (dashboardEntries == null || dashboardEntries.isEmpty()) {
            return;
        }
        List<Map<String, String>> dashboards = new ArrayList<>();
        for (Map<String, Object> entry : dashboardEntries) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("path", String.valueOf(entry.get("path")));
            item.put("title", entry.get("title") != null ? String.valueOf(entry.get("title")) : String.valueOf(entry.get("path")));
            dashboards.add(item);
        }
        if (defaultDashboard.isBlank()) {
            defaultDashboard = dashboards.get(0).get("path");
        }
        operatorAppUiStore.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                appId,
                title,
                defaultDashboard,
                objectMapper.writeValueAsString(dashboards),
                Instant.now()
        ));
        operatorAppObjectTreeService.syncAll();
    }

    public static String applicationTreePath(String appId) {
        return ApplicationObjectTreeService.APPLICATIONS_ROOT + "."
                + ApplicationObjectTreeService.sanitizeNodeName(appId);
    }

    public static String operatorAppTreePath(String appId) {
        return OperatorAppObjectTreeService.OPERATOR_APPS_ROOT + "."
                + ApplicationObjectTreeService.sanitizeNodeName(appId);
    }

    public Map<String, Object> rollback(String appId, String bundleVersion) throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = snapshotStore.findByVersion(appId, bundleVersion)
                .orElseThrow(() -> new IllegalArgumentException("Bundle version not found: " + bundleVersion));

        BundleManifest manifest = objectMapper.readValue(snapshot.manifestJson(), BundleManifest.class);
        Map<String, Object> result = deploy(appId, manifest);
        result.put("rolledBackTo", bundleVersion);
        return result;
    }

    public List<Map<String, Object>> deployHistory(String appId) {
        return snapshotStore.listHistory(appId);
    }

    public Map<String, Object> operatorManifest(String appId) throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalArgumentException("No active bundle deployment for app: " + appId));
        if (snapshot.operatorManifestJson() == null || snapshot.operatorManifestJson().isBlank()) {
            throw new IllegalArgumentException("Operator manifest not defined in active bundle");
        }
        return objectMapper.readValue(snapshot.operatorManifestJson(), new TypeReference<>() {
        });
    }

    public boolean supportsOperatorUi(String appId) {
        return snapshotStore.findActive(appId)
                .map(snapshot -> {
                    try {
                        BundleManifest manifest = objectMapper.readValue(snapshot.manifestJson(), BundleManifest.class);
                        return hasOperatorUiManifest(manifest);
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .orElse(false);
    }

    private static boolean hasOperatorUiManifest(BundleManifest manifest) {
        return (manifest.operatorUi() != null && !manifest.operatorUi().isEmpty())
                || (manifest.dashboards() != null && !manifest.dashboards().isEmpty())
                || (manifest.reports() != null && !manifest.reports().isEmpty());
    }

    public Map<String, Object> operatorUi(String appId) throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalArgumentException("No active bundle deployment for app: " + appId));
        BundleManifest manifest = objectMapper.readValue(snapshot.manifestJson(), BundleManifest.class);
        if (manifest.operatorUi() != null && !manifest.operatorUi().isEmpty()) {
            return manifest.operatorUi();
        }
        if (manifest.dashboards() != null && !manifest.dashboards().isEmpty()) {
            return buildOperatorUiFromBundle(appId, manifest);
        }
        if (manifest.reports() != null && !manifest.reports().isEmpty()) {
            return buildOperatorUiFromBundle(appId, manifest);
        }
        throw new IllegalArgumentException(
                "Operator UI not defined: set operatorUi or dashboards[] in bundle for app: " + appId
        );
    }

    private Map<String, Object> buildOperatorUiFromBundle(String appId, BundleManifest manifest) {
        List<Map<String, Object>> dashboards = new ArrayList<>();
        String defaultDashboard = null;
        if (manifest.dashboards() != null) {
            for (BundleDashboard dashboard : manifest.dashboards()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("path", dashboard.path());
                entry.put("title", dashboard.title() != null ? dashboard.title() : dashboard.path());
                dashboards.add(entry);
                if (defaultDashboard == null) {
                    defaultDashboard = dashboard.path();
                }
            }
        }

        List<Map<String, Object>> reports = new ArrayList<>();
        String defaultReport = null;
        if (manifest.reports() != null) {
            for (BundleReport report : manifest.reports()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String path = ReportService.reportPath(report.reportId());
                entry.put("path", path);
                entry.put("title", report.title() != null ? report.title() : report.reportId());
                reports.add(entry);
                if (defaultReport == null) {
                    defaultReport = path;
                }
            }
        }

        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("appId", appId);
        ui.put("title", manifest.displayName() != null ? manifest.displayName() : appId);
        ui.put("defaultDashboard", defaultDashboard != null ? defaultDashboard : "");
        ui.put("dashboards", dashboards);
        if (!reports.isEmpty()) {
            ui.put("reports", reports);
            ui.put("defaultReport", defaultReport);
        }
        return ui;
    }

    private Map<String, Object> buildOperatorUiFromDashboards(String appId, BundleManifest manifest) {
        return buildOperatorUiFromBundle(appId, manifest);
    }

    private static String resolvePackageSchemaName(String appId, BundleManifest manifest) {
        if (manifest.schemaName() != null && !manifest.schemaName().isBlank()) {
            return manifest.schemaName();
        }
        return ApplicationSchemaSupport.defaultSchemaName(appId);
    }

    private void deployAlertRule(BundleAlertRule rule) {
        String path = AutomationTreeService.rulePathForName(rule.name());
        if (objectManager.tree().findByPath(path).isPresent()) {
            automationTreeService.updateAlertRule(
                    path,
                    rule.name(),
                    rule.objectPath(),
                    rule.watchVariable(),
                    rule.conditionExpr(),
                    rule.eventName(),
                    rule.payloadVariable(),
                    rule.enabled(),
                    rule.edgeTrigger(),
                    rule.delaySeconds(),
                    rule.sustainWhileTrue()
            );
            return;
        }
        automationTreeService.createAlertRule(
                rule.name(),
                rule.objectPath(),
                rule.watchVariable(),
                rule.conditionExpr(),
                rule.eventName(),
                rule.payloadVariable(),
                rule.enabled() == null || rule.enabled(),
                rule.edgeTrigger() == null || rule.edgeTrigger(),
                rule.delaySeconds() != null ? rule.delaySeconds() : 0,
                rule.sustainWhileTrue() != null && rule.sustainWhileTrue()
        );
    }

    private void deployCorrelator(BundleCorrelator correlator) {
        String path = AutomationTreeService.correlatorPathForName(correlator.name());
        CorrelatorPatternType patternType = CorrelatorPatternType.valueOf(
                correlator.patternType() != null ? correlator.patternType() : "COUNT"
        );
        CorrelatorActionType actionType = CorrelatorActionType.valueOf(
                correlator.actionType() != null ? correlator.actionType() : "RUN_WORKFLOW"
        );
        if (objectManager.tree().findByPath(path).isPresent()) {
            automationTreeService.updateCorrelator(
                    path,
                    correlator.name(),
                    correlator.objectPath(),
                    patternType,
                    correlator.eventName(),
                    correlator.secondEventName(),
                    correlator.windowSeconds(),
                    correlator.minOccurrences(),
                    correlator.cooldownSeconds(),
                    correlator.sequenceGapSeconds(),
                    actionType,
                    correlator.actionTarget(),
                    correlator.payloadFilterExpr(),
                    correlator.enabled()
            );
            return;
        }
        automationTreeService.createCorrelator(
                correlator.name(),
                correlator.objectPath(),
                patternType,
                correlator.eventName(),
                correlator.secondEventName(),
                correlator.windowSeconds() != null ? correlator.windowSeconds() : 0,
                correlator.minOccurrences() != null ? correlator.minOccurrences() : 1,
                correlator.cooldownSeconds() != null ? correlator.cooldownSeconds() : 120,
                correlator.sequenceGapSeconds() != null ? correlator.sequenceGapSeconds() : 0,
                actionType,
                correlator.actionTarget(),
                correlator.payloadFilterExpr(),
                correlator.enabled() == null || correlator.enabled()
        );
    }

    private void deployFunction(String appId, String dataSourcePath, BundleFunction function) throws Exception {
        String version = function.version() != null ? function.version() : "1";
        String inputSchemaJson = objectMapper.writeValueAsString(function.descriptor().inputSchema());
        String outputSchemaJson = objectMapper.writeValueAsString(function.descriptor().outputSchema());

        ApplicationFunctionHandler.DeployedFunction deployed = new ApplicationFunctionHandler.DeployedFunction(
                UUID.randomUUID(),
                appId,
                function.objectPath(),
                function.functionName(),
                version,
                function.source().type(),
                function.source().body(),
                inputSchemaJson,
                outputSchemaJson
        );
        functionStore.deploy(deployed);

        FunctionDescriptor treeFunction = new FunctionDescriptor(
                function.functionName(),
                "Script function " + function.functionName(),
                function.descriptor().inputSchema(),
                function.descriptor().outputSchema(),
                function.source().type(),
                function.source().body(),
                dataSourcePath,
                version
        );
        objectManager.upsertFunction(function.objectPath(), treeFunction);
    }

    private void deployModel(BundleModel model) throws ModelException {
        Instant now = Instant.now();
        ModelDefinition definition = modelRegistry.findByName(model.name())
                .map(existing -> new ModelDefinition(
                        existing.id(),
                        model.name(),
                        model.description(),
                        model.type() != null ? model.type() : existing.type(),
                        model.targetObjectType() != null ? model.targetObjectType() : existing.targetObjectType(),
                        model.suitabilityExpression() != null
                                ? model.suitabilityExpression()
                                : existing.suitabilityExpression(),
                        model.variables() != null ? model.variables() : existing.variables(),
                        model.events() != null ? model.events() : existing.events(),
                        model.functions() != null ? model.functions() : existing.functions(),
                        model.bindings() != null ? model.bindings() : existing.bindings(),
                        model.parameters() != null ? model.parameters() : existing.parameters(),
                        existing.createdAt(),
                        now
                ))
                .orElseGet(() -> new ModelDefinition(
                        UUID.randomUUID().toString(),
                        model.name(),
                        model.description(),
                        model.type(),
                        model.targetObjectType(),
                        model.suitabilityExpression(),
                        model.variables() != null ? model.variables() : List.of(),
                        model.events() != null ? model.events() : List.of(),
                        model.functions() != null ? model.functions() : List.of(),
                        model.bindings() != null ? model.bindings() : List.of(),
                        model.parameters() != null ? model.parameters() : Map.of(),
                        now,
                        now
                ));

        ModelDefinition saved = modelRegistry.findByName(model.name()).isPresent()
                ? modelEngine.updateModel(definition)
                : modelEngine.createModel(definition);
        modelPersistence.persist(saved, false);
    }

    public record BundleManifest(
            String version,
            String displayName,
            String tablePrefix,
            String schemaName,
            List<BundleObject> objects,
            List<BundleDashboard> dashboards,
            List<BundleWorkflow> workflows,
            List<BundleModel> models,
            List<BundleMigration> migrations,
            List<BundleFunction> functions,
            List<BundleSqlBinding> bindings,
            List<BundleReport> reports,
            List<BundleAlertRule> alertRules,
            List<BundleCorrelator> correlators,
            List<BundleSchedule> schedules,
            List<BundleEvent> events,
            List<BundleDependency> requires,
            Map<String, Object> license,
            Map<String, Object> metadata,
            Map<String, Object> operatorUi,
            Map<String, Object> operatorManifest
    ) {
    }

    public record BundleObject(
            String parentPath,
            String name,
            String type,
            String displayName,
            String description,
            String templateId
    ) {
    }

    public record BundleDashboard(
            String path,
            String title,
            String layoutJson,
            Integer refreshIntervalMs
    ) {
    }

    public record BundleWorkflow(
            String path,
            String bpmnXml,
            String status
    ) {
    }

    public record BundleModel(
            String name,
            String description,
            ModelType type,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<ModelVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<ModelBindingRule> bindings,
            Map<String, String> parameters
    ) {
    }

    public record BundleMigration(String id, String sql) {
    }

    public record BundleFunction(
            String objectPath,
            String functionName,
            String version,
            ApplicationController.FunctionDescriptorDto descriptor,
            ApplicationController.FunctionSourceDto source
    ) {
    }

    public record BundleSqlBinding(
            String objectPath,
            String variable,
            String query,
            String refresh,
            Long refreshIntervalMs,
            String valueField,
            String triggerObjectPath,
            String triggerFunctionName,
            Boolean enabled
    ) {
    }

    public record BundleReport(
            String reportId,
            String title,
            String description,
            String reportType,
            String devicePathPattern,
            String variableName,
            String query,
            List<String> parameters,
            List<BundleReportColumn> columns,
            Integer maxRows
    ) {
    }

    public record BundleAlertRule(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            Boolean enabled,
            Boolean edgeTrigger,
            Integer delaySeconds,
            Boolean sustainWhileTrue
    ) {
    }

    public record BundleCorrelator(
            String name,
            String objectPath,
            String patternType,
            String eventName,
            String secondEventName,
            Integer windowSeconds,
            Integer minOccurrences,
            Integer cooldownSeconds,
            Integer sequenceGapSeconds,
            String actionType,
            String actionTarget,
            String payloadFilterExpr,
            Boolean enabled
    ) {
    }

    public record BundleReportColumn(String field, String label) {
    }

    public record BundleSchedule(
            String scheduleId,
            boolean enabled,
            long intervalMs,
            String actionType,
            Map<String, Object> action
    ) {
    }

    public record BundleEvent(
            String id,
            List<String> roles,
            Object payloadSchema
    ) {
    }

    public record BundleDependency(
            String appId,
            String minVersion
    ) {
    }
}
