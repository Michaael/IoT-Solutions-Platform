package com.ispf.driver.opcbridge;

import java.util.Locale;

/**
 * Point mapping: {@code connected} TCP reachability to LON/OPC bridge.
 */
public record OpcBridgePoint(Mode mode) {

    public enum Mode {
        CONNECTED
    }

    public static OpcBridgePoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new OpcBridgePoint(Mode.CONNECTED);
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("connected".equals(normalized) || "reachable".equals(normalized) || "bridge".equals(normalized)) {
            return new OpcBridgePoint(Mode.CONNECTED);
        }
        throw new IllegalArgumentException("Unknown OPC bridge point mapping: " + raw);
    }
}
