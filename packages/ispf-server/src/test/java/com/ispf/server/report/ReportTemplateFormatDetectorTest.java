package com.ispf.server.report;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportTemplateFormatDetectorTest {

    @Test
    void detectsXlsFromOleFileEvenWhenDropdownSaysXlsx() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/smoke-test.xls")) {
            template = input.readAllBytes();
        }
        assertEquals(
                "xls",
                ReportTemplateFormatDetector.resolve("xlsx", "incomes.xls", template)
        );
    }

    @Test
    void detectsXlsxFromZipWorkbook() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/incomes-band1.xlsx")) {
            template = input.readAllBytes();
        }
        assertEquals(
                "xlsx",
                ReportTemplateFormatDetector.resolve("xls", "report.xlsx", template)
        );
    }
}
