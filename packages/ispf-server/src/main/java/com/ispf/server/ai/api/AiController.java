package com.ispf.server.ai.api;

import com.ispf.server.ai.generation.AiBundleGenerationService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiToolRegistry toolRegistry;
    private final LlmProviderRegistry llmProviderRegistry;
    private final AiBundleGenerationService generationService;

    public AiController(
            AiToolRegistry toolRegistry,
            LlmProviderRegistry llmProviderRegistry,
            AiBundleGenerationService generationService
    ) {
        this.toolRegistry = toolRegistry;
        this.llmProviderRegistry = llmProviderRegistry;
        this.generationService = generationService;
    }

    @GetMapping("/models")
    public Map<String, Object> listModels(Authentication authentication) {
        return toolRegistry.listModels(actor(authentication));
    }

    @GetMapping("/provider")
    public Map<String, Object> providerStatus() {
        return llmProviderRegistry.status();
    }

    @GetMapping("/tools/context-pack")
    public Map<String, Object> contextPackInfo() {
        return toolRegistry.contextPackInfo();
    }

    @PostMapping("/tools/validate-bundle")
    public Map<String, Object> validateBundle(
            Authentication authentication,
            @Valid @RequestBody BundleToolRequest request
    ) {
        return toolRegistry.validateBundle(request.appId(), request.manifest(), actor(authentication));
    }

    @PostMapping("/tools/dry-run-deploy")
    public Map<String, Object> dryRunDeploy(
            Authentication authentication,
            @Valid @RequestBody BundleToolRequest request
    ) {
        return toolRegistry.dryRunDeploy(request.appId(), request.manifest(), actor(authentication));
    }

    @PostMapping("/bundles/generate")
    public Map<String, Object> generateBundle(
            Authentication authentication,
            @Valid @RequestBody GenerateBundleRequest request
    ) {
        try {
            return generationService.generate(
                    request.appId(),
                    request.prompt(),
                    request.baseManifest(),
                    actor(authentication)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private static String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "anonymous";
    }

    public record BundleToolRequest(
            @NotBlank String appId,
            @Valid ApplicationBundleDeployService.BundleManifest manifest
    ) {
    }

    public record GenerateBundleRequest(
            @NotBlank String appId,
            @NotBlank String prompt,
            ApplicationBundleDeployService.BundleManifest baseManifest
    ) {
    }
}
