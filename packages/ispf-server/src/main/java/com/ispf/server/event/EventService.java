package com.ispf.server.event;

import com.ispf.core.object.ObjectEvent;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.model.DataRecord;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.api.dto.DataRecordPayloadResolver;
import com.ispf.server.application.catalog.EventCatalogPayloadValidator;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.entity.EventHistoryEntity;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EventService {

    private final ObjectManager objectManager;
    private final EventJournalStore eventJournalStore;
    private final ObjectEntityMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventCatalogPayloadValidator catalogPayloadValidator;
    private final EventJournalAsyncWriter eventJournalAsyncWriter;
    private final RecentEventCache recentEventCache;
    private final AutomationMetricsRecorder automationMetricsRecorder;

    public EventService(
            ObjectManager objectManager,
            EventJournalStore eventJournalStore,
            ObjectEntityMapper mapper,
            ApplicationEventPublisher eventPublisher,
            EventCatalogPayloadValidator catalogPayloadValidator,
            EventJournalAsyncWriter eventJournalAsyncWriter,
            RecentEventCache recentEventCache,
            AutomationMetricsRecorder automationMetricsRecorder
    ) {
        this.objectManager = objectManager;
        this.eventJournalStore = eventJournalStore;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.catalogPayloadValidator = catalogPayloadValidator;
        this.eventJournalAsyncWriter = eventJournalAsyncWriter;
        this.recentEventCache = recentEventCache;
        this.automationMetricsRecorder = automationMetricsRecorder;
    }

    /**
     * Hot-path fire for automation (alerts). No surrounding transaction — journal is async.
     */
    public ObjectEvent fireAutomation(String objectPath, String eventName, DataRecord payload) {
        return fireInternal(
                objectPath,
                eventName,
                DataRecordPayloadResolver.fromRecord(payload),
                null,
                AutomationMetricsRecorder.EventFireSource.ALERT
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ObjectEvent fire(String objectPath, String eventName, DataRecordPayloadRequest payload) {
        return fireInternal(objectPath, eventName, payload, null, AutomationMetricsRecorder.EventFireSource.API);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ObjectEvent fire(String objectPath, String eventName, DataRecordPayloadRequest payload, String appId) {
        return fireInternal(objectPath, eventName, payload, appId, AutomationMetricsRecorder.EventFireSource.API);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ObjectEvent fire(String objectPath, String eventName, DataRecord payload) {
        return fireInternal(
                objectPath,
                eventName,
                DataRecordPayloadResolver.fromRecord(payload),
                null,
                AutomationMetricsRecorder.EventFireSource.API
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ObjectEvent fire(
            String objectPath,
            String eventName,
            DataRecord payload,
            AutomationMetricsRecorder.EventFireSource source
    ) {
        return fireInternal(objectPath, eventName, DataRecordPayloadResolver.fromRecord(payload), null, source);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ObjectEvent fire(
            String objectPath,
            String eventName,
            DataRecordPayloadRequest payload,
            AutomationMetricsRecorder.EventFireSource source
    ) {
        return fireInternal(objectPath, eventName, payload, null, source);
    }

    @Transactional(readOnly = true)
    public List<ObjectEvent> list(String objectPath, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        List<ObjectEvent> fromCache = recentEventCache.isEnabled()
                ? recentEventCache.query(objectPath, capped)
                : List.of();
        if (fromCache.size() >= capped) {
            return fromCache;
        }
        Set<String> seen = new HashSet<>();
        for (ObjectEvent event : fromCache) {
            seen.add(event.id());
        }
        int remaining = capped - fromCache.size();
        List<EventJournalRecord> records = eventJournalStore.queryRecent(objectPath, remaining);
        List<ObjectEvent> merged = new ArrayList<>(fromCache);
        for (EventJournalRecord record : records) {
            if (seen.add(record.id())) {
                merged.add(toObjectEvent(record));
                if (merged.size() >= capped) {
                    break;
                }
            }
        }
        return merged;
    }

    private ObjectEvent fireInternal(
            String objectPath,
            String eventName,
            DataRecordPayloadRequest payload,
            String appId,
            AutomationMetricsRecorder.EventFireSource source
    ) {
        if (appId != null && !appId.isBlank()) {
            catalogPayloadValidator.validateAtFire(appId, eventName, payload);
        }
        PlatformObject node = objectManager.require(objectPath);
        EventDescriptor descriptor = Optional.ofNullable(node.events().get(eventName))
                .orElseThrow(() -> new IllegalArgumentException("Unknown event: " + eventName));

        DataRecord resolvedPayload = DataRecordPayloadResolver.resolve(descriptor.payloadSchema(), payload);
        ObjectEvent event = ObjectEvent.of(objectPath, eventName, descriptor.level(), resolvedPayload);
        eventJournalAsyncWriter.enqueue(event, mapper.writeDataRecord(event.payload()));
        automationMetricsRecorder.recordEventFired(source);
        eventPublisher.publishEvent(ObjectChangeEvent.eventFired(objectPath, eventName));
        return event;
    }

    private ObjectEvent toObjectEvent(EventJournalRecord record) {
        DataRecord payload = mapper.readDataRecord(record.payloadJson());
        if (payload == null) {
            payload = DataRecord.empty(
                    com.ispf.core.model.DataSchema.builder("eventPayload").build()
            );
        }
        return new ObjectEvent(
                record.id(),
                record.objectPath(),
                record.eventName(),
                EventLevel.valueOf(record.level()),
                payload,
                record.occurredAt()
        );
    }

    private ObjectEvent toObjectEvent(EventHistoryEntity entity) {
        return toObjectEvent(EventJournalRecord.fromEntity(entity));
    }
}
