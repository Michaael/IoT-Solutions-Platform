package com.ispf.server.ai.agent;

import java.util.Locale;

/**
 * Heuristic assignment type classifier for SIF — testable without LLM.
 */
public final class AgentAssignmentClassifier {

    private AgentAssignmentClassifier() {
    }

    public record Classification(
            AgentAssignmentType type,
            double confidence,
            boolean fastPath,
            String domainAdapter
    ) {
    }

    public static Classification classify(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new Classification(AgentAssignmentType.EXPLORE_READONLY, 0.5, true, "_default");
        }
        String text = userMessage.toLowerCase(Locale.ROOT).trim();

        if (isFollowUp(text)) {
            return new Classification(AgentAssignmentType.FOLLOW_UP, 0.9, true, "_default");
        }
        if (matchesAny(text, "покажи", "show ", "list ", "list_", "explain", "объясни", "what ", "какие", "список",
                "where is", "где ", "открой", "open ", "describe", "get ", "get_")) {
            if (matchesAny(text, "mes-reference", "mes reference", "import_package", "bundle deploy", "разверни mes")) {
                return new Classification(AgentAssignmentType.APPLICATION_BUNDLE, 0.88, false, "mes_terminal");
            }
            return new Classification(AgentAssignmentType.EXPLORE_READONLY, 0.85, true, "_default");
        }
        if (matchesAny(text, "snmp", "demo-sensor", "virtual lab", "virtual cluster", "localhost монитор",
                "localhost monitor")) {
            return new Classification(AgentAssignmentType.MONITORING_LAB, 0.9, true, "snmp_lab");
        }
        if (matchesAny(text, "mes-reference", "mes reference", "разверни mes", "deploy mes", "import_package",
                "import package", "bundle deploy", "validate_bundle", "dry_run_deploy")) {
            return new Classification(AgentAssignmentType.APPLICATION_BUNDLE, 0.88, false, "mes_terminal");
        }
        if (matchesAny(text, "modbus", "opc ua", "opc-ua", "mqtt", "driver skeleton", "integration skeleton")) {
            return new Classification(AgentAssignmentType.INTEGRATION_SKELETON, 0.85, false, "integration_skeleton");
        }
        if (matchesAny(text, "отчёт", "отчет", "report", "yarg", "export report")) {
            return new Classification(AgentAssignmentType.REPORTING, 0.8, false, "_default");
        }
        if (matchesAny(text, "workflow", "bpmn", "configure_alert", "configure_correlator", "alert rule",
                "correlator")) {
            return new Classification(AgentAssignmentType.AUTOMATION_RULES, 0.82, false, "_default");
        }
        if (matchesAny(text, "насосн", "pump station", "tank farm", "резервуар", "pipeline", "нефтебаз",
                "цифровой двойник", "digital twin", "техническое задание", "тз на", "nps")) {
            return new Classification(AgentAssignmentType.INDUSTRIAL_FACILITY, 0.9, false, "industrial_oil_gas");
        }
        if (matchesAny(text, "scada", "mimic", "мимик", "мнемо", "p&id", "p&id", "hmi")) {
            return new Classification(AgentAssignmentType.SCADA_HMI, 0.86, false, "scada_hmi");
        }
        if (text.length() > 500 || matchesAny(text, "приложение", "appendix", "kpi", "опэ", "forecast", "lstm",
                "machine learning", "оператор", "ролевая модель")) {
            return new Classification(AgentAssignmentType.INDUSTRIAL_FACILITY, 0.75, false, "industrial_oil_gas");
        }
        if (text.length() < 48) {
            return new Classification(AgentAssignmentType.MONITORING_LAB, 0.6, true, "snmp_lab");
        }
        return new Classification(AgentAssignmentType.INDUSTRIAL_FACILITY, 0.55, false, "industrial_oil_gas");
    }

    public static boolean isComplexAssignment(String userMessage) {
        Classification c = classify(userMessage);
        if (c.fastPath()) {
            return false;
        }
        if (c.type() == AgentAssignmentType.FOLLOW_UP || c.type() == AgentAssignmentType.EXPLORE_READONLY) {
            return false;
        }
        return AgentPlanGuard.requiresPlanning(userMessage) || c.type().requiresFullSpecIntake();
    }

    private static boolean isFollowUp(String text) {
        return matchesAny(text, "добавь", "add ", "на тот дашборд", "that dashboard", "that device",
                "на дашборд", "график на", "chart on", "update widget", "ещё один");
    }

    private static boolean matchesAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
