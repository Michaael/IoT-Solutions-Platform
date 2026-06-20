package com.ispf.driver.smis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmisPointTest {

    @Test
    void parsesClassAndProperty() {
        SmisPoint point = SmisPoint.parse("CIM_RegisteredProfile:RegisteredOrganization");
        assertEquals("CIM_RegisteredProfile", point.className());
        assertEquals("RegisteredOrganization", point.propertyName());
    }

    @Test
    void trimsClassAndProperty() {
        SmisPoint point = SmisPoint.parse("  CIM_StoragePool : ElementName  ");
        assertEquals("CIM_StoragePool", point.className());
        assertEquals("ElementName", point.propertyName());
    }

    @Test
    void rejectsMissingProperty() {
        assertThrows(IllegalArgumentException.class, () -> SmisPoint.parse("CIM_StoragePool"));
    }
}
