package com.ispf.ai;

public record LlmResponse(
        String content,
        String model,
        LlmUsage usage
) {
}
