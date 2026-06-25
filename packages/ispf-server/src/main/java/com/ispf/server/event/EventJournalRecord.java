package com.ispf.server.event;

import com.ispf.server.persistence.entity.EventHistoryEntity;

import java.time.Instant;

public record EventJournalRecord(
        String id,
        String objectPath,
        String eventName,
        String level,
        String payloadJson,
        Instant occurredAt
) {
    public static EventJournalRecord fromEntity(EventHistoryEntity entity) {
        return new EventJournalRecord(
                entity.getId(),
                entity.getObjectPath(),
                entity.getEventName(),
                entity.getLevel(),
                entity.getPayloadJson(),
                entity.getOccurredAt()
        );
    }

    public EventHistoryEntity toEntity() {
        EventHistoryEntity entity = new EventHistoryEntity();
        entity.setId(id);
        entity.setObjectPath(objectPath);
        entity.setEventName(eventName);
        entity.setLevel(level);
        entity.setPayloadJson(payloadJson);
        entity.setOccurredAt(occurredAt);
        return entity;
    }
}
