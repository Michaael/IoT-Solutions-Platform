package com.ispf.server.ai.llm;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmModelInfo;
import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.ollama.OllamaLlmProvider;
import com.ispf.ai.openai.OpenAiCompatibleLlmProvider;
import com.ispf.server.ai.agent.AgentInputCapabilities;
import com.ispf.server.config.AiProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LlmProviderRegistry {

    private static final long VISION_CACHE_TTL_MS = 3_600_000L;

    private record VisionCacheEntry(boolean supported, long expiresAtMs) {
    }

    private final AiProperties properties;
    private final NoopLlmProvider noopProvider;
    private volatile LlmProvider activeProvider;
    private final ConcurrentHashMap<String, VisionCacheEntry> visionCache = new ConcurrentHashMap<>();

    public LlmProviderRegistry(AiProperties properties, NoopLlmProvider noopProvider) {
        this.properties = properties;
        this.noopProvider = noopProvider;
    }

    public LlmProvider activeProvider() {
        if (!properties.isEnabled()) {
            return noopProvider;
        }
        LlmProvider cached = activeProvider;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (activeProvider == null) {
                activeProvider = createProvider(properties.getProvider());
            }
            return activeProvider;
        }
    }

    public boolean isGenerationAvailable() {
        LlmProvider provider = activeProvider();
        if (NoopLlmProvider.PROVIDER_ID.equals(provider.providerId())) {
            return false;
        }
        if (!provider.isAvailable()) {
            return false;
        }
        return !requiresApiKey(provider) || !resolveApiKey().isBlank();
    }

    public Map<String, Object> status() {
        LlmProvider provider = activeProvider();
        boolean available = provider.isAvailable();
        if (available && requiresApiKey(provider) && resolveApiKey().isBlank()) {
            available = false;
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("providerId", provider.providerId());
        status.put("available", available);
        status.put("model", properties.getModel());
        if (!available && requiresApiKey(provider) && resolveApiKey().isBlank()) {
            status.put("reason", "missing-api-key");
        }
        boolean vision = visionEnabled();
        status.put("capabilities", AgentInputCapabilities.capabilitiesMap(
                properties,
                provider.providerId(),
                vision
        ));
        status.put(
                "supportedAttachmentTypes",
                AgentInputCapabilities.supportedAttachmentTypes(
                        properties,
                        provider.providerId(),
                        vision
                )
        );
        return status;
    }

    /**
     * Resolves vision support via explicit config override, live provider probe (cached), or name heuristic fallback.
     */
    public boolean visionEnabled() {
        if (!properties.isEnabled()) {
            return false;
        }
        LlmProvider provider = activeProvider();
        String providerId = provider.providerId();
        if (NoopLlmProvider.PROVIDER_ID.equals(providerId)) {
            return false;
        }
        Boolean override = properties.getAgentVisionEnabled();
        if (override != null) {
            return override;
        }
        if (!isGenerationAvailable()) {
            return false;
        }
        String model = properties.getModel();
        if (model == null || model.isBlank()) {
            return false;
        }
        String cacheKey = providerId + "|" + properties.getBaseUrl() + "|" + model.trim();
        long now = System.currentTimeMillis();
        VisionCacheEntry cached = visionCache.get(cacheKey);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.supported();
        }
        try {
            boolean supported = provider.supportsVision(model);
            visionCache.put(cacheKey, new VisionCacheEntry(supported, now + VISION_CACHE_TTL_MS));
            return supported;
        } catch (LlmException ex) {
            return AgentInputCapabilities.modelSupportsVision(model);
        }
    }

    public List<LlmModelInfo> listModels() throws LlmException {
        LlmProvider provider = activeProvider();
        if (!provider.isAvailable()) {
            return List.of();
        }
        try {
            return provider.listModels();
        } catch (LlmException ex) {
            if (properties.getModel() != null && !properties.getModel().isBlank()) {
                return List.of(new LlmModelInfo(properties.getModel(), properties.getModel()));
            }
            throw ex;
        }
    }

    public LlmResponse complete(LlmRequest request) throws LlmException {
        LlmProvider provider = activeProvider();
        if (!provider.isAvailable()) {
            throw new LlmException("LLM provider '" + provider.providerId() + "' is not configured");
        }
        return provider.complete(request);
    }

    private LlmProvider createProvider(String providerId) {
        if (providerId == null || providerId.isBlank() || "noop".equalsIgnoreCase(providerId)) {
            return noopProvider;
        }
        if ("openai-compatible".equalsIgnoreCase(providerId) || "openai".equalsIgnoreCase(providerId)) {
            return new OpenAiCompatibleLlmProvider(
                    properties.getBaseUrl(),
                    properties.getModel(),
                    resolveApiKey(),
                    properties.timeout()
            );
        }
        if ("ollama".equalsIgnoreCase(providerId)) {
            return new OllamaLlmProvider(
                    properties.getBaseUrl(),
                    properties.getModel(),
                    properties.timeout()
            );
        }
        if ("custom-url".equalsIgnoreCase(providerId)) {
            return new OpenAiCompatibleLlmProvider(
                    properties.getBaseUrl(),
                    properties.getModel(),
                    resolveApiKey(),
                    properties.timeout()
            );
        }
        return noopProvider;
    }

    private String resolveApiKey() {
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            return properties.getApiKey();
        }
        String envName = properties.getApiKeyEnv();
        if (envName == null || envName.isBlank()) {
            return "";
        }
        String value = System.getenv(envName);
        return value != null ? value : "";
    }

    private static boolean requiresApiKey(LlmProvider provider) {
        String id = provider.providerId();
        return "openai-compatible".equals(id) || "openai".equals(id) || "custom-url".equals(id);
    }
}
