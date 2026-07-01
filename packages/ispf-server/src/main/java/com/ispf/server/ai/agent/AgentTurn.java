package com.ispf.server.ai.agent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentTurn {

    private final String turnId;
    private final String userMessage;
    private final String assistantSummary;
    private final String status;
    private final List<Map<String, Object>> steps;
    private final Map<String, Object> result;
    private final List<Map<String, Object>> attachments;
    private final String interactionMode;
    private final Instant createdAt;

    public AgentTurn(
            String turnId,
            String userMessage,
            String assistantSummary,
            String status,
            List<Map<String, Object>> steps,
            Map<String, Object> result,
            List<Map<String, Object>> attachments,
            String interactionMode,
            Instant createdAt
    ) {
        this.turnId = turnId;
        this.userMessage = userMessage;
        this.assistantSummary = assistantSummary;
        this.status = status;
        this.steps = steps != null ? List.copyOf(steps) : List.of();
        this.result = result != null ? Map.copyOf(result) : Map.of();
        this.attachments = attachments != null ? List.copyOf(attachments) : List.of();
        this.interactionMode = interactionMode;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static AgentTurn create(
            String userMessage,
            String assistantSummary,
            String status,
            List<Map<String, Object>> steps,
            Map<String, Object> result
    ) {
        return create(userMessage, assistantSummary, status, steps, result, List.of());
    }

    public static AgentTurn create(
            String userMessage,
            String assistantSummary,
            String status,
            List<Map<String, Object>> steps,
            Map<String, Object> result,
            List<Map<String, Object>> attachments,
            String interactionMode
    ) {
        return new AgentTurn(
                UUID.randomUUID().toString(),
                userMessage,
                assistantSummary,
                status,
                steps,
                result,
                attachments,
                interactionMode,
                Instant.now()
        );
    }

    public static AgentTurn create(
            String userMessage,
            String assistantSummary,
            String status,
            List<Map<String, Object>> steps,
            Map<String, Object> result,
            List<Map<String, Object>> attachments
    ) {
        return create(userMessage, assistantSummary, status, steps, result, attachments, null);
    }

    public String turnId() {
        return turnId;
    }

    public String userMessage() {
        return userMessage;
    }

    public String assistantSummary() {
        return assistantSummary;
    }

    public String status() {
        return status;
    }

    public List<Map<String, Object>> steps() {
        return steps;
    }

    public Map<String, Object> result() {
        return result;
    }

    public List<Map<String, Object>> attachments() {
        return attachments;
    }

    public String interactionMode() {
        return interactionMode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("turnId", turnId);
        map.put("userMessage", userMessage);
        map.put("assistantSummary", assistantSummary);
        map.put("status", status);
        map.put("steps", steps);
        map.put("result", result);
        map.put("createdAt", createdAt.toString());
        if (!attachments.isEmpty()) {
            map.put("attachments", attachments);
        }
        if (interactionMode != null && !interactionMode.isBlank()) {
            map.put("interactionMode", interactionMode);
        }
        return map;
    }
}
