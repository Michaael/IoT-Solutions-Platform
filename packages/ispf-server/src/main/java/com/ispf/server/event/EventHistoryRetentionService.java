package com.ispf.server.event;

import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class EventHistoryRetentionService {

    static final String RETENTION_LOCK = "event-history-retention";

    private final EventJournalStore eventJournalStore;
    private final EventJournalProperties properties;
    private final PlatformLeaderLockService leaderLockService;

    public EventHistoryRetentionService(
            EventJournalStore eventJournalStore,
            EventJournalProperties properties,
            PlatformLeaderLockService leaderLockService
    ) {
        this.eventJournalStore = eventJournalStore;
        this.properties = properties;
        this.leaderLockService = leaderLockService;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredEvents() {
        if (!leaderLockService.tryAcquire(RETENTION_LOCK, Duration.ofHours(1))) {
            return;
        }
        try {
            purgeExpiredEventsInternal();
        } finally {
            leaderLockService.release(RETENTION_LOCK);
        }
    }

    void purgeExpiredEventsInternal() {
        if (!eventJournalStore.supportsApplicationRetentionPurge()) {
            return;
        }
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        eventJournalStore.purgeOlderThan(cutoff);
    }
}
