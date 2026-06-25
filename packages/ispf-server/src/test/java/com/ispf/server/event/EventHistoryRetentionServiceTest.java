package com.ispf.server.event;

import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventHistoryRetentionServiceTest {

    @Mock
    private EventJournalStore eventJournalStore;
    @Mock
    private PlatformLeaderLockService leaderLockService;

    private EventJournalProperties properties;
    private EventHistoryRetentionService service;

    @BeforeEach
    void setUp() {
        properties = new EventJournalProperties();
        properties.setRetentionDays(90);
        service = new EventHistoryRetentionService(
                eventJournalStore,
                properties,
                leaderLockService
        );
    }

    @Test
    void skipsPurgeWhenStoreHandlesRetention() {
        when(eventJournalStore.supportsApplicationRetentionPurge()).thenReturn(false);

        service.purgeExpiredEventsInternal();

        verify(eventJournalStore, never()).purgeOlderThan(any());
    }

    @Test
    void purgesRowsOlderThanRetentionWhenApplicationPurgeEnabled() {
        when(eventJournalStore.supportsApplicationRetentionPurge()).thenReturn(true);

        service.purgeExpiredEventsInternal();

        verify(eventJournalStore).purgeOlderThan(org.mockito.ArgumentMatchers.argThat(cutoff ->
                cutoff.isBefore(Instant.now().minus(89, ChronoUnit.DAYS))
                        && cutoff.isAfter(Instant.now().minus(91, ChronoUnit.DAYS))
        ));
    }

    @Test
    void skipsPurgeWhenRetentionDisabled() {
        when(eventJournalStore.supportsApplicationRetentionPurge()).thenReturn(true);
        properties.setRetentionDays(0);

        service.purgeExpiredEventsInternal();

        verify(eventJournalStore, never()).purgeOlderThan(any());
    }
}
