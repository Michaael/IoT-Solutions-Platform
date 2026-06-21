package com.ispf.server.platform;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectRevisionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformChangeSetService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectManager objectManager;

    public PlatformChangeSetService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectManager objectManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.objectManager = objectManager;
    }

    public ChangeSet create(String title, String author, List<ChangeOp> ops) {
        try {
            String id = UUID.randomUUID().toString();
            Instant now = Instant.now();
            jdbcTemplate.update("""
                    INSERT INTO platform_change_sets (
                        id, title, author, status, base_snapshot, ops_json, created_at, updated_at
                    ) VALUES (?, ?, ?, 'DRAFT', NULL, ?, ?, ?)
                    """,
                    id,
                    title,
                    author,
                    objectMapper.writeValueAsString(ops),
                    Timestamp.from(now),
                    Timestamp.from(now)
            );
            return require(id);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create change-set: " + ex.getMessage(), ex);
        }
    }

    public List<ChangeSetSummary> list(String status) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, title, author, status, created_at, updated_at
                    FROM platform_change_sets
                    ORDER BY updated_at DESC
                    """,
                    (rs, rowNum) -> summaryFromRow(rs)
            );
        }
        return jdbcTemplate.query("""
                SELECT id, title, author, status, created_at, updated_at
                FROM platform_change_sets
                WHERE status = ?
                ORDER BY updated_at DESC
                """,
                (rs, rowNum) -> summaryFromRow(rs),
                status.toUpperCase()
        );
    }

    public ChangeSet require(String id) {
        List<ChangeSet> rows = jdbcTemplate.query("""
                SELECT id, title, author, status, base_snapshot, ops_json, created_at, updated_at
                FROM platform_change_sets
                WHERE id = ?
                """,
                (rs, rowNum) -> mapRow(rs),
                id
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Change-set not found: " + id);
        }
        return rows.getFirst();
    }

    public Map<String, Object> preview(String id) {
        ChangeSet changeSet = require(id);
        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Map<String, Object>> applicable = new ArrayList<>();
        for (ChangeOp op : changeSet.ops()) {
            if (!objectManager.tree().findByPath(op.path()).isPresent()) {
                applicable.add(Map.of("op", op.op(), "path", op.path(), "status", "CREATE"));
                continue;
            }
            PlatformObject current = objectManager.require(op.path());
            if (op.expectedRevision() != null && op.expectedRevision() != current.revision()) {
                conflicts.add(Map.of(
                        "path", op.path(),
                        "expectedRevision", op.expectedRevision(),
                        "currentRevision", current.revision(),
                        "changedBy", current.lastChangedBy() != null ? current.lastChangedBy() : "",
                        "op", op.op()
                ));
            } else {
                applicable.add(Map.of(
                        "op", op.op(),
                        "path", op.path(),
                        "status", "UPDATE",
                        "currentRevision", current.revision()
                ));
            }
        }
        return Map.of(
                "changeSetId", id,
                "title", changeSet.title(),
                "conflicts", conflicts,
                "applicable", applicable,
                "conflictCount", conflicts.size()
        );
    }

    @Transactional
    public Map<String, Object> apply(String id, boolean force) {
        ChangeSet changeSet = require(id);
        Map<String, Object> preview = preview(id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) preview.get("conflicts");
        if (!conflicts.isEmpty() && !force) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Change-set has " + conflicts.size() + " conflicts");
        }
        List<String> applied = new ArrayList<>();
        for (ChangeOp op : changeSet.ops()) {
            applyOp(op, force);
            applied.add(op.path());
        }
        jdbcTemplate.update(
                "UPDATE platform_change_sets SET status = 'APPLIED', updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                id
        );
        return Map.of("status", "APPLIED", "applied", applied, "count", applied.size());
    }

    private void applyOp(ChangeOp op, boolean force) {
        switch (op.op()) {
            case "UPDATE_INFO" -> {
                if (op.payload() == null) {
                    return;
                }
                ObjectRevisionContextHelper.runWithForce(force, () -> objectManager.updateInfo(
                        op.path(),
                        stringField(op.payload(), "displayName"),
                        stringField(op.payload(), "description")
                ));
            }
            case "SET_VARIABLE" -> {
                if (op.payload() == null) {
                    return;
                }
                String name = stringField(op.payload(), "name");
                if (name == null) {
                    return;
                }
                ObjectRevisionContextHelper.runWithForce(force, () -> {
                    // value applied via existing APIs in future extensions
                    return null;
                });
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported change-set op: " + op.op()
            );
        }
    }

    private static String stringField(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private ChangeSet mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            List<ChangeOp> ops = objectMapper.readValue(
                    rs.getString("ops_json"),
                    new TypeReference<>() {
                    }
            );
            return new ChangeSet(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("author"),
                    rs.getString("status"),
                    rs.getString("base_snapshot"),
                    ops,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid change-set payload", ex);
        }
    }

    private static ChangeSetSummary summaryFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ChangeSetSummary(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    public record ChangeOp(String op, String path, Long expectedRevision, Map<String, Object> payload) {
    }

    public record ChangeSet(
            String id,
            String title,
            String author,
            String status,
            String baseSnapshot,
            List<ChangeOp> ops,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ChangeSetSummary(
            String id,
            String title,
            String author,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /**
     * Helper to run ObjectManager writes with force-overwrite during change-set apply.
     */
    static final class ObjectRevisionContextHelper {
        private ObjectRevisionContextHelper() {
        }

        static void runWithForce(boolean force, Runnable action) {
            com.ispf.server.object.ObjectRevisionContext.setExpectation(null, force);
            try {
                action.run();
            } finally {
                com.ispf.server.object.ObjectRevisionContext.clear();
            }
        }

        static <T> T runWithForce(boolean force, java.util.concurrent.Callable<T> action) {
            com.ispf.server.object.ObjectRevisionContext.setExpectation(null, force);
            try {
                try {
                    return action.call();
                } catch (ObjectRevisionConflictException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            } finally {
                com.ispf.server.object.ObjectRevisionContext.clear();
            }
        }
    }
}
