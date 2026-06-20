package com.ispf.driver.pop3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pop3PointTest {

    @Test
    void parsesStat() {
        Pop3Point point = Pop3Point.parse("stat");
        assertEquals(Pop3Point.Kind.STAT, point.kind());
    }

    @Test
    void parsesRetrWithColon() {
        Pop3Point point = Pop3Point.parse("retr:2");
        assertEquals(Pop3Point.Kind.RETR, point.kind());
        assertEquals(2, point.messageNumber());
    }

    @Test
    void parsesRetrWithSpace() {
        Pop3Point point = Pop3Point.parse("retr 4");
        assertEquals(4, point.messageNumber());
    }

    @Test
    void rejectsUnknownMapping() {
        assertThrows(IllegalArgumentException.class, () -> Pop3Point.parse("list"));
    }
}
