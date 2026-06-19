package com.ispf.server.application.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.api.ApplicationController;
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
    private final PlatformSchedulerService schedulerService;
    private final ObjectMapper objectMapper;

    public ApplicationBundleDeployService(
            ApplicationDataService dataService,
            ApplicationFunctionStore functionStore,
            PlatformSchedulerService schedulerService,
            ObjectMapper objectMapper
    ) {
        this.dataService = dataService;
        this.functionStore = functionStore;
        this.schedulerService = schedulerService;
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
        return response;
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
            List<BundleMigration> migrations,
            List<BundleFunction> functions,
            List<BundleSchedule> schedules
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

    public record BundleSchedule(
            String scheduleId,
            boolean enabled,
            long intervalMs,
            String actionType,
            Map<String, Object> action
    ) {
    }
}
