package com.ispf.server.application.function;

import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.config.FunctionProperties;
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
public class FunctionInvokeAuditService {

    private static final Logger log = LoggerFactory.getLogger(FunctionInvokeAuditService.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    record PendingRecord(
            String appId,
            String objectPath,
            String functionName,
            boolean success,
            String errorMessage
    ) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final FunctionProperties properties;
    private final ObjectManager objectManager;
    private final String auditTable;

    private BlockingQueue<PendingRecord> queue;
    private ExecutorService worker;
    private volatile boolean running;

    private static final RowMapper<FunctionInvokeAuditEntry> ROW_MAPPER = (rs, rowNum) -> new FunctionInvokeAuditEntry(
            rs.getObject("id", UUID.class),
            rs.getString("correlation_id"),
            rs.getString("object_path"),
            rs.getString("function_name"),
            rs.getString("app_id"),
            rs.getBoolean("success"),
            rs.getString("error_message"),
            rs.getTimestamp("invoked_at").toInstant()
    );

    public FunctionInvokeAuditService(
            JdbcTemplate jdbcTemplate,
            PlatformSqlCatalog platformSqlCatalog,
            FunctionProperties properties,
            ObjectManager objectManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectManager = objectManager;
        this.auditTable = platformSqlCatalog.table("function_invoke_audit");
    }

    @PostConstruct
    void start() {
        FunctionProperties.Audit audit = properties.getAudit();
        if (!audit.isAsyncEnabled()) {
            return;
        }
        queue = new LinkedBlockingQueue<>(audit.getQueueCapacity());
        running = true;
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "function-audit-writer");
            thread.setDaemon(true);
            return thread;
        });
        worker.submit(this::writerLoop);
        log.info(
                "Function audit async writer started (enabled={}, mode={}, queueCapacity={}, batchSize={})",
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

    public void record(String appId, String objectPath, String functionName, boolean success, String errorMessage) {
        record(new PendingRecord(appId, objectPath, functionName, success, errorMessage));
    }

    public boolean isMasterEnabled() {
        return properties.getAudit().isEnabled();
    }

    public boolean isObjectAuditEnabled(String objectPath) {
        return isMasterEnabled() && objectManager.isFunctionAuditEnabled(objectPath);
    }

    public List<FunctionInvokeAuditEntry> list(
            String objectPath,
            String functionName,
            Boolean success,
            int limit
    ) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("""
                SELECT id, correlation_id, object_path, function_name, app_id, success, error_message, invoked_at
                FROM %s
                WHERE 1=1
                """.formatted(auditTable));
        List<Object> args = new ArrayList<>();
        if (objectPath != null && !objectPath.isBlank()) {
            sql.append(" AND object_path = ?");
            args.add(objectPath);
        }
        if (functionName != null && !functionName.isBlank()) {
            sql.append(" AND function_name = ?");
            args.add(functionName);
        }
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success);
        }
        sql.append(" ORDER BY invoked_at DESC LIMIT ?");
        args.add(cappedLimit);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    private void record(PendingRecord pending) {
        FunctionProperties.Audit audit = properties.getAudit();
        if (!isMasterEnabled()) {
            return;
        }
        if (!objectManager.isFunctionAuditEnabled(pending.objectPath())) {
            return;
        }
        if (!shouldRecord(audit.resolvedMode(), pending.success())) {
            return;
        }
        if (!passesSampleRate(audit.getSampleRate())) {
            return;
        }
        if (audit.isAsyncEnabled() && queue != null) {
            if (!queue.offer(pending)) {
                log.warn("Function audit queue full, dropping record for {}", pending.objectPath());
            }
            return;
        }
        persistOne(pending);
    }

    static boolean shouldRecord(FunctionAuditMode mode, boolean success) {
        return switch (mode) {
            case ERRORS -> !success;
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
        FunctionProperties.Audit audit = properties.getAudit();
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
                    id, correlation_id, object_path, function_name, app_id, success, error_message, invoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(auditTable),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                pending.objectPath(),
                pending.functionName(),
                pending.appId(),
                pending.success(),
                truncate(pending.errorMessage()),
                Timestamp.from(Instant.now())
        );
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
    }
}
