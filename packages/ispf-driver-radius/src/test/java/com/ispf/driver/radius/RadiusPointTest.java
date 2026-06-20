package com.ispf.driver.radius;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RadiusPointTest {

    @Test
    void parsesAuth() {
        RadiusPoint point = RadiusPoint.parse("auth");
        assertEquals(RadiusPoint.Kind.AUTH, point.kind());
    }

    @Test
    void rejectsUnknownMapping() {
        assertThrows(IllegalArgumentException.class, () -> RadiusPoint.parse("acct"));
    }
}
