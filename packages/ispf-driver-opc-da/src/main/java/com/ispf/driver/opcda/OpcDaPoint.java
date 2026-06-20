package com.ispf.driver.opcda;

/**
 * Point mapping: OPC DA item id placeholder or alias {@code status}.
 */
public record OpcDaPoint(String itemId, boolean statusOnly) {

    public static OpcDaPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new OpcDaPoint("", true);
        }
        String trimmed = raw.trim();
        if ("status".equalsIgnoreCase(trimmed) || "dcom".equalsIgnoreCase(trimmed)) {
            return new OpcDaPoint(trimmed, true);
        }
        return new OpcDaPoint(trimmed, false);
    }
}
