package com.ispf.server.application.report;

import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.report.ReportExportFormat;
import com.ispf.server.report.ReportExportService;
import com.ispf.server.report.ReportService;
import com.ispf.server.report.YargReportService;
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
    private final ReportExportService reportExportService;
    private final DataSourceObjectService dataSourceObjectService;
    private final ApplicationDataStore applicationDataStore;

    public ApplicationReportService(
            ReportService reportService,
            ReportExportService reportExportService,
            DataSourceObjectService dataSourceObjectService,
            ApplicationDataStore applicationDataStore
    ) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
        this.dataSourceObjectService = dataSourceObjectService;
        this.applicationDataStore = applicationDataStore;
    }

    @Transactional
    public void deploy(String appId, DeployReportRequest request) {
        List<ReportService.ReportColumn> columns = request.columns() == null
                ? List.of()
                : request.columns().stream()
                        .map(col -> new ReportService.ReportColumn(col.field(), col.label()))
                        .toList();
        if (ReportService.REPORT_TYPE_TREE_VARIABLES.equals(request.reportType())) {
            reportService.deployTreeVariables(
                    request.reportId(),
                    request.title(),
                    request.description(),
                    request.devicePathPattern(),
                    request.variableName(),
                    columns,
                    request.maxRows()
            );
            return;
        }
        dataSourceObjectService.ensureDataSource(
                appId,
                request.title() != null ? request.title() : appId,
                inferSchema(appId),
                "Bundle data source for " + appId
        );
        reportService.deploy(
                dataSourceObjectService.pathForNodeName(appId),
                request.reportId(),
                request.title(),
                request.description(),
                request.query(),
                request.parameters(),
                columns,
                request.maxRows(),
                Map.of()
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String appId) {
        return reportService.listByDataSource(dataSourceObjectService.pathForNodeName(appId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(String appId, String reportId, Map<String, Object> parameters) {
        return reportService.runByApp(appId, reportId, parameters);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String appId, String reportId, Map<String, Object> parameters) {
        return reportService.exportCsvByApp(appId, reportId, parameters);
    }

    @Transactional(readOnly = true)
    public YargReportService.ExportedReport export(String appId, String reportId, ReportExportFormat format, Map<String, Object> parameters) {
        String path = ReportService.reportPath(reportId);
        ReportExportService.ExportedFile exported = reportExportService.export(path, format, parameters);
        return new YargReportService.ExportedReport(exported.content(), exported.filename(), exported.contentType());
    }

    private String inferSchema(String appId) {
        return applicationDataStore.findApp(appId)
                .map(row -> String.valueOf(row.get("schema_name")))
                .filter(s -> s != null && !s.isBlank() && !"null".equals(s))
                .orElse(ApplicationSchemaSupport.defaultSchemaName(appId));
    }

    public record ReportColumn(String field, String label) {
    }

    public record DeployReportRequest(
            String reportId,
            String title,
            String description,
            String reportType,
            String devicePathPattern,
            String variableName,
            String query,
            List<String> parameters,
            List<ReportColumn> columns,
            Integer maxRows
    ) {
    }
}
