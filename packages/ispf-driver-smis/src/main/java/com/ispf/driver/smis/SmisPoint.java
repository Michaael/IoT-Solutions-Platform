package com.ispf.driver.smis;

/**
 * Point mapping: {@code className:property} for SMI-S CIM instance properties.
 */
public record SmisPoint(String className, String propertyName) {

    public static SmisPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SMI-S point mapping is blank");
        }
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon >= trimmed.length() - 1) {
            throw new IllegalArgumentException("SMI-S point mapping must be className:property");
        }
        String className = trimmed.substring(0, colon).trim();
        String propertyName = trimmed.substring(colon + 1).trim();
        if (className.isEmpty() || propertyName.isEmpty()) {
            throw new IllegalArgumentException("SMI-S point mapping must be className:property");
        }
        return new SmisPoint(className, propertyName);
    }
}
