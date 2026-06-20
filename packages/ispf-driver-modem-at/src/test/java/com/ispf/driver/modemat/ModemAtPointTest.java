package com.ispf.driver.modemat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModemAtPointTest {

    @Test
    void parsesAtCommand() {
        ModemAtPoint point = ModemAtPoint.parse("AT+CSQ");
        assertEquals("AT+CSQ", point.command());
        assertEquals("AT+CSQ\r", point.wireCommand());
    }

    @Test
    void resolvesSignalAlias() {
        ModemAtPoint point = ModemAtPoint.parse("signal");
        assertEquals("AT+CSQ", point.command());
    }

    @Test
    void prefixesShortCommand() {
        ModemAtPoint point = ModemAtPoint.parse("CGMI");
        assertEquals("AT+CGMI", point.command());
    }
}
