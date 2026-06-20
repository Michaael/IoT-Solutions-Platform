package com.ispf.driver.opcuaserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpcUaServerPointTest {

    @Test
    void parsesNodeIdInConfiguredNamespace() throws Exception {
        OpcUaServerPoint point = OpcUaServerPoint.parse("Temperature", 2);
        assertEquals(2, point.nodeId().getNamespaceIndex().intValue());
        assertEquals("Temperature", point.nodeIdText());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(Exception.class, () -> OpcUaServerPoint.parse(" ", 2));
    }
}
