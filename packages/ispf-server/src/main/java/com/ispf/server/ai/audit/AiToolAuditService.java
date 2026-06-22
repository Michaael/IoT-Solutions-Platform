package com.ispf.server.ai.audit;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class AiToolAuditService {

    private final AiToolAuditStore store;

    public AiToolAuditService(AiToolAuditStore store) {
        this.store = store;
    }

    public long record(
            String toolName,
            String appId,
            String actor,
            String requestBody,
            String status,
            String providerId,
            String modelId,
            String contextPackVersion,
            List<String> errors
    ) {
        return store.insert(new AiToolAuditStore.AuditEntry(
                toolName,
                appId,
                actor,
                sha256(requestBody),
                status,
                providerId,
                modelId,
                contextPackVersion,
                errors,
                Instant.now()
        ));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return "hash-error";
        }
    }
}
