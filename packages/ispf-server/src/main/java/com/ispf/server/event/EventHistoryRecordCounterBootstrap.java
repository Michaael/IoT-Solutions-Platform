package com.ispf.server.event;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class EventHistoryRecordCounterBootstrap {

    private final EventJournalStore eventJournalStore;
    private final EventHistoryRecordCounter recordCounter;

    public EventHistoryRecordCounterBootstrap(
            EventJournalStore eventJournalStore,
            EventHistoryRecordCounter recordCounter
    ) {
        this.eventJournalStore = eventJournalStore;
        this.recordCounter = recordCounter;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(50)
    public void initializeCounter() {
        if (!recordCounter.isInitialized()) {
            recordCounter.initialize(eventJournalStore.countTotal());
        }
    }
}
