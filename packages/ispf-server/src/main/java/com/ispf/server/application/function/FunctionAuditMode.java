package com.ispf.server.application.function;

public enum FunctionAuditMode {
    ERRORS,
    ALL;

    public static FunctionAuditMode parse(String value) {
        if (value == null || value.isBlank()) {
            return ERRORS;
        }
        try {
            return FunctionAuditMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ERRORS;
        }
    }
}
