package com.ispf.server.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingInvokeAuditServiceTest {

    @Test
    void shouldRecordErrorsModeOnlyOnFailure() {
        assertTrue(BindingInvokeAuditService.shouldRecord(BindingAuditMode.ERRORS, false, false));
        assertTrue(BindingInvokeAuditService.shouldRecord(BindingAuditMode.ERRORS, false, true));
        assertFalse(BindingInvokeAuditService.shouldRecord(BindingAuditMode.ERRORS, true, true));
    }

    @Test
    void shouldRecordChangesModeOnlyOnSuccessfulChange() {
        assertTrue(BindingInvokeAuditService.shouldRecord(BindingAuditMode.CHANGES, true, true));
        assertFalse(BindingInvokeAuditService.shouldRecord(BindingAuditMode.CHANGES, true, false));
        assertFalse(BindingInvokeAuditService.shouldRecord(BindingAuditMode.CHANGES, false, true));
    }

    @Test
    void shouldRecordAllModeAlways() {
        assertTrue(BindingInvokeAuditService.shouldRecord(BindingAuditMode.ALL, true, false));
        assertTrue(BindingInvokeAuditService.shouldRecord(BindingAuditMode.ALL, false, false));
    }
}
