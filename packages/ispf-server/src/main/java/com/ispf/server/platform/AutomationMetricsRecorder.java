package com.ispf.server.platform;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AutomationMetricsRecorder {

    public enum EventFireSource {
        ALERT("alert"),
        API("api"),
        CORRELATOR("correlator"),
        FUNCTION("function");

        private final String tag;

        EventFireSource(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    public enum WorkflowStartTrigger {
        VARIABLE("variable"),
        CORRELATOR("correlator"),
        EVENT("event"),
        MANUAL("manual");

        private final String tag;

        WorkflowStartTrigger(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private final Optional<MeterRegistry> meterRegistry;
    private final AtomicLong objectChangeProcessed = new AtomicLong();
    private final AtomicLong alertEvaluationsCount = new AtomicLong();
    private final AtomicLong alertFiresCount = new AtomicLong();
    private final AtomicLong correlatorTriggersCount = new AtomicLong();
    private final AtomicLong objectChangeDroppedCount = new AtomicLong();
    private final AtomicLong eventJournalSyncFallbackCount = new AtomicLong();
    private final AtomicLong eventJournalFlushedCount = new AtomicLong();
    private final EnumMap<EventFireSource, AtomicLong> eventsFiredBySource = new EnumMap<>(EventFireSource.class);
    private final EnumMap<WorkflowStartTrigger, AtomicLong> workflowStartsByTrigger = new EnumMap<>(WorkflowStartTrigger.class);
    private final List<BlockingQueue<?>> objectChangeQueues = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile BlockingQueue<?> eventJournalQueue;

    public AutomationMetricsRecorder(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (EventFireSource source : EventFireSource.values()) {
            eventsFiredBySource.put(source, new AtomicLong());
        }
        for (WorkflowStartTrigger trigger : WorkflowStartTrigger.values()) {
            workflowStartsByTrigger.put(trigger, new AtomicLong());
        }
        meterRegistry.ifPresent(this::registerMeters);
    }

    private void registerMeters(MeterRegistry registry) {
        Counter.builder("ispf.alert.evaluations.total").register(registry);
        Counter.builder("ispf.alert.fires.total").register(registry);
        Counter.builder("ispf.correlator.triggers.total").register(registry);
        Counter.builder("ispf.object_change.queue.dropped.total").register(registry);
        Counter.builder("ispf.event_journal.queue_full.sync_fallback.total").register(registry);
        for (EventFireSource source : EventFireSource.values()) {
            Counter.builder("ispf.events.fired.total")
                    .tag("source", source.tag())
                    .register(registry);
        }
        for (WorkflowStartTrigger trigger : WorkflowStartTrigger.values()) {
            Counter.builder("ispf.workflow.starts.total")
                    .tag("trigger", trigger.tag())
                    .register(registry);
        }
    }

    public void bindObjectChangeQueue(String lane, BlockingQueue<?> queue) {
        objectChangeQueues.add(queue);
        meterRegistry.ifPresent(registry -> registry.gauge(
                "ispf.object_change.queue.size",
                Tags.of("lane", lane),
                queue,
                BlockingQueue::size
        ));
        if (objectChangeQueues.size() == 1) {
            meterRegistry.ifPresent(registry -> registry.gauge(
                    "ispf.object_change.processed.total",
                    Tags.empty(),
                    objectChangeProcessed,
                    AtomicLong::doubleValue
            ));
        }
    }

    public void bindEventJournalQueue(BlockingQueue<?> queue) {
        this.eventJournalQueue = queue;
        meterRegistry.ifPresent(registry -> {
            registry.gauge("ispf.event_journal.queue.size", Tags.empty(), queue, BlockingQueue::size);
            registry.gauge(
                    "ispf.event_journal.flushed.total",
                    Tags.empty(),
                    eventJournalFlushedCount,
                    AtomicLong::doubleValue
            );
        });
    }

    public void recordEventFired(EventFireSource source) {
        eventsFiredBySource.get(source).incrementAndGet();
        meterRegistry.ifPresent(registry -> registry.counter(
                "ispf.events.fired.total",
                "source",
                source.tag()
        ).increment());
    }

    public void recordAlertEvaluation() {
        alertEvaluationsCount.incrementAndGet();
        meterRegistry.ifPresent(registry -> registry.counter("ispf.alert.evaluations.total").increment());
    }

    public void recordAlertFire() {
        alertFiresCount.incrementAndGet();
        meterRegistry.ifPresent(registry -> registry.counter("ispf.alert.fires.total").increment());
    }

    public void recordCorrelatorTrigger() {
        correlatorTriggersCount.incrementAndGet();
        meterRegistry.ifPresent(registry -> registry.counter("ispf.correlator.triggers.total").increment());
    }

    public void recordWorkflowStart(WorkflowStartTrigger trigger) {
        workflowStartsByTrigger.get(trigger).incrementAndGet();
        meterRegistry.ifPresent(registry -> registry.counter(
                "ispf.workflow.starts.total",
                "trigger",
                trigger.tag()
        ).increment());
    }

    public void recordObjectChangeProcessed() {
        objectChangeProcessed.incrementAndGet();
    }

    public void recordObjectChangeQueueDropped() {
        objectChangeDroppedCount.incrementAndGet();
        meterRegistry.ifPresent(registry -> registry.counter("ispf.object_change.queue.dropped.total").increment());
    }

    public void recordEventJournalSyncFallback() {
        eventJournalSyncFallbackCount.incrementAndGet();
        meterRegistry.ifPresent(registry ->
                registry.counter("ispf.event_journal.queue_full.sync_fallback.total").increment()
        );
    }

    public void recordEventJournalFlushed(long count) {
        eventJournalFlushedCount.addAndGet(count);
    }

    public Map<String, Object> automationSnapshot() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("eventsFiredTotal", eventsFiredTotal());
        section.put("alertFiresTotal", alertFiresCount.get());
        section.put("correlatorTriggersTotal", correlatorTriggersCount.get());
        section.put("workflowStartsTotal", workflowStartsTotal());
        section.put("objectChangeQueueSize", objectChangeQueueSize());
        section.put("objectChangeProcessedTotal", objectChangeProcessed.get());
        section.put("eventJournalQueueSize", eventJournalQueueSize());
        section.put("eventJournalFlushedTotal", eventJournalFlushedCount.get());
        section.put("eventJournalSyncFallbackTotal", eventJournalSyncFallbackCount.get());
        return section;
    }

    public long eventsFiredTotal() {
        return eventsFiredBySource.values().stream().mapToLong(AtomicLong::get).sum();
    }

    public long workflowStartsTotal() {
        return workflowStartsByTrigger.values().stream().mapToLong(AtomicLong::get).sum();
    }

    public int objectChangeQueueSize() {
        return objectChangeQueues.stream().mapToInt(BlockingQueue::size).sum();
    }

    public int eventJournalQueueSize() {
        BlockingQueue<?> queue = eventJournalQueue;
        return queue != null ? queue.size() : 0;
    }
}
