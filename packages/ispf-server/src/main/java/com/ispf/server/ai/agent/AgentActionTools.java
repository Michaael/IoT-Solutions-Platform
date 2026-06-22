package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.ObjectEvent;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.application.bff.BffWireMapper;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FW-46: platform action tools (invoke, search, events, models).
 */
final class AgentActionTools {

    private static final int SEARCH_DEFAULT_LIMIT = 50;
    private static final int SEARCH_MAX_LIMIT = 100;

    private AgentActionTools() {
    }

    static List<PlatformAgentTool> all(
            FunctionService functionService,
            ApplicationFunctionStore functionStore,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            EventService eventService,
            ModelRegistry modelRegistry,
            ObjectMapper objectMapper
    ) {
        return List.of(
                invokeBffTool(functionService, functionStore, objectAccessService, objectMapper),
                invokeTreeFunctionTool(functionService, objectAccessService, objectMapper),
                searchObjectsTool(objectManager, objectAccessService, tenantScopeService),
                listObjectModelsTool(modelRegistry),
                fireEventTool(eventService, objectAccessService),
                listEventsTool(eventService, objectAccessService)
        );
    }

    private static PlatformAgentTool invokeBffTool(
            FunctionService functionService,
            ApplicationFunctionStore functionStore,
            ObjectAccessService objectAccessService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "invoke_bff";
            }

