package com.ispf.server.object;

import java.time.Instant;

public class ObjectRevisionConflictException extends RuntimeException {

    private final String objectPath;
    private final long expectedRevision;
    private final long currentRevision;
    private final String changedBy;
    private final Instant changedAt;

    public ObjectRevisionConflictException(
            String objectPath,
            long expectedRevision,
            long currentRevision,
            String changedBy,
            Instant changedAt
    ) {
        super("Object revision conflict for " + objectPath
                + ": expected " + expectedRevision + ", current " + currentRevision);
        this.objectPath = objectPath;
        this.expectedRevision = expectedRevision;
        this.currentRevision = currentRevision;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public String objectPath() {
        return objectPath;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long currentRevision() {
        return currentRevision;
    }

    public String changedBy() {
        return changedBy;
    }

    public Instant changedAt() {
        return changedAt;
    }
}
