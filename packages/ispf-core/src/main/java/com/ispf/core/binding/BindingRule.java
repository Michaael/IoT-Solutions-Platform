package com.ispf.core.binding;

/**
 * Declarative binding: activator (when) → condition (if) → expression (how) → target (where).
 */
public record BindingRule(
        String id,
        String name,
        boolean enabled,
        int order,
        BindingActivators activators,
        String condition,
        String expression,
        BindingTarget target
) {
    public BindingRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Binding rule id is required");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Binding rule expression is required");
        }
        if (target == null) {
            throw new IllegalArgumentException("Binding rule target is required");
        }
        if (target.isVariable() && (target.variableName() == null || target.variableName().isBlank())) {
            throw new IllegalArgumentException("Binding rule target.variableName is required");
        }
        if (target.isContext() && (target.path() == null || target.path().isBlank())) {
            throw new IllegalArgumentException("Binding rule target.path is required for context effect");
        }
        if (target.isEvent() && (target.eventName() == null || target.eventName().isBlank())) {
            throw new IllegalArgumentException("Binding rule target.eventName is required for event effect");
        }
        if (activators == null) {
            activators = BindingActivators.onLocalChange();
        }
        if (condition == null) {
            condition = "";
        }
        if (name == null) {
            name = id;
        }
    }
}
