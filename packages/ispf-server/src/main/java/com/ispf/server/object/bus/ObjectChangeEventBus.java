package com.ispf.server.object.bus;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.platform.AutomationMetricsRecorder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async dispatcher for {@link ObjectChangeEvent} handlers.
 *
 * <p>When {@link ObjectChangeProperties#isSplitLanesEnabled()} is true, two lanes isolate
 * high-volume telemetry history from automation reactions:
 * <ul>
 *   <li><b>Telemetry lane</b> — {@code event.telemetry()==true} events; handlers with
 *       {@link ObjectChangeAsyncHandler#lane()} {@link ObjectChangeLane#TELEMETRY}
 *       (typically {@code VariableHistoryListener}).</li>
 *   <li><b>Automation lane</b> — all events; handlers with lane {@link ObjectChangeLane#AUTOMATION}
 *       (alerts, workflows, correlator, SQL bindings).</li>
 * </ul>
 *
 * <p><b>Coalescing with {@link com.ispf.server.object.RuntimeTelemetryCoalescer}:</b>
 * {@code RuntimeTelemetryCoalescer} is the first stage — it merges rapid variable value changes
 * before publishing {@code ObjectChangeEvent} with {@code telemetry=true}. When
 * {@link ObjectChangeProperties#isCoalesceTelemetryUpdates()} is also enabled, the bus applies a
 * second coalesce (50 ms window) only on the telemetry lane enqueue path. The automation lane
 * never bus-coalesces, so alert/workflow handlers still see every published event. Disable
 * {@code coalesce-telemetry-updates} when {@code runtime-telemetry.enabled=true} to avoid redundant
 * double coalescing on the history path.
 */
@Component
public class ObjectChangeEventBus {

    private static final Logger log = LoggerFactory.getLogger(ObjectChangeEventBus.class);

    private final ObjectChangeProperties properties;
    private final List<ObjectChangeAsyncHandler> handlers;
    private final AutomationMetricsRecorder automationMetricsRecorder;

    private Lane unifiedLane;
    private Lane telemetryLane;
    private Lane automationLane;
    private ScheduledExecutorService coalesceScheduler;
    private volatile boolean running;
    private final ConcurrentHashMap<String, ObjectChangeEvent> telemetryCoalesce = new ConcurrentHashMap<>();
    private volatile boolean coalesceFlushScheduled;

    public ObjectChangeEventBus(
            ObjectChangeProperties properties,
            List<ObjectChangeAsyncHandler> handlers,
            AutomationMetricsRecorder automationMetricsRecorder
    ) {
        this.properties = properties;
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(ObjectChangeAsyncHandler::order))
                .toList();
        this.automationMetricsRecorder = automationMetricsRecorder;
    }

    @jakarta.annotation.PostConstruct
    void start() {
        if (!properties.isAsyncEnabled()) {
            return;
        }
        running = true;
        if (properties.isSplitLanesEnabled()) {
            telemetryLane = new Lane(
                    ObjectChangeLane.TELEMETRY,
                    properties.getTelemetryQueueCapacity(),
                    properties.getTelemetryWorkerThreads(),
                    handlersForLane(ObjectChangeLane.TELEMETRY)
            );
            automationLane = new Lane(
                    ObjectChangeLane.AUTOMATION,
                    properties.getAutomationQueueCapacity(),
                    properties.getAutomationWorkerThreads(),
                    handlersForLane(ObjectChangeLane.AUTOMATION)
            );
            telemetryLane.start();
            automationLane.start();
            automationMetricsRecorder.bindObjectChangeQueue("telemetry", telemetryLane.queue);
            automationMetricsRecorder.bindObjectChangeQueue("automation", automationLane.queue);
            log.info(
                    "Object change event bus started (split lanes: telemetry workers={}, queue={}; "
                            + "automation workers={}, queue={}; handlers={})",
                    properties.getTelemetryWorkerThreads(),
                    properties.getTelemetryQueueCapacity(),
                    properties.getAutomationWorkerThreads(),
                    properties.getAutomationQueueCapacity(),
                    handlers.size()
            );
        } else {
            unifiedLane = new Lane(
                    ObjectChangeLane.AUTOMATION,
                    properties.getQueueCapacity(),
                    properties.getWorkerThreads(),
                    handlers
            );
            unifiedLane.start();
            automationMetricsRecorder.bindObjectChangeQueue("unified", unifiedLane.queue);
            log.info(
                    "Object change event bus started (workers={}, queueCapacity={}, handlers={})",
                    properties.getWorkerThreads(),
                    properties.getQueueCapacity(),
                    handlers.size()
            );
        }
        if (properties.isCoalesceTelemetryUpdates()) {
            coalesceScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "object-change-coalesce");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    public void submit(ObjectChangeEvent event) {
        if (!properties.isAsyncEnabled()) {
            dispatchSync(event);
            return;
        }
        if (properties.isSplitLanesEnabled()) {
            submitSplit(event);
            return;
        }
        submitUnified(event);
    }

    private void submitSplit(ObjectChangeEvent event) {
        if (event.telemetry() && telemetryLane != null && telemetryLane.hasHandlers()) {
            if (shouldBusCoalesceTelemetry(event)) {
                coalesceTelemetry(event, telemetryLane);
            } else {
                telemetryLane.enqueue(event);
            }
        }
        if (automationLane != null) {
            automationLane.enqueue(event);
        }
    }

    private void submitUnified(ObjectChangeEvent event) {
        if (shouldBusCoalesceTelemetry(event)) {
            coalesceTelemetry(event, unifiedLane);
            return;
        }
        unifiedLane.enqueue(event);
    }

    private static boolean shouldCoalesceTelemetry(ObjectChangeEvent event) {
        return event.telemetry()
                && event.type() == ObjectChangeType.VARIABLE_UPDATED
                && event.variableName() != null;
    }

    private boolean shouldBusCoalesceTelemetry(ObjectChangeEvent event) {
        return properties.isCoalesceTelemetryUpdates() && shouldCoalesceTelemetry(event);
    }

    private void coalesceTelemetry(ObjectChangeEvent event, Lane lane) {
        String key = event.path() + '\0' + event.variableName();
        telemetryCoalesce.put(key, event);
        scheduleCoalesceFlush(lane);
    }

    private void scheduleCoalesceFlush(Lane lane) {
        if (coalesceScheduler == null || coalesceFlushScheduled) {
            return;
        }
        coalesceFlushScheduled = true;
        coalesceScheduler.schedule(() -> {
            coalesceFlushScheduled = false;
            flushCoalesce(lane);
        }, 50, TimeUnit.MILLISECONDS);
    }

    private void flushCoalesce(Lane lane) {
        List<ObjectChangeEvent> pending = new ArrayList<>(telemetryCoalesce.values());
        telemetryCoalesce.clear();
        for (ObjectChangeEvent event : pending) {
            lane.enqueue(event);
        }
    }

    private void dispatchSync(ObjectChangeEvent event) {
        for (ObjectChangeAsyncHandler handler : handlers) {
            invokeHandler(handler, event);
        }
        automationMetricsRecorder.recordObjectChangeProcessed();
    }

    private List<ObjectChangeAsyncHandler> handlersForLane(ObjectChangeLane lane) {
        return handlers.stream()
                .filter(handler -> handler.lane() == lane)
                .toList();
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (unifiedLane != null) {
            unifiedLane.shutdown();
        }
        if (telemetryLane != null) {
            telemetryLane.shutdown();
        }
        if (automationLane != null) {
            automationLane.shutdown();
        }
        if (coalesceScheduler != null) {
            coalesceScheduler.shutdownNow();
        }
    }

    private void invokeHandler(ObjectChangeAsyncHandler handler, ObjectChangeEvent event) {
        try {
            handler.handle(event);
        } catch (Exception ex) {
            log.error(
                    "Object change handler {} failed for {}",
                    handler.getClass().getSimpleName(),
                    event.path(),
                    ex
            );
        }
    }

    private final class Lane {
        private final ObjectChangeLane id;
        private final int queueCapacity;
        private final int workerThreads;
        private final BlockingQueue<ObjectChangeEvent> queue;
        private final ExecutorService workers;
        private final List<ObjectChangeAsyncHandler> laneHandlers;

        private Lane(ObjectChangeLane id, int queueCapacity, int workerThreads, List<ObjectChangeAsyncHandler> laneHandlers) {
            this.id = id;
            this.queueCapacity = queueCapacity;
            this.workerThreads = workerThreads;
            this.queue = new LinkedBlockingQueue<>(queueCapacity);
            this.laneHandlers = laneHandlers;
            this.workers = Executors.newFixedThreadPool(
                    workerThreads,
                    runnable -> {
                        Thread thread = new Thread(runnable, "object-change-bus-" + id.name().toLowerCase());
                        thread.setDaemon(true);
                        return thread;
                    }
            );
        }

        private void start() {
            for (int i = 0; i < workerThreads; i++) {
                workers.submit(this::workerLoop);
            }
        }

        private boolean hasHandlers() {
            return !laneHandlers.isEmpty();
        }

        private void enqueue(ObjectChangeEvent event) {
            if (!queue.offer(event)) {
                automationMetricsRecorder.recordObjectChangeQueueDropped();
                log.warn(
                        "Object change {} queue full (capacity={}); processing on publisher thread for {}",
                        id.name().toLowerCase(),
                        queueCapacity,
                        event.path()
                );
                dispatch(event);
            }
        }

        private void workerLoop() {
            while (running) {
                try {
                    ObjectChangeEvent event = queue.poll(1, TimeUnit.SECONDS);
                    if (event != null) {
                        dispatch(event);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void dispatch(ObjectChangeEvent event) {
            for (ObjectChangeAsyncHandler handler : laneHandlers) {
                invokeHandler(handler, event);
            }
            automationMetricsRecorder.recordObjectChangeProcessed();
        }

        private void shutdown() {
            workers.shutdownNow();
        }
    }
}
