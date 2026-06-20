package com.ispf.driver.ethernetip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EthernetIpPointTest {

    @Test
    void parsesTagPath() throws Exception {
        EthernetIpPoint point = EthernetIpPoint.parse("Program:MainProgram.Counter");
        assertEquals("Program:MainProgram.Counter", point.tagPath());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(Exception.class, () -> EthernetIpPoint.parse(""));
    }
}
