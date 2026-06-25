package com.ispf.server.correlator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sliding-window state for event correlators (COUNT / SEQUENCE / EVENT_CHAIN).
 */
public interface CorrelatorWindowStore {

    void recordHit(String correlatorId, String objectPath, String eventName, Instant occurredAt);

    long countHitsSince(String correlatorId, String objectPath, Instant since);

    Optional<CorrelatorHit> findFirstHitSince(
            String correlatorId,
            String objectPath,
            String eventName,
            Instant since
    );

    List<CorrelatorHit> listHitsSince(String correlatorId, String objectPath, Instant since);

    void clearCorrelator(String correlatorId);

    void purgeOlderThan(Instant cutoff);

    void remapCorrelatorId(String oldId, String newId);
}
