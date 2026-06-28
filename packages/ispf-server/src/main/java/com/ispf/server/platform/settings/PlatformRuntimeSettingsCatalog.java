package com.ispf.server.platform.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlatformRuntimeSettingsCatalog {

    static final Map<String, String> SECTION_TITLES = Map.ofEntries(
            Map.entry("server", "Server"),
            Map.entry("database", "Database"),
            Map.entry("automation", "Automation pipeline"),
            Map.entry("observability", "Observability"),
            Map.entry("messaging", "Messaging & cache"),
            Map.entry("drivers", "Drivers"),
            Map.entry("ai", "AI"),
            Map.entry("platform", "Platform"),
            Map.entry("license", "License")
    );

    private static final List<PlatformRuntimeSettingDefinition> DEFINITIONS = List.of(
            def("server", "server.port", "ISPF_SERVER_PORT", "server.port", PlatformRuntimeSettingType.INTEGER, "8080", false, false),
            def("server", "server.tomcat.threads.max", "ISPF_TOMCAT_THREADS_MAX", "server.tomcat.threads.max", PlatformRuntimeSettingType.INTEGER, "200", false, false),
            def("server", "server.tomcat.threads.min-spare", "ISPF_TOMCAT_THREADS_MIN", "server.tomcat.threads.min-spare", PlatformRuntimeSettingType.INTEGER, "10", false, false),

            def("database", "database.pool-size", "ISPF_DB_POOL_SIZE", "spring.datasource.hikari.maximum-pool-size", PlatformRuntimeSettingType.INTEGER, "30", false, false),
            def("database", "database.pool-min-idle", "ISPF_DB_POOL_MIN_IDLE", "spring.datasource.hikari.minimum-idle", PlatformRuntimeSettingType.INTEGER, "8", false, false),
            def("database", "database.url", "ISPF_DB_URL", "spring.datasource.url", PlatformRuntimeSettingType.STRING, "jdbc:postgresql://localhost:5432/ispf", false, false),
            def("database", "database.user", "ISPF_DB_USER", "spring.datasource.username", PlatformRuntimeSettingType.STRING, "ispf", false, false),
            def("database", "database.password", "ISPF_DB_PASSWORD", "spring.datasource.password", PlatformRuntimeSettingType.STRING, "", true, false),

            def("automation", "runtime-telemetry.coalesce-ms", "ISPF_RUNTIME_TELEMETRY_COALESCE_MS", "ispf.runtime-telemetry.coalesce-ms", PlatformRuntimeSettingType.INTEGER, "250", false, true),

            def("automation", "object-change.async-enabled", "ISPF_OBJECT_CHANGE_ASYNC_ENABLED", "ispf.object-change.async-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("automation", "object-change.split-lanes", "ISPF_OBJECT_CHANGE_SPLIT_LANES", "ispf.object-change.split-lanes-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("automation", "object-change.coalesce-telemetry", "ISPF_OBJECT_CHANGE_COALESCE_TELEMETRY", "ispf.object-change.coalesce-telemetry-updates", PlatformRuntimeSettingType.BOOLEAN, "true", false, true),
            def("automation", "object-change.queue-capacity", "ISPF_OBJECT_CHANGE_QUEUE_CAPACITY", "ispf.object-change.queue-capacity", PlatformRuntimeSettingType.INTEGER, "10000", false, false),
            def("automation", "object-change.worker-threads", "ISPF_OBJECT_CHANGE_WORKER_THREADS", "ispf.object-change.worker-threads", PlatformRuntimeSettingType.INTEGER, "4", false, true),
            def("automation", "object-change.telemetry-queue-capacity", "ISPF_OBJECT_CHANGE_TELEMETRY_QUEUE_CAPACITY", "ispf.object-change.telemetry-queue-capacity", PlatformRuntimeSettingType.INTEGER, "10000", false, false),
            def("automation", "object-change.automation-queue-capacity", "ISPF_OBJECT_CHANGE_AUTOMATION_QUEUE_CAPACITY", "ispf.object-change.automation-queue-capacity", PlatformRuntimeSettingType.INTEGER, "10000", false, false),
            def("automation", "object-change.telemetry-workers", "ISPF_OBJECT_CHANGE_TELEMETRY_WORKERS", "ispf.object-change.telemetry-worker-threads", PlatformRuntimeSettingType.INTEGER, "2", false, true),
            def("automation", "object-change.automation-workers", "ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS", "ispf.object-change.automation-worker-threads", PlatformRuntimeSettingType.INTEGER, "4", false, true),
            def("automation", "object-change.elastic-workers", "ISPF_OBJECT_CHANGE_ELASTIC_WORKERS", "ispf.object-change.elastic-workers-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("automation", "object-change.elastic-scale-up-threshold", "ISPF_OBJECT_CHANGE_ELASTIC_SCALE_UP_THRESHOLD", "ispf.object-change.elastic-scale-up-queue-threshold", PlatformRuntimeSettingType.INTEGER, "50", false, true),
            def("automation", "object-change.elastic-scale-down-steps", "ISPF_OBJECT_CHANGE_ELASTIC_SCALE_DOWN_STEPS", "ispf.object-change.elastic-scale-down-steps", PlatformRuntimeSettingType.INTEGER, "6", false, true),
            def("automation", "object-change.elastic-scale-check-ms", "ISPF_OBJECT_CHANGE_ELASTIC_SCALE_CHECK_MS", "ispf.object-change.elastic-scale-check-interval-ms", PlatformRuntimeSettingType.INTEGER, "500", false, true),
            def("automation", "object-change.worker-threads-min", "ISPF_OBJECT_CHANGE_WORKER_THREADS_MIN", "ispf.object-change.worker-threads-min", PlatformRuntimeSettingType.INTEGER, "2", false, true),
            def("automation", "object-change.worker-threads-max", "ISPF_OBJECT_CHANGE_WORKER_THREADS_MAX", "ispf.object-change.worker-threads-max", PlatformRuntimeSettingType.INTEGER, "16", false, true),
            def("automation", "object-change.telemetry-workers-min", "ISPF_OBJECT_CHANGE_TELEMETRY_WORKERS_MIN", "ispf.object-change.telemetry-worker-threads-min", PlatformRuntimeSettingType.INTEGER, "1", false, true),
            def("automation", "object-change.telemetry-workers-max", "ISPF_OBJECT_CHANGE_TELEMETRY_WORKERS_MAX", "ispf.object-change.telemetry-worker-threads-max", PlatformRuntimeSettingType.INTEGER, "8", false, true),
            def("automation", "object-change.automation-workers-min", "ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS_MIN", "ispf.object-change.automation-worker-threads-min", PlatformRuntimeSettingType.INTEGER, "2", false, true),
            def("automation", "object-change.automation-workers-max", "ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS_MAX", "ispf.object-change.automation-worker-threads-max", PlatformRuntimeSettingType.INTEGER, "16", false, true),

            def("automation", "event-journal.async-enabled", "ISPF_EVENT_JOURNAL_ASYNC_ENABLED", "ispf.event-journal.async-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("automation", "event-journal.queue-capacity", "ISPF_EVENT_JOURNAL_QUEUE_CAPACITY", "ispf.event-journal.queue-capacity", PlatformRuntimeSettingType.INTEGER, "10000", false, false),
            def("automation", "event-journal.batch-size", "ISPF_EVENT_JOURNAL_BATCH_SIZE", "ispf.event-journal.batch-size", PlatformRuntimeSettingType.INTEGER, "200", false, false),
            def("automation", "event-journal.flush-interval-ms", "ISPF_EVENT_JOURNAL_FLUSH_INTERVAL_MS", "ispf.event-journal.flush-interval-ms", PlatformRuntimeSettingType.INTEGER, "100", false, false),
            def("automation", "event-journal.writer-threads", "ISPF_EVENT_JOURNAL_WRITER_THREADS", "ispf.event-journal.writer-threads", PlatformRuntimeSettingType.INTEGER, "2", false, false),
            def("automation", "event-journal.recent-cache-size", "ISPF_EVENT_JOURNAL_RECENT_CACHE_SIZE", "ispf.event-journal.recent-cache-size", PlatformRuntimeSettingType.INTEGER, "2000", false, false),

            def("automation", "binding-audit.enabled", "ISPF_BINDING_AUDIT_ENABLED", "ispf.binding.audit.enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, true),
            def("automation", "binding-audit.mode", "ISPF_BINDING_AUDIT_MODE", "ispf.binding.audit.mode", PlatformRuntimeSettingType.STRING, "changes", false, true),
            def("automation", "binding-audit.sample-rate", "ISPF_BINDING_AUDIT_SAMPLE_RATE", "ispf.binding.audit.sample-rate", PlatformRuntimeSettingType.STRING, "1.0", false, true),
            def("automation", "binding-audit.async-enabled", "ISPF_BINDING_AUDIT_ASYNC_ENABLED", "ispf.binding.audit.async-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("automation", "binding-audit.queue-capacity", "ISPF_BINDING_AUDIT_QUEUE_CAPACITY", "ispf.binding.audit.queue-capacity", PlatformRuntimeSettingType.INTEGER, "10000", false, false),
            def("automation", "binding-audit.batch-size", "ISPF_BINDING_AUDIT_BATCH_SIZE", "ispf.binding.audit.batch-size", PlatformRuntimeSettingType.INTEGER, "100", false, false),
            def("automation", "binding-audit.flush-interval-ms", "ISPF_BINDING_AUDIT_FLUSH_INTERVAL_MS", "ispf.binding.audit.flush-interval-ms", PlatformRuntimeSettingType.INTEGER, "200", false, false),
            def("automation", "binding-audit.retention-days", "ISPF_BINDING_AUDIT_RETENTION_DAYS", "ispf.binding.audit.retention-days", PlatformRuntimeSettingType.INTEGER, "7", false, false),

            def("automation", "function-audit.enabled", "ISPF_FUNCTION_AUDIT_ENABLED", "ispf.function.audit.enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, true),
            def("automation", "function-audit.mode", "ISPF_FUNCTION_AUDIT_MODE", "ispf.function.audit.mode", PlatformRuntimeSettingType.STRING, "errors", false, true),
            def("automation", "function-audit.sample-rate", "ISPF_FUNCTION_AUDIT_SAMPLE_RATE", "ispf.function.audit.sample-rate", PlatformRuntimeSettingType.STRING, "1.0", false, true),
            def("automation", "function-audit.async-enabled", "ISPF_FUNCTION_AUDIT_ASYNC_ENABLED", "ispf.function.audit.async-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("automation", "function-audit.queue-capacity", "ISPF_FUNCTION_AUDIT_QUEUE_CAPACITY", "ispf.function.audit.queue-capacity", PlatformRuntimeSettingType.INTEGER, "10000", false, false),
            def("automation", "function-audit.batch-size", "ISPF_FUNCTION_AUDIT_BATCH_SIZE", "ispf.function.audit.batch-size", PlatformRuntimeSettingType.INTEGER, "100", false, false),
            def("automation", "function-audit.flush-interval-ms", "ISPF_FUNCTION_AUDIT_FLUSH_INTERVAL_MS", "ispf.function.audit.flush-interval-ms", PlatformRuntimeSettingType.INTEGER, "200", false, false),
            def("automation", "function-audit.retention-days", "ISPF_FUNCTION_AUDIT_RETENTION_DAYS", "ispf.function.audit.retention-days", PlatformRuntimeSettingType.INTEGER, "30", false, false),

            def("observability", "metrics-probe.enabled", "ISPF_PLATFORM_METRICS_PROBE_ENABLED", "ispf.platform-metrics-probe.enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("observability", "metrics-probe.interval-ms", "ISPF_PLATFORM_METRICS_PROBE_INTERVAL_MS", "ispf.platform-metrics-probe.interval-ms", PlatformRuntimeSettingType.INTEGER, "5000", false, false),
            def("observability", "otlp.metrics.enabled", "ISPF_OTLP_METRICS_ENABLED", "management.otlp.metrics.export.enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("observability", "otlp.metrics.url", "ISPF_OTLP_METRICS_URL", "management.otlp.metrics.export.url", PlatformRuntimeSettingType.STRING, "http://localhost:4318/v1/metrics", false, false),
            def("observability", "otlp.metrics.step", "ISPF_OTLP_METRICS_STEP", "management.otlp.metrics.export.step", PlatformRuntimeSettingType.DURATION, "30s", false, false),
            def("observability", "otlp.tracing.enabled", "ISPF_OTLP_TRACING_ENABLED", "management.tracing.enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("observability", "otlp.tracing.url", "ISPF_OTLP_TRACING_URL", "management.opentelemetry.tracing.export.otlp.endpoint", PlatformRuntimeSettingType.STRING, "http://localhost:4318/v1/traces", false, false),
            def("observability", "otlp.tracing.sampling", "ISPF_OTLP_TRACING_SAMPLING", "management.tracing.sampling.probability", PlatformRuntimeSettingType.STRING, "0.1", false, false),
            def("observability", "environment", "ISPF_ENVIRONMENT", "ISPF_ENVIRONMENT", PlatformRuntimeSettingType.STRING, "local", false, false),

            def("messaging", "nats.enabled", "ISPF_NATS_ENABLED", "ispf.nats.enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("messaging", "nats.url", "ISPF_NATS_URL", "ispf.nats.url", PlatformRuntimeSettingType.STRING, "nats://localhost:4222", false, false),
            def("messaging", "nats.replica-events", "ISPF_NATS_REPLICA_EVENTS", "ispf.nats.replica-events-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("messaging", "nats.replica-id", "ISPF_REPLICA_ID", "ispf.nats.replica-id", PlatformRuntimeSettingType.STRING, "", false, false),
            def("messaging", "nats.jetstream.enabled", "ISPF_NATS_JETSTREAM_ENABLED", "ispf.nats.jet-stream-enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("messaging", "nats.jetstream.stream", "ISPF_NATS_JETSTREAM_STREAM", "ispf.nats.jet-stream-stream-name", PlatformRuntimeSettingType.STRING, "ispf-automation", false, false),
            def("messaging", "nats.jetstream.max-age-hours", "ISPF_NATS_JETSTREAM_MAX_AGE_HOURS", "ispf.nats.jet-stream-max-age-hours", PlatformRuntimeSettingType.INTEGER, "24", false, false),
            def("messaging", "mqtt.enabled", "ISPF_MQTT_ENABLED", "ispf.mqtt.enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("messaging", "mqtt.broker", "ISPF_MQTT_BROKER", "ispf.mqtt.broker-url", PlatformRuntimeSettingType.STRING, "tcp://localhost:1883", false, false),
            def("messaging", "redis.enabled", "ISPF_REDIS_ENABLED", "ispf.redis.enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("messaging", "redis.host", "ISPF_REDIS_HOST", "ispf.redis.host", PlatformRuntimeSettingType.STRING, "localhost", false, false),
            def("messaging", "redis.port", "ISPF_REDIS_PORT", "ispf.redis.port", PlatformRuntimeSettingType.INTEGER, "6379", false, false),
            def("messaging", "redis.password", "ISPF_REDIS_PASSWORD", "ispf.redis.password", PlatformRuntimeSettingType.STRING, "", true, false),
            def("messaging", "redis.database", "ISPF_REDIS_DATABASE", "ispf.redis.database", PlatformRuntimeSettingType.INTEGER, "0", false, false),
            def("messaging", "redis.timeout", "ISPF_REDIS_TIMEOUT", "ispf.redis.timeout", PlatformRuntimeSettingType.DURATION, "2s", false, false),
            def("messaging", "redis.correlator-windows", "ISPF_REDIS_CORRELATOR_WINDOWS", "ispf.redis.correlator-windows-enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),

            def("drivers", "driver.scheduler-threads", "ISPF_DRIVER_SCHEDULER_THREADS", "ispf.driver.scheduler-threads", PlatformRuntimeSettingType.INTEGER, "8", false, false),
            def("drivers", "driver.packs-dir", "ISPF_DRIVER_PACKS_DIR", "ispf.driver.packs-dir", PlatformRuntimeSettingType.STRING, "./data/drivers", false, false),

            def("ai", "ai.enabled", "ISPF_AI_ENABLED", "ispf.ai.enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("ai", "ai.provider", "ISPF_AI_PROVIDER", "ispf.ai.provider", PlatformRuntimeSettingType.STRING, "noop", false, false),
            def("ai", "ai.base-url", "ISPF_AI_BASE_URL", "ispf.ai.base-url", PlatformRuntimeSettingType.STRING, "", false, false),
            def("ai", "ai.model", "ISPF_AI_MODEL", "ispf.ai.model", PlatformRuntimeSettingType.STRING, "gpt-4o-mini", false, false),
            def("ai", "ai.api-key", "ISPF_AI_API_KEY", "ispf.ai.api-key", PlatformRuntimeSettingType.STRING, "", true, false),
            def("ai", "ai.timeout-seconds", "ISPF_AI_TIMEOUT_SECONDS", "ispf.ai.timeout-seconds", PlatformRuntimeSettingType.INTEGER, "60", false, false),
            def("ai", "ai.max-tokens", "ISPF_AI_MAX_TOKENS", "ispf.ai.max-tokens", PlatformRuntimeSettingType.INTEGER, "16384", false, false),
            def("ai", "ai.temperature", "ISPF_AI_TEMPERATURE", "ispf.ai.temperature", PlatformRuntimeSettingType.STRING, "0.2", false, false),
            def("ai", "ai.agent-max-steps", "ISPF_AI_AGENT_MAX_STEPS", "ispf.ai.agent-max-steps", PlatformRuntimeSettingType.INTEGER, "96", false, false),
            def("ai", "ai.agent-max-history-turns", "ISPF_AI_AGENT_MAX_HISTORY_TURNS", "ispf.ai.agent-max-history-turns", PlatformRuntimeSettingType.INTEGER, "50", false, false),

            def("platform", "update.check-enabled", "ISPF_UPDATE_CHECK_ENABLED", "ispf.platform.update.check-enabled", PlatformRuntimeSettingType.BOOLEAN, "true", false, false),
            def("platform", "update.apply-enabled", "ISPF_UPDATE_APPLY_ENABLED", "ispf.platform.update.apply-enabled", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("platform", "update.check-interval-ms", "ISPF_UPDATE_CHECK_INTERVAL_MS", "ispf.platform.update.check-interval-ms", PlatformRuntimeSettingType.INTEGER, "3600000", false, false),
            def("platform", "update.staging-dir", "ISPF_UPDATE_STAGING_DIR", "ispf.platform.update.staging-dir", PlatformRuntimeSettingType.STRING, "/opt/ispf/staging", false, false),

            def("license", "license.enforce", "ISPF_LICENSE_ENFORCE", "ispf.license.enforce", PlatformRuntimeSettingType.BOOLEAN, "false", false, false),
            def("license", "data-dir", "ISPF_DATA_DIR", "ispf.license.data-dir", PlatformRuntimeSettingType.STRING, "./data", false, false)
    );

    private PlatformRuntimeSettingsCatalog() {
    }

    static List<PlatformRuntimeSettingDefinition> all() {
        return DEFINITIONS;
    }

    static Map<String, PlatformRuntimeSettingDefinition> byId() {
        Map<String, PlatformRuntimeSettingDefinition> map = new LinkedHashMap<>();
        for (PlatformRuntimeSettingDefinition definition : DEFINITIONS) {
            map.put(definition.id(), definition);
        }
        return map;
    }

    private static PlatformRuntimeSettingDefinition def(
            String sectionId,
            String id,
            String envVar,
            String propertyKey,
            PlatformRuntimeSettingType type,
            String defaultValue,
            boolean sensitive,
            boolean hotReloadable
    ) {
        return new PlatformRuntimeSettingDefinition(id, sectionId, envVar, propertyKey, type, defaultValue, sensitive, hotReloadable);
    }
}
