package com.ispf.server.ai.agent;

import com.ispf.ai.LlmContentPart;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.config.AiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AgentAttachmentValidator {

    private final AiProperties properties;
    private final LlmProviderRegistry llmProviderRegistry;

    public AgentAttachmentValidator(AiProperties properties, LlmProviderRegistry llmProviderRegistry) {
        this.properties = properties;
        this.llmProviderRegistry = llmProviderRegistry;
    }

    public record AttachmentInput(String name, String mimeType, String contentBase64) {
    }

    public record PreparedUserMessage(
            String displayMessage,
            String llmText,
            List<LlmContentPart> imageParts,
            List<Map<String, Object>> attachmentMetadata,
            boolean hasImages
    ) {
    }

    public PreparedUserMessage prepare(String message, List<AttachmentInput> attachments) {
        String trimmedMessage = message != null ? message.trim() : "";
        if (trimmedMessage.isEmpty() && (attachments == null || attachments.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        if (attachments == null || attachments.isEmpty()) {
            return new PreparedUserMessage(trimmedMessage, trimmedMessage, List.of(), List.of(), false);
        }

        String providerId = llmProviderRegistry.activeProvider().providerId();
        boolean vision = llmProviderRegistry.visionEnabled();
        boolean textAllowed = AgentInputCapabilities.textAttachmentsEnabled(properties, providerId);

        StringBuilder llmTextBuilder = new StringBuilder(trimmedMessage);
        List<LlmContentPart> imageParts = new ArrayList<>();
        List<Map<String, Object>> metadata = new ArrayList<>();

        for (AttachmentInput attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String name = attachment.name() != null ? attachment.name().trim() : "attachment";
            String mimeType = normalizeMime(attachment.mimeType(), name);
            byte[] bytes = decodeBase64(attachment.contentBase64());

            int maxBytes = properties.getAgentMaxAttachmentBytes();
            if (bytes.length > maxBytes) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Attachment '" + name + "' exceeds max size of " + maxBytes + " bytes"
                );
            }

            if (AgentInputCapabilities.isImageMime(mimeType)
                    || AgentInputCapabilities.isAllowedExtension(name, AgentInputCapabilities.imageExtensions())) {
                if (!vision) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "vision-not-supported: current model does not support image attachments"
                    );
                }
                String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
                imageParts.add(LlmContentPart.imageUrl(dataUri));
                metadata.add(attachmentMeta(name, mimeType, bytes.length, "image", null));
                continue;
            }

            if (!textAllowed) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text attachments are not available");
            }
            if (!AgentInputCapabilities.isTextMime(mimeType)
                    && !AgentInputCapabilities.isAllowedExtension(name, AgentInputCapabilities.textExtensions())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported attachment type: " + mimeType + " (" + name + ")"
                );
            }

            String textContent = new String(bytes, StandardCharsets.UTF_8);
            int maxInject = properties.getAgentMaxTextInjectChars();
            boolean truncated = textContent.length() > maxInject;
            if (truncated) {
                textContent = textContent.substring(0, maxInject) + "\n… [truncated]";
            }
            appendTextAttachment(llmTextBuilder, name, textContent);
            metadata.add(attachmentMeta(name, mimeType, bytes.length, "text", truncated));
        }

        String llmText = llmTextBuilder.toString().trim();
        String display = buildDisplayMessage(trimmedMessage, metadata);
        return new PreparedUserMessage(display, llmText, List.copyOf(imageParts), List.copyOf(metadata), !imageParts.isEmpty());
    }

    private static void appendTextAttachment(StringBuilder builder, String name, String content) {
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append("--- Attachment: ").append(name).append(" ---\n");
        builder.append("```\n").append(content).append("\n```");
    }

    private static String buildDisplayMessage(String message, List<Map<String, Object>> metadata) {
        if (metadata.isEmpty()) {
            return message;
        }
        StringBuilder display = new StringBuilder(message != null ? message : "");
        for (Map<String, Object> item : metadata) {
            if (!display.isEmpty()) {
                display.append("\n");
            }
            display.append("📎 ").append(item.get("name"));
        }
        return display.toString().trim();
    }

    private static Map<String, Object> attachmentMeta(
            String name,
            String mimeType,
            int byteSize,
            String kind,
            Boolean truncated
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", name);
        meta.put("mimeType", mimeType);
        meta.put("byteSize", byteSize);
        meta.put("kind", kind);
        if (truncated != null) {
            meta.put("truncated", truncated);
        }
        return meta;
    }

    private static byte[] decodeBase64(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attachment contentBase64 is required");
        }
        String payload = raw.trim();
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment base64 content");
        }
    }

    private static String normalizeMime(String mimeType, String fileName) {
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType.trim().toLowerCase(Locale.ROOT);
        }
        if (fileName == null) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".xml") || lower.endsWith(".bpmn")) {
            return "application/xml";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".properties")) {
            return "text/plain";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }
}
