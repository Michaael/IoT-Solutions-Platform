package com.ispf.core.binding;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * When a binding rule is evaluated.
 */
public record BindingActivators(
        boolean onStartup,
        List<BindingVariableRef> onVariableChange,
        String onEvent,
        long periodicMs,
        @JsonProperty("async") Boolean asyncFlag,
        @JsonProperty("onContextChange") Boolean onContextChangeFlag
) {
    public BindingActivators {
        onVariableChange = onVariableChange != null ? List.copyOf(onVariableChange) : List.of();
    }

    /** Whether this rule runs on a dedicated async executor (default false). */
    public boolean async() {
        return asyncFlag != null && asyncFlag;
    }

    /** Whether this rule runs when {@code @dashboardContext} changes. */
    public boolean onContextChange() {
        return onContextChangeFlag != null && onContextChangeFlag;
    }

    public BindingActivators(
            boolean onStartup,
            List<BindingVariableRef> onVariableChange,
            String onEvent,
            long periodicMs
    ) {
        this(onStartup, onVariableChange, onEvent, periodicMs, false, false);
    }

    public BindingActivators(
            boolean onStartup,
            List<BindingVariableRef> onVariableChange,
            String onEvent,
            long periodicMs,
            boolean async
    ) {
        this(onStartup, onVariableChange, onEvent, periodicMs, Boolean.valueOf(async), false);
    }

    public static BindingActivators onLocalChange() {
        return new BindingActivators(false, List.of(BindingVariableRef.localAny()), null, 0);
    }

    public static BindingActivators onRemoteChange(String objectPath, String variableName) {
        return new BindingActivators(false, List.of(BindingVariableRef.remote(objectPath, variableName)), null, 0);
    }

    public boolean matchesVariableChange(String ruleObjectPath, String changedObjectPath, String changedVariable) {
        if (onVariableChange.isEmpty()) {
            return false;
        }
        return onVariableChange.stream()
                .anyMatch(ref -> ref.matches(ruleObjectPath, changedObjectPath, changedVariable));
    }

    public boolean matchesEvent(String eventName) {
        return onEvent != null && !onEvent.isBlank() && onEvent.equals(eventName);
    }

    public boolean hasPeriodicSchedule() {
        return periodicMs > 0;
    }
}
