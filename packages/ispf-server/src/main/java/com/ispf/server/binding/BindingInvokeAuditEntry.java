package com.ispf.server.binding;

import java.time.Instant;
import java.util.UUID;

public record BindingInvokeAuditEntry(
        UUID id,
        String bindingKind,
        String objectPath,
        String ruleId,
        String ruleName,
        String triggerKind,
        String targetVariable,
        boolean success,
        boolean changed,
        String errorMessage,
        Integer durationMs,
        String detailJson,
        Instant invokedAt
) {
}
