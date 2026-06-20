package com.ispf.driver.modemat;

import java.util.Locale;
import java.util.Map;

/**
 * Point mapping: AT command (e.g. {@code AT+CSQ}) or alias such as {@code signal}.
 */
public record ModemAtPoint(String command) {

    private static final Map<String, String> ALIASES = Map.of(
            "signal", "AT+CSQ",
            "rssi", "AT+CSQ",
            "imei", "AT+CGSN",
            "operator", "AT+COPS?",
            "registration", "AT+CREG?"
    );

    public static ModemAtPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Modem AT point mapping is blank");
        }
        String trimmed = raw.trim();
        String alias = ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        if (alias != null) {
            return new ModemAtPoint(alias);
        }
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("AT")) {
            return new ModemAtPoint(trimmed);
        }
        return new ModemAtPoint("AT+" + trimmed);
    }

    public String wireCommand() {
        return command.endsWith("\r") ? command : command + "\r";
    }
}
