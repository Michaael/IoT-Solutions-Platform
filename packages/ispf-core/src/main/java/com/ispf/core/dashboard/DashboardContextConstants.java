package com.ispf.core.dashboard;

/**
 * Reserved variable holding dashboard operator context (selection, params, widgets).
 */
public final class DashboardContextConstants {

    public static final String VARIABLE = "@dashboardContext";

    private DashboardContextConstants() {
    }

    public static boolean isReservedVariable(String name) {
        return VARIABLE.equals(name);
    }
}
