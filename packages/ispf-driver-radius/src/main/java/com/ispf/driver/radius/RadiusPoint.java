package com.ispf.driver.radius;

import java.util.Locale;

/**
 * Point mapping: {@code auth} — RADIUS Access-Request authentication probe.
 */
public record RadiusPoint(Kind kind) {

    public enum Kind {
        AUTH
    }

    public static RadiusPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("RADIUS point mapping is blank");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("auth".equals(normalized) || "authentication".equals(normalized)) {
            return new RadiusPoint(Kind.AUTH);
        }
        throw new IllegalArgumentException("Unknown RADIUS point mapping: " + raw);
    }
}
