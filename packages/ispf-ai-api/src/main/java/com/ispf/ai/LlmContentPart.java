package com.ispf.ai;

/**
 * One part of a multimodal LLM message (OpenAI-compatible chat format).
 */
public record LlmContentPart(String type, String text, String imageUrl) {

    public static LlmContentPart text(String text) {
        return new LlmContentPart("text", text, null);
    }

    public static LlmContentPart imageUrl(String url) {
        return new LlmContentPart("image_url", null, url);
    }

    public boolean isText() {
        return "text".equals(type);
    }

    public boolean isImageUrl() {
        return "image_url".equals(type);
    }
}
