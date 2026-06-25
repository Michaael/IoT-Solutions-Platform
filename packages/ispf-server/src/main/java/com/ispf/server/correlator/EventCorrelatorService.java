package com.ispf.server.correlator;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.event.EventService;
import com.ispf.server.event.RecentEventCache;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.EventHistoryRepository;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.entity.EventHistoryEntity;
import com.ispf.server.platform.AutomationMetricsRecorder;
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
import java.util.Map;
import java.util.Optional;

@Service
public class EventCorrelatorService {

    private static final Logger log = LoggerFactory.getLogger(EventCorrelatorService.class);

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema INTEGER_VALUE = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();
    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();
    private static final DataSchema REPORT_PATH_PAYLOAD = DataSchema.builder("openOperatorReportPayload")
            .field("reportPath", FieldType.STRING)
            .build();

    private final AutomationTreeService automationTreeService;
    private final CorrelatorWindowStore windowStore;
    private final EventHistoryRepository eventHistoryRepository;
    private final WorkflowService workflowService;
    private final EventService eventService;
    private final ObjectManager objectManager;
    private final ExpressionEngine expressionEngine;
    private final ObjectEntityMapper entityMapper;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final RecentEventCache recentEventCache;

    public EventCorrelatorService(
            AutomationTreeService automationTreeService,
            CorrelatorWindowStore windowStore,
            EventHistoryRepository eventHistoryRepository,
            @Lazy WorkflowService workflowService,
            EventService eventService,
            ObjectManager objectManager,
            ExpressionEngine expressionEngine,
            ObjectEntityMapper entityMapper,
            AutomationMetricsRecorder automationMetricsRecorder,
            RecentEventCache recentEventCache
    ) {
        this.automationTreeService = automationTreeService;
        this.windowStore = windowStore;
        this.eventHistoryRepository = eventHistoryRepository;
        this.workflowService = workflowService;
        this.eventService = eventService;
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
        this.entityMapper = entityMapper;
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.recentEventCache = recentEventCache;
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
                request.sequenceGapSeconds(),
                request.actionType(),
                request.actionTarget(),
                request.payloadFilterExpr(),
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
                request.sequenceGapSeconds(),
                request.actionType(),
                request.actionTarget(),
                request.payloadFilterExpr(),
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
            if (!passesPayloadFilter(correlator, objectPath, eventName)) {
                continue;
            }
            boolean triggered = switch (correlator.patternType()) {
                case COUNT -> processCountPattern(correlator, objectPath, eventName, now);
                case SEQUENCE -> processSequencePattern(correlator, objectPath, eventName, now);
                case EVENT_CHAIN -> processEventChainPattern(correlator, objectPath, eventName, now);
            };
            if (triggered) {
                automationMetricsRecorder.recordCorrelatorTrigger();
                executeAction(correlator, objectPath);
                automationTreeService.setCorrelatorLastTriggeredAt(correlator.id(), now);
                windowStore.clearCorrelator(correlator.id());
            }
        }
        windowStore.purgeOlderThan(now.minus(1, ChronoUnit.HOURS));
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
        var firstHit = windowStore.findFirstHitSince(
                correlator.id(),
                objectPath,
                firstEvent,
                since
        );
        if (firstHit.isEmpty()) {
            return false;
        }
        if (correlator.sequenceGapSeconds() > 0) {
            long gap = java.time.temporal.ChronoUnit.SECONDS.between(firstHit.get().occurredAt(), now);
            if (gap > correlator.sequenceGapSeconds()) {
                return false;
            }
        }
        return true;
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
        List<CorrelatorHit> hits = windowStore.listHitsSince(
                correlator.id(),
                objectPath,
                since
        );
        return matchesEventChainWithGap(chain, hits, correlator.sequenceGapSeconds());
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

