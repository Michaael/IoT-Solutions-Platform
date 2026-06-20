package com.ispf.driver.opcbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpcBridgePointTest {

    @Test
    void defaultsToConnected() {
        assertEquals(OpcBridgePoint.Mode.CONNECTED, OpcBridgePoint.parse(null).mode());
        assertEquals(OpcBridgePoint.Mode.CONNECTED, OpcBridgePoint.parse("bridge").mode());
    }
}
