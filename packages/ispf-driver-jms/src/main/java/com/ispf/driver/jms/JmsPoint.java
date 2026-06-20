package com.ispf.driver.jms;

import java.util.Locale;

/**
 * Point mapping: {@code consume} or {@code browse} / {@code browse:depth}.
 */
public record JmsPoint(JmsMode mode, int browseDepth) {

    public enum JmsMode {
        CONSUME,
        BROWSE
    }

    public static JmsPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("JMS point mapping is blank");
        }
        String trimmed = raw.trim();
        if ("consume".equalsIgnoreCase(trimmed)) {
            return new JmsPoint(JmsMode.CONSUME, 0);
        }
        if ("browse".equalsIgnoreCase(trimmed)) {
            return new JmsPoint(JmsMode.BROWSE, Integer.MAX_VALUE);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("browse:")) {
            String depthText = trimmed.substring("browse:".length()).trim();
            if (depthText.isEmpty()) {
                throw new IllegalArgumentException("JMS browse depth is blank");
            }
            int depth = Integer.parseInt(depthText);
            if (depth < 0) {
                throw new IllegalArgumentException("JMS browse depth must be non-negative");
            }
            return new JmsPoint(JmsMode.BROWSE, depth);
        }
        throw new IllegalArgumentException("Unknown JMS point mapping: " + trimmed);
    }
}
