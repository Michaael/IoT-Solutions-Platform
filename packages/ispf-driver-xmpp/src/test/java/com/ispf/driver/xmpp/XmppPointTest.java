package com.ispf.driver.xmpp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmppPointTest {

    @Test
    void parsesPresence() {
        assertEquals(XmppPoint.Mode.PRESENCE, XmppPoint.parse("presence").mode());
    }

    @Test
    void parsesRosterCount() {
        assertEquals(XmppPoint.Mode.ROSTER_COUNT, XmppPoint.parse("rosterCount").mode());
        assertEquals(XmppPoint.Mode.ROSTER_COUNT, XmppPoint.parse("contacts").mode());
    }
}
