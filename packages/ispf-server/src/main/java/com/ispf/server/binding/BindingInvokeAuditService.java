package com.ispf.server.binding;

import com.ispf.core.binding.BindingRule;
import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.config.BindingProperties;
import com.ispf.server.object.ObjectManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class BindingInvokeAuditService {

    private static final Logger log = LoggerFactory.getLogger(BindingInvokeAuditService.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final int MAX_JSON_LENGTH = 32_768;

    record PendingRecord(
            String bindingKind,
            String objectPath,
            String ruleId,
            String ruleName,
            String triggerKind,
            String targetVariable,
            boolean success,
            boolean changed,
            String errorMessage,
            int durationMs,
            String detailJson
    ) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final BindingProperties properties;
    private final ObjectManager objectManager;
    private final String auditTable;

    private BlockingQueue<PendingRecord> queue;
    private ExecutorService worker;
    private volatile boolean running;

    private static final RowMapper<BindingInvokeAuditEntry> ROW_MAPPER = (rs, rowNum) -> new BindingInvokeAuditEntry(
            rs.getObject("id", UUID.class),
            rs.getString("binding_kind"),
            rs.getString("object_path"),
            rs.getString("rule_id"),
            rs.getString("rule_name"),
            rs.getString("trigger_kind"),
            rs.getString("target_variable"),
            rs.getBoolean("success"),
            rs.getBoolean("changed"),
            rs.getString("error_message"),
            rs.getObject("duration_ms") != null ? rs.getInt("duration_ms") : null,
            rs.getString("detail_json"),
            rs.getTimestamp("invoked_at").toInstant()
    );

    public BindingInvokeAuditService(
            JdbcTemplate jdbcTemplate,
            PlatformSqlCatalog platformSqlCatalog,
            BindingProperties properties,
            ObjectManager objectManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectManager = objectManager;
        this.auditTable = platformSqlCatalog.table("binding_invoke_audit");
    }

    @PostConstruct
    void start() {
        BindingProperties.Audit audit = properties.getAudit();
        if (!audit.isAsyncEnabled()) {
            return;
        }
        queue = new LinkedBlockingQueue<>(audit.getQueueCapacity());
        running = true;
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "binding-audit-writer");
            thread.setDaemon(true);
            return thread;
        });
        worker.submit(this::writerLoop);
        log.info(
                "Binding audit async writer started (enabled={}, mode={}, queueCapacity={}, batchSize={})",
                audit.isEnabled(),
                audit.getMode(),
                audit.getQueueCapacity(),
                audit.getBatchSize()
        );
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (worker != null) {
            worker.shutdown();
            try {
                worker.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        drainQueueSync();
    }

    public void recordCel(
            String objectPath,
            BindingRule rule,
            String triggerKind,
            boolean success,
            boolean changed,
            String errorMessage,
            long durationNanos,
            String detailJson
    ) {
        record(new PendingRecord(
                "cel",
                objectPath,
                rule.id(),
                rule.name(),
                triggerKind,
                rule.target().variableName(),
                success,
                changed,
                errorMessage,
                toDurationMs(durationNanos),
                detailJson
        ));
    }

    public void recordSql(
            String objectPath,
            String bindingId,
            String variableName,
            String triggerKind,
            boolean success,
            boolean changed,
            String errorMessage,
            long durationNanos,
            String detailJson
    ) {
        record(new PendingRecord(
                "sql",
                objectPath,
                bindingId,
                variableName,
                triggerKind,
                variableName,
                success,
                changed,
                errorMessage,
                toDurationMs(durationNanos),
                detailJson
        ));
    }

    public boolean isMasterEnabled() {
        return properties.getAudit().isEnabled();
    }

    public boolean isObjectAuditEnabled(String objectPath) {
        return isMasterEnabled() && objectManager.isBindingAuditEnabled(objectPath);
    }

    public List<BindingInvokeAuditEntry> list(
            String objectPath,
            String bindingKind,
            String ruleId,
            Boolean success,
            Boolean changed,
            int limit
    ) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("""
                SELECT id, binding_kind, object_path, rule_id, rule_name, trigger_kind,
                       target_variable, success, changed, error_message, duration_ms, detail_json, invoked_at
                FROM %s
                WHERE 1=1
                """.formatted(auditTable));
        List<Object> args = new ArrayList<>();
        if (objectPath != null && !objectPath.isBlank()) {
            sql.append(" AND object_path = ?");
            args.add(objectPath);
        }
        if (bindingKind != null && !bindingKind.isBlank()) {
            sql.append(" AND binding_kind = ?");
            args.add(bindingKind);
        }
        if (ruleId != null && !ruleId.isBlank()) {
            sql.append(" AND rule_id = ?");
            args.add(ruleId);
        }
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success);
        }
        if (changed != null) {
            sql.append(" AND changed = ?");
            args.add(changed);
        }
        sql.append(" ORDER BY invoked_at DESC, id DESC LIMIT ?");
        args.add(cappedLimit);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    private void record(PendingRecord pending) {
        BindingProperties.Audit audit = properties.getAudit();
        if (!isMasterEnabled()) {
            return;
        }
        if (!objectManager.isBindingAuditEnabled(pending.objectPath())) {
            return;
        }
        if (!shouldRecord(audit.resolvedMode(), pending.success(), pending.changed())) {
            return;
        }
        if (!passesSampleRate(audit.getSampleRate())) {
            return;
        }
        if (audit.isAsyncEnabled() && queue != null) {
            if (!queue.offer(pending)) {
                log.warn("Binding audit queue full, dropping record for {}", pending.objectPath());
            }
            return;
        }
        persistOne(pending);
    }

    static boolean shouldRecord(BindingAuditMode mode, boolean success, boolean changed) {
        return switch (mode) {
            case ERRORS -> !success;
            case CHANGES -> success && changed;
            case ALL -> true;
        };
    }

    private static boolean passesSampleRate(double sampleRate) {
        if (sampleRate >= 1.0) {
            return true;
        }
        if (sampleRate <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private void writerLoop() {
        BindingProperties.Audit audit = properties.getAudit();
        List<PendingRecord> batch = new ArrayList<>(audit.getBatchSize());
        long lastFlush = System.nanoTime();
        while (running || (queue != null && !queue.isEmpty())) {
            try {
                PendingRecord next = queue.poll(audit.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
                if (next != null) {
                    batch.add(next);
                }
                long elapsedMs = (System.nanoTime() - lastFlush) / 1_000_000L;
                boolean flush = !batch.isEmpty()
                        && (batch.size() >= audit.getBatchSize() || elapsedMs >= audit.getFlushIntervalMs());
                if (flush) {
                    persistBatch(batch);
                    batch.clear();
                    lastFlush = System.nanoTime();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!batch.isEmpty()) {
            persistBatch(batch);
        }
    }

    private void drainQueueSync() {
        if (queue == null) {
            return;
        }
        List<PendingRecord> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            persistBatch(remaining);
        }
    }

    private void persistBatch(List<PendingRecord> batch) {
        for (PendingRecord pending : batch) {
            persistOne(pending);
        }
    }

    private void persistOne(PendingRecord pending) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, binding_kind, object_path, rule_id, rule_name, trigger_kind,
                    target_variable, success, changed, error_message, duration_ms, detail_json, invoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(auditTable),
                UUID.randomUUID(),
                pending.bindingKind(),
                pending.objectPath(),
                pending.ruleId(),
                pending.ruleName(),
                pending.triggerKind(),
                pending.targetVariable(),
                pending.success(),
                pending.changed(),
                truncate(pending.errorMessage()),
                pending.durationMs(),
                truncateJson(pending.detailJson()),
                Timestamp.from(Instant.now())
        );
    }

    private static int toDurationMs(long durationNanos) {
        return (int) Math.min(Integer.MAX_VALUE, durationNanos / 1_000_000L);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
    }

    private static String truncateJson(String value) {
        if (value == null || value.length() <= MAX_JSON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_JSON_LENGTH - 3) + "...";
    }
}
