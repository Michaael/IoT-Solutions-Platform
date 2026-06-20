package com.ispf.driver.xmpp;

import java.util.Locale;

/**
 * Point mapping: {@code presence} or {@code rosterCount}.
 */
public record XmppPoint(Mode mode) {

    public enum Mode {
        PRESENCE,
        ROSTER_COUNT
    }

    public static XmppPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("XMPP point mapping is blank");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "presence", "status", "online" -> new XmppPoint(Mode.PRESENCE);
            case "rostercount", "roster", "contacts" -> new XmppPoint(Mode.ROSTER_COUNT);
            default -> throw new IllegalArgumentException("Unknown XMPP point mapping: " + raw);
        };
    }
}
