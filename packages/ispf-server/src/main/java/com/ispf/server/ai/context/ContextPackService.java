package com.ispf.server.ai.context;

import com.ispf.server.config.AiProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContextPackService {

    private final AiProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private volatile Map<String, Object> cachedPack;

    public ContextPackService(
            AiProperties properties,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> info() {
        Map<String, Object> pack = loadPack();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("contextPackVersion", pack.getOrDefault("contextPackVersion", "unknown"));
        info.put("platformVersion", pack.getOrDefault("platformVersion", "unknown"));
        info.put("generatedAt", pack.getOrDefault("generatedAt", null));
        info.put("contentSha256", pack.getOrDefault("contentSha256", null));
        info.put("exampleCount", pack.get("examples") instanceof java.util.List<?> list ? list.size() : 0);
        info.put("scriptStepCount", pack.get("scriptSteps") instanceof java.util.List<?> list ? list.size() : 0);
        info.put("widgetTypeCount", pack.get("widgetTypes") instanceof java.util.List<?> list ? list.size() : 0);
        return info;
    }

    public Map<String, Object> loadPack() {
        Map<String, Object> local = cachedPack;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedPack != null) {
                return cachedPack;
            }
            cachedPack = readPack();
            return cachedPack;
        }
    }

    public String contextPackVersion() {
        Object version = loadPack().get("contextPackVersion");
        return version != null ? String.valueOf(version) : "unknown";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPack() {
        String location = properties.getContextPackClasspath();
        if (location == null || location.isBlank()) {
            location = "classpath:ai/context-pack.json";
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return Map.of(
                    "contextPackVersion", "missing",
                    "generationPolicy", Map.of(
                            "allowedArtifacts", java.util.List.of("bundle"),
                            "forbidden", java.util.List.of("java in ispf-server")
                    )
            );
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load context pack from " + location + ": " + ex.getMessage(), ex);
        }
    }
}
