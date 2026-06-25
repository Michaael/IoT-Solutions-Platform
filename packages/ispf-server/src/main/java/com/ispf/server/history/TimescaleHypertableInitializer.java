package com.ispf.server.history;

import com.ispf.server.config.EventJournalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts time-series tables to TimescaleDB hypertables when the extension is available:
 * {@code variable_samples} (historian) and {@code event_history} (event journal).
 */
@Component
public class TimescaleHypertableInitializer {

    private static final Logger log = LoggerFactory.getLogger(TimescaleHypertableInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final EventJournalProperties eventJournalProperties;
    private final AtomicBoolean variableSamplesTimescaleActive = new AtomicBoolean(false);
    private final AtomicBoolean eventHistoryTimescaleActive = new AtomicBoolean(false);

    public TimescaleHypertableInitializer(DataSource dataSource, EventJournalProperties eventJournalProperties) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.eventJournalProperties = eventJournalProperties;
    }

    /** @deprecated use {@link #isVariableSamplesTimescaleActive()} */
    @Deprecated
    public boolean isTimescaleRetentionActive() {
        return variableSamplesTimescaleActive.get();
    }

    public boolean isVariableSamplesTimescaleActive() {
        return variableSamplesTimescaleActive.get();
    }

    public boolean isEventHistoryTimescaleActive() {
        return eventHistoryTimescaleActive.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(120)
    public void ensureHypertables() {
        if (!isPostgreSql()) {
            return;
        }
        if (!timescaleExtensionPresent()) {
            log.info("TimescaleDB extension not installed — using application retention purge for time-series tables");
            return;
        }
        ensureVariableSamplesHypertable();
        ensureEventHistoryHypertable();
    }

    private void ensureVariableSamplesHypertable() {
        try {
            jdbcTemplate.execute("ALTER TABLE variable_samples DROP CONSTRAINT IF EXISTS variable_samples_pkey");
            jdbcTemplate.execute("ALTER TABLE variable_samples ADD PRIMARY KEY (sampled_at, id)");

            jdbcTemplate.execute("""
                    SELECT create_hypertable(
                        'variable_samples',
                        'sampled_at',
                        if_not_exists => TRUE,
                        migrate_data => TRUE
                    )
                    """);

            addRetentionPolicy("variable_samples", 90);
            variableSamplesTimescaleActive.set(true);
            log.info("TimescaleDB hypertable and retention policy enabled for variable_samples");
        } catch (Exception ex) {
            log.info("TimescaleDB hypertable setup skipped for variable_samples: {}", ex.getMessage());
        }
    }

    private void ensureEventHistoryHypertable() {
        if (eventJournalProperties.isClickHouseStore()) {
            log.info("TimescaleDB hypertable skipped for event_history (event-journal.store=clickhouse)");
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE event_history DROP CONSTRAINT IF EXISTS event_history_pkey");
            jdbcTemplate.execute("ALTER TABLE event_history ADD PRIMARY KEY (occurred_at, id)");

            jdbcTemplate.execute("""
                    SELECT create_hypertable(
                        'event_history',
                        'occurred_at',
                        if_not_exists => TRUE,
                        migrate_data => TRUE
                    )
                    """);

            int retentionDays = eventJournalProperties.getRetentionDays();
            if (retentionDays > 0) {
                addRetentionPolicy("event_history", retentionDays);
            }

            enableEventHistoryCompression();
            eventHistoryTimescaleActive.set(true);
            log.info("TimescaleDB hypertable enabled for event_history (retention {} days)", retentionDays);
        } catch (Exception ex) {
            log.info("TimescaleDB hypertable setup skipped for event_history: {}", ex.getMessage());
        }
    }

    private void enableEventHistoryCompression() {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE event_history SET (
                        timescaledb.compress,
                        timescaledb.compress_segmentby = 'object_path'
                    )
                    """);
            jdbcTemplate.execute("""
                    SELECT add_compression_policy(
                        'event_history',
                        INTERVAL '7 days',
                        if_not_exists => TRUE
                    )
                    """);
            log.info("TimescaleDB compression policy enabled for event_history (segmentby object_path, after 7 days)");
        } catch (Exception ex) {
            log.info("TimescaleDB compression policy skipped for event_history: {}", ex.getMessage());
        }
    }

    private void addRetentionPolicy(String table, int retentionDays) {
        jdbcTemplate.execute(String.format(
                "SELECT add_retention_policy('%s', INTERVAL '%d days', if_not_exists => TRUE)",
                table,
                retentionDays
        ));
    }

    private boolean timescaleExtensionPresent() {
        Boolean hasExtension = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_extension WHERE extname = 'timescaledb'
                )
                """, Boolean.class);
        return hasExtension != null && hasExtension;
    }

    public boolean isPostgreSql() {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("postgresql");
        } catch (Exception ex) {
            return false;
        }
    }
}
