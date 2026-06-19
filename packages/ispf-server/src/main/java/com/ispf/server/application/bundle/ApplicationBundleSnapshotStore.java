package com.ispf.server.application.bundle;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApplicationBundleSnapshotStore {

    private final JdbcTemplate jdbcTemplate;
    private final String deploymentsTable;

    public ApplicationBundleSnapshotStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.deploymentsTable = platformSqlCatalog.table("application_bundle_deployments");
    }

    public void recordDeployment(
            String appId,
            String bundleVersion,
            String manifestJson,
            String operatorManifestJson
    ) {
        jdbcTemplate.update(
                "UPDATE %s SET is_active = FALSE WHERE app_id = ?".formatted(deploymentsTable),
                appId
        );

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM %s
                WHERE app_id = ? AND bundle_version = ?
                """.formatted(deploymentsTable),
                Integer.class,
                appId,
                bundleVersion
        );

        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET manifest_json = ?, operator_manifest_json = ?, deployed_at = ?, is_active = TRUE
                    WHERE app_id = ? AND bundle_version = ?
                    """.formatted(deploymentsTable),
                    manifestJson,
                    operatorManifestJson,
                    Timestamp.from(Instant.now()),
                    appId,
                    bundleVersion
            );
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, app_id, bundle_version, manifest_json, operator_manifest_json, deployed_at, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """.formatted(deploymentsTable),
                UUID.randomUUID(),
                appId,
                bundleVersion,
                manifestJson,
                operatorManifestJson,
                Timestamp.from(Instant.now())
        );
    }

    public Optional<BundleSnapshot> findByVersion(String appId, String bundleVersion) {
        List<BundleSnapshot> rows = jdbcTemplate.query("""
                SELECT id, app_id, bundle_version, manifest_json, operator_manifest_json, deployed_at, is_active
                FROM %s
                WHERE app_id = ? AND bundle_version = ?
                """.formatted(deploymentsTable),
                this::mapRow,
                appId,
                bundleVersion
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<BundleSnapshot> findActive(String appId) {
        List<BundleSnapshot> rows = jdbcTemplate.query("""
                SELECT id, app_id, bundle_version, manifest_json, operator_manifest_json, deployed_at, is_active
                FROM %s
                WHERE app_id = ? AND is_active = TRUE
                ORDER BY deployed_at DESC
                LIMIT 1
                """.formatted(deploymentsTable),
                this::mapRow,
                appId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> listHistory(String appId) {
        return jdbcTemplate.query("""
                SELECT bundle_version, deployed_at, is_active
                FROM %s
                WHERE app_id = ?
                ORDER BY deployed_at DESC
                """.formatted(deploymentsTable),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("version", rs.getString("bundle_version"));
                    row.put("deployedAt", rs.getTimestamp("deployed_at"));
                    row.put("active", rs.getBoolean("is_active"));
                    return row;
                },
                appId
        );
    }

    public void activate(String appId, String bundleVersion) {
        jdbcTemplate.update(
                "UPDATE %s SET is_active = FALSE WHERE app_id = ?".formatted(deploymentsTable),
                appId
        );
        int updated = jdbcTemplate.update("""
                UPDATE %s SET is_active = TRUE, deployed_at = ?
                WHERE app_id = ? AND bundle_version = ?
                """.formatted(deploymentsTable),
                Timestamp.from(Instant.now()),
                appId,
                bundleVersion
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Bundle version not found: " + bundleVersion);
        }
    }

    private BundleSnapshot mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BundleSnapshot(
                UUID.fromString(rs.getString("id")),
                rs.getString("app_id"),
                rs.getString("bundle_version"),
                rs.getString("manifest_json"),
                rs.getString("operator_manifest_json"),
                rs.getTimestamp("deployed_at").toInstant(),
                rs.getBoolean("is_active")
        );
    }

    public record BundleSnapshot(
            UUID id,
            String appId,
            String bundleVersion,
            String manifestJson,
            String operatorManifestJson,
            Instant deployedAt,
            boolean active
    ) {
    }
}
