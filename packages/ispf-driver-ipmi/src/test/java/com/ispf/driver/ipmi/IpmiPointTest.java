package com.ispf.driver.ipmi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IpmiPointTest {

    @Test
    void parsesPower() {
        IpmiPoint point = IpmiPoint.parse("power");
        assertEquals(IpmiPoint.Kind.POWER, point.kind());
        assertNull(point.sensorName());
    }

    @Test
    void parsesSensorName() {
        IpmiPoint point = IpmiPoint.parse("CPU Temp");
        assertEquals(IpmiPoint.Kind.SENSOR, point.kind());
        assertEquals("CPU Temp", point.sensorName());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(IllegalArgumentException.class, () -> IpmiPoint.parse(" "));
    }
}
