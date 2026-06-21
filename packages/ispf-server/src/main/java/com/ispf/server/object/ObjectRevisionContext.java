package com.ispf.server.object;

/**
 * Thread-local collaboration context for optimistic concurrency and audit actor.
 */
public final class ObjectRevisionContext {

    private static final ThreadLocal<RevisionExpectation> EXPECTATION = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private ObjectRevisionContext() {
    }

    public record RevisionExpectation(Long expectedRevision, boolean forceOverwrite) {
    }

    public static void setExpectation(Long expectedRevision, boolean forceOverwrite) {
        if (expectedRevision == null && !forceOverwrite) {
            EXPECTATION.remove();
        } else {
            EXPECTATION.set(new RevisionExpectation(expectedRevision, forceOverwrite));
        }
    }

    public static RevisionExpectation expectation() {
        return EXPECTATION.get();
    }

    public static void setActor(String actor) {
        if (actor == null || actor.isBlank()) {
            ACTOR.remove();
        } else {
            ACTOR.set(actor);
        }
    }

    public static String actor() {
        String actor = ACTOR.get();
        return actor != null ? actor : "system";
    }

    public static void clear() {
        EXPECTATION.remove();
        ACTOR.remove();
    }
}
