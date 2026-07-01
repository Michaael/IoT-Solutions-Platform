package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Auto-preflight hints before mutation tools — transparent path grounding assistance (hooks H01-H30).
 */
public final class AgentPreflightService {

    private static final Set<String> PARENT_MUTATIONS = Set.of("create_object", "create_virtual_device", "instantiate_instance_type");
    private static final Set<String> PATH_MUTATIONS = Set.of(
            "configure_driver", "driver_control", "set_variable", "save_mimic_diagram",
            "set_dashboard_layout", "add_dashboard_widget", "configure_alert", "configure_correlator",
            "configure_report", "configure_variable_history", "save_workflow_bpmn"
    );

    private AgentPreflightService() {
    }

    public record PreflightHint(
            boolean blocked,
            String error,
            String hint,
            Map<String, Object> suggestedDiscovery
    ) {
        Optional<Map<String, Object>> asToolResultOverlay() {
            if (!blocked && hint == null) {
                return Optional.empty();
            }
            Map<String, Object> overlay = new LinkedHashMap<>();
            overlay.put("preflight", true);
            if (error != null) {
                overlay.put("preflightError", error);
            }
            if (hint != null) {
                overlay.put("hint", hint);
            }
            if (suggestedDiscovery != null && !suggestedDiscovery.isEmpty()) {
                overlay.put("suggestedDiscovery", suggestedDiscovery);
            }
            return Optional.of(overlay);
        }
    }

    public static Optional<PreflightHint> checkBeforeTool(
            String toolName,
            Map<String, Object> arguments,
            List<Map<String, Object>> steps
    ) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        String tool = toolName.toLowerCase(Locale.ROOT);

        if (PARENT_MUTATIONS.contains(tool)) {
            String parentPath = AgentGroundTruthGuard.resolveParentPath(arguments);
            if (!parentPath.isBlank() && !AgentGroundTruthGuard.isParentGrounded(steps, parentPath)) {
                String canonical = AgentGroundTruthGuard.resolveCanonicalPath(steps, parentPath);
                Map<String, Object> discovery = Map.of(
                        "tool", "list_objects",
                        "arguments", Map.of("parent", parentOf(parentPath))
                );
                String hint = AgentGroundTruthGuard.treeFirstOrderHint(parentOf(parentPath), null, tool)
                        + (canonical.equalsIgnoreCase(parentPath) ? "" : "\nUse exact path: " + canonical);
                return Optional.of(new PreflightHint(
                        false,
                        "Parent path not grounded: " + parentPath,
                        hint,
                        discovery
                ));
            }
        }

        if (PATH_MUTATIONS.contains(tool) || AgentGroundTruthGuard.resolveObjectPath(arguments) != null) {
            String objectPath = AgentGroundTruthGuard.resolveObjectPath(arguments);
            if (!objectPath.isBlank() && !AgentGroundTruthGuard.isObjectPathGrounded(steps, objectPath)) {
                String canonical = AgentGroundTruthGuard.resolveCanonicalPath(steps, objectPath);
                String parent = parentOf(objectPath);
                Map<String, Object> discovery = Map.of(
                        "tool", parent.isBlank() ? "get_object" : "list_objects",
                        "arguments", parent.isBlank()
                                ? Map.of("path", objectPath)
                                : Map.of("parent", parent)
                );
                String hint = AgentGroundTruthGuard.treeFirstOrderHint(
                        parent.isBlank() ? objectPath : parent,
                        objectPath,
                        tool
                );
                if (!canonical.equalsIgnoreCase(objectPath)) {
                    hint += "\nUse exact path: " + canonical;
                }
                return Optional.of(new PreflightHint(
                        false,
                        "Object path not grounded: " + objectPath,
                        hint,
                        discovery
                ));
            }
        }

        if ("configure_variable_history".equals(tool)) {
            String path = stringArg(arguments, "path");
            if (!path.isBlank()) {
                String canonical = AgentGroundTruthGuard.resolveCanonicalPath(steps, path);
                if (!canonical.equals(path)) {
                    return Optional.of(new PreflightHint(
                            false,
                            null,
                            "Resolved canonical path for configure_variable_history: " + canonical,
                            Map.of()
                    ));
                }
            }
        }

        return Optional.empty();
    }

    public static String hookChecklist() {
        return """
                Preflight hooks (H01-H30 subset):
                H01 — mutations need approved plan
                H05 — create_object: parentPath + type + templateId from discovery
                H06 — post-create verify: list_variables / get_object
                H11-H12 — smoke invoke + compare contract before finish
                H29 — post-approve: include liveSummary in finish result
                """;
    }

    private static String parentOf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        return dot <= 0 ? "" : path.substring(0, dot);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
