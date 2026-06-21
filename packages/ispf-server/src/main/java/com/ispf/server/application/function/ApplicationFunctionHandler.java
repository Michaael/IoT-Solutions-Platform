package com.ispf.server.application.function;

import com.ispf.core.model.DataRecord;
import com.ispf.server.function.FunctionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class ApplicationFunctionHandler implements FunctionHandler {

    private final ApplicationFunctionStore store;
    private final ApplicationFunctionRuntime runtime;

    public ApplicationFunctionHandler(ApplicationFunctionStore store, ApplicationFunctionRuntime runtime) {
        this.store = store;
        this.runtime = runtime;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (store.findLatest(objectPath, functionName).isPresent()) {
            return true;
        }
        return store.findLatestByTreeFunctionPath(objectPath).isPresent();
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        return store.findLatestByTreeFunctionPath(objectPath)
                .map(fn -> runtime.invoke(fn.objectPath(), fn.functionName(), input))
                .orElseGet(() -> runtime.invoke(objectPath, functionName, input));
    }

    public record DeployedFunction(
            java.util.UUID id,
            String appId,
            String objectPath,
            String functionName,
            String version,
            String sourceType,
            String sourceBody,
            String inputSchemaJson,
            String outputSchemaJson
    ) {
    }
}
