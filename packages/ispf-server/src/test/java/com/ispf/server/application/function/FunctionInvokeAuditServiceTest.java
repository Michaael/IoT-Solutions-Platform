package com.ispf.server.application.function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionInvokeAuditServiceTest {

    @Test
    void shouldRecordErrorsModeOnlyOnFailure() {
        assertTrue(FunctionInvokeAuditService.shouldRecord(FunctionAuditMode.ERRORS, false));
        assertFalse(FunctionInvokeAuditService.shouldRecord(FunctionAuditMode.ERRORS, true));
    }

    @Test
    void shouldRecordAllModeAlways() {
        assertTrue(FunctionInvokeAuditService.shouldRecord(FunctionAuditMode.ALL, true));
        assertTrue(FunctionInvokeAuditService.shouldRecord(FunctionAuditMode.ALL, false));
    }
}
