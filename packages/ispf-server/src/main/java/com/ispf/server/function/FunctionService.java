package com.ispf.server.function;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.api.dto.DataRecordPayloadResolver;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.function.FunctionInvokeAuditService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FunctionService {

    private final ObjectManager objectManager;
    private final List<FunctionHandler> handlers;
    private final ApplicationFunctionStore applicationFunctionStore;
    private final FunctionInvokeAuditService auditService;

    public FunctionService(
            ObjectManager objectManager,
            List<FunctionHandler> handlers,
            ApplicationFunctionStore applicationFunctionStore,
            FunctionInvokeAuditService auditService
    ) {
        this.objectManager = objectManager;
        this.handlers = handlers;
        this.applicationFunctionStore = applicationFunctionStore;
        this.auditService = auditService;
    }

    public DataRecord invoke(String objectPath, String functionName) {
        return invoke(objectPath, functionName, (DataRecordPayloadRequest) null);
    }

    public DataRecord invoke(String objectPath, String functionName, DataRecordPayloadRequest input) {
        if (applicationFunctionStore.findLatestByTreeFunctionPath(objectPath).isPresent()) {
            String appId = resolveAppId(objectPath, functionName);
            DataRecord resolvedInput = resolveInput(input, null);
            for (FunctionHandler handler : handlers) {
                if (handler.supports(objectPath, functionName)) {
                    try {
                        DataRecord result = handler.invoke(objectPath, functionName, resolvedInput);
                        auditService.record(appId, objectPath, functionName, true, null);
                        return result;
                    } catch (RuntimeException ex) {
                        auditService.record(appId, objectPath, functionName, false, ex.getMessage());
                        throw ex;
                    }
                }
            }
            throw new IllegalArgumentException("Unknown application function for tree path: " + objectPath);
        }

        PlatformObject node = objectManager.require(objectPath);
        FunctionDescriptor descriptor = node.functions().get(functionName);
        String appId = resolveAppId(objectPath, functionName);
        DataRecord resolvedInput = resolveInput(input, descriptor);

        for (FunctionHandler handler : handlers) {
            if (handler.supports(objectPath, functionName)) {
                try {
                    DataRecord result = handler.invoke(objectPath, functionName, resolvedInput);
                    auditService.record(appId, objectPath, functionName, true, null);
                    return result;
                } catch (RuntimeException ex) {
                    auditService.record(appId, objectPath, functionName, false, ex.getMessage());
                    throw ex;
                }
            }
        }

        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown function: " + functionName);
        }

        IllegalStateException ex = new IllegalStateException("No handler registered for function: " + functionName);
        auditService.record(appId, objectPath, functionName, false, ex.getMessage());
        throw ex;
    }

    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        return invoke(objectPath, functionName, DataRecordPayloadResolver.fromRecord(input));
    }

    private static DataRecord resolveInput(DataRecordPayloadRequest input, FunctionDescriptor descriptor) {
        DataSchema schema = descriptor != null
                ? descriptor.inputSchema()
                : DataSchema.builder("voidInput").build();
        return DataRecordPayloadResolver.resolve(schema, input);
    }

    private String resolveAppId(String objectPath, String functionName) {
        return applicationFunctionStore.findLatest(objectPath, functionName)
                .map(ApplicationFunctionHandler.DeployedFunction::appId)
                .orElse(null);
    }
}
