package com.ispf.server.ai.agent;

import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.ApplicationBundlePullFromTreeService;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.core.model.DataSchema;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent tools for application lifecycle (REST D parity): register, migrate, bindings, export.
 */
final class AgentApplicationTools {

    private AgentApplicationTools() {
    }

    static List<PlatformAgentTool> all(
            ApplicationDataService dataService,
            ApplicationSqlBindingService sqlBindingService,
            ApplicationBundleDeployService bundleDeployService,
            ApplicationBundlePullFromTreeService pullFromTreeService,
            ApplicationFunctionStore functionStore,
            ObjectMapper objectMapper,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return List.of(
                registerApplicationTool(dataService),
                applicationDataStatusTool(dataService),
                applicationDataMigrateTool(dataService),
                applicationDataSeedTool(dataService),
                listAppBindingsTool(sqlBindingService),
                deployAppBindingTool(sqlBindingService),
                exportApplicationBundleTool(bundleDeployService),
                rollbackApplicationDeployTool(bundleDeployService),
                pullApplicationFromTreeTool(pullFromTreeService),
                deployAppFunctionTool(functionStore, objectMapper)
        );
    }

    private static PlatformAgentTool registerApplicationTool(ApplicationDataService dataService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "register_application";
            }

            @Override
            public String description() {
                return "Register application record (isolated SQL schema). Args: appId, displayName, "
                        + "optional tablePrefix, schemaName.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                String displayName = stringArg(arguments, "displayName");
                if (appId.isBlank() || displayName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId and displayName are required");
                }
                try {
                    Map<String, Object> result = dataService.register(
                            appId,
                            displayName,
                            stringArg(arguments, "tablePrefix"),
                            stringArg(arguments, "schemaName")
                    );
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool applicationDataStatusTool(ApplicationDataService dataService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "application_data_status";
            }

            @Override
            public String description() {
                return "Schema/migration status for application. Arg: appId.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                if (appId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId is required");
                }
                try {
                    Map<String, Object> result = dataService.status(appId);
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool applicationDataMigrateTool(ApplicationDataService dataService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "application_data_migrate";
            }

            @Override
            public String description() {
                return "Apply SQL migrations to app schema. Args: appId, version, scripts[] "
                        + "({id, sql}). Use get_example_bundle for migration patterns.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                String version = stringArg(arguments, "version");
                Object scriptsRaw = arguments.get("scripts");
                if (appId.isBlank() || version.isBlank() || !(scriptsRaw instanceof List<?> scripts) || scripts.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "appId, version, scripts[] are required");
                }
                try {
                    List<ApplicationDataService.MigrationScript> parsed = new ArrayList<>();
                    for (Object item : scripts) {
                        if (item instanceof Map<?, ?> row) {
                            String id = String.valueOf(row.get("id"));
                            String sql = String.valueOf(row.get("sql"));
                            if (!id.isBlank() && !sql.isBlank()) {
                                parsed.add(new ApplicationDataService.MigrationScript(id, sql));
                            }
                        }
                    }
                    if (parsed.isEmpty()) {
                        return Map.of("status", "ERROR", "error", "scripts must contain {id, sql} entries");
                    }
                    Map<String, Object> result = dataService.migrate(appId, version, parsed);
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool applicationDataSeedTool(ApplicationDataService dataService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "application_data_seed";
            }

            @Override
            public String description() {
                return "Seed demo data for application schema. Args: appId, profile (string).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                String profile = stringArg(arguments, "profile");
                if (appId.isBlank() || profile.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId and profile are required");
                }
                try {
                    Map<String, Object> result = dataService.seed(appId, profile);
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool listAppBindingsTool(ApplicationSqlBindingService sqlBindingService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_app_bindings";
            }

            @Override
            public String description() {
                return "List SQL bindings for application. Arg: appId.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                if (appId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId is required");
                }
                try {
                    return Map.of("status", "OK", "appId", appId, "bindings", sqlBindingService.list(appId));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool deployAppBindingTool(ApplicationSqlBindingService sqlBindingService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_app_binding";
            }

            @Override
            public String description() {
                return "Deploy SQL binding to tree variable. Args: appId, objectPath, variable, query, "
                        + "optional refresh, refreshIntervalMs, valueField, triggerObjectPath, triggerFunctionName, enabled.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                String objectPath = stringArg(arguments, "objectPath");
                String variable = stringArg(arguments, "variable");
                String query = stringArg(arguments, "query");
                if (appId.isBlank() || objectPath.isBlank() || variable.isBlank() || query.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId, objectPath, variable, query are required");
                }
                try {
                    sqlBindingService.deploy(appId, new ApplicationSqlBindingService.DeploySqlBindingRequest(
                            objectPath,
                            variable,
                            query,
                            stringArg(arguments, "refresh"),
                            longArg(arguments, "refreshIntervalMs"),
                            stringArg(arguments, "valueField"),
                            stringArg(arguments, "triggerObjectPath"),
                            stringArg(arguments, "triggerFunctionName"),
                            boolArg(arguments, "enabled", true)
                    ));
                    return Map.of(
                            "status", "OK",
                            "appId", appId,
                            "objectPath", objectPath,
                            "variable", variable
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool exportApplicationBundleTool(ApplicationBundleDeployService bundleDeployService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "export_application_bundle";
            }

            @Override
            public String description() {
                return "Export active bundle manifest for app. Args: appId, optional version.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                if (appId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId is required");
                }
                try {
                    Map<String, Object> manifest = bundleDeployService.exportActiveBundle(
                            appId,
                            stringArg(arguments, "version")
                    );
                    return Map.of("status", "OK", "appId", appId, "manifest", manifest);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool rollbackApplicationDeployTool(ApplicationBundleDeployService bundleDeployService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "rollback_application_deploy";
            }

            @Override
            public String description() {
                return "Rollback application to previous bundle version. Args: appId, version.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                String version = stringArg(arguments, "version");
                if (appId.isBlank() || version.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId and version are required");
                }
                try {
                    Map<String, Object> result = bundleDeployService.rollback(appId, version);
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool pullApplicationFromTreeTool(ApplicationBundlePullFromTreeService pullFromTreeService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "pull_application_from_tree";
            }

            @Override
            public String description() {
                return "Build bundle manifest from live tree. Args: appId, optional sections[], paths[], mergeActive.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                if (appId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId is required");
                }
                try {
                    List<String> sections = listArg(arguments, "sections");
                    List<String> paths = listArg(arguments, "paths");
                    boolean mergeActive = !Boolean.FALSE.equals(arguments.get("mergeActive"));
                    ApplicationBundlePullFromTreeService.PullFromTreeOptions options =
                            new ApplicationBundlePullFromTreeService.PullFromTreeOptions(sections, paths, mergeActive);
                    Map<String, Object> result = pullFromTreeService.pullFromTree(appId, options);
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool deployAppFunctionTool(
            ApplicationFunctionStore functionStore,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_app_function";
            }

            @Override
            public String description() {
                return "Deploy application BFF function (script engine with SQL steps). Args: appId, objectPath, "
                        + "functionName, sourceType=script, sourceBody (JSON steps), optional version, inputSchema, outputSchema. "
                        + "For Java logic on tree use deploy_tree_function sourceType=java. "
                        + "Prefer import_package for full bundles.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                String objectPath = stringArg(arguments, "objectPath");
                String functionName = stringArg(arguments, "functionName");
                String sourceType = AgentFunctionTools.normalizeSourceType(stringArg(arguments, "sourceType"));
                String sourceBody = stringArg(arguments, "sourceBody");
                if (appId.isBlank() || objectPath.isBlank() || functionName.isBlank()
                        || sourceType.isBlank() || sourceBody.isBlank()) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "appId, objectPath, functionName, sourceType=script, sourceBody are required"
                    );
                }
                if (!"script".equals(sourceType)) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "Application functions support sourceType=script only; use deploy_tree_function for java"
                    );
                }
                try {
                    String version = stringArg(arguments, "version");
                    if (version.isBlank()) {
                        version = "1";
                    }
                    DataSchema inputSchema = AgentFunctionTools.schemaFromArg(arguments.get("inputSchema"), functionName + "Input", objectMapper);
                    DataSchema outputSchema = AgentFunctionTools.schemaFromArg(arguments.get("outputSchema"), functionName + "Output", objectMapper);
                    String inputSchemaJson = objectMapper.writeValueAsString(inputSchema);
                    String outputSchemaJson = objectMapper.writeValueAsString(outputSchema);
                    ApplicationFunctionHandler.DeployedFunction deployed = new ApplicationFunctionHandler.DeployedFunction(
                            UUID.randomUUID(),
                            appId,
                            objectPath,
                            functionName,
                            version,
                            sourceType,
                            sourceBody,
                            inputSchemaJson,
                            outputSchemaJson
                    );
                    functionStore.deploy(deployed);
                    return Map.of(
                            "status", "OK",
                            "appId", appId,
                            "objectPath", objectPath,
                            "functionName", functionName,
                            "version", version
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }

        };
    }

    private static List<String> listArg(Map<String, Object> arguments, String key) {
        Object raw = arguments.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String text = String.valueOf(item).trim();
                if (!text.isBlank()) {
                    out.add(text);
                }
            }
        }
        return out;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object raw = args != null ? args.get(key) : null;
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean fallback) {
        Object raw = args != null ? args.get(key) : null;
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }
}
