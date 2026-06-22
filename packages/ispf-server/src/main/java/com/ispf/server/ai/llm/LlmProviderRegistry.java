package com.ispf.server.ai.llm;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmModelInfo;
import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.ollama.OllamaLlmProvider;
import com.ispf.ai.openai.OpenAiCompatibleLlmProvider;
import com.ispf.server.config.AiProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmProviderRegistry {

    private final AiProperties properties;
    private final NoopLlmProvider noopProvider;
    private volatile LlmProvider activeProvider;

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
        return status;
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
