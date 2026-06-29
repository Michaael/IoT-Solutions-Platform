package com.ispf.server.object;

import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.dashboard.DashboardContextConstants;
import com.ispf.core.object.VisualGroupConstants;

/**
 * Reserved system variables managed by the platform (hidden from operator UI).
 */
public final class BindingStateVariables {

    public static final String BINDING_STATE = "@bindingState";

    private BindingStateVariables() {
    }

    public static boolean isReserved(String name) {
        return BINDING_STATE.equals(name)
                || BindingRulesConstants.isReservedVariable(name)
                || DashboardContextConstants.isReservedVariable(name)
                || VisualGroupConstants.isReservedVariable(name);
    }
}
