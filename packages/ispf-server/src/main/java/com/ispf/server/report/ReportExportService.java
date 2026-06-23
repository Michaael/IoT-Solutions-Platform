package com.ispf.server.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class ReportExportService {

    private final ReportService reportService;
    private final YargReportService yargReportService;
    private final ReportTemplateStore templateStore;
    private final LibreOfficeDocumentConverter libreOfficeDocumentConverter;

    public ReportExportService(
            ReportService reportService,
            YargReportService yargReportService,
            ReportTemplateStore templateStore,
            LibreOfficeDocumentConverter libreOfficeDocumentConverter
    ) {
        this.reportService = reportService;
        this.yargReportService = yargReportService;
        this.templateStore = templateStore;
        this.libreOfficeDocumentConverter = libreOfficeDocumentConverter;
    }

    @Transactional(readOnly = true)
    public ExportedFile export(String path, ReportExportFormat format, Map<String, Object> parameters) {
        return switch (format) {
            case CSV -> csv(path, parameters);
            case HTML -> exportHtml(path, parameters);
            case XLSX -> exportSpreadsheet(path, parameters, ReportExportFormat.XLSX);
            case XLS -> exportSpreadsheet(path, parameters, ReportExportFormat.XLS);
            case PDF -> exportPdf(path, parameters);
            case DOCX -> exportYarg(path, ReportExportFormat.DOCX, parameters);
        };
    }

    private ExportedFile csv(String path, Map<String, Object> parameters) {
        byte[] content = reportService.exportCsv(path, parameters);
        return new ExportedFile(
                content,
                ReportService.reportIdFromPath(path) + ".csv",
                ReportExportFormat.CSV.contentType()
        );
    }

    private ExportedFile exportHtml(String path, Map<String, Object> parameters) {
        if (reportService.hasTemplate(path)) {
            try {
                return exportYarg(path, ReportExportFormat.HTML, parameters);
            } catch (IllegalArgumentException ignored) {
                // fall through to table HTML
            }
        }
        return table(path, parameters, ReportExportFormat.HTML);
    }

    private ExportedFile table(String path, Map<String, Object> parameters, ReportExportFormat format) {
        byte[] content = format == ReportExportFormat.HTML
                ? reportService.exportHtmlTable(path, parameters)
                : format == ReportExportFormat.XLS
                ? reportService.exportXlsTable(path, parameters)
                : reportService.exportXlsxTable(path, parameters);
        return new ExportedFile(
                content,
                ReportService.reportIdFromPath(path) + "." + format.fileExtension(),
                format.contentType()
        );
    }

    private ExportedFile exportSpreadsheet(String path, Map<String, Object> parameters, ReportExportFormat targetFormat) {
        Optional<ReportTemplateStore.StoredTemplate> template = templateStore.find(path);
        if (template.isEmpty()) {
            return table(path, parameters, targetFormat);
        }

        String templateFormat = template.get().format().toLowerCase();
        if (!"xls".equals(templateFormat) && !"xlsx".equals(templateFormat)) {
            return table(path, parameters, targetFormat);
        }

        ReportExportFormat yargFormat = "xls".equals(templateFormat)
                ? ReportExportFormat.XLS
                : ReportExportFormat.XLSX;

        try {
            Map<String, Object> runResult = reportService.run(path, parameters);
            YargReportService.ExportedReport exported = yargReportService.export(path, yargFormat, parameters);
            byte[] content = exported.content();
            String filename = exported.filename();
            String contentType = exported.contentType();

            if (targetFormat == ReportExportFormat.XLSX && yargFormat == ReportExportFormat.XLS) {
                content = libreOfficeDocumentConverter.convertSpreadsheet(content, "xls", "xlsx");
                filename = ReportService.reportIdFromPath(path) + ".xlsx";
                contentType = ReportExportFormat.XLSX.contentType();
            } else if (targetFormat == ReportExportFormat.XLS && yargFormat == ReportExportFormat.XLSX) {
                content = libreOfficeDocumentConverter.convertSpreadsheet(content, "xlsx", "xls");
                filename = ReportService.reportIdFromPath(path) + ".xls";
                contentType = ReportExportFormat.XLS.contentType();
            } else if (targetFormat == ReportExportFormat.XLSX) {
                filename = ReportService.reportIdFromPath(path) + ".xlsx";
                contentType = ReportExportFormat.XLSX.contentType();
            }

            if (yargFormat == ReportExportFormat.XLSX
                    && YargExportContentGuard.outputMissingReportData(content, runResult)) {
                return table(path, parameters, targetFormat);
            }

            return new ExportedFile(content, filename, contentType);
        } catch (IllegalArgumentException ex) {
            if (YargReportingSupport.isLibreOfficeRequiredError(ex.getMessage())) {
                throw ex;
            }
            return table(path, parameters, targetFormat);
        }
    }

    private ExportedFile exportPdf(String path, Map<String, Object> parameters) {
        Optional<ReportTemplateStore.StoredTemplate> template = templateStore.find(path);
        if (template.isEmpty()) {
            return pdfFromTable(path, parameters);
        }

        String templateFormat = template.get().format().toLowerCase();
        if ("xlsx".equals(templateFormat)) {
            return exportPdfFromExcelTemplate(path, parameters);
        }

        try {
            return exportYarg(path, ReportExportFormat.PDF, parameters);
        } catch (IllegalArgumentException ex) {
            if (YargReportingSupport.isLibreOfficeRequiredError(ex.getMessage())) {
                throw ex;
            }
            return pdfFromTable(path, parameters);
        }
    }

    private ExportedFile exportPdfFromExcelTemplate(String path, Map<String, Object> parameters) {
        try {
            YargReportService.ExportedReport exported = yargReportService.export(path, ReportExportFormat.XLSX, parameters);
            if (!YargExportContentGuard.outputMissingReportData(exported.content(), reportService.run(path, parameters))) {
                byte[] pdf = libreOfficeDocumentConverter.convertSpreadsheetToPdf(exported.content(), "xlsx");
                return new ExportedFile(
                        pdf,
                        ReportService.reportIdFromPath(path) + ".pdf",
                        ReportExportFormat.PDF.contentType()
                );
            }
        } catch (IllegalArgumentException ex) {
            if (YargReportingSupport.isLibreOfficeRequiredError(ex.getMessage())) {
                throw ex;
            }
        }
        return pdfFromTable(path, parameters);
    }

    private ExportedFile exportYarg(String path, ReportExportFormat format, Map<String, Object> parameters) {
        if (!reportService.hasTemplate(path)) {
            throw new IllegalArgumentException(
                    "Export format " + format.fileExtension().toUpperCase()
                            + " requires a YARG template. Upload via Report Builder → Шаблон YARG."
            );
        }
        YargReportService.ExportedReport exported = yargReportService.export(path, format, parameters);
        if (YargExportContentGuard.shouldValidate(format)) {
            Map<String, Object> runResult = reportService.run(path, parameters);
            if (YargExportContentGuard.outputMissingReportData(exported.content(), runResult)) {
                throw new IllegalArgumentException(
                        "YARG template did not receive report data — check ${Band1.FIELD} placeholders "
                                + "match report columns in UPPERCASE."
                );
            }
        }
        return new ExportedFile(exported.content(), exported.filename(), exported.contentType());
    }

    private ExportedFile pdfFromTable(String path, Map<String, Object> parameters) {
        byte[] xlsx = reportService.exportXlsxTable(path, parameters);
        byte[] pdf = libreOfficeDocumentConverter.convertSpreadsheetToPdf(xlsx, "xlsx");
        return new ExportedFile(
                pdf,
                ReportService.reportIdFromPath(path) + ".pdf",
                ReportExportFormat.PDF.contentType()
        );
    }

    public record ExportedFile(byte[] content, String filename, String contentType) {
    }
}
