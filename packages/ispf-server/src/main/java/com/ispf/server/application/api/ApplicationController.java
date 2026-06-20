package com.ispf.server.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.report.ApplicationReportService;
import com.ispf.server.application.tree.ApplicationObjectTreeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationDataService dataService;
    private final ApplicationFunctionStore functionStore;
    private final ApplicationBundleDeployService bundleDeployService;
    private final ApplicationSqlBindingService sqlBindingService;
    private final ApplicationReportService reportService;
    private final ApplicationObjectTreeService objectTreeService;
    private final ObjectMapper objectMapper;

    public ApplicationController(
            ApplicationDataService dataService,
            ApplicationFunctionStore functionStore,
            ApplicationBundleDeployService bundleDeployService,
            ApplicationSqlBindingService sqlBindingService,
            ApplicationReportService reportService,
            ApplicationObjectTreeService objectTreeService,
            ObjectMapper objectMapper
    ) {
        this.dataService = dataService;
        this.functionStore = functionStore;
        this.bundleDeployService = bundleDeployService;
        this.sqlBindingService = sqlBindingService;
        this.reportService = reportService;
        this.objectTreeService = objectTreeService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{appId}/deploy")
    public Map<String, Object> deployBundle(
            @PathVariable String appId,
            @RequestBody ApplicationBundleDeployService.BundleManifest manifest
    ) {
        return bundleDeployService.deploy(appId, manifest);
    }

    @GetMapping("/{appId}/deploy/history")
    public List<Map<String, Object>> deployHistory(@PathVariable String appId) {
        return bundleDeployService.deployHistory(appId);
    }

    @PostMapping("/{appId}/deploy/rollback")
    public Map<String, Object> rollbackBundle(
            @PathVariable String appId,
            @RequestBody RollbackBundleRequest request
    ) throws Exception {
        try {
            return bundleDeployService.rollback(appId, request.version());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{appId}/operator-manifest")
    public Map<String, Object> operatorManifest(@PathVariable String appId) throws Exception {
        try {
            return bundleDeployService.operatorManifest(appId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{appId}/operator-ui")
    public Map<String, Object> operatorUi(@PathVariable String appId) throws Exception {
        return loadOperatorUi(appId);
    }

    /** Alias for environments where legacy security rules block paths containing "operator". */
    @GetMapping("/{appId}/hmi-ui")
    public Map<String, Object> hmiUi(@PathVariable String appId) throws Exception {
        return loadOperatorUi(appId);
    }

    private Map<String, Object> loadOperatorUi(String appId) throws Exception {
        try {
            return bundleDeployService.operatorUi(appId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping
    public Map<String, Object> register(@RequestBody RegisterApplicationRequest request) {
        return dataService.register(
                request.appId(),
                request.displayName(),
                request.tablePrefix(),
                request.schemaName()
        );
    }

    @PostMapping("/{appId}/data/migrate")
    public Map<String, Object> migrate(
            @PathVariable String appId,
            @RequestBody MigrateRequest request
    ) {
        List<ApplicationDataService.MigrationScript> scripts = request.scripts().stream()
                .map(script -> new ApplicationDataService.MigrationScript(script.id(), script.sql()))
                .toList();
        return dataService.migrate(appId, request.version(), scripts);
    }

    @GetMapping("/{appId}/data/status")
    public Map<String, Object> status(@PathVariable String appId) {
        return dataService.status(appId);
    }

    @PostMapping("/{appId}/data/seed")
    public Map<String, Object> seed(
            @PathVariable String appId,
            @RequestBody SeedRequest request
    ) {
        return dataService.seed(appId, request.profile());
    }

    @GetMapping("/{appId}/functions")
    public List<Map<String, Object>> listFunctionVersions(
            @PathVariable String appId,
            @RequestParam String objectPath,
            @RequestParam String functionName
    ) {
        return functionStore.listVersions(appId, objectPath, functionName);
    }

    @PostMapping("/{appId}/functions/rollback")
    public Map<String, Object> rollbackFunction(
            @PathVariable String appId,
            @RequestBody RollbackFunctionRequest request
    ) {
        functionStore.activateVersion(appId, request.objectPath(), request.functionName(), request.version());
        return Map.of(
                "appId", appId,
                "objectPath", request.objectPath(),
                "functionName", request.functionName(),
                "version", request.version(),
                "status", "active"
        );
    }

    @GetMapping("/{appId}/bindings")
    public List<Map<String, Object>> listBindings(@PathVariable String appId) {
        return sqlBindingService.list(appId);
    }

    @PostMapping("/{appId}/bindings/deploy")
    public Map<String, Object> deployBinding(
            @PathVariable String appId,
            @RequestBody DeploySqlBindingRequest request
    ) {
        sqlBindingService.deploy(appId, new ApplicationSqlBindingService.DeploySqlBindingRequest(
                request.objectPath(),
                request.variable(),
                request.query(),
                request.refresh(),
                request.refreshIntervalMs(),
                request.valueField(),
                request.triggerObjectPath(),
                request.triggerFunctionName(),
                request.enabled()
        ));
        objectTreeService.syncApplication(appId);
        return Map.of(
                "appId", appId,
                "objectPath", request.objectPath(),
                "variable", request.variable(),
                "status", "deployed"
        );
    }

    @PostMapping("/{appId}/bindings/refresh")
    public Map<String, Object> refreshBinding(
            @PathVariable String appId,
            @RequestBody RefreshBindingRequest request
    ) {
        return sqlBindingService.refresh(appId, request.objectPath(), request.variable());
    }

    @GetMapping("/{appId}/reports")
    public List<Map<String, Object>> listReports(@PathVariable String appId) {
        return reportService.list(appId);
    }

    @PostMapping("/{appId}/reports/deploy")
    public Map<String, Object> deployReport(
            @PathVariable String appId,
            @RequestBody DeployReportRequest request
    ) {
        try {
            reportService.deploy(appId, toDeployReportRequest(request));
            objectTreeService.syncApplication(appId);
            return Map.of("appId", appId, "reportId", request.reportId(), "status", "deployed");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{appId}/reports/{reportId}/run")
    public Map<String, Object> runReport(
            @PathVariable String appId,
            @PathVariable String reportId,
            @RequestBody(required = false) RunReportRequest request
    ) {
        try {
            Map<String, Object> parameters = request != null && request.parameters() != null
                    ? request.parameters()
                    : Map.of();
            return reportService.run(appId, reportId, parameters);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/{appId}/reports/{reportId}/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable String appId,
            @PathVariable String reportId,
            @RequestParam Map<String, String> queryParams
    ) {
        try {
            Map<String, Object> parameters = new LinkedHashMap<>(queryParams);
            byte[] csv = reportService.exportCsv(appId, reportId, parameters);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reportId + ".csv\"")
                    .body(csv);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{appId}/functions/deploy")
    public Map<String, Object> deployFunction(
            @PathVariable String appId,
            @RequestBody DeployFunctionRequest request
    ) throws Exception {
        String version = request.version() != null ? request.version() : "1";
        String inputSchemaJson = objectMapper.writeValueAsString(request.descriptor().inputSchema());
        String outputSchemaJson = objectMapper.writeValueAsString(request.descriptor().outputSchema());
        return deployFunctionRecord(appId, request, version, inputSchemaJson, outputSchemaJson);
    }

    private ApplicationFunctionHandler.DeployedFunction toDeployedFunction(
            String appId,
            DeployFunctionRequest request,
            String version,
            String inputSchemaJson,
            String outputSchemaJson
    ) {
        return new ApplicationFunctionHandler.DeployedFunction(
                UUID.randomUUID(),
                appId,
                request.objectPath(),
                request.functionName(),
                version,
                request.source().type(),
                request.source().body(),
                inputSchemaJson,
                outputSchemaJson
        );
    }

    private Map<String, Object> deployFunctionRecord(
            String appId,
            DeployFunctionRequest request,
            String version,
            String inputSchemaJson,
            String outputSchemaJson
    ) {
        try {
            functionStore.deploy(toDeployedFunction(appId, request, version, inputSchemaJson, outputSchemaJson));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IllegalArgumentException cause) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, cause.getMessage(), cause);
            }
            throw ex;
        }
        objectTreeService.syncApplication(appId);
        return Map.of(
                "appId", appId,
                "objectPath", request.objectPath(),
                "functionName", request.functionName(),
                "version", version,
                "status", "deployed"
        );
    }

    public record RegisterApplicationRequest(
            String appId,
            String displayName,
            String tablePrefix,
            String schemaName
    ) {
    }

    public record SeedRequest(String profile) {
    }

    public record MigrateRequest(String version, List<MigrationScriptDto> scripts) {
    }

    public record MigrationScriptDto(String id, String sql) {
    }

    public record DeployFunctionRequest(
            String objectPath,
            String functionName,
            String version,
            FunctionDescriptorDto descriptor,
            FunctionSourceDto source
    ) {
    }

    public record FunctionDescriptorDto(DataSchema inputSchema, DataSchema outputSchema) {
    }

    public record FunctionSourceDto(String type, String body) {
    }

    public record RollbackFunctionRequest(String objectPath, String functionName, String version) {
    }

    public record DeploySqlBindingRequest(
            String objectPath,
            String variable,
            String query,
            String refresh,
            Long refreshIntervalMs,
            String valueField,
            String triggerObjectPath,
            String triggerFunctionName,
            Boolean enabled
    ) {
    }

    public record RefreshBindingRequest(String objectPath, String variable) {
    }

    public record RollbackBundleRequest(String version) {
    }

    public record ReportColumnDto(String field, String label) {
    }

    public record DeployReportRequest(
            String reportId,
            String title,
            String description,
            String query,
            List<String> parameters,
            List<ReportColumnDto> columns,
            Integer maxRows
    ) {
    }

    public record RunReportRequest(Map<String, Object> parameters) {
    }

    private static ApplicationReportService.DeployReportRequest toDeployReportRequest(
            DeployReportRequest request
    ) {
        List<ApplicationReportService.ReportColumn> columns = request.columns() == null
                ? List.of()
                : request.columns().stream()
                        .map(col -> new ApplicationReportService.ReportColumn(col.field(), col.label()))
                        .toList();
        return new ApplicationReportService.DeployReportRequest(
                request.reportId(),
                request.title(),
                request.description(),
                request.query(),
                request.parameters(),
                columns,
                request.maxRows()
        );
    }
}
