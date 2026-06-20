package com.ispf.driver.sip;

import java.util.Locale;

/**
 * Point mapping: {@code options} reachability or {@code register} status.
 */
public record SipPoint(Mode mode) {

    public enum Mode {
        OPTIONS,
        REGISTER
    }

    public static SipPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SIP point mapping is blank");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "options", "ping", "reachability" -> new SipPoint(Mode.OPTIONS);
            case "register", "registration", "status" -> new SipPoint(Mode.REGISTER);
            default -> throw new IllegalArgumentException("Unknown SIP point mapping: " + raw);
        };
    }
}
