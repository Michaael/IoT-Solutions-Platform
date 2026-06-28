package com.ispf.server.application.function;

import java.time.Instant;
import java.util.UUID;

public record FunctionInvokeAuditEntry(
        UUID id,
        String correlationId,
        String objectPath,
        String functionName,
        String appId,
        boolean success,
        String errorMessage,
        String inputJson,
        String outputJson,
        Instant invokedAt
) {
}
