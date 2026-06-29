package com.ispf.core.binding;

/**
 * Where a binding rule writes its computed result.
 */
public record BindingTarget(
        String kind,
        String variableName,
        String field,
        String path,
        String eventName
) {

    public BindingTarget {
        kind = BindingTargetKind.normalize(kind);
        if (field == null || field.isBlank()) {
            field = "value";
        }
    }

    /** Legacy constructor — variable target. */
    public BindingTarget(String variableName, String field) {
        this(BindingTargetKind.VARIABLE, variableName, field, null, null);
    }

    public boolean isVariable() {
        return BindingTargetKind.VARIABLE.equals(kind);
    }

    public boolean isContext() {
        return BindingTargetKind.CONTEXT.equals(kind);
    }

    public boolean isEvent() {
        return BindingTargetKind.EVENT.equals(kind);
    }
}
