package com.ispf.ai;

import java.util.List;

public record LlmRequest(
        String model,
        List<LlmMessage> messages,
        Integer maxTokens,
        Double temperature
) {
}
