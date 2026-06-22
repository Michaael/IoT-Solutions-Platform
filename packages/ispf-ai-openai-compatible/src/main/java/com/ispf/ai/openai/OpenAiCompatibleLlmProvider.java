package com.ispf.ai.openai;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmModelInfo;
import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.http.LlmHttpSupport;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completions API (OpenAI, Azure OpenAI, local proxies).
 */
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private final String baseUrl;
    private final String defaultModel;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmProvider(String baseUrl, String defaultModel, String apiKey, Duration timeout) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.defaultModel = defaultModel;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.httpClient = LlmHttpSupport.client(timeout);
    }

    @Override
    public String providerId() {
        return "openai-compatible";
    }

    @Override
    public boolean isAvailable() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public List<LlmModelInfo> listModels() throws LlmException {
        if (!isAvailable()) {
            throw new LlmException("OpenAI-compatible provider is not configured");
        }
        String json = LlmHttpSupport.getJson(
                httpClient,
                timeout,
                baseUrl + "/models",
                authHeaders()
        );
        return LlmHttpSupport.parseOpenAiModels(json);
    }

    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
        if (!isAvailable()) {
            throw new LlmException("OpenAI-compatible provider is not configured");
        }
        String model = request.model() != null && !request.model().isBlank() ? request.model() : defaultModel;
        var body = LlmHttpSupport.chatCompletionBody(new LlmRequest(
                model,
                request.messages(),
                request.maxTokens(),
                request.temperature()
        ));
        String json = LlmHttpSupport.postJson(
                httpClient,
                timeout,
                baseUrl + "/chat/completions",
                authHeaders(),
                body
        );
        return LlmHttpSupport.parseChatCompletion(json, model);
    }

    private Map<String, String> authHeaders() {
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of();
        }
        return Map.of("Authorization", "Bearer " + apiKey);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
