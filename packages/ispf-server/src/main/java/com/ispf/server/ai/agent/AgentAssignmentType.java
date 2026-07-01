package com.ispf.server.ai.agent;

import java.util.Locale;

/**
 * High-level assignment categories for Specification Intake Framework (SIF).
 */
public enum AgentAssignmentType {
    MONITORING_LAB("monitoring_lab"),
    SCADA_HMI("scada_hmi"),
    INDUSTRIAL_FACILITY("industrial_facility"),
    AUTOMATION_RULES("automation_rules"),
    APPLICATION_BUNDLE("application_bundle"),
    INTEGRATION_SKELETON("integration_skeleton"),
    REPORTING("reporting"),
    EXPLORE_READONLY("explore_readonly"),
    FOLLOW_UP("follow_up");

    private final String id;

    AgentAssignmentType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AgentAssignmentType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return INDUSTRIAL_FACILITY;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AgentAssignmentType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return INDUSTRIAL_FACILITY;
    }

    public boolean requiresFullSpecIntake() {
        return this != EXPLORE_READONLY && this != FOLLOW_UP && this != MONITORING_LAB;
    }

    public boolean supportsFastPath() {
        return this == MONITORING_LAB || this == EXPLORE_READONLY || this == FOLLOW_UP;
    }

    public String defaultDomainAdapter() {
        return switch (this) {
            case INDUSTRIAL_FACILITY -> "industrial_oil_gas";
            case MONITORING_LAB -> "snmp_lab";
            case SCADA_HMI -> "scada_hmi";
            case APPLICATION_BUNDLE -> "mes_terminal";
            case INTEGRATION_SKELETON -> "integration_skeleton";
            case AUTOMATION_RULES -> "_default";
            case REPORTING -> "_default";
            default -> "_default";
        };
    }

    public String defaultDashboardTemplate() {
        return switch (this) {
            case MONITORING_LAB -> "snmp-host-monitoring";
            case SCADA_HMI, INDUSTRIAL_FACILITY -> "scada-facility-overview";
            case AUTOMATION_RULES -> "monitoring-overview";
            default -> "empty";
        };
    }
}
