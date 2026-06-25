package com.ispf.server.event;

import com.ispf.server.persistence.entity.EventHistoryEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventJournalBatchPersister {

    private final EventJournalStore eventJournalStore;

    public EventJournalBatchPersister(EventJournalStore eventJournalStore) {
        this.eventJournalStore = eventJournalStore;
    }

    public void persistBatch(List<EventHistoryEntity> batch) {
        eventJournalStore.appendBatch(batch.stream().map(EventJournalRecord::fromEntity).toList());
    }

    public void persistOne(EventHistoryEntity entity) {
        eventJournalStore.appendOne(EventJournalRecord.fromEntity(entity));
    }
}
