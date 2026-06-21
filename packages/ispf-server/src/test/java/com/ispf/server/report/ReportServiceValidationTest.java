package com.ispf.server.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportServiceValidationTest {

    @Test
    void acceptsSelectQuery() {
        ReportService.validateSelectQuery("SELECT 1");
        ReportService.validateSelectQuery("WITH cte AS (SELECT 1) SELECT * FROM cte");
    }

    @Test
    void rejectsForbiddenKeywords() {
        assertThrows(IllegalArgumentException.class, () ->
                ReportService.validateSelectQuery("SELECT 1; DELETE FROM demo_item"));
    }

    @Test
    void rejectsBlankAppId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ReportService.validateAppId(""));
        assertEquals(
                "Report appId is required — set deploy-application id (e.g. demo) in report editor",
                ex.getMessage()
        );
    }

    @Test
    void reportPathSanitizesReportId() {
        assertEquals("root.platform.reports.ready-items", ReportService.reportPath("ready-items"));
        assertEquals("root.platform.reports.tree-report", ReportService.reportPath("tree-report"));
    }
}
