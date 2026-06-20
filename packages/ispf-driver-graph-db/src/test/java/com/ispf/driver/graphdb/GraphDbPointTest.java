package com.ispf.driver.graphdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphDbPointTest {

    @Test
    void parsesCypherQuery() {
        GraphDbPoint point = GraphDbPoint.parse("RETURN 1");
        assertEquals("RETURN 1", point.cypher());
    }

    @Test
    void trimsCypherQuery() {
        GraphDbPoint point = GraphDbPoint.parse("  MATCH (n) RETURN count(n)  ");
        assertEquals("MATCH (n) RETURN count(n)", point.cypher());
    }

    @Test
    void rejectsBlankQuery() {
        assertThrows(IllegalArgumentException.class, () -> GraphDbPoint.parse(" "));
    }
}
