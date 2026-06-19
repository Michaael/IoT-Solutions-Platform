package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;

import java.util.Map;

public interface ScriptExecutionContext {

    int MAX_CALL_DEPTH = 8;

    DataRecord invokeFunction(String objectPath, String functionName, Map<String, Object> input);
}
