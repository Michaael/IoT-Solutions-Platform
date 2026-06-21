package com.ispf.server.correlator;

import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.event.EventService;
import com.ispf.server.persistence.CorrelatorHitRepository;
import com.ispf.server.persistence.entity.CorrelatorHitEntity;
import com.ispf.server.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class EventCorrelatorService {

    private static final Logger log = LoggerFactory.getLogger(EventCorrelatorService.class);

    private final AutomationTreeService automationTreeService;
    private final CorrelatorHitRepository hitRepository;
    private final WorkflowService workflowService;
    private final EventService eventService;

    public EventCorrelatorService(
            AutomationTreeService automationTreeService,
            CorrelatorHitRepository hitRepository,
            @Lazy WorkflowService workflowService,
            EventService eventService
    ) {
        this.automationTreeService = automationTreeService;
        this.hitRepository = hitRepository;
        this.workflowService = workflowService;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<EventCorrelator> list() {
        return automationTreeService.listCorrelators();
    }

    @Transactional(readOnly = true)
    public EventCorrelator get(String id) {
        return automationTreeService.getCorrelator(id);
    }

    @Transactional
    public EventCorrelator create(CreateCorrelatorRequest request) {
        CorrelatorPatternType patternType = request.patternType() != null
                ? request.patternType()
                : CorrelatorPatternType.COUNT;
        validateRequest(
                patternType,
                request.eventName(),
                request.secondEventName(),
                request.actionType(),
                request.actionTarget(),
                request.minOccurrences(),
                request.windowSeconds()
        );
        return automationTreeService.createCorrelator(
                request.name(),
                request.objectPath(),
                patternType,
                request.eventName(),
                request.secondEventName(),
                request.windowSeconds(),
                request.minOccurrences(),
                request.cooldownSeconds(),
                request.actionType(),
                request.actionTarget(),
                request.enabled()
        );
    }

    @Transactional
    public EventCorrelator update(String id, UpdateCorrelatorRequest request) {
        EventCorrelator current = get(id);
        CorrelatorPatternType patternType = request.patternType() != null
                ? request.patternType()
                : current.patternType();
        validateRequest(
                patternType,
                request.eventName() != null ? request.eventName() : current.eventName(),
                request.secondEventName() != null ? request.secondEventName() : current.secondEventName(),
                request.actionType() != null ? request.actionType() : current.actionType(),
                request.actionTarget() != null ? request.actionTarget() : current.actionTarget(),
                request.minOccurrences() != null ? request.minOccurrences() : current.minOccurrences(),
                request.windowSeconds() != null ? request.windowSeconds() : current.windowSeconds()
        );
        return automationTreeService.updateCorrelator(
                id,
                request.name(),
                request.objectPath(),
                request.patternType(),
                request.eventName(),
                request.secondEventName(),
                request.windowSeconds(),
                request.minOccurrences(),
                request.cooldownSeconds(),
                request.actionType(),
                request.actionTarget(),
                request.enabled()
        );
    }

    @Transactional
    public void delete(String id) {
        automationTreeService.deleteCorrelator(id);
    }

    @Transactional
    public void processEventFired(String objectPath, String eventName) {
        LinkedHashMap<String, EventCorrelator> correlators = new LinkedHashMap<>();
        for (EventCorrelator correlator : automationTreeService.findEnabledCorrelatorsForEvent(eventName)) {
            correlators.put(correlator.id(), correlator);
        }

        Instant now = Instant.now();
        for (EventCorrelator correlator : correlators.values()) {
            if (!matchesObject(correlator.objectPath(), objectPath)) {
                continue;
            }
            if (isInCooldown(correlator, now)) {
                continue;
            }
            boolean triggered = switch (correlator.patternType()) {
                case COUNT -> processCountPattern(correlator, objectPath, eventName, now);
                case SEQUENCE -> processSequencePattern(correlator, objectPath, eventName, now);
                case EVENT_CHAIN -> processEventChainPattern(correlator, objectPath, eventName, now);
            };
            if (triggered) {
                executeAction(correlator, objectPath);
                automationTreeService.setCorrelatorLastTriggeredAt(correlator.id(), now);
                hitRepository.deleteByCorrelatorId(correlator.id());
            }
        }
        hitRepository.deleteOlderThan(now.minus(1, ChronoUnit.HOURS));
    }

    @Transactional
    public void ensureDemoCorrelators() {
        automationTreeService.ensureDemoCorrelators();
    }

    private boolean processCountPattern(
            EventCorrelator correlator,
            String objectPath,
            String eventName,
            Instant now
    ) {
        if (!eventName.equals(correlator.eventName())) {
            return false;
        }
        recordHit(correlator.id(), objectPath, eventName, now);
        return thresholdMet(correlator, objectPath, now);
    }

    private boolean processSequencePattern(
            EventCorrelator correlator,
            String objectPath,
            String eventName,
            Instant now
    ) {
        String firstEvent = correlator.eventName();
        String secondEvent = correlator.secondEventName();
        if (secondEvent == null || secondEvent.isBlank()) {
            return false;
        }
        if (eventName.equals(firstEvent)) {
            recordHit(correlator.id(), objectPath, firstEvent, now);
            return false;
        }
        if (!eventName.equals(secondEvent)) {
            return false;
        }
        Instant since = correlator.windowSeconds() > 0
                ? now.minusSeconds(correlator.windowSeconds())
                : now.minusSeconds(1);
        return hitRepository.existsByCorrelatorIdAndObjectPathAndEventNameAndOccurredAtAfter(
                correlator.id(),
                objectPath,
                firstEvent,
                since
        );
    }

    private boolean processEventChainPattern(
            EventCorrelator correlator,
            String objectPath,
            String eventName,
            Instant now
    ) {
        List<String> chain = eventChain(correlator);
        if (chain.isEmpty() || !chain.contains(eventName)) {
            return false;
        }
        recordHit(correlator.id(), objectPath, eventName, now);
        if (!eventName.equals(chain.get(chain.size() - 1))) {
            return false;
        }
        Instant since = correlator.windowSeconds() > 0
                ? now.minusSeconds(correlator.windowSeconds())
                : now.minusSeconds(3600);
        List<String> observed = hitRepository
                .findByCorrelatorIdAndObjectPathAndOccurredAtAfterOrderByOccurredAtAsc(
                        correlator.id(),
                        objectPath,
                        since
                )
                .stream()
                .map(hit -> hit.getEventName())
                .toList();
        return matchesEventChain(chain, observed);
    }

    private static List<String> eventChain(EventCorrelator correlator) {
        List<String> chain = new ArrayList<>();
        if (correlator.eventName() != null && !correlator.eventName().isBlank()) {
            chain.add(correlator.eventName());
        }
        if (correlator.secondEventName() != null && !correlator.secondEventName().isBlank()) {
            for (String part : correlator.secondEventName().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    chain.add(trimmed);
                }
            }
        }
        return chain;
    }

    private static boolean matchesEventChain(List<String> chain, List<String> observed) {
        int chainIndex = 0;
        for (String event : observed) {
            if (chainIndex < chain.size() && chain.get(chainIndex).equals(event)) {
                chainIndex++;
            }
        }
        return chainIndex >= chain.size();
    }

    private void executeAction(EventCorrelator correlator, String objectPath) {
        try {
            switch (correlator.actionType()) {
                case RUN_WORKFLOW -> workflowService.runWorkflow(correlator.actionTarget(), objectPath);
                case FIRE_EVENT -> eventService.fire(objectPath, correlator.actionTarget(), (com.ispf.core.model.DataRecord) null);
            }
        } catch (Exception e) {
            log.warn("Correlator {} action failed: {}", correlator.id(), e.getMessage());
        }
    }

    private boolean thresholdMet(EventCorrelator correlator, String objectPath, Instant now) {
        if (correlator.windowSeconds() <= 0) {
            return correlator.minOccurrences() <= 1
                    || hitRepository.countByCorrelatorIdAndObjectPathAndOccurredAtAfter(
                    correlator.id(), objectPath, now.minusSeconds(1)) >= correlator.minOccurrences();
        }
        Instant since = now.minusSeconds(correlator.windowSeconds());
        long hits = hitRepository.countByCorrelatorIdAndObjectPathAndOccurredAtAfter(
                correlator.id(),
                objectPath,
                since
        );
        return hits >= correlator.minOccurrences();
    }

    private void recordHit(String correlatorId, String objectPath, String eventName, Instant now) {
        CorrelatorHitEntity hit = new CorrelatorHitEntity();
        hit.setCorrelatorId(correlatorId);
        hit.setObjectPath(objectPath);
        hit.setEventName(eventName);
        hit.setOccurredAt(now);
        hitRepository.save(hit);
    }

    private static boolean isInCooldown(EventCorrelator correlator, Instant now) {
        if (correlator.cooldownSeconds() <= 0 || correlator.lastTriggeredAt() == null) {
            return false;
        }
        return correlator.lastTriggeredAt()
                .plusSeconds(correlator.cooldownSeconds())
                .isAfter(now);
    }

    private static boolean matchesObject(String filterPath, String eventPath) {
        return filterPath == null || filterPath.isBlank() || filterPath.equals(eventPath);
    }

    private static void validateRequest(
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            CorrelatorActionType actionType,
            String actionTarget,
            int minOccurrences,
            int windowSeconds
    ) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        if (actionTarget == null || actionTarget.isBlank()) {
            throw new IllegalArgumentException("actionTarget is required");
        }
        if (minOccurrences < 1) {
            throw new IllegalArgumentException("minOccurrences must be >= 1");
        }
        if (windowSeconds < 0) {
            throw new IllegalArgumentException("windowSeconds must be >= 0");
        }
        if (actionType == CorrelatorActionType.RUN_WORKFLOW && (actionTarget == null || actionTarget.isBlank())) {
            throw new IllegalArgumentException("actionTarget workflow path is required for RUN_WORKFLOW");
        }
        if (actionType == CorrelatorActionType.FIRE_EVENT && (actionTarget == null || actionTarget.isBlank())) {
            throw new IllegalArgumentException("actionTarget event name is required for FIRE_EVENT");
        }
        if (patternType == CorrelatorPatternType.SEQUENCE) {
            if (secondEventName == null || secondEventName.isBlank()) {
                throw new IllegalArgumentException("secondEventName is required for SEQUENCE pattern");
            }
            if (eventName.equals(secondEventName)) {
                throw new IllegalArgumentException("secondEventName must differ from eventName");
            }
            if (windowSeconds <= 0) {
                throw new IllegalArgumentException("windowSeconds must be > 0 for SEQUENCE pattern");
            }
        }
        if (patternType == CorrelatorPatternType.EVENT_CHAIN) {
            if (secondEventName == null || secondEventName.isBlank()) {
                throw new IllegalArgumentException("secondEventName chain (comma-separated) is required for EVENT_CHAIN");
            }
        }
    }

    public record CreateCorrelatorRequest(
            String name,
            String objectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            int windowSeconds,
            int minOccurrences,
            int cooldownSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            boolean enabled
    ) {
    }

    public record UpdateCorrelatorRequest(
            String name,
            String objectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            Integer windowSeconds,
            Integer minOccurrences,
            Integer cooldownSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            Boolean enabled
    ) {
    }
}
