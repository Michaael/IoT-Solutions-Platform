package com.ispf.server.application.function;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class FunctionInvokeAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final String auditTable;

    public FunctionInvokeAuditService(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditTable = platformSqlCatalog.table("function_invoke_audit");
    }

    public void record(String appId, String objectPath, String functionName, boolean success, String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, correlation_id, object_path, function_name, app_id, success, error_message, invoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(auditTable),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                objectPath,
                functionName,
                appId,
                success,
                errorMessage,
                Timestamp.from(Instant.now())
        );
    }
}
