package com.ispf.server.correlator;

import java.time.Instant;

public record CorrelatorHit(
        String correlatorId,
        String objectPath,
        String eventName,
        Instant occurredAt
) {
}
