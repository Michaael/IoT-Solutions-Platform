package com.ispf.server.event;

import com.ispf.server.history.TimescaleHypertableInitializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "ispf.event-journal.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcEventJournalStore implements EventJournalStore {

    private static final String INSERT_SQL = """
            INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final RowMapper<EventJournalRecord> ROW_MAPPER = (rs, rowNum) -> new EventJournalRecord(
            rs.getString("id"),
            rs.getString("object_path"),
            rs.getString("event_name"),
            rs.getString("level"),
            rs.getString("payload_json"),
            rs.getTimestamp("occurred_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;
    private final EventHistoryRecordCounter recordCounter;
    private final TimescaleHypertableInitializer timescaleHypertableInitializer;

    public JdbcEventJournalStore(
            JdbcTemplate jdbcTemplate,
            EventHistoryRecordCounter recordCounter,
            TimescaleHypertableInitializer timescaleHypertableInitializer
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.recordCounter = recordCounter;
        this.timescaleHypertableInitializer = timescaleHypertableInitializer;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendBatch(List<EventJournalRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                records,
                records.size(),
                (statement, record) -> bindInsert(statement, record)
        );
        recordCounter.recordPersisted(records.size());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendOne(EventJournalRecord record) {
        jdbcTemplate.update(
                INSERT_SQL,
                record.id(),
                record.objectPath(),
                record.eventName(),
                record.level(),
                record.payloadJson(),
                Timestamp.from(record.occurredAt())
        );
        recordCounter.recordPersisted(1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventJournalRecord> queryRecent(String objectPath, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        if (objectPath == null || objectPath.isBlank()) {
            return jdbcTemplate.query(
                    """
                            SELECT id, object_path, event_name, level, payload_json, occurred_at
                            FROM event_history
                            ORDER BY occurred_at DESC
                            LIMIT ?
                            """,
                    ROW_MAPPER,
                    capped
            );
        }
        return jdbcTemplate.query(
                """
                        SELECT id, object_path, event_name, level, payload_json, occurred_at
                        FROM event_history
                        WHERE object_path = ?
                        ORDER BY occurred_at DESC
                        LIMIT ?
                        """,
                ROW_MAPPER,
                objectPath,
                capped
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventJournalRecord> findLatest(String objectPath, String eventName) {
        List<EventJournalRecord> rows = jdbcTemplate.query(
                """
                        SELECT id, object_path, event_name, level, payload_json, occurred_at
                        FROM event_history
                        WHERE object_path = ? AND event_name = ?
                        ORDER BY occurred_at DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                objectPath,
                eventName
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotal() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_history", Long.class);
        return count != null ? count : 0L;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeOlderThan(Instant cutoff) {
        jdbcTemplate.update("DELETE FROM event_history WHERE occurred_at < ?", Timestamp.from(cutoff));
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return !timescaleHypertableInitializer.isEventHistoryTimescaleActive();
    }

    private static void bindInsert(java.sql.PreparedStatement statement, EventJournalRecord record)
            throws java.sql.SQLException {
        statement.setString(1, record.id());
        statement.setString(2, record.objectPath());
        statement.setString(3, record.eventName());
        statement.setString(4, record.level());
        statement.setString(5, record.payloadJson());
        statement.setTimestamp(6, Timestamp.from(record.occurredAt()));
    }
}