    private static boolean matchesEventChainWithGap(
            List<String> chain,
            List<CorrelatorHit> hits,
            int maxGapSeconds
    ) {
        int chainIndex = 0;
        Instant previousTime = null;
        for (CorrelatorHit hit : hits) {
            if (chainIndex >= chain.size()) {
                break;
            }
            if (!chain.get(chainIndex).equals(hit.eventName())) {
                continue;
            }
            if (previousTime != null && maxGapSeconds > 0) {
                long gap = java.time.temporal.ChronoUnit.SECONDS.between(previousTime, hit.occurredAt());
                if (gap > maxGapSeconds) {
                    chainIndex = 0;
                    previousTime = null;
                    if (chain.get(0).equals(hit.eventName())) {
                        chainIndex = 1;
                        previousTime = hit.occurredAt();
                    }
                    continue;
                }
            }
            previousTime = hit.occurredAt();
            chainIndex++;
        }
        return chainIndex >= chain.size();
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
                case FIRE_EVENT -> eventService.fire(objectPath, correlator.actionTarget(), (DataRecord) null);
                case SET_VARIABLE -> executeSetVariable(objectPath, correlator.actionTarget());
                case OPEN_OPERATOR_REPORT -> eventService.fire(
                        objectPath,
                        "openOperatorReport",
                        DataRecord.single(REPORT_PATH_PAYLOAD, Map.of("reportPath", correlator.actionTarget()))
                );
            }
        } catch (Exception e) {
            log.warn("Correlator {} action failed: {}", correlator.id(), e.getMessage());
        }
    }

    private void executeSetVariable(String objectPath, String actionTarget) {
        int separator = actionTarget.indexOf('=');
        if (separator <= 0 || separator >= actionTarget.length() - 1) {
            throw new IllegalArgumentException("SET_VARIABLE actionTarget must be variableName=value");
        }
        String variableName = actionTarget.substring(0, separator).trim();
        String rawValue = actionTarget.substring(separator + 1).trim();
        objectManager.setVariableValue(objectPath, variableName, parseVariableAssignment(rawValue));
    }

    private static DataRecord parseVariableAssignment(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
            return DataRecord.single(BOOLEAN_VALUE, Map.of("value", Boolean.parseBoolean(rawValue)));
        }
        try {
            if (rawValue.contains(".")) {
                return DataRecord.single(DOUBLE_VALUE, Map.of("value", Double.parseDouble(rawValue)));
            }
            return DataRecord.single(INTEGER_VALUE, Map.of("value", Integer.parseInt(rawValue)));
        } catch (NumberFormatException ignored) {
            return DataRecord.single(STRING_VALUE, Map.of("value", rawValue));
        }
    }

    private boolean passesPayloadFilter(EventCorrelator correlator, String objectPath, String eventName) {
        String filterExpr = correlator.payloadFilterExpr();
        if (filterExpr == null || filterExpr.isBlank()) {
            return true;
        }
        Optional<EventHistoryEntity> fromDb = eventHistoryRepository
                .findFirstByObjectPathAndEventNameOrderByOccurredAtDesc(objectPath, eventName);
        if (fromDb.isPresent()) {
            return evaluatePayloadFilter(filterExpr, fromDb.get());
        }
        return recentEventCache.findLatest(objectPath, eventName)
                .map(event -> evaluatePayloadFilter(filterExpr, event))
                .orElse(false);
    }

    private boolean evaluatePayloadFilter(String filterExpr, EventHistoryEntity entity) {
        return evaluatePayloadFilter(filterExpr, payloadMap(entity));
    }

    private boolean evaluatePayloadFilter(String filterExpr, ObjectEvent event) {
        return evaluatePayloadFilter(filterExpr, payloadMap(event));
    }

    private boolean evaluatePayloadFilter(String filterExpr, Map<String, Object> payload) {
        try {
            Object result = expressionEngine.evaluateWithPayload(filterExpr, payload);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (ExpressionException e) {
            log.warn("Payload filter evaluation failed: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> payloadMap(EventHistoryEntity entity) {
        if (entity.getPayloadJson() == null || entity.getPayloadJson().isBlank()) {
            return Map.of();
        }
        try {
            DataRecord record = entityMapper.readDataRecord(entity.getPayloadJson());
            return payloadMap(record);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> payloadMap(ObjectEvent event) {
        if (event.payload() == null || event.payload().rowCount() == 0) {
            return Map.of();
        }
        return payloadMap(event.payload());
    }

    private static Map<String, Object> payloadMap(DataRecord record) {
        if (record == null || record.rowCount() == 0) {
            return Map.of();
        }
        return new LinkedHashMap<>(record.firstRow());
    }

    private boolean thresholdMet(EventCorrelator correlator, String objectPath, Instant now) {
        if (correlator.windowSeconds() <= 0) {
            return correlator.minOccurrences() <= 1
                    || windowStore.countHitsSince(
                    correlator.id(), objectPath, now.minusSeconds(1)) >= correlator.minOccurrences();
        }
        Instant since = now.minusSeconds(correlator.windowSeconds());
        long hits = windowStore.countHitsSince(correlator.id(), objectPath, since);
        return hits >= correlator.minOccurrences();
    }

    private void recordHit(String correlatorId, String objectPath, String eventName, Instant now) {
        windowStore.recordHit(correlatorId, objectPath, eventName, now);
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
        if (actionType == CorrelatorActionType.SET_VARIABLE && !actionTarget.contains("=")) {
            throw new IllegalArgumentException("actionTarget must be variableName=value for SET_VARIABLE");
        }
        if (actionType == CorrelatorActionType.OPEN_OPERATOR_REPORT && (actionTarget == null || actionTarget.isBlank())) {
            throw new IllegalArgumentException("actionTarget report path is required for OPEN_OPERATOR_REPORT");
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
            int sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
            boolean enabled
    ) {
        public CreateCorrelatorRequest(
                String name,
                String objectPath,
                CorrelatorPatternType patternType,
                String eventName,
                String secondEventName,
                int windowSeconds,
                int minOccurrences,
                int cooldownSeconds,
                int sequenceGapSeconds,
                CorrelatorActionType actionType,
                String actionTarget,
                boolean enabled
        ) {
            this(name, objectPath, patternType, eventName, secondEventName, windowSeconds, minOccurrences,
                    cooldownSeconds, sequenceGapSeconds, actionType, actionTarget, "", enabled);
        }
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
            Integer sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
            Boolean enabled
    ) {
    }
}
