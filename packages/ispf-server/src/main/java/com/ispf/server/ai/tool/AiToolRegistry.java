package com.ispf.server.ai.tool;

import com.ispf.ai.LlmModelInfo;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.validation.BundleManifestValidator;
import com.ispf.server.ai.validation.BundleValidationResult;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiToolRegistry {

    private final BundleManifestValidator manifestValidator;
    private final ContextPackService contextPackService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final AiToolAuditService auditService;
    private final ObjectMapper objectMapper;

    public AiToolRegistry(
            BundleManifestValidator manifestValidator,
            ContextPackService contextPackService,
            LlmProviderRegistry llmProviderRegistry,
            AiToolAuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.manifestValidator = manifestValidator;
        this.contextPackService = contextPackService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> contextPackInfo() {
        return contextPackService.info();
    }

    public Map<String, Object> validateBundle(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            String actor
    ) {
        BundleValidationResult result = manifestValidator.validate(appId, manifest);
        long auditId = auditService.record(
                "validate_bundle",
                appId,
                actor,
                writeManifest(manifest),
                result.status(),
                null,
                null,
                contextPackService.contextPackVersion(),
                result.errors()
        );
        Map<String, Object> response = result.toMap();
        response.put("auditId", auditId);
        return response;
    }

    public Map<String, Object> dryRunDeploy(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            String actor
    ) {
        BundleValidationResult result = manifestValidator.dryRun(appId, manifest);
        long auditId = auditService.record(
                "dry_run_deploy",
                appId,
                actor,
                writeManifest(manifest),
                result.status(),
                null,
                null,
                contextPackService.contextPackVersion(),
                result.errors()
        );
        Map<String, Object> response = result.toMap();
        response.put("auditId", auditId);
        return response;
    }

    public Map<String, Object> listModels(String actor) {
        List<Map<String, Object>> models = new ArrayList<>();
        String status = BundleValidationResult.OK;
        List<String> errors = List.of();
        try {
            for (LlmModelInfo model : llmProviderRegistry.listModels()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", model.id());
                item.put("displayName", model.displayName());
                models.add(item);
            }
        } catch (Exception ex) {
            status = BundleValidationResult.ERROR;
            errors = List.of(ex.getMessage());
        }
        long auditId = auditService.record(
                "list_models",
                null,
                actor,
                "{}",
                status,
                llmProviderRegistry.activeProvider().providerId(),
                null,
                contextPackService.contextPackVersion(),
                errors
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status);
        response.put("models", models);
        response.put("provider", llmProviderRegistry.status());
        response.put("auditId", auditId);
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        }
        return response;
    }

    private String writeManifest(ApplicationBundleDeployService.BundleManifest manifest) {
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
