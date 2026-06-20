package com.ispf.driver.sip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SipPointTest {

    @Test
    void parsesOptions() {
        assertEquals(SipPoint.Mode.OPTIONS, SipPoint.parse("options").mode());
        assertEquals(SipPoint.Mode.OPTIONS, SipPoint.parse("reachability").mode());
    }

    @Test
    void parsesRegister() {
        assertEquals(SipPoint.Mode.REGISTER, SipPoint.parse("register").mode());
        assertEquals(SipPoint.Mode.REGISTER, SipPoint.parse("status").mode());
    }
}
