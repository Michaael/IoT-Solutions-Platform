package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conformance invariants and smoke case templates by assignment type (docanima patterns).
 */
public final class AgentConformanceCatalog {

    private AgentConformanceCatalog() {
    }

    public record SmokeCase(String id, String scenario, String expected) {
        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("case", scenario);
            row.put("expected", expected);
            return row;
        }
    }

    public record Invariant(String id, String rule, String errorCode) {
        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("rule", rule);
            row.put("error", errorCode);
            return row;
        }
    }

    public static Map<String, Object> defaultConformance(AgentAssignmentType type) {
        Map<String, Object> conformance = new LinkedHashMap<>();
        conformance.put("invariants", defaultInvariants(type).stream().map(Invariant::toMap).toList());
        conformance.put("smokeCases", defaultSmokeCases(type).stream().map(SmokeCase::toMap).toList());
        return conformance;
    }

    public static List<Invariant> defaultInvariants(AgentAssignmentType type) {
        List<Invariant> invariants = new ArrayList<>(List.of(
                new Invariant("INV-GT-01", "Paths from list_objects/create_object only", "P_PATH_GROUND_TRUTH"),
                new Invariant("INV-VAR-01", "Bindings use list_variables names only", "P_VAR_INVENTION")
        ));
        if (type == AgentAssignmentType.SCADA_HMI || type == AgentAssignmentType.INDUSTRIAL_FACILITY) {
            invariants.add(new Invariant("INV-MIMIC-01", "get_mimic_diagram elementCount>0 before finish", "P_EMPTY_MIMIC"));
            invariants.add(new Invariant("INV-PROFILE-01", "Profile matches entity semantics", "P_PROFILE_MISMATCH"));
        }
        if (type == AgentAssignmentType.AUTOMATION_RULES || type == AgentAssignmentType.INDUSTRIAL_FACILITY) {
            invariants.add(new Invariant("INV-ALERT-01", "configure_alert when monitoring intent", "ALERT_MISSING"));
        }
        return invariants;
    }

    public static List<SmokeCase> defaultSmokeCases(AgentAssignmentType type) {
        return switch (type) {
            case MONITORING_LAB -> List.of(
                    new SmokeCase("S1", "list_variables on device returns count>0", "OK"),
                    new SmokeCase("S2", "get_dashboard_layout widgetCount>0", "OK")
            );
            case SCADA_HMI, INDUSTRIAL_FACILITY -> List.of(
                    new SmokeCase("S1", "list_variables on each device", "OK"),
                    new SmokeCase("S2", "get_mimic_diagram elementCount>0", "OK"),
                    new SmokeCase("S3", "get_dashboard_layout widgetCount>0", "OK")
            );
            case APPLICATION_BUNDLE -> List.of(
                    new SmokeCase("S1", "validate_bundle status OK", "OK"),
                    new SmokeCase("S2", "dry_run_deploy status OK", "OK")
            );
            case AUTOMATION_RULES -> List.of(
                    new SmokeCase("S1", "configure_alert succeeds", "OK"),
                    new SmokeCase("S2", "run_workflow or fire_event smoke", "OK")
            );
            default -> List.of(
                    new SmokeCase("S1", "Primary discovery tool returns OK", "OK")
            );
        };
    }

    public static boolean requiresSmokeCases(AgentAssignmentType type) {
        return type == AgentAssignmentType.AUTOMATION_RULES
                || type == AgentAssignmentType.APPLICATION_BUNDLE
                || type == AgentAssignmentType.INDUSTRIAL_FACILITY
                || type == AgentAssignmentType.SCADA_HMI;
    }
}
