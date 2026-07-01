package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorAgentTurnGuardTest {

    @Test
    void blocksSecondListReports() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "tool", "tool", "list_reports", "result", Map.of(
                        "status", "OK",
                        "reports", List.of(
                                Map.of(
                                        "path", "root.platform.reports.tec-daily-energy",
                                        "title", "Суточный журнал энергии",
                                        "reportType", "sql"
                                )
                        )
                ))
        );
        var decision = OperatorAgentTurnGuard.checkBeforeTool(
                "list_reports",
                Map.of(),
                steps,
                "Запусти сменный отчёт",
                null
        );
        assertTrue(decision.blocked());
        assertTrue(decision.hasClarification() || decision.hint().contains("suggestions"));
    }

    @Test
    void clarifiesInvalidRunReportPath() {
        OperatorAgentScope scope = new OperatorAgentScope(
                "mini-tec",
                "Мини-ТЭЦ",
                List.of("root.platform.reports.tec-daily-energy"),
                "root.platform.reports.tec-daily-energy"
        );
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "tool", "tool", "list_reports", "result", Map.of(
                        "status", "OK",
                        "reports", List.of(
                                Map.of(
                                        "path", "root.platform.reports.tec-daily-energy",
                                        "title", "Суточный журнал энергии",
                                        "reportType", "sql"
                                )
                        )
                ))
        );
        var decision = OperatorAgentTurnGuard.checkBeforeTool(
                "run_report",
                Map.of("path", "root.platform.reports.mini-tec-shift-report"),
                steps,
                "mini-tec-shift-report - покажи отчет",
                scope
        );
        assertTrue(decision.hasClarification());
        assertTrue(decision.clarification().summary().contains("недоступен"));
    }

    @Test
    void blocksMemorySearchWhenUserWantsReport() {
        var decision = OperatorAgentTurnGuard.checkBeforeTool(
                "search_app_documents",
                Map.of(),
                List.of(),
                "Запусти сменный отчёт и опиши цифры",
                null
        );
        assertTrue(decision.blocked());
        assertTrue(decision.hint().contains("list_reports"));
    }

    @Test
    void allowsFirstListReports() {
        var decision = OperatorAgentTurnGuard.checkBeforeTool(
                "list_reports",
                Map.of(),
                List.of(),
                "Запусти сменный отчёт",
                null
        );
        assertFalse(decision.blocked());
    }

    @Test
    void gentlePaceSuffixAppearsAfterManySteps() {
        List<Map<String, Object>> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 22; i++) {
            steps.add(Map.of("type", "tool", "tool", "list_variables"));
        }
        String hint = OperatorAgentTurnGuard.gentlePaceSuffix(steps, 256);
        assertTrue(hint.contains("catalog"));
    }
}
