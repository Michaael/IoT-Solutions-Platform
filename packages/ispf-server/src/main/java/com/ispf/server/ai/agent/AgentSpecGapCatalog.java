package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Universal capability catalog for SIF gap matrix rows.
 */
public final class AgentSpecGapCatalog {

    public record CapabilitySpec(
            String capabilityId,
            String frCategory,
            String ispfMechanism,
            String typicalStatus,
            boolean outOfPlatform
    ) {
    }

    private static final List<CapabilitySpec> CAPABILITIES = List.of(
            new CapabilitySpec("CAP_REALTIME_TELEMETRY", "realtime", "drivers + historian + alerts", "full", false),
            new CapabilitySpec("CAP_SCADA_MIMIC", "hmi", "MIMIC + save_mimic_diagram", "full", false),
            new CapabilitySpec("CAP_DASHBOARD", "hmi", "DASHBOARD + set_dashboard_layout template", "full", false),
            new CapabilitySpec("CAP_ALERTS", "realtime", "configure_alert", "full", false),
            new CapabilitySpec("CAP_CORRELATOR", "forecast", "configure_correlator", "full", false),
            new CapabilitySpec("CAP_WORKFLOW_SIM", "simulation", "WORKFLOW + save_workflow_bpmn", "full", false),
            new CapabilitySpec("CAP_BUNDLE_APP", "integration", "import_package / register_application", "full", false),
            new CapabilitySpec("CAP_REPORTING", "reporting", "configure_report + run_report", "full", false),
            new CapabilitySpec("CAP_ML_PREDICT", "ml", "external ML service", "out_of_scope", true),
            new CapabilitySpec("CAP_HIGH_FREQ", "realtime", "edge high-frequency acquisition", "out_of_scope", true),
            new CapabilitySpec("CAP_3D_VIS", "hmi", "external 3D / BI", "out_of_scope", true),
            new CapabilitySpec("CAP_OPE_KPI", "reporting", "manual acceptance testing", "out_of_scope", true)
    );

    private AgentSpecGapCatalog() {
    }

    public static List<CapabilitySpec> all() {
        return CAPABILITIES;
    }

    public static String capabilityForCategory(String frCategory) {
        if (frCategory == null || frCategory.isBlank()) {
            return "CAP_REALTIME_TELEMETRY";
        }
        String cat = frCategory.trim().toLowerCase(Locale.ROOT);
        return CAPABILITIES.stream()
                .filter(spec -> spec.frCategory().equals(cat))
                .map(CapabilitySpec::capabilityId)
                .findFirst()
                .orElse("CAP_REALTIME_TELEMETRY");
    }

    public static Map<String, Object> defaultGapRow(String requirementId, String frCategory, String requirementText) {
        String capabilityId = capabilityForCategory(frCategory);
        CapabilitySpec spec = CAPABILITIES.stream()
                .filter(c -> c.capabilityId().equals(capabilityId))
                .findFirst()
                .orElse(CAPABILITIES.getFirst());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("requirementId", requirementId != null ? requirementId : "FR-?");
        row.put("capabilityId", spec.capabilityId());
        row.put("requirement", requirementText != null ? requirementText : "");
        row.put("status", spec.typicalStatus());
        row.put("platformPath", spec.ispfMechanism());
        row.put("gapId", "GAP-" + spec.capabilityId().replace("CAP_", ""));
        row.put("blocksDev", spec.outOfPlatform());
        row.put("gapStatus", spec.outOfPlatform() ? "open" : "closed");
        return row;
    }

    public static List<String> pitfallCodesForAssignment(AgentAssignmentType type) {
        List<String> codes = new java.util.ArrayList<>(List.of(
                "P_PATH_GROUND_TRUTH",
                "P_VAR_INVENTION",
                "P_SCOPE_CREEP",
                "P_CHART_NO_HISTORY",
                "P_EMPTY_MIMIC",
                "P_LAYOUT_HAND_ROLL"
        ));
        if (type == AgentAssignmentType.INDUSTRIAL_FACILITY || type == AgentAssignmentType.SCADA_HMI) {
            codes.add("P_PROFILE_MISMATCH");
            codes.add("P_ENTITY_CONFUSION");
            codes.add("P_NAME_WITHOUT_CONFIRM");
        }
        if (type == AgentAssignmentType.INDUSTRIAL_FACILITY) {
            codes.add("P_WRITE_TO_PLC");
        }
        return codes;
    }
}
