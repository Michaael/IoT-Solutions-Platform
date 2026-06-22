package com.ispf.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Map;

public final class LlmHttpSupport {

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

    public static String postJson(
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

    public static ObjectNode chatCompletionBody(LlmRequest request) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", request.model() != null ? request.model() : "");
        ArrayNode messages = root.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode item = messages.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
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
            String content = choices.get(0).path("message").path("content").asText("");
            String model = root.path("model").asText(fallbackModel);
            return new LlmResponse(content, model, parseUsage(root.path("usage")));
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
