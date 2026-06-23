package com.ispf.server.report;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YargExportContentGuardTest {

    @Test
    void detectsMissingRowDataInTextOutput() {
        byte[] output = "Incomes by months".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> run = Map.of(
                "rows",
                List.of(Map.of("devicepath", "root.platform.devices.lab-userA-01", "online", true))
        );
        assertTrue(YargExportContentGuard.outputMissingReportData(output, run));
    }

    @Test
    void acceptsUtf8TextOutput() {
        byte[] output = "root.platform.devices.lab-userA-01".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> run = Map.of(
                "rows",
                List.of(Map.of("devicepath", "root.platform.devices.lab-userA-01"))
        );
        assertFalse(YargExportContentGuard.outputMissingReportData(output, run));
    }

    @Test
    void acceptsUtf16LeBinaryOutput() {
        byte[] output = encodeUtf16Le("root.platform.devices.lab-userA-01");
        Map<String, Object> run = Map.of(
                "rows",
                List.of(Map.of("devicepath", "root.platform.devices.lab-userA-01"))
        );
        assertFalse(YargExportContentGuard.outputMissingReportData(output, run));
    }

    @Test
    void skipsValidationForPdfAndXlsFormats() {
        assertFalse(YargExportContentGuard.shouldValidate(ReportExportFormat.PDF));
        assertFalse(YargExportContentGuard.shouldValidate(ReportExportFormat.XLS));
        assertTrue(YargExportContentGuard.shouldValidate(ReportExportFormat.HTML));
    }

    private static byte[] encodeUtf16Le(String text) {
        byte[] bytes = new byte[text.length() * 2];
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            bytes[index * 2] = (byte) (ch & 0xFF);
            bytes[index * 2 + 1] = (byte) ((ch >> 8) & 0xFF);
        }
        return bytes;
    }
}
