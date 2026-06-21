package com.ispf.server.api;

import com.ispf.server.report.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
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
                        request.refreshIntervalMs()
                )
        );
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

    @GetMapping(value = "/by-path/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam String path,
            HttpServletRequest request
    ) {
        Map<String, Object> parameters = queryParameters(request);
        byte[] csv = reportService.exportCsv(path, parameters);
        String filename = ReportService.reportIdFromPath(path) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    private static Map<String, Object> queryParameters(HttpServletRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if ("path".equals(key) || values == null || values.length == 0) {
                return;
            }
            parameters.put(key, values[0]);
        });
        return parameters;
    }

    public record SaveDefinitionRequest(
            String title,
            String appId,
            @NotBlank String query,
            List<String> parameters,
            List<ReportColumnDto> columns,
            Map<String, Object> defaultParameters,
            Integer maxRows,
            Integer refreshIntervalMs
    ) {
    }

    public record ReportColumnDto(String field, String label) {
    }

    public record RunRequest(Map<String, Object> parameters) {
    }
}
