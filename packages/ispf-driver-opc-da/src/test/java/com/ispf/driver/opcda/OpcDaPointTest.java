package com.ispf.driver.opcda;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcDaPointTest {

    @Test
    void parsesItemId() {
        OpcDaPoint point = OpcDaPoint.parse("Channel1.Device1.Tag1");
        assertEquals("Channel1.Device1.Tag1", point.itemId());
        assertEquals(false, point.statusOnly());
    }

    @Test
    void parsesStatusAlias() {
        OpcDaPoint point = OpcDaPoint.parse("status");
        assertTrue(point.statusOnly());
    }
}
