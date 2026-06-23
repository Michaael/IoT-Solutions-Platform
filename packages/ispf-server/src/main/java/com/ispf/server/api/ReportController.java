package com.ispf.server.api;

import com.ispf.server.report.ReportExportFormat;
import com.ispf.server.report.ReportExportService;
import com.ispf.server.report.ReportService;
import com.ispf.server.report.ReportTemplateFormatDetector;
import com.ispf.server.report.ReportTemplateStore;
import com.ispf.server.report.YargReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;
    private final YargReportService yargReportService;

    public ReportController(
            ReportService reportService,
            ReportExportService reportExportService,
            YargReportService yargReportService
    ) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
        this.yargReportService = yargReportService;
    }

    @GetMapping("/by-path")
    public ReportService.ReportView get(@RequestParam String path) {
        return reportService.getReport(path);
    }

    @PutMapping("/by-path/definition")
    public ReportService.ReportView saveDefinition(
            @RequestParam String path,
            @Valid @RequestBody SaveDefinitionRequest request
    ) {
        return reportService.saveDefinition(
                path,
                new ReportService.SaveReportDefinitionRequest(
                        request.title(),
                        request.dataSourcePath(),
                        request.appId(),
                        request.query(),
                        request.parameters(),
                        request.columns() == null
                                ? null
                                : request.columns().stream()
                                        .map(col -> new ReportService.ReportColumn(col.field(), col.label()))
                                        .toList(),
                        request.defaultParameters(),
                        request.maxRows(),
                        request.refreshIntervalMs(),
                        request.layout()
                )
        );
    }

    @PutMapping("/by-path/tree-variables-definition")
    public ReportService.ReportView saveTreeVariablesDefinition(
            @RequestParam String path,
            @Valid @RequestBody TreeVariablesDefinitionRequest request
    ) {
        return reportService.saveTreeVariablesDefinition(
                path,
                new ReportService.SaveTreeVariablesDefinitionRequest(
                        request.title(),
                        request.devicePathPattern(),
                        request.variableName(),
                        request.columns() == null
                                ? null
                                : request.columns().stream()
                                        .map(col -> new ReportService.ReportColumn(col.field(), col.label()))
                                        .toList(),
                        request.maxRows(),
                        request.refreshIntervalMs()
                )
        );
    }

    @PutMapping("/by-path/layout")
    public ReportService.ReportView saveLayout(
            @RequestParam String path,
            @RequestBody LayoutRequest request
    ) {
        return reportService.saveLayout(path, request.layout());
    }

    @PostMapping("/by-path/run")
    public Map<String, Object> run(
            @RequestParam String path,
            @RequestBody(required = false) RunRequest request
    ) {
        Map<String, Object> parameters = request != null && request.parameters() != null
                ? request.parameters()
                : Map.of();
        return reportService.run(path, parameters);
    }

    @GetMapping("/by-path/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String path,
            @RequestParam(defaultValue = "csv") String format,
            HttpServletRequest request
    ) {
        Map<String, Object> parameters = queryParameters(request);
        ReportExportFormat exportFormat = ReportExportFormat.parse(format);
        ReportExportService.ExportedFile exported = reportExportService.export(path, exportFormat, parameters);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.filename() + "\"")
                .contentType(MediaType.parseMediaType(exported.contentType()))
                .body(exported.content());
    }

    @PutMapping(value = "/by-path/template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReportService.ReportView uploadTemplate(
            @RequestParam String path,
            @RequestParam String format,
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        return saveUploadedTemplate(path, format, file);
    }

    @PostMapping(value = "/by-path/template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReportService.ReportView uploadTemplatePost(
            @RequestParam String path,
            @RequestParam String format,
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        return saveUploadedTemplate(path, format, file);
    }

    private ReportService.ReportView saveUploadedTemplate(String path, String format, MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Template file is required");
        }
        byte[] content = file.getBytes();
        String resolvedFormat = ReportTemplateFormatDetector.resolve(format, file.getOriginalFilename(), content);
        yargReportService.validateTemplate(content, resolvedFormat);
        return reportService.saveTemplate(path, resolvedFormat, content);
    }

    @GetMapping("/by-path/template")
    public ResponseEntity<byte[]> downloadTemplate(@RequestParam String path) {
        ReportTemplateStore.StoredTemplate template = reportService.getTemplate(path)
                .orElseThrow(() -> new IllegalArgumentException("Template not configured for report: " + path));
        MediaType mediaType = templateMediaType(template.format());
        String filename = ReportService.reportIdFromPath(path) + "." + template.format();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(template.content());
    }

    @DeleteMapping("/by-path/template")
    public ReportService.ReportView deleteTemplate(@RequestParam String path) {
        return reportService.deleteTemplate(path);
    }

    private static MediaType templateMediaType(String format) {
        return switch (format.toLowerCase()) {
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "html" -> MediaType.parseMediaType("text/html; charset=UTF-8");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private static Map<String, Object> queryParameters(HttpServletRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if ("path".equals(key) || "format".equals(key) || values == null || values.length == 0) {
                return;
            }
            parameters.put(key, values[0]);
        });
        return parameters;
    }

    public record SaveDefinitionRequest(
            String title,
            String dataSourcePath,
            String appId,
            @NotBlank String query,
            List<String> parameters,
            List<ReportColumnDto> columns,
            Map<String, Object> defaultParameters,
            Integer maxRows,
            Integer refreshIntervalMs,
            String layout
    ) {
    }

    public record TreeVariablesDefinitionRequest(
            String title,
            @NotBlank String devicePathPattern,
            @NotBlank String variableName,
            List<ReportColumnDto> columns,
            Integer maxRows,
            Integer refreshIntervalMs
    ) {
    }

    public record LayoutRequest(String layout) {
    }

    public record ReportColumnDto(String field, String label) {
    }

    public record RunRequest(Map<String, Object> parameters) {
    }
}
