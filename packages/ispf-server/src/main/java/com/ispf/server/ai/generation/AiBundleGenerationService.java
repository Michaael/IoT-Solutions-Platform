package com.ispf.server.ai.generation;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.ai.validation.BundleValidationResult;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.config.AiProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiBundleGenerationService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final ContextPackService contextPackService;
    private final AiToolRegistry toolRegistry;
    private final AiToolAuditService auditService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiBundleGenerationService(
            LlmProviderRegistry llmProviderRegistry,
            ContextPackService contextPackService,
            AiToolRegistry toolRegistry,
            AiToolAuditService auditService,
            AiProperties aiProperties,
            ObjectMapper objectMapper
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.contextPackService = contextPackService;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> generate(
            String appId,
            String prompt,
            ApplicationBundleDeployService.BundleManifest baseManifest,
            String actor
    ) throws Exception {
        if (!llmProviderRegistry.isGenerationAvailable()) {
            throw new IllegalStateException(
                    "LLM provider is not configured. Set ispf.ai.provider and base-url/model."
            );
        }

        String systemPrompt = buildSystemPrompt(appId);
        LlmResponse response = llmProviderRegistry.complete(new LlmRequest(
                aiProperties.getModel(),
                List.of(
                        new LlmMessage("system", systemPrompt),
                        new LlmMessage("user", prompt)
                ),
                aiProperties.getMaxTokens(),
                aiProperties.getTemperature()
        ));

        ApplicationBundleDeployService.BundleManifest generated = parseBundleManifest(
                response.content(),
                baseManifest
        );

        Map<String, Object> validation = toolRegistry.validateBundle(appId, generated, actor);
        Map<String, Object> dryRun = toolRegistry.dryRunDeploy(appId, generated, actor);

        boolean publishable = BundleValidationResult.OK.equals(validation.get("status"))
                && BundleValidationResult.OK.equals(dryRun.get("status"));

        long auditId = auditService.record(
                "generate_bundle",
                appId,
                actor,
                prompt,
                publishable ? BundleValidationResult.OK : BundleValidationResult.ERROR,
                llmProviderRegistry.activeProvider().providerId(),
                response.model(),
                contextPackService.contextPackVersion(),
                mergeErrors(validation, dryRun)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifact", objectMapper.convertValue(generated, Map.class));
        result.put("validation", validation);
        result.put("dryRun", dryRun);
        result.put("publishable", publishable);
        result.put("auditId", auditId);
        result.put("provider", llmProviderRegistry.status());
        return result;
    }

    private List<String> mergeErrors(Map<String, Object> validation, Map<String, Object> dryRun) {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        appendErrors(errors, validation);
        appendErrors(errors, dryRun);
        return errors;
    }

    @SuppressWarnings("unchecked")
    private void appendErrors(List<String> errors, Map<String, Object> section) {
        Object raw = section.get("errors");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                errors.add(String.valueOf(item));
            }
        }
    }

    private String buildSystemPrompt(String appId) {
        Map<String, Object> pack = contextPackService.loadPack();
        return """
                You are an ISPF solution developer assistant.
                Generate ONLY a valid bundle manifest JSON object.
                Do NOT output Java, React, explanations, or markdown fences.
                Target appId: %s
                Allowed artifacts: bundle JSON with migrations, functions, dashboards, operatorUi, events, reports, models, workflows.
                Forbidden: Java in ispf-server, custom BFF routes, platform Flyway for app tables.
                Context pack:
                %s
                """.formatted(appId, writeJson(pack));
    }

    private ApplicationBundleDeployService.BundleManifest parseBundleManifest(
            String content,
            ApplicationBundleDeployService.BundleManifest baseManifest
    ) throws Exception {
        String json = extractJsonObject(content);
        if (baseManifest != null) {
            Map<String, Object> merged = objectMapper.convertValue(baseManifest, Map.class);
            Map<String, Object> generated = objectMapper.readValue(json, Map.class);
            merged.putAll(generated);
            return objectMapper.convertValue(merged, ApplicationBundleDeployService.BundleManifest.class);
        }
        return objectMapper.readValue(json, ApplicationBundleDeployService.BundleManifest.class);
    }

    private String extractJsonObject(String content) throws Exception {
        String trimmed = content != null ? content.trim() : "";
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
        }
        JsonNode node = objectMapper.readTree(trimmed);
        if (node.isObject()) {
            return objectMapper.writeValueAsString(node);
        }
        throw new IllegalArgumentException("LLM response is not a JSON object");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
