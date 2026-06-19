package com.ispf.server.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationDataService dataService;
    private final ApplicationFunctionStore functionStore;
    private final ApplicationBundleDeployService bundleDeployService;
    private final ObjectMapper objectMapper;

    public ApplicationController(
            ApplicationDataService dataService,
            ApplicationFunctionStore functionStore,
            ApplicationBundleDeployService bundleDeployService,
            ObjectMapper objectMapper
    ) {
        this.dataService = dataService;
        this.functionStore = functionStore;
        this.bundleDeployService = bundleDeployService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{appId}/deploy")
    public Map<String, Object> deployBundle(
            @PathVariable String appId,
            @RequestBody ApplicationBundleDeployService.BundleManifest manifest
    ) {
        return bundleDeployService.deploy(appId, manifest);
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
}
