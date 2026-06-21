package com.ispf.server.application.report;

import com.ispf.server.report.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Legacy app-scoped report API — delegates to tree-first {@link ReportService}.
 */
@Service
public class ApplicationReportService {

    private final ReportService reportService;

    public ApplicationReportService(ReportService reportService) {
        this.reportService = reportService;
    }

    @Transactional
    public void deploy(String appId, DeployReportRequest request) {
        reportService.deploy(
                appId,
                request.reportId(),
                request.title(),
                request.description(),
                request.query(),
                request.parameters(),
                request.columns() == null
                        ? List.of()
                        : request.columns().stream()
                                .map(col -> new ReportService.ReportColumn(col.field(), col.label()))
                                .toList(),
                request.maxRows(),
                Map.of()
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String appId) {
        return reportService.listByApp(appId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(String appId, String reportId, Map<String, Object> parameters) {
        return reportService.runByApp(appId, reportId, parameters);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String appId, String reportId, Map<String, Object> parameters) {
        return reportService.exportCsvByApp(appId, reportId, parameters);
    }

    public record ReportColumn(String field, String label) {
    }

    public record DeployReportRequest(
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows
    ) {
    }
}
