package com.ispf.server.ai.audit;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class AiToolAuditStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String auditTable;

    public AiToolAuditStore(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformSqlCatalog platformSqlCatalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditTable = platformSqlCatalog.table("ai_tool_audit");
    }

    public long insert(AuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    tool_name, app_id, actor, request_hash, status,
                    provider_id, model_id, context_pack_version, errors_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(auditTable),
                entry.toolName(),
                entry.appId(),
                entry.actor(),
                entry.requestHash(),
                entry.status(),
                entry.providerId(),
                entry.modelId(),
                entry.contextPackVersion(),
                writeErrors(entry.errors()),
                Timestamp.from(entry.createdAt())
        );
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM %s".formatted(auditTable), Long.class);
        return id != null ? id : 0L;
    }

    private String writeErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (Exception ex) {
            return "[\"audit serialization failed\"]";
        }
    }

    public record AuditEntry(
            String toolName,
            String appId,
            String actor,
            String requestHash,
            String status,
            String providerId,
            String modelId,
            String contextPackVersion,
            List<String> errors,
            Instant createdAt
    ) {
    }
}