            @Override
            public String description() {
                return "Invoke application BFF function (e.g. mes_listOrders). "
                        + "Args: objectPath, functionName, optional inputRows (list of maps), optional wireProfile.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) throws Exception {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    objectPath = stringArg(arguments, "path");
                }
                String functionName = stringArg(arguments, "functionName");
                if (objectPath.isBlank() || functionName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and functionName are required");
                }
                objectAccessService.requireInvoke(objectPath, context.authentication());
                DataRecord output = functionService.invoke(
                        objectPath,
                        functionName,
                        parseInputPayload(arguments)
                );
                DataSchema outputSchema = resolveOutputSchema(functionStore, objectMapper, objectPath, functionName);
                String wireProfile = optionalString(arguments, "wireProfile");
                Map<String, Object> wire = BffWireMapper.toWire(output, wireProfile, outputSchema);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("objectPath", objectPath);
                result.put("functionName", functionName);
                result.put("wire", wire);
                return result;
            }
        };
    }

    private static PlatformAgentTool invokeTreeFunctionTool(
            FunctionService functionService,
            ObjectAccessService objectAccessService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "invoke_tree_function";
            }

            @Override
            public String description() {
                return "Invoke a function registered on an object in the tree. "
                        + "Args: objectPath, functionName, optional inputRows (list of maps).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    objectPath = stringArg(arguments, "path");
                }
                String functionName = stringArg(arguments, "functionName");
                if (objectPath.isBlank() || functionName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and functionName are required");
                }
                objectAccessService.requireInvoke(objectPath, context.authentication());
                DataRecord output = functionService.invoke(
                        objectPath,
                        functionName,
                        parseInputPayload(arguments)
                );
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("objectPath", objectPath);
                result.put("functionName", functionName);
                result.put("rows", output.rows());
                if (output.schema() != null) {
                    result.put("schemaName", output.schema().name());
                }
                return result;
            }
        };
    }

    private static PlatformAgentTool searchObjectsTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "search_objects";
            }

            @Override
            public String description() {
                return "Search object tree by path/displayName substring and optional type. "
                        + "Args: query (required), optional type (DEVICE|DASHBOARD|...), optional parentPrefix, limit (default 50).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);
                if (query.isBlank()) {
                    return Map.of("status", "ERROR", "error", "query is required");
                }
                String typeFilter = stringArg(arguments, "type").toUpperCase(Locale.ROOT);
                String parentPrefix = stringArg(arguments, "parentPrefix");
                int limit = intArg(arguments, "limit", SEARCH_DEFAULT_LIMIT);
                limit = Math.max(1, Math.min(limit, SEARCH_MAX_LIMIT));

                var auth = context.authentication();
                List<Map<String, Object>> matches = new ArrayList<>();
                for (PlatformObject node : objectManager.tree().all()) {
                    if (matches.size() >= limit) {
                        break;
                    }
                    if (!parentPrefix.isBlank() && !node.path().startsWith(parentPrefix)) {
                        continue;
                    }
                    if (!typeFilter.isBlank() && !node.type().name().equalsIgnoreCase(typeFilter)) {
                        continue;
                    }
                    if (!tenantScopeService.isPathVisible(node.path(), auth)) {
                        continue;
                    }
                    if (!objectAccessService.canRead(node.path(), auth)) {
                        continue;
                    }
                    String haystack = (node.path() + " " + node.displayName()).toLowerCase(Locale.ROOT);
                    if (!haystack.contains(query)) {
                        continue;
                    }
                    matches.add(Map.of(
                            "path", node.path(),
                            "type", node.type().name(),
                            "displayName", node.displayName(),
                            "templateId", node.templateId() != null ? node.templateId() : ""
                    ));
                }
                return Map.of("status", "OK", "query", query, "count", matches.size(), "objects", matches);
            }
        };
    }

    private static PlatformAgentTool listObjectModelsTool(ModelRegistry modelRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_object_models";
            }

            @Override
            public String description() {
                return "List platform object model templates (templateId for create_object). Optional query filter.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (ModelDefinition model : modelRegistry.all()) {
                    String haystack = (model.name() + " " + model.description() + " "
                            + model.targetObjectType()).toLowerCase(Locale.ROOT);
                    if (!query.isBlank() && !haystack.contains(query)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("templateId", model.name());
                    row.put("modelId", model.id());
                    row.put("description", model.description());
                    row.put("targetObjectType", model.targetObjectType().name());
                    row.put("variableCount", model.variables().size());
                    row.put("functionCount", model.functions().size());
                    rows.add(row);
                }
                return Map.of("status", "OK", "count", rows.size(), "models", rows);
            }
        };
    }

    private static PlatformAgentTool fireEventTool(
            EventService eventService,
            ObjectAccessService objectAccessService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "fire_event";
            }

            @Override
            public String description() {
                return "Fire an object event (e.g. thresholdExceeded). "
                        + "Args: objectPath, eventName, optional appId, optional inputRows.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    objectPath = stringArg(arguments, "path");
                }
                String eventName = stringArg(arguments, "eventName");
                if (objectPath.isBlank() || eventName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and eventName are required");
                }
                objectAccessService.requireInvoke(objectPath, context.authentication());
                ObjectEvent event = eventService.fire(
                        objectPath,
                        eventName,
                        parseInputPayload(arguments),
                        optionalString(arguments, "appId")
                );
                return Map.of(
                        "status", "OK",
                        "eventId", event.id(),
                        "objectPath", event.objectPath(),
                        "eventName", event.eventName(),
                        "level", event.level().name(),
                        "timestamp", event.timestamp().toString()
                );
            }
        };
    }

    private static PlatformAgentTool listEventsTool(
            EventService eventService,
            ObjectAccessService objectAccessService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_events";
            }

            @Override
            public String description() {
                return "List recent event journal entries. Args: optional objectPath, limit (default 20, max 100).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = optionalString(arguments, "objectPath");
                if (objectPath == null) {
                    objectPath = optionalString(arguments, "path");
                }
                int limit = intArg(arguments, "limit", 20);
                if (objectPath != null && !objectPath.isBlank()) {
                    objectAccessService.requireRead(objectPath, context.authentication());
                }
                List<ObjectEvent> events = eventService.list(objectPath, limit);
                List<Map<String, Object>> rows = events.stream()
                        .map(event -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", event.id());
                            row.put("objectPath", event.objectPath());
                            row.put("eventName", event.eventName());
                            row.put("level", event.level().name());
                            row.put("timestamp", event.timestamp().toString());
                            return row;
                        })
                        .toList();
                return Map.of("status", "OK", "count", rows.size(), "events", rows);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static DataRecordPayloadRequest parseInputPayload(Map<String, Object> arguments) {
        Object input = arguments.get("input");
        if (input instanceof Map<?, ?> inputMap) {
            Object rows = inputMap.get("rows");
            if (rows instanceof List<?> list) {
                return new DataRecordPayloadRequest(null, castRowList(list));
            }
        }
        Object rows = arguments.get("inputRows");
        if (rows instanceof List<?> list) {
            return new DataRecordPayloadRequest(null, castRowList(list));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRowList(List<?> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    row.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                rows.add(row);
            }
        }
        return rows.isEmpty() ? List.of(Map.of()) : rows;
    }

    private static DataSchema resolveOutputSchema(
            ApplicationFunctionStore functionStore,
            ObjectMapper objectMapper,
            String objectPath,
            String functionName
    ) {
        return functionStore.findLatest(objectPath, functionName)
                .map(deployed -> {
                    try {
                        return objectMapper.readValue(deployed.outputSchemaJson(), DataSchema.class);
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
