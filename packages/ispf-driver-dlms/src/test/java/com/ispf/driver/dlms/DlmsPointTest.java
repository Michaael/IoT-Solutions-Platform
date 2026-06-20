package com.ispf.driver.dlms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DlmsPointTest {

    @Test
    void parsesLogicalDeviceAndObis() throws Exception {
        DlmsPoint point = DlmsPoint.parse("1:1.0.1.8.0.255");
        assertEquals(1, point.logicalDevice());
        assertEquals("1.0.1.8.0.255", point.obis());
    }

    @Test
    void rejectsMissingObis() {
        assertThrows(Exception.class, () -> DlmsPoint.parse("1:"));
    }
}
