package com.ispf.driver.corba;

import java.util.Locale;

/**
 * Point mapping: {@code connected} IIOP reachability.
 */
public record CorbaPoint(Mode mode) {

    public enum Mode {
        CONNECTED
    }

    public static CorbaPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new CorbaPoint(Mode.CONNECTED);
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("connected".equals(normalized) || "reachable".equals(normalized) || "iiop".equals(normalized)) {
            return new CorbaPoint(Mode.CONNECTED);
        }
        throw new IllegalArgumentException("Unknown CORBA point mapping: " + raw);
    }
}
