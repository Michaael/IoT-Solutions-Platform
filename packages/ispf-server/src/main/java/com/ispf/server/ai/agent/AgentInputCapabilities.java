package com.ispf.server.ai.agent;

import com.ispf.server.ai.llm.NoopLlmProvider;
import com.ispf.server.config.AiProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-side source of truth for agent chat input capabilities (vision, text attachments).
 */
public final class AgentInputCapabilities {

    private static final List<String> TEXT_MIME_TYPES = List.of(
            "text/plain",
            "text/csv",
            "text/markdown",
            "text/xml",
            "text/yaml",
            "application/json",
            "application/xml",
            "application/yaml",
            "application/x-yaml"
    );

    private static final List<String> TEXT_EXTENSIONS = List.of(
            ".txt", ".csv", ".md", ".json", ".xml", ".yaml", ".yml", ".bpmn", ".properties"
    );

    private static final List<String> IMAGE_MIME_TYPES = List.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/gif"
    );

    private static final List<String> IMAGE_EXTENSIONS = List.of(
            ".png", ".jpg", ".jpeg", ".webp", ".gif"
    );

    private static final Pattern VISION_MODEL_PATTERN = Pattern.compile(
            "(gpt-4o|gpt-4\\.1|gpt-4-vision|claude-3|gemini-.*|qwen.*vl|llava|pixtral|vision|moondream|idefics|internvl|minicpm-v)",
            Pattern.CASE_INSENSITIVE
    );

    private AgentInputCapabilities() {
    }

    public static boolean visionEnabled(AiProperties properties, String providerId) {
        if (properties == null || !properties.isEnabled()) {
            return false;
        }
        if (providerId == null || NoopLlmProvider.PROVIDER_ID.equals(providerId)) {
            return false;
        }
        Boolean override = properties.getAgentVisionEnabled();
        if (override != null) {
            return override;
        }
        return modelSupportsVision(properties.getModel());
    }

    public static boolean textAttachmentsEnabled(AiProperties properties, String providerId) {
        return properties != null
                && properties.isEnabled()
                && providerId != null
                && !NoopLlmProvider.PROVIDER_ID.equals(providerId);
    }

    public static boolean modelSupportsVision(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        return VISION_MODEL_PATTERN.matcher(model.trim()).find();
    }

    public static Map<String, Object> capabilitiesMap(AiProperties properties, String providerId) {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("vision", visionEnabled(properties, providerId));
        caps.put("textAttachments", textAttachmentsEnabled(properties, providerId));
        return caps;
    }

    public static List<Map<String, Object>> supportedAttachmentTypes(AiProperties properties, String providerId) {
        List<Map<String, Object>> types = new ArrayList<>();
        if (textAttachmentsEnabled(properties, providerId)) {
            types.add(attachmentType("text", TEXT_MIME_TYPES, TEXT_EXTENSIONS));
        }
        if (visionEnabled(properties, providerId)) {
            types.add(attachmentType("image", IMAGE_MIME_TYPES, IMAGE_EXTENSIONS));
        }
        return types;
    }

    public static boolean isImageMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("image/");
    }

    public static boolean isTextMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        if (TEXT_MIME_TYPES.contains(normalized)) {
            return true;
        }
        return normalized.startsWith("text/");
    }

    public static boolean isAllowedExtension(String fileName, List<String> extensions) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        for (String ext : extensions) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> textExtensions() {
        return TEXT_EXTENSIONS;
    }

    public static List<String> imageExtensions() {
        return IMAGE_EXTENSIONS;
    }

    private static Map<String, Object> attachmentType(String kind, List<String> mimeTypes, List<String> extensions) {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("kind", kind);
        type.put("mimeTypes", mimeTypes);
        type.put("extensions", extensions);
        return type;
    }
}
