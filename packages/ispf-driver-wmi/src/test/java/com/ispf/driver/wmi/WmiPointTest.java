package com.ispf.driver.wmi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WmiPointTest {

    @Test
    void parsesFullQuery() {
        WmiPoint point = WmiPoint.parse("SELECT FreeSpace FROM Win32_LogicalDisk WHERE DeviceID='C:'", "");
        assertEquals("SELECT FreeSpace FROM Win32_LogicalDisk WHERE DeviceID='C:'", point.query());
        assertNull(point.property());
    }

    @Test
    void parsesQueryWithPropertySuffix() {
        WmiPoint point = WmiPoint.parse("SELECT FreeSpace FROM Win32_LogicalDisk WHERE DeviceID='C:':FreeSpace", "");
        assertEquals("SELECT FreeSpace FROM Win32_LogicalDisk WHERE DeviceID='C:'", point.query());
        assertEquals("FreeSpace", point.property());
    }

    @Test
    void usesDefaultQueryWhenBlank() {
        WmiPoint point = WmiPoint.parse(" ", "SELECT Name FROM Win32_OperatingSystem");
        assertEquals("SELECT Name FROM Win32_OperatingSystem", point.query());
    }
}
