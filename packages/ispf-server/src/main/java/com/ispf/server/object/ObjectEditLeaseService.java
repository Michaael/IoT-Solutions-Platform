package com.ispf.server.object;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ObjectEditLeaseService {

    private final JdbcTemplate jdbcTemplate;

    public ObjectEditLeaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EditLease acquire(String pathPrefix, String holder, Duration ttl) {
        purgeExpired();
        String normalized = normalizePrefix(pathPrefix);
        try {
            jdbcTemplate.update("""
                    INSERT INTO object_edit_leases (id, path_prefix, holder, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID().toString(),
                    normalized,
                    holder,
                    Timestamp.from(Instant.now().plus(ttl)),
                    Timestamp.from(Instant.now())
            );
        } catch (Exception ex) {
            EditLease existing = findActive(normalized).orElse(null);
            if (existing != null && !existing.holder().equalsIgnoreCase(holder)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Lease held by " + existing.holder() + " until " + existing.expiresAt()
                );
            }
            jdbcTemplate.update("""
                    UPDATE object_edit_leases
                    SET holder = ?, expires_at = ?, created_at = ?
                    WHERE path_prefix = ?
                    """,
                    holder,
                    Timestamp.from(Instant.now().plus(ttl)),
                    Timestamp.from(Instant.now()),
                    normalized
            );
        }
        return findActive(normalized).orElseThrow();
    }

    public void release(String pathPrefix, String holder) {
        String normalized = normalizePrefix(pathPrefix);
        jdbcTemplate.update(
                "DELETE FROM object_edit_leases WHERE path_prefix = ? AND holder = ?",
                normalized,
                holder
        );
    }

    public List<EditLease> listActive() {
        purgeExpired();
        return jdbcTemplate.query("""
                SELECT id, path_prefix, holder, expires_at, created_at
                FROM object_edit_leases
                WHERE expires_at > ?
                ORDER BY path_prefix
                """,
                (rs, rowNum) -> mapRow(rs),
                Timestamp.from(Instant.now())
        );
    }

    public void assertWritable(String objectPath, String holder) {
        purgeExpired();
        List<EditLease> leases = listActive();
        for (EditLease lease : leases) {
            if (matches(objectPath, lease.pathPrefix())
                    && !lease.holder().equalsIgnoreCase(holder)) {
                throw new ResponseStatusException(
                        HttpStatus.LOCKED,
                        "Subtree locked by " + lease.holder() + " until " + lease.expiresAt()
                );
            }
        }
    }

    public java.util.Optional<EditLease> findActive(String pathPrefix) {
        purgeExpired();
        List<EditLease> rows = jdbcTemplate.query("""
                SELECT id, path_prefix, holder, expires_at, created_at
                FROM object_edit_leases
                WHERE path_prefix = ? AND expires_at > ?
                """,
                (rs, rowNum) -> mapRow(rs),
                normalizePrefix(pathPrefix),
                Timestamp.from(Instant.now())
        );
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.getFirst());
    }

    private void purgeExpired() {
        jdbcTemplate.update("DELETE FROM object_edit_leases WHERE expires_at <= ?", Timestamp.from(Instant.now()));
    }

    private static boolean matches(String objectPath, String prefix) {
        return objectPath.equals(prefix) || objectPath.startsWith(prefix + ".");
    }

    private static String normalizePrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            throw new IllegalArgumentException("pathPrefix is required");
        }
        return pathPrefix.trim().replaceAll("\\.+$", "");
    }

    private static EditLease mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new EditLease(
                rs.getString("id"),
                rs.getString("path_prefix"),
                rs.getString("holder"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public record EditLease(
            String id,
            String pathPrefix,
            String holder,
            Instant expiresAt,
            Instant createdAt
    ) {
    }
}
