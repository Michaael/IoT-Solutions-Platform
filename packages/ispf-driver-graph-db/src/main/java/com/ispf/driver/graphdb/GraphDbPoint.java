package com.ispf.driver.graphdb;

/**
 * Point mapping: Cypher query expected to return a single scalar value.
 */
public record GraphDbPoint(String cypher) {

    public static GraphDbPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Graph DB point mapping (Cypher query) is blank");
        }
        return new GraphDbPoint(raw.trim());
    }
}
