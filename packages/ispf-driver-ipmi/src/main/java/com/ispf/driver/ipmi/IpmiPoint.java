package com.ispf.driver.ipmi;

import java.util.Locale;

/**
 * Point mapping: {@code power} chassis status or sensor name.
 */
public record IpmiPoint(Kind kind, String sensorName) {

    public enum Kind {
        POWER,
        SENSOR
    }

    public static IpmiPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IPMI point mapping is blank");
        }
        String trimmed = raw.trim();
        if ("power".equalsIgnoreCase(trimmed) || "chassis".equalsIgnoreCase(trimmed)) {
            return new IpmiPoint(Kind.POWER, null);
        }
        return new IpmiPoint(Kind.SENSOR, trimmed);
    }
}
