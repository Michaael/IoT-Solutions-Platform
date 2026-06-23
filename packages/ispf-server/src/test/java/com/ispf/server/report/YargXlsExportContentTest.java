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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class YargXlsExportContentTest {

    @Test
    void xlsExportProducesNonEmptyDocument() throws Exception {
        byte[] template;
        try (InputStream input = getClass().getResourceAsStream("/yarg/smoke-test.xls")) {
            assumeTrue(input != null);
            template = input.readAllBytes();
        }

        String jsonData = new ObjectMapper().writeValueAsString(Map.of(
                "rows",
                YargReportService.prepareRows(List.of(
                        Map.of("COL1", "root.platform.devices.lab-userA-01", "COL2", "ready")
                ))
        ));

        var reportTemplate = new ReportTemplateBuilder()
                .code("DEFAULT")
                .documentName("report.xls")
                .documentPath("report.xls")
                .documentContent(template)
                .outputType(ReportOutputType.xls)
                .outputNamePattern("report.xls")
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

        assertTrue(document.getContent().length > template.length / 2, "xls export should produce filled workbook");
    }
}
