package com.ispf.ai;

import java.util.List;

public record LlmMessage(String role, String content, List<LlmContentPart> parts) {

    public LlmMessage(String role, String content) {
        this(role, content, null);
    }

    public boolean hasMultimodalParts() {
        return parts != null && !parts.isEmpty();
    }
}
