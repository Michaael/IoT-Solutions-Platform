package com.ispf.server.operator;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class OperatorAppUiStore {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public OperatorAppUiStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = platformSqlCatalog.table("operator_app_ui");
    }

    public List<OperatorAppUiRecord> listAll() {
        return jdbcTemplate.query(
                "SELECT app_id, title, default_dashboard, dashboards_json, updated_at FROM %s ORDER BY app_id"
                        .formatted(table),
                (rs, rowNum) -> new OperatorAppUiRecord(
                        rs.getString("app_id"),
                        rs.getString("title"),
                        rs.getString("default_dashboard"),
                        rs.getString("dashboards_json"),
                        rs.getTimestamp("updated_at").toInstant()
                )
        );
    }

    public Optional<OperatorAppUiRecord> findByAppId(String appId) {
        List<OperatorAppUiRecord> rows = jdbcTemplate.query(
                "SELECT app_id, title, default_dashboard, dashboards_json, updated_at FROM %s WHERE app_id = ?"
                        .formatted(table),
                (rs, rowNum) -> new OperatorAppUiRecord(
                        rs.getString("app_id"),
                        rs.getString("title"),
                        rs.getString("default_dashboard"),
                        rs.getString("dashboards_json"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                appId
        );
        return rows.stream().findFirst();
    }

    public void upsert(OperatorAppUiRecord record) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE app_id = ?".formatted(table),
                Integer.class,
                record.appId()
        );
        Instant now = Instant.now();
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET title = ?, default_dashboard = ?, dashboards_json = ?, updated_at = ?
                    WHERE app_id = ?
                    """.formatted(table),
                    record.title(),
                    record.defaultDashboard(),
                    record.dashboardsJson(),
                    Timestamp.from(now),
                    record.appId()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (app_id, title, default_dashboard, dashboards_json, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(table),
                record.appId(),
                record.title(),
                record.defaultDashboard(),
                record.dashboardsJson(),
                Timestamp.from(now)
        );
    }

    public record OperatorAppUiRecord(
            String appId,
            String title,
            String defaultDashboard,
            String dashboardsJson,
            Instant updatedAt
    ) {
    }
}
