package com.ispf.driver.jms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JmsPointTest {

    @Test
    void parsesConsume() {
        JmsPoint point = JmsPoint.parse("consume");
        assertEquals(JmsPoint.JmsMode.CONSUME, point.mode());
    }

    @Test
    void parsesBrowseAll() {
        JmsPoint point = JmsPoint.parse("browse");
        assertEquals(JmsPoint.JmsMode.BROWSE, point.mode());
        assertEquals(Integer.MAX_VALUE, point.browseDepth());
    }

    @Test
    void parsesBrowseWithDepth() {
        JmsPoint point = JmsPoint.parse("browse:25");
        assertEquals(JmsPoint.JmsMode.BROWSE, point.mode());
        assertEquals(25, point.browseDepth());
    }

    @Test
    void rejectsUnknownMapping() {
        assertThrows(IllegalArgumentException.class, () -> JmsPoint.parse("publish"));
    }
}
