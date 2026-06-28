package com.ispf.server.application.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ApplicationSqlBindingService {

    private static final DataSchema SINGLE_VALUE_SCHEMA = DataSchema.builder("sqlBindingValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private final ApplicationSqlBindingStore store;
    private final ApplicationSchemaSession schemaSession;
    private final ApplicationDataStore dataStore;
    private final ObjectManager objectManager;
    private final AlertRuleService alertRuleService;
    private final BindingInvokeAuditService bindingAuditService;

    public ApplicationSqlBindingService(
            ApplicationSqlBindingStore store,
            ApplicationSchemaSession schemaSession,
            ApplicationDataStore dataStore,
            ObjectManager objectManager,
            @Lazy AlertRuleService alertRuleService,
            BindingInvokeAuditService bindingAuditService
    ) {
        this.store = store;
        this.schemaSession = schemaSession;
        this.dataStore = dataStore;
        this.objectManager = objectManager;
        this.alertRuleService = alertRuleService;
        this.bindingAuditService = bindingAuditService;
    }

    public void deploy(String appId, DeploySqlBindingRequest request) {
        String valueField = request.valueField() != null && !request.valueField().isBlank()
                ? request.valueField()
                : "value";
        String refreshMode = normalizeRefreshMode(request.refresh());
        Long intervalMs = request.refreshIntervalMs();
        if ("on_schedule".equals(refreshMode) && (intervalMs == null || intervalMs <= 0)) {
            intervalMs = 30_000L;
        }
        store.upsert(new ApplicationSqlBindingStore.SqlBinding(
                UUID.randomUUID(),
                appId,
                request.objectPath(),
                request.variable(),
                request.query(),
                refreshMode,
                intervalMs,
                valueField,
                request.triggerObjectPath(),
                request.triggerFunctionName(),
                request.enabled() == null || request.enabled(),
                null
        ));
        ensureVariable(request.objectPath(), request.variable());
        store.listByApp(appId).stream()
                .filter(binding -> binding.objectPath().equals(request.objectPath())
                        && binding.variableName().equals(request.variable()))
                .findFirst()
                .ifPresent(binding -> executeRefresh(binding, "MANUAL"));
    }

    public List<Map<String, Object>> list(String appId) {
        return store.listByApp(appId).stream().map(this::toMap).toList();
    }

    public Map<String, Object> refresh(String appId, String objectPath, String variableName) {
        store.listByApp(appId).stream()
                .filter(binding -> binding.objectPath().equals(objectPath)
                        && binding.variableName().equals(variableName))
                .findFirst()
                .ifPresent(binding -> executeRefresh(binding, "MANUAL"));
        return Map.of(
                "appId", appId,
                "objectPath", objectPath,
                "variable", variableName,
                "status", "refreshed"
        );
    }

    public void refreshBinding(ApplicationSqlBindingStore.SqlBinding binding) {
        executeRefresh(binding, triggerForRefreshMode(binding.refreshMode()));
    }

    public void refreshAfterFunction(String appId, String objectPath, String functionName) {
        for (ApplicationSqlBindingStore.SqlBinding binding : store.listForFunctionSuccess(appId, objectPath, functionName)) {
            executeRefresh(binding, "FUNCTION_SUCCESS");
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void refreshScheduledBindings() {
        for (ApplicationSqlBindingStore.SqlBinding binding : store.listEnabledForSchedule()) {
            long intervalMs = binding.refreshIntervalMs() != null ? binding.refreshIntervalMs() : 30_000L;
            if (binding.lastRefreshedAt() != null
                    && binding.lastRefreshedAt().plusMillis(intervalMs).isAfter(java.time.Instant.now())) {
                continue;
            }
            executeRefresh(binding, "SCHEDULE");
        }
    }

    public void refreshBinding(String appId, String objectPath, String variableName) {
        store.listByApp(appId).stream()
                .filter(binding -> binding.objectPath().equals(objectPath)
                        && binding.variableName().equals(variableName))
                .findFirst()
                .ifPresent(binding -> executeRefresh(binding, "MANUAL"));
    }

    private void executeRefresh(ApplicationSqlBindingStore.SqlBinding binding, String triggerKind) {
        long start = System.nanoTime();
        boolean success = true;
        boolean changed = true;
        String error = null;
        try {
            String schemaName = resolveSchemaName(binding.appId());
            Object[] extractedHolder = new Object[1];
            schemaSession.runInSchema(schemaName, () -> {
                List<Map<String, Object>> rows = dataStore.queryForList(binding.querySql());
                if (rows.isEmpty()) {
                    extractedHolder[0] = 0;
                    return;
                }
                Map<String, Object> row = normalizeRow(rows.getFirst());
                String field = binding.valueField() != null ? binding.valueField() : "value";
                Object value = row.get(field.toLowerCase());
                if (value == null) {
                    value = row.get(field);
                }
                if (value == null && row.size() == 1) {
                    value = row.values().iterator().next();
                }
                extractedHolder[0] = value;
            });
            DataRecord record = toValueRecord(extractedHolder[0], binding);
            schemaSession.runWithPlatformCatalog(() ->
                    objectManager.setSystemVariableValue(binding.objectPath(), binding.variableName(), record)
            );
            store.markRefreshed(binding.id());
            schemaSession.runWithPlatformCatalog(() ->
                    alertRuleService.processVariableChange(binding.objectPath(), binding.variableName())
            );
        } catch (RuntimeException ex) {
            success = false;
            changed = false;
            error = ex.getMessage();
            throw ex;
        } finally {
            bindingAuditService.recordSql(
                    binding.objectPath(),
                    binding.id().toString(),
                    binding.variableName(),
                    triggerKind,
                    success,
                    changed,
                    error,
                    System.nanoTime() - start
            );
        }
    }

    private static String triggerForRefreshMode(String refreshMode) {
        return switch (normalizeRefreshMode(refreshMode)) {
            case "on_function_success" -> "FUNCTION_SUCCESS";
            case "on_event" -> "EVENT";
            default -> "SCHEDULE";
        };
    }

    private void ensureVariable(String objectPath, String variableName) {
        schemaSession.runWithPlatformCatalog(() -> {
            PlatformObject node = objectManager.require(objectPath);
            if (node.getVariable(variableName).isEmpty()) {
                node.addVariable(new Variable(
                        variableName,
                        SINGLE_VALUE_SCHEMA,
                        true,
                        false, DataRecord.single(SINGLE_VALUE_SCHEMA, Map.of("value", 0.0))
                ));
                objectManager.persistNodeTree(objectPath);
            }
        });
    }

    private static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            normalized.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        return normalized;
    }

    private static DataRecord toValueRecord(Object value, ApplicationSqlBindingStore.SqlBinding binding) {
        com.ispf.core.model.FieldType fieldType = resolveValueFieldType(binding);
        return switch (fieldType) {
            case STRING -> DataRecord.single(
                    DataSchema.builder("sqlBindingValue").field("value", FieldType.STRING).build(),
                    Map.of("value", value != null ? String.valueOf(value) : "")
            );
            case INTEGER, LONG -> {
                long numeric = 0L;
                if (value instanceof Number number) {
                    numeric = number.longValue();
                } else if (value != null) {
                    try {
                        numeric = Long.parseLong(value.toString());
                    } catch (NumberFormatException ignored) {
                        numeric = 0L;
                    }
                }
                yield DataRecord.single(
                        DataSchema.builder("sqlBindingValue").field("value", fieldType).build(),
                        Map.of("value", numeric)
                );
            }
            default -> {
                double numeric = 0.0;
                if (value instanceof Number number) {
                    numeric = number.doubleValue();
                } else if (value != null) {
                    try {
                        numeric = Double.parseDouble(value.toString());
                    } catch (NumberFormatException ignored) {
                        numeric = 0.0;
                    }
                }
                yield DataRecord.single(SINGLE_VALUE_SCHEMA, Map.of("value", numeric));
            }
        };
    }

    private static com.ispf.core.model.FieldType resolveValueFieldType(ApplicationSqlBindingStore.SqlBinding binding) {
        if (binding == null) {
            return FieldType.DOUBLE;
        }
        String field = binding.valueField() != null ? binding.valueField() : "value";
        if ("value".equalsIgnoreCase(field) || field.isBlank()) {
            return FieldType.DOUBLE;
        }
        if (field.toLowerCase().contains("code") || field.toLowerCase().contains("name") || field.toLowerCase().contains("status")) {
            return FieldType.STRING;
        }
        return FieldType.DOUBLE;
    }

    private static DataRecord toValueRecord(Object value) {
        return toValueRecord(value, null);
    }

    private String resolveSchemaName(String appId) {
        return dataStore.findApp(appId)
                .map(app -> String.valueOf(app.get("schema_name")))
                .filter(name -> name != null && !name.isBlank() && !"null".equals(name))
                .orElse(ApplicationSchemaSupport.defaultSchemaName(appId));
    }

    private static String normalizeRefreshMode(String refresh) {
        if (refresh == null || refresh.isBlank()) {
            return "on_schedule";
        }
        return switch (refresh) {
            case "on_function_success" -> "on_function_success";
            case "on_event" -> "on_event";
            default -> "on_schedule";
        };
    }

    private Map<String, Object> toMap(ApplicationSqlBindingStore.SqlBinding binding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("objectPath", binding.objectPath());
        map.put("variable", binding.variableName());
        map.put("refresh", binding.refreshMode());
        map.put("refreshIntervalMs", binding.refreshIntervalMs());
        map.put("valueField", binding.valueField());
        map.put("enabled", binding.enabled());
        map.put("lastRefreshedAt", binding.lastRefreshedAt());
        return map;
    }

    public record DeploySqlBindingRequest(
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
}
