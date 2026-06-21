package com.ispf.server.correlator;

public enum CorrelatorPatternType {
    /** Single event with optional repetition threshold within a window. */
    COUNT,
    /** Event A followed by event B on the same object within a window. */
    SEQUENCE,
    /** Ordered chain of 3+ events (comma-separated in secondEventName slot after first). */
    EVENT_CHAIN
}
