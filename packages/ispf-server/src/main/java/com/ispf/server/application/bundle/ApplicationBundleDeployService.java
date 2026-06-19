package com.ispf.server.application.bundle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.api.ApplicationController;
import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.schedule.PlatformSchedulerService;
import org.springframework.stereotype.Service;

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
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ObjectMapper objectMapper;

    public ApplicationBundleDeployService(
            ApplicationDataService dataService,
            ApplicationFunctionStore functionStore,
            ApplicationBundleMetadataService metadataService,
            PlatformSchedulerService schedulerService,
            ApplicationSqlBindingService sqlBindingService,
            ApplicationBundleSnapshotStore snapshotStore,
            ObjectMapper objectMapper
    ) {
        this.dataService = dataService;
        this.functionStore = functionStore;
        this.metadataService = metadataService;
        this.schedulerService = schedulerService;
        this.sqlBindingService = sqlBindingService;
        this.snapshotStore = snapshotStore;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> deploy(String appId, BundleManifest manifest) {
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            if (manifest.displayName() != null) {
                dataService.register(
                        appId,
                        manifest.displayName(),
                        manifest.tablePrefix(),
                        manifest.schemaName()
                );
                applied.add("register");
            }
        } catch (Exception ex) {
            errors.add("register: " + ex.getMessage());
        }

        if (manifest.objects() != null) {
            for (BundleObject object : manifest.objects()) {
                try {
                    if (metadataService.deployObject(object) == ApplicationBundleMetadataService.DeployOutcome.APPLIED) {
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
                List<ApplicationDataService.MigrationScript> scripts = manifest.migrations().stream()
                        .map(script -> new ApplicationDataService.MigrationScript(script.id(), script.sql()))
                        .toList();
                Map<String, Object> result = dataService.migrate(appId, manifest.version(), scripts);
                applied.addAll((List<String>) result.get("applied"));
                skipped.addAll((List<String>) result.get("skipped"));
            } catch (Exception ex) {
                errors.add("migrations: " + ex.getMessage());
            }
        }

        if (manifest.functions() != null) {
            for (BundleFunction function : manifest.functions()) {
                try {
                    deployFunction(appId, function);
                    applied.add("function:" + function.functionName());
                } catch (Exception ex) {
                    errors.add("function:" + function.functionName() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.bindings() != null) {
            for (BundleSqlBinding binding : manifest.bindings()) {
                try {
                    sqlBindingService.deploy(appId, new ApplicationSqlBindingService.DeploySqlBindingRequest(
                            binding.objectPath(),
                            binding.variable(),
                            binding.query(),
                            binding.refresh(),
                            binding.refreshIntervalMs(),
                            binding.valueField(),
                            binding.triggerObjectPath(),
                            binding.triggerFunctionName(),
                            binding.enabled()
                    ));
                    applied.add("binding:" + binding.variable());
                } catch (Exception ex) {
                    errors.add("binding:" + binding.variable() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.schedules() != null) {
            for (BundleSchedule schedule : manifest.schedules()) {
                try {
                    String actionJson = objectMapper.writeValueAsString(schedule.action());
                    schedulerService.upsert(new PlatformSchedulerService.PlatformSchedule(
                            schedule.scheduleId(),
                            appId,
                            schedule.enabled(),
                            schedule.intervalMs(),
                            schedule.actionType(),
                            actionJson
                    ));
                    applied.add("schedule:" + schedule.scheduleId());
                } catch (Exception ex) {
                    errors.add("schedule:" + schedule.scheduleId() + ": " + ex.getMessage());
                }
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

        return response;
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

    private void deployFunction(String appId, BundleFunction function) throws Exception {
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
    }

    public record BundleManifest(
            String version,
            String displayName,
            String tablePrefix,
            String schemaName,
            List<BundleObject> objects,
            List<BundleDashboard> dashboards,
            List<BundleWorkflow> workflows,
            List<BundleMigration> migrations,
            List<BundleFunction> functions,
            List<BundleSqlBinding> bindings,
            List<BundleSchedule> schedules,
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

    public record BundleSchedule(
            String scheduleId,
            boolean enabled,
            long intervalMs,
            String actionType,
            Map<String, Object> action
    ) {
    }
}
