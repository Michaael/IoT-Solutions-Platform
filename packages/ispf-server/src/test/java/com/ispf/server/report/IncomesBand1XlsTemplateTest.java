package com.ispf.server.report;

import com.haulmont.yarg.reporting.Reporting;
import com.haulmont.yarg.reporting.RunParams;
import com.haulmont.yarg.structure.ReportOutputType;
import com.haulmont.yarg.structure.impl.BandBuilder;
import com.haulmont.yarg.structure.impl.ReportBuilder;
import com.haulmont.yarg.structure.impl.ReportTemplateBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IncomesBand1XlsTemplateTest {

    @Test
    void fillsDevicePathForLabVirtualStatusColumns() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/incomes-band1.xls")) {
            assumeTrue(input != null, "missing incomes-band1.xls");
            template = input.readAllBytes();
            template = XlsTemplatePlaceholderNormalizer.normalize(template);
        }

        String jsonData = new ObjectMapper().writeValueAsString(Map.of(
                "rows",
                YargReportService.prepareRows(List.of(
                        Map.of(
                                "DEVICEPATH", "root.platform.devices.lab-userA-01",
                                "ONLINE", true,
                                "LASTSEEN", "init",
                                "VALUE", "42"
                        ),
                        Map.of(
                                "DEVICEPATH", "root.platform.devices.lab-userB-01",
                                "ONLINE", true,
                                "LASTSEEN", "init",
                                "VALUE", "43"
                        )
                ))
        ));

        var reportTemplate = new ReportTemplateBuilder()
                .code("DEFAULT")
                .documentName("report.xls")
                .documentPath("report.xls")
                .documentContent(template)
                .outputType(ReportOutputType.xls)
                .outputNamePattern("lab-virtual-status.xls")
                .build();

        var report = new ReportBuilder()
                .name("Lab device status")
                .band(new BandBuilder()
                        .name("Band1")
                        .query("", "parameter=reportData $.rows", "json")
                        .build())
                .template(reportTemplate)
                .build();

        Reporting reporting = YargReportingSupport.createReporting(new com.ispf.server.config.ReportYargProperties());
        var document = reporting.runReport(
                new RunParams(report)
                        .templateCode("DEFAULT")
                        .output(ReportOutputType.xls)
                        .param("reportData", jsonData)
        );

        byte[] content = document.getContent();
        Path out = Path.of("build", "incomes-band1-export.xls");
        Files.createDirectories(out.getParent());
        Files.write(out, content);

        Map<String, Object> runResult = Map.of(
                "rows",
                List.of(Map.of("devicepath", "root.platform.devices.lab-userA-01", "online", true))
        );

        assertTrue(content.length > 1000, "export size=" + content.length);
        assertFalse(
                YargExportContentGuard.outputMissingReportData(content, runResult),
                "export should contain device path; wrote " + out.toAbsolutePath()
        );
        assertTrue(
                YargExportContentGuard.binaryContainsText(content, "lab-userA-01")
                        || YargExportContentGuard.binaryContainsText(content, "42"),
                "export should contain row values"
        );
    }
}
