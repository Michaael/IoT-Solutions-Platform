package com.ispf.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ispf.ai.LlmContentPart;
import com.ispf.ai.LlmException;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmModelInfo;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmUsage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LlmHttpSupport {

    /** 1×1 PNG for vision capability probes (minimal valid image). */
    public static final String VISION_PROBE_PNG_DATA_URI =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmHttpSupport() {
    }

    public static HttpClient client(Duration timeout) {
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public static String getJson(
            HttpClient client,
            Duration timeout,
            String url,
            Map<String, String> headers
    ) throws LlmException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET();
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException("HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
            return response.body();
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException("LLM HTTP request failed: " + ex.getMessage(), ex);
        }
    }

    public record HttpResult(int statusCode, String body) {
    }

    public static String postJson(
            HttpClient client,
            Duration timeout,
            String url,
            Map<String, String> headers,
            ObjectNode body
    ) throws LlmException {
        HttpResult result = postJsonWithStatus(client, timeout, url, headers, body);
        if (result.statusCode() < 200 || result.statusCode() >= 300) {
            throw new LlmException("HTTP " + result.statusCode() + ": " + truncate(result.body()));
        }
        return result.body();
    }

    public static HttpResult postJsonWithStatus(
            HttpClient client,
            Duration timeout,
            String url,
            Map<String, String> headers,
            ObjectNode body
    ) throws LlmException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (Exception ex) {
            throw new LlmException("LLM HTTP request failed: " + ex.getMessage(), ex);
        }
    }

    public static ObjectNode chatCompletionBody(LlmRequest request) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", request.model() != null ? request.model() : "");
        ArrayNode messages = root.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode item = messages.addObject();
            item.put("role", message.role());
            if (message.hasMultimodalParts()) {
                ArrayNode contentParts = item.putArray("content");
                for (LlmContentPart part : message.parts()) {
                    if (part.isText() && part.text() != null && !part.text().isBlank()) {
                        ObjectNode textPart = contentParts.addObject();
                        textPart.put("type", "text");
                        textPart.put("text", part.text());
                    } else if (part.isImageUrl() && part.imageUrl() != null && !part.imageUrl().isBlank()) {
                        ObjectNode imagePart = contentParts.addObject();
                        imagePart.put("type", "image_url");
                        ObjectNode imageUrl = imagePart.putObject("image_url");
                        imageUrl.put("url", part.imageUrl());
                    }
                }
            } else {
                item.put("content", message.content() != null ? message.content() : "");
            }
        }
        if (request.maxTokens() != null) {
            root.put("max_tokens", request.maxTokens());
        }
        if (request.temperature() != null) {
            root.put("temperature", request.temperature());
        }
        if (request.providerOptions() != null && !request.providerOptions().isEmpty()) {
            mergeProviderOptions(root, request.providerOptions());
        }
        return root;
    }

    private static void mergeProviderOptions(ObjectNode root, Map<String, Object> providerOptions) {
        for (Map.Entry<String, Object> entry : providerOptions.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                ObjectNode child = root.putObject(entry.getKey());
                for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                    String key = String.valueOf(nestedEntry.getKey());
                    Object nestedValue = nestedEntry.getValue();
                    if (nestedValue instanceof Boolean bool) {
                        child.put(key, bool);
                    } else if (nestedValue instanceof Integer intValue) {
                        child.put(key, intValue);
                    } else if (nestedValue instanceof Long longValue) {
                        child.put(key, longValue);
                    } else if (nestedValue instanceof Double doubleValue) {
                        child.put(key, doubleValue);
                    } else if (nestedValue != null) {
                        child.put(key, nestedValue.toString());
                    }
                }
            } else {
                root.putPOJO(entry.getKey(), value);
            }
        }
    }

    public static ObjectNode ollamaChatBody(LlmRequest request) {
        ObjectNode root = chatCompletionBody(request);
        root.put("stream", false);
        return root;
    }

    public static LlmResponse parseChatCompletion(String json, String fallbackModel) throws LlmException {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new LlmException("LLM response missing choices");
            }
            JsonNode firstChoice = choices.get(0);
            String content = firstChoice.path("message").path("content").asText("");
            String model = root.path("model").asText(fallbackModel);
            String finishReason = firstChoice.path("finish_reason").asText(null);
            if (finishReason != null && finishReason.isBlank()) {
                finishReason = null;
            }
            return new LlmResponse(content, model, parseUsage(root.path("usage")), finishReason);
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse LLM response: " + ex.getMessage(), ex);
        }
    }

    public static LlmResponse parseOllamaChat(String json, String fallbackModel) throws LlmException {
        try {
            JsonNode root = MAPPER.readTree(json);
            String content = root.path("message").path("content").asText("");
            return new LlmResponse(content, fallbackModel, null);
        } catch (Exception ex) {
            throw new LlmException("Failed to parse Ollama response: " + ex.getMessage(), ex);
        }
    }

    public static List<LlmModelInfo> parseOpenAiModels(String json) throws LlmException {
        try {
            JsonNode data = MAPPER.readTree(json).path("data");
            List<LlmModelInfo> models = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String id = item.path("id").asText("");
                    if (!id.isBlank()) {
                        models.add(new LlmModelInfo(id, id));
                    }
                }
            }
            return models;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse models response: " + ex.getMessage(), ex);
        }
    }

    public static List<LlmModelInfo> parseOllamaModels(String json) throws LlmException {
        try {
            JsonNode models = MAPPER.readTree(json).path("models");
            List<LlmModelInfo> result = new ArrayList<>();
            if (models.isArray()) {
                for (JsonNode item : models) {
                    String name = item.path("name").asText("");
                    if (!name.isBlank()) {
                        result.add(new LlmModelInfo(name, name));
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse Ollama models: " + ex.getMessage(), ex);
        }
    }

    public static ObjectNode ollamaShowBody(String model) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model != null ? model : "");
        return root;
    }

    public static boolean parseOllamaShowVision(String json) throws LlmException {
        try {
            JsonNode capabilities = MAPPER.readTree(json).path("capabilities");
            if (!capabilities.isArray()) {
                return false;
            }
            for (JsonNode capability : capabilities) {
                if ("vision".equalsIgnoreCase(capability.asText(""))) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse Ollama show response: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads vision capability from OpenAI-compatible {@code GET /models} when present
     * (e.g. OpenRouter {@code architecture.modality}, {@code input_modalities}).
     *
     * @return {@code null} when the model entry has no modality metadata
     */
    public static Boolean visionFromOpenAiModelsList(String json, String modelId) throws LlmException {
        try {
            JsonNode data = MAPPER.readTree(json).path("data");
            if (!data.isArray() || modelId == null || modelId.isBlank()) {
                return null;
            }
            for (JsonNode item : data) {
                if (!modelId.equals(item.path("id").asText(""))) {
                    continue;
                }
                Boolean fromModality = visionFromModalityText(item.path("architecture").path("modality").asText(null));
                if (fromModality != null) {
                    return fromModality;
                }
                JsonNode inputModalities = item.path("input_modalities");
                if (inputModalities.isArray()) {
                    for (JsonNode modality : inputModalities) {
                        if ("image".equalsIgnoreCase(modality.asText(""))) {
                            return true;
                        }
                    }
                    return false;
                }
                JsonNode capabilities = item.path("capabilities");
                if (capabilities.isObject()) {
                    JsonNode vision = capabilities.path("vision");
                    if (vision.isBoolean()) {
                        return vision.asBoolean();
                    }
                }
                return null;
            }
            return null;
        } catch (Exception ex) {
            throw new LlmException("Failed to parse models vision metadata: " + ex.getMessage(), ex);
        }
    }

    public static LlmRequest visionProbeRequest(String model) {
        return new LlmRequest(
                model,
                List.of(new LlmMessage(
                        "user",
                        "ping",
                        List.of(
                                LlmContentPart.text("ping"),
                                LlmContentPart.imageUrl(VISION_PROBE_PNG_DATA_URI)
                        )
                )),
                1,
                0.0
        );
    }

    /**
     * Interprets a chat-completions probe response: 2xx = vision supported;
     * 4xx with vision-related error text = not supported.
     */
    public static boolean interpretVisionProbeResult(int statusCode, String body) throws LlmException {
        if (statusCode >= 200 && statusCode < 300) {
            return true;
        }
        if (statusCode >= 400 && statusCode < 500) {
            if (looksLikeVisionRejection(body)) {
                return false;
            }
            throw new LlmException("Vision probe HTTP " + statusCode + ": " + truncate(body));
        }
        throw new LlmException("Vision probe HTTP " + statusCode + ": " + truncate(body));
    }

    private static Boolean visionFromModalityText(String modality) {
        if (modality == null || modality.isBlank()) {
            return null;
        }
        String normalized = modality.toLowerCase(Locale.ROOT);
        if (normalized.contains("image")) {
            return true;
        }
        if (normalized.contains("text") && !normalized.contains("image")) {
            return false;
        }
        return null;
    }

    private static boolean looksLikeVisionRejection(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("vision")
                || lower.contains("image")
                || lower.contains("multimodal")
                || lower.contains("modality")
                || lower.contains("does not support")
                || lower.contains("not support")
                || lower.contains("unsupported")
                || lower.contains("invalid content")
                || lower.contains("expected text");
    }

    private static LlmUsage parseUsage(JsonNode usageNode) {
        if (!usageNode.isObject()) {
            return null;
        }
        return new LlmUsage(
                intOrNull(usageNode.path("prompt_tokens")),
                intOrNull(usageNode.path("completion_tokens")),
                intOrNull(usageNode.path("total_tokens"))
        );
    }

    private static Integer intOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
