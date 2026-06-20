package com.ispf.driver.odbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OdbcPointTest {

    @Test
    void parsesColumnName() {
        OdbcPoint point = OdbcPoint.parse("temperature");
        assertEquals("temperature", point.columnName());
    }

    @Test
    void trimsColumnName() {
        OdbcPoint point = OdbcPoint.parse("  status_code  ");
        assertEquals("status_code", point.columnName());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(IllegalArgumentException.class, () -> OdbcPoint.parse("  "));
    }
}
