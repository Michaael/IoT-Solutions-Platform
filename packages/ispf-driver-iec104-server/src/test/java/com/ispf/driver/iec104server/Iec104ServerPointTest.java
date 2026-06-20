package com.ispf.driver.iec104server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Iec104ServerPointTest {

    @Test
    void parsesIoa() throws Exception {
        Iec104ServerPoint point = Iec104ServerPoint.parse("2001");
        assertEquals(2001, point.ioa());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(Exception.class, () -> Iec104ServerPoint.parse(" "));
    }
}
