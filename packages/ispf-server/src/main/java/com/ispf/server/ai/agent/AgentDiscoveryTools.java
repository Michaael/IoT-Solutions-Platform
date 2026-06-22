package com.ispf.server.ai.agent;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * FW-47: discovery tools (functions, events, variable schemas) before invoke/fire/set.
 */
final class AgentDiscoveryTools {

    private AgentDiscoveryTools() {
    }

    static List<PlatformAgentTool> all(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ApplicationFunctionStore functionStore,
            ApplicationEventCatalogService eventCatalogService,
            ObjectMapper objectMapper
    ) {
        return List.of(
                listFunctionsTool(objectManager, objectAccessService, tenantScopeService, functionStore),
                getFunctionTool(objectManager, objectAccessService, tenantScopeService, functionStore, objectMapper),
                listEventCatalogTool(eventCatalogService),
                getEventSchemaTool(objectManager, objectAccessService, tenantScopeService, eventCatalogService),
                describeVariablesTool(objectManager, objectAccessService, tenantScopeService)
        );
    }

    private static PlatformAgentTool listFunctionsTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ApplicationFunctionStore functionStore
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_functions";
            }

            @Override
            public String description() {
                return "List callable functions on an object (tree descriptors + deployed app/BFF). "
                        + "Args: objectPath (required), optional appId filter, optional query.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = requireObjectPath(arguments);
                if (objectPath == null) {
                    return Map.of("status", "ERROR", "error", "objectPath is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(objectPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + objectPath);
                }
                objectAccessService.requireRead(objectPath, auth);

                String appFilter = stringArg(arguments, "appId");
                String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);

                PlatformObject node = objectManager.require(objectPath);
                List<Map<String, Object>> functions = new ArrayList<>();
                for (FunctionDescriptor fn : node.functions().values()) {
                    if (!matchesQuery(query, fn.name(), fn.description())) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("functionName", fn.name());
                    row.put("source", "tree");
                    row.put("description", fn.description() != null ? fn.description() : "");
                    row.put("hasScript", fn.hasScriptBody());
                    functions.add(row);
                }

                for (ApplicationFunctionHandler.DeployedFunction deployed : functionStore.listLatestByObjectPath(objectPath)) {
                    if (!appFilter.isBlank() && !appFilter.equals(deployed.appId())) {
                        continue;
                    }
                    if (!matchesQuery(query, deployed.functionName(), deployed.appId())) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("functionName", deployed.functionName());
                    row.put("source", "deployed");
                    row.put("appId", deployed.appId());
                    row.put("version", deployed.version());
                    row.put("sourceType", deployed.sourceType());
                    functions.add(row);
                }

                return Map.of(
                        "status", "OK",
                        "objectPath", objectPath,
                        "count", functions.size(),
                        "functions", functions,
                        "hint", "Use get_function for input/output schemas before invoke_bff or invoke_tree_function"
                );
            }
        };
    }

    private static PlatformAgentTool getFunctionTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ApplicationFunctionStore functionStore,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_function";
            }

            @Override
            public String description() {
                return "Get function signature (input/output schema). "
                        + "Args: objectPath, functionName (required). Returns tree or deployed definition.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = requireObjectPath(arguments);
                String functionName = stringArg(arguments, "functionName");
                if (objectPath == null || functionName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and functionName are required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(objectPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + objectPath);
                }
                objectAccessService.requireRead(objectPath, auth);

                PlatformObject node = objectManager.require(objectPath);
                FunctionDescriptor treeFn = node.functions().get(functionName);
                if (treeFn != null) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("objectPath", objectPath);
                    result.put("functionName", functionName);
                    result.put("source", "tree");
                    result.put("description", treeFn.description());
                    result.put("inputSchema", schemaPreview(treeFn.inputSchema()));
                    result.put("outputSchema", schemaPreview(treeFn.outputSchema()));
                    result.put("sourceType", treeFn.sourceType());
                    result.put("version", treeFn.version());
                    return result;
                }

                Optional<ApplicationFunctionHandler.DeployedFunction> deployed =
                        functionStore.findLatest(objectPath, functionName);
                if (deployed.isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Function not found: " + functionName + " on " + objectPath
                    );
                }
                ApplicationFunctionHandler.DeployedFunction fn = deployed.get();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("objectPath", objectPath);
                result.put("functionName", functionName);
                result.put("source", "deployed");
                result.put("appId", fn.appId());
                result.put("version", fn.version());
                result.put("sourceType", fn.sourceType());
                result.put("inputSchema", parseSchemaJson(objectMapper, fn.inputSchemaJson()));
                result.put("outputSchema", parseSchemaJson(objectMapper, fn.outputSchemaJson()));
                return result;
            }
        };
    }

    private static PlatformAgentTool listEventCatalogTool(ApplicationEventCatalogService eventCatalogService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_event_catalog";
            }

            @Override
            public String description() {
                return "List bundle event catalog for an application. Args: appId (required), optional query.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                if (appId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId is required");
                }
                String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);
                List<Map<String, Object>> events = new ArrayList<>();
                for (Map<String, Object> entry : eventCatalogService.listEvents(appId)) {
                    String id = String.valueOf(entry.get("id"));
                    if (!query.isBlank() && !id.toLowerCase(Locale.ROOT).contains(query)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("eventId", id);
                    row.put("roles", entry.get("roles"));
                    if (entry.containsKey("payloadSchema")) {
                        row.put("hasPayloadSchema", true);
                    }
                    events.add(row);
                }
                return Map.of(
                        "status", "OK",
                        "appId", appId,
                        "count", events.size(),
                        "events", events,
                        "hint", "Use get_event_schema for object-level payload fields before fire_event"
                );
            }
        };
    }

    private static PlatformAgentTool getEventSchemaTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ApplicationEventCatalogService eventCatalogService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_event_schema";
            }

            @Override
            public String description() {
                return "Get event payload schema for fire_event. "
                        + "Args: objectPath, eventName (required), optional appId for bundle catalog merge.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = requireObjectPath(arguments);
                String eventName = stringArg(arguments, "eventName");
                if (objectPath == null || eventName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and eventName are required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(objectPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + objectPath);
                }
                objectAccessService.requireRead(objectPath, auth);

                PlatformObject node = objectManager.require(objectPath);
                EventDescriptor treeEvent = node.events().get(eventName);
                if (treeEvent == null) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Unknown event on object: " + eventName
                    );
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("objectPath", objectPath);
                result.put("eventName", eventName);
                result.put("level", treeEvent.level().name());
                result.put("description", treeEvent.description());
                result.put("payloadSchema", schemaPreview(treeEvent.payloadSchema()));

                String appId = optionalString(arguments, "appId");
                if (appId != null) {
                    eventCatalogService.listEvents(appId).stream()
                            .filter(entry -> eventName.equals(String.valueOf(entry.get("id"))))
                            .findFirst()
                            .ifPresent(catalog -> {
                                result.put("catalogRoles", catalog.get("roles"));
                                if (catalog.get("payloadSchema") != null) {
                                    result.put("catalogPayloadSchema", catalog.get("payloadSchema"));
                                }
                            });
                }
                return result;
            }
        };
    }

    private static PlatformAgentTool describeVariablesTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "describe_variables";
            }

            @Override
            public String description() {
                return "Variable schemas on an object (fields, writable, history). "
                        + "Args: objectPath (required), optional name filter.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = requireObjectPath(arguments);
                if (objectPath == null) {
                    return Map.of("status", "ERROR", "error", "objectPath is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(objectPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + objectPath);
                }
                objectAccessService.requireRead(objectPath, auth);

                String nameFilter = stringArg(arguments, "name").toLowerCase(Locale.ROOT);
                PlatformObject node = objectManager.require(objectPath);
                List<Map<String, Object>> variables = new ArrayList<>();
                for (Variable variable : node.variables().values()) {
                    if (!nameFilter.isBlank() && !variable.name().toLowerCase(Locale.ROOT).contains(nameFilter)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", variable.name());
                    row.put("readable", variable.readable());
                    row.put("writable", variable.writable());
                    row.put("historyEnabled", variable.historyEnabled());
                    variable.historyRetentionDays().ifPresent(days -> row.put("historyRetentionDays", days));
                    variable.bindingExpression().ifPresent(expr -> row.put("bindingExpression", expr));
                    row.put("schema", schemaPreview(variable.schema()));
                    variables.add(row);
                }
                return Map.of(
                        "status", "OK",
                        "objectPath", objectPath,
                        "count", variables.size(),
                        "variables", variables
                );
            }
        };
    }

    private static Map<String, Object> schemaPreview(DataSchema schema) {
        Map<String, Object> preview = new LinkedHashMap<>();
        if (schema == null) {
            preview.put("fields", List.of());
            return preview;
        }
        preview.put("name", schema.name());
        preview.put("fields", schema.fields().stream().map(AgentDiscoveryTools::fieldPreview).toList());
        return preview;
    }

    private static Map<String, Object> fieldPreview(FieldDefinition field) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", field.name());
        row.put("type", field.type().name());
        row.put("nullable", field.nullable());
        if (field.description() != null && !field.description().isBlank()) {
            row.put("description", field.description());
        }
        if (field.nestedSchema() != null) {
            row.put("nestedSchema", schemaPreview(field.nestedSchema()));
        }
        return row;
    }

    private static Object parseSchemaJson(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of("fields", List.of());
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private static boolean matchesQuery(String query, String... parts) {
        if (query.isBlank()) {
            return true;
        }
        StringBuilder haystack = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                haystack.append(part).append(' ');
            }
        }
        return haystack.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static String requireObjectPath(Map<String, Object> arguments) {
        String objectPath = stringArg(arguments, "objectPath");
        if (objectPath.isBlank()) {
            objectPath = stringArg(arguments, "path");
        }
        return objectPath.isBlank() ? null : objectPath;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }
}
