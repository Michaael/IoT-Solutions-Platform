package com.ispf.server.application.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.application.script.FunctionScriptEngine;
import com.ispf.server.application.script.ScriptExecutionContext;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ApplicationFunctionRuntime {

    private final ApplicationFunctionStore store;
    private final FunctionScriptEngine scriptEngine;
    private final ApplicationDataStore dataStore;
    private final ApplicationSchemaSession schemaSession;
    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final FunctionInvokeAuditService auditService;

    public ApplicationFunctionRuntime(
            ApplicationFunctionStore store,
            FunctionScriptEngine scriptEngine,
            ApplicationDataStore dataStore,
            ApplicationSchemaSession schemaSession,
            ObjectManager objectManager,
            ObjectMapper objectMapper,
            FunctionInvokeAuditService auditService
    ) {
        this.store = store;
        this.scriptEngine = scriptEngine;
        this.dataStore = dataStore;
        this.schemaSession = schemaSession;
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        return invoke(objectPath, functionName, input, 0);
    }

    private DataRecord invoke(String objectPath, String functionName, DataRecord input, int depth) {
        if (depth > ScriptExecutionContext.MAX_CALL_DEPTH) {
            throw new IllegalStateException("Application function call depth exceeded limit of "
                    + ScriptExecutionContext.MAX_CALL_DEPTH);
        }

        ApplicationFunctionHandler.DeployedFunction deployed = store.findLatest(objectPath, functionName)
                .orElseThrow(() -> new IllegalStateException("Deployed function missing: " + functionName));

        ensureDescriptor(objectPath, functionName, deployed);

        DataSchema outputSchema = readSchema(deployed.outputSchemaJson());
        String schemaName = resolveSchemaName(deployed.appId());
        int nextDepth = depth + 1;
        ScriptExecutionContext context = (nestedPath, nestedName, nestedInput) -> {
            ApplicationFunctionHandler.DeployedFunction nested = store.findLatest(nestedPath, nestedName)
                    .orElseThrow(() -> new IllegalStateException("Deployed function missing: " + nestedName));
            DataSchema nestedInputSchema = readSchema(nested.inputSchemaJson());
            DataRecord nestedRecord = DataRecord.single(
                    nestedInputSchema,
                    nestedInput != null ? nestedInput : Map.of()
            );
            return invoke(nestedPath, nestedName, nestedRecord, nextDepth);
        };

        try {
            DataRecord[] outputHolder = new DataRecord[1];
            schemaSession.runInSchema(schemaName, () -> outputHolder[0] = switch (deployed.sourceType()) {
                case "script" -> scriptEngine.execute(
                        deployed.sourceBody(),
                        input,
                        outputSchema,
                        context
                );
                default -> throw new IllegalStateException("Unsupported source type: " + deployed.sourceType());
            });
            auditService.record(deployed.appId(), objectPath, functionName, true, null);
            return outputHolder[0];
        } catch (RuntimeException ex) {
            auditService.record(deployed.appId(), objectPath, functionName, false, ex.getMessage());
            throw ex;
        }
    }

    private String resolveSchemaName(String appId) {
        return dataStore.findApp(appId)
                .map(app -> String.valueOf(app.get("schema_name")))
                .filter(name -> name != null && !name.isBlank() && !"null".equals(name))
                .orElse(ApplicationSchemaSupport.defaultSchemaName(appId));
    }

    private void ensureDescriptor(String objectPath, String functionName, ApplicationFunctionHandler.DeployedFunction deployed) {
        schemaSession.runWithPlatformCatalog(() -> {
            var node = objectManager.require(objectPath);
            if (!node.functions().containsKey(functionName)) {
                DataSchema inputSchema = readSchema(deployed.inputSchemaJson());
                node.addFunction(new FunctionDescriptor(
                        functionName,
                        "Application function " + functionName,
                        inputSchema,
                        readSchema(deployed.outputSchemaJson())
                ));
                objectManager.persistNodeTree(objectPath);
            }
        });
    }

    private DataSchema readSchema(String json) {
        try {
            return objectMapper.readValue(json, DataSchema.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid function schema JSON", ex);
        }
    }

    public static Map<String, Object> rowAsMap(DataRecord record) {
        if (record == null || record.rowCount() == 0) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(record.firstRow());
    }

    public static String errorCode(Map<String, Object> row) {
        Object value = row.get("error_code");
        return value == null ? "OK" : String.valueOf(value);
    }
}
