package com.ispf.server.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class IncomesXlsxTemplateTest {

    @Autowired
    private YargReportService yargReportService;

  @Test
    void smokeXlsTemplateValidates() throws Exception {
        byte[] template = ReportYargTemplateTestHelper.smokeTestTemplate();
        assertDoesNotThrow(() -> yargReportService.validateTemplate(template, "xls"));
    }

    @Test
    void minimalXlsxWithBand1NamedRangeValidates() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/incomes-minimal.xlsx")) {
            if (input == null) {
                throw new IllegalStateException("missing incomes-minimal.xlsx");
            }
            template = input.readAllBytes();
        }
        assertDoesNotThrow(() -> yargReportService.validateTemplate(template, "xlsx"));
    }

    @Test
    void incomesBand1XlsxValidatesAfterJaxbFix() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/incomes-band1.xlsx")) {
            if (input == null) {
                throw new IllegalStateException("missing incomes-band1.xlsx");
            }
            template = input.readAllBytes();
        }
        assertDoesNotThrow(() -> yargReportService.validateTemplate(template, "xlsx"));
    }
}
