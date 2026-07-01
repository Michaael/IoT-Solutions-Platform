package com.ispf.ai;

public record LlmResponse(
        String content,
        String model,
        LlmUsage usage,
        String finishReason
) {
    public LlmResponse(String content, String model, LlmUsage usage) {
        this(content, model, usage, null);
    }

    public boolean truncatedByLength() {
        return finishReason != null && finishReason.equalsIgnoreCase("length");
    }
}
