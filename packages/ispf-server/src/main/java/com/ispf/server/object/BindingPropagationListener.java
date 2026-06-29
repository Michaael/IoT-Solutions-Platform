package com.ispf.server.object;

import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.dashboard.DashboardContextConstants;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class BindingPropagationListener {

    private final BindingRuleEngine bindingRuleEngine;
    private final BindingDependencyIndex dependencyIndex;

    public BindingPropagationListener(BindingRuleEngine bindingRuleEngine, BindingDependencyIndex dependencyIndex) {
        this.bindingRuleEngine = bindingRuleEngine;
        this.dependencyIndex = dependencyIndex;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() == ObjectChangeType.EVENT_FIRED && event.variableName() != null) {
            bindingRuleEngine.onEvent(event.path(), event.variableName());
            return;
        }
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        if (DashboardContextConstants.isReservedVariable(event.variableName())) {
            bindingRuleEngine.onContextChange(event.path());
            return;
        }
        if (BindingRulesConstants.isReservedVariable(event.variableName())
                || BindingStateVariables.BINDING_STATE.equals(event.variableName())) {
            return;
        }
        String changedPath = event.path();
        String changedVariable = event.variableName();

        bindingRuleEngine.onVariableChanged(changedPath, changedPath, changedVariable);

        for (String consumerPath : dependencyIndex.consumers(changedPath, changedVariable)) {
            if (!consumerPath.equals(changedPath)) {
                bindingRuleEngine.onVariableChanged(consumerPath, changedPath, changedVariable);
            }
        }
    }
}
