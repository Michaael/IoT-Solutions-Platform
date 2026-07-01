package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAnalyticalIntakeTest {

    @Test
    void emitsRussianGapsWhenUserMessageIsRussian() {
        Map<String, Object> plan = Map.of(
                "goal", "NPS demo",
                "sections", List.of(Map.of("id", "ground_truth", "summary", "x".repeat(90), "steps", List.of("a", "b"), "deliverables", List.of("d")))
        );
        List<String> gaps = AgentAnalyticalIntake.completenessGaps(plan, Map.of(), "Сделай цифровой двойник НПС");
        assertThat(gaps).anyMatch(g -> g.contains("specBrief неполный"));
        assertThat(gaps).anyMatch(g -> g.contains("Нет секции"));
    }

    @Test
    void rejectsApprovalWhenSpecBriefMissing() {
        Map<String, Object> plan = Map.of(
                "goal", "NPS",
                "sections", List.of(Map.of("id", "ground_truth", "summary", "x".repeat(90), "steps", List.of("a", "b"), "deliverables", List.of("d")))
        );
        assertThat(AgentAnalyticalIntake.readyForApproval(plan, Map.of())).isFalse();
        assertThat(AgentAnalyticalIntake.completenessGaps(plan, Map.of()))
                .anyMatch(g -> g.contains("specBrief"));
    }

    @Test
    void acceptsApprovalWhenComplete() {
        Map<String, Object> specBrief = Map.of(
                "title", "ЦД НПС",
                "entities", List.of(Map.of("id", "MNA", "label", "Насос")),
                "functionalRequirements", List.of(
                        Map.of("id", "FR-1", "title", "Telemetry", "sourcePhrase", "цифровой двойник"),
                        Map.of("id", "FR-2", "title", "SCADA", "sourcePhrase", "мнемосхема"),
                        Map.of("id", "FR-3", "title", "Historian", "sourcePhrase", "архив")
                )
        );
        List<Map<String, Object>> sections = AgentAnalyticalIntake.REQUIRED_SECTION_IDS.stream()
                .map(id -> Map.<String, Object>of(
                        "id", id,
                        "summary", "Section ".repeat(20),
                        "steps", List.of("list_objects", "create_virtual_device"),
                        "deliverables", List.of("root.platform.devices.nps")
                ))
                .toList();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("goal", "ЦД НПС");
        plan.put("executiveSummary", "Полная реализация насосной станции по ТЗ.");
        plan.put("specBrief", specBrief);
        plan.put("sections", sections);
        plan.put("objectTypesCoverage", List.of(Map.of("type", "DEVICE", "action", "create")));
        plan.put("gapMatrix", List.of(Map.of("requirementId", "FR-1", "capabilityId", "CAP_DEVICE", "status", "full")));
        plan.put("handoffFrame", Map.of("handoffId", "H1", "assignmentType", "industrial_facility",
                "domainAdapter", "industrial_oil_gas", "specBrief", "brief", "gapMatrix", List.of(),
                "deliveryPhases", List.of(Map.of("phaseId", "full", "steps", List.of("validate")))));

        assertThat(AgentAnalyticalIntake.readyForApproval(plan, Map.of())).isTrue();
    }

    @Test
    void mergesIntakeArtifactsIntoPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        Map<String, Object> finish = Map.of(
                "specBrief", Map.of("title", "T"),
                "gapMatrix", List.of(Map.of("requirementId", "FR-1"))
        );
        AgentAnalyticalIntake.mergeFinishIntakeIntoPlan(plan, finish);
        assertThat(plan).containsKeys("specBrief", "gapMatrix");
    }
}
