package com.ispf.driver.corba;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorbaPointTest {

    @Test
    void defaultsToConnected() {
        assertEquals(CorbaPoint.Mode.CONNECTED, CorbaPoint.parse("").mode());
        assertEquals(CorbaPoint.Mode.CONNECTED, CorbaPoint.parse("connected").mode());
    }
}
