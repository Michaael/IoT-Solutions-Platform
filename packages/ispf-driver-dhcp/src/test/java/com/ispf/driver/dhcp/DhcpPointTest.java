package com.ispf.driver.dhcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DhcpPointTest {

    @Test
    void parsesServerIp() {
        DhcpPoint point = DhcpPoint.parse("serverIp");
        assertEquals(DhcpPoint.Kind.SERVER_IP, point.kind());
    }

    @Test
    void parsesLease() {
        DhcpPoint point = DhcpPoint.parse("lease");
        assertEquals(DhcpPoint.Kind.LEASE, point.kind());
    }

    @Test
    void rejectsUnknownMapping() {
        assertThrows(IllegalArgumentException.class, () -> DhcpPoint.parse("gateway"));
    }
}
