package com.ispf.server.platform;

import com.ispf.core.object.ObjectType;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.schedule.PlatformSchedulerService;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.event.EventHistoryRecordCounter;
import com.ispf.server.event.EventJournalStore;
import com.ispf.server.persistence.ObjectNodeRepository;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.security.PlatformAuthSessionStore;
import com.ispf.server.security.PlatformUserStore;
import com.ispf.server.websocket.ObjectWebSocketHandler;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformMetricsService {

    private final ObjectNodeRepository objectNodeRepository;
    private final ObjectVariableRepository objectVariableRepository;
    private final VariableSampleRepository variableSampleRepository;
    private final EventJournalStore eventJournalStore;
    private final EventHistoryRecordCounter eventHistoryRecordCounter;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final VariableHistoryProperties variableHistoryProperties;
    private final DataSource dataSource;
    private final DriverRuntimeService driverRuntimeService;
    private final ObjectWebSocketHandler objectWebSocketHandler;
    private final PlatformAuthSessionStore authSessionStore;
    private final PlatformUserStore userStore;
    private final ApplicationFunctionStore applicationFunctionStore;
    private final PlatformSchedulerService platformSchedulerService;
    private final AutomationMetricsRecorder automationMetricsRecorder;

    public PlatformMetricsService(
            ObjectNodeRepository objectNodeRepository,
            ObjectVariableRepository objectVariableRepository,
            VariableSampleRepository variableSampleRepository,
            EventJournalStore eventJournalStore,
            EventHistoryRecordCounter eventHistoryRecordCounter,
            WorkflowInstanceRepository workflowInstanceRepository,
            VariableHistoryProperties variableHistoryProperties,
            DataSource dataSource,
            DriverRuntimeService driverRuntimeService,
            ObjectWebSocketHandler objectWebSocketHandler,
            PlatformAuthSessionStore authSessionStore,
            PlatformUserStore userStore,
            ApplicationFunctionStore applicationFunctionStore,
            PlatformSchedulerService platformSchedulerService,
            AutomationMetricsRecorder automationMetricsRecorder
    ) {
        this.objectNodeRepository = objectNodeRepository;
        this.objectVariableRepository = objectVariableRepository;
        this.variableSampleRepository = variableSampleRepository;
        this.eventJournalStore = eventJournalStore;
        this.eventHistoryRecordCounter = eventHistoryRecordCounter;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.variableHistoryProperties = variableHistoryProperties;
        this.dataSource = dataSource;
        this.driverRuntimeService = driverRuntimeService;
        this.objectWebSocketHandler = objectWebSocketHandler;
        this.authSessionStore = authSessionStore;
        this.userStore = userStore;
        this.applicationFunctionStore = applicationFunctionStore;
        this.platformSchedulerService = platformSchedulerService;
        this.automationMetricsRecorder = automationMetricsRecorder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", Instant.now().toString());
        metrics.put("runtime", runtimeMetrics());
        metrics.put("database", databaseMetrics());
        metrics.put("objectTree", objectTreeMetrics());
        metrics.put("drivers", driverRuntimeService.runtimeMetrics());
        metrics.put("connections", connectionMetrics());
        metrics.put("security", securityMetrics());
        metrics.put("variableHistory", variableHistoryMetrics());
        metrics.put("automation", automationMetrics());
        return metrics;
    }

    private Map<String, Object> runtimeMetrics() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memory.getNonHeapMemoryUsage().getUsed();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("uptimeMs", uptimeMs);
        section.put("uptimeHuman", formatDuration(uptimeMs));
        section.put("heapUsedBytes", heapUsed);
        section.put("heapMaxBytes", heapMax > 0 ? heapMax : null);
        section.put("heapUsedMb", roundMb(heapUsed));
        section.put("heapMaxMb", heapMax > 0 ? roundMb(heapMax) : null);
        section.put("nonHeapUsedMb", roundMb(nonHeapUsed));
        section.put("processors", Runtime.getRuntime().availableProcessors());
        section.put("threadCount", threads.getThreadCount());
        section.put("peakThreadCount", threads.getPeakThreadCount());
        return section;
    }

    private Map<String, Object> databaseMetrics() {
        Map<String, Object> section = new LinkedHashMap<>();
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            section.put("poolName", hikari.getPoolName());
            section.put("activeConnections", pool.getActiveConnections());
            section.put("idleConnections", pool.getIdleConnections());
            section.put("totalConnections", pool.getTotalConnections());
            section.put("threadsAwaitingConnection", pool.getThreadsAwaitingConnection());
            section.put("maxPoolSize", hikari.getMaximumPoolSize());
        } else {
            section.put("poolAvailable", false);
        }
        return section;
    }

    private Map<String, Object> objectTreeMetrics() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("objectNodes", objectNodeRepository.count());
        section.put("variables", objectVariableRepository.count());
        section.put("devices", objectNodeRepository.countByType(ObjectType.DEVICE));
        section.put("dashboards", objectNodeRepository.countByType(ObjectType.DASHBOARD));
        section.put("workflows", objectNodeRepository.countByType(ObjectType.WORKFLOW));
        section.put("applications", objectNodeRepository.countByType(ObjectType.APPLICATION));
        section.put("models", objectNodeRepository.countByType(ObjectType.MODEL));
        section.put("alerts", objectNodeRepository.countByType(ObjectType.ALERT));
        section.put("correlators", objectNodeRepository.countByType(ObjectType.CORRELATOR));
        return section;
    }

    private Map<String, Object> connectionMetrics() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("websocketClients", objectWebSocketHandler.activeSessionCount());
        return section;
    }

    private Map<String, Object> securityMetrics() {
        Instant now = Instant.now();
        long enabledUsers = userStore.listAll().stream().filter(PlatformUserStore.PlatformUser::enabled).count();

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("platformUsers", userStore.listAll().size());
        section.put("enabledUsers", enabledUsers);
        section.put("activeAuthSessions", authSessionStore.countValidSessions(now));
        return section;
    }

    private Map<String, Object> variableHistoryMetrics() {
        long historizedVariables = objectVariableRepository.countByHistoryEnabledTrue();
        long sampleCount = variableSampleRepository.count();
        Instant oldest = variableSampleRepository.findOldestSampledAt();
        Instant newest = variableSampleRepository.findNewestSampledAt();

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("enabled", variableHistoryProperties.isEnabled());
        section.put("minIntervalMs", variableHistoryProperties.getMinIntervalMs());
        section.put("defaultRetentionDays", variableHistoryProperties.getRetentionDays());
        section.put("historizedVariables", historizedVariables);
        section.put("sampleCount", sampleCount);
        section.put("oldestSampleAt", oldest != null ? oldest.toString() : null);
        section.put("newestSampleAt", newest != null ? newest.toString() : null);
        return section;
    }

    private Map<String, Object> automationMetrics() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("eventHistoryRecords", eventHistoryRecordCounter.isInitialized()
                ? eventHistoryRecordCounter.totalRecords()
                : eventJournalStore.countTotal());
        section.put("workflowInstancesTotal", workflowInstanceRepository.count());
        section.put("workflowInstancesRunning", workflowInstanceRepository.countByStatus("RUNNING"));
        section.put("workflowInstancesCompleted", workflowInstanceRepository.countByStatus("COMPLETED"));
        section.put("workflowInstancesFailed", workflowInstanceRepository.countByStatus("FAILED"));
        section.put("workflowInstancesCancelled", workflowInstanceRepository.countByStatus("CANCELLED"));
        section.put("alertRules", objectNodeRepository.countByType(ObjectType.ALERT));
        section.put("eventCorrelators", objectNodeRepository.countByType(ObjectType.CORRELATOR));
        section.put("applicationFunctions", applicationFunctionStore.countDistinctFunctions());
        section.put("applicationFunctionVersions", applicationFunctionStore.countDeployedVersions());
        section.put("platformSchedules", platformSchedulerService.countSchedules());
        section.put("platformSchedulesEnabled", platformSchedulerService.countEnabledSchedules());
        section.putAll(automationMetricsRecorder.automationSnapshot());
        return section;
    }

    public List<Map<String, Object>> metricSections() {
        Map<String, Object> snapshot = snapshot();
        return List.of(
                section("runtime", "Среда выполнения", snapshot.get("runtime")),
                section("database", "База данных", snapshot.get("database")),
                section("objectTree", "Дерево объектов", snapshot.get("objectTree")),
                section("drivers", "Драйверы устройств", snapshot.get("drivers")),
                section("connections", "Подключения", snapshot.get("connections")),
                section("security", "Безопасность", snapshot.get("security")),
                section("variableHistory", "Historian переменных", snapshot.get("variableHistory")),
                section("automation", "Автоматизация", snapshot.get("automation"))
        );
    }

    private static Map<String, Object> section(String id, String title, Object values) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("id", id);
        section.put("title", title);
        section.put("values", values);
        return section;
    }

    private static double roundMb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0 * 10.0) / 10.0;
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86_400;
        seconds %= 86_400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        if (days > 0) {
            return "%dd %dh %dm".formatted(days, hours, minutes);
        }
        if (hours > 0) {
            return "%dh %dm".formatted(hours, minutes);
        }
        if (minutes > 0) {
            return "%dm %ds".formatted(minutes, seconds);
        }
        return "%ds".formatted(seconds);
    }
}
