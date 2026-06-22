package com.ispf.ai;

public record LlmUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
