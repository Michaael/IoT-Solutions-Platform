package com.ispf.driver.wmi;

/**
 * Point mapping: WQL query returning scalar, optional {@code query:property} suffix.
 */
public record WmiPoint(String query, String property) {

    public static WmiPoint parse(String raw, String defaultQuery) {
        if (raw == null || raw.isBlank()) {
            if (defaultQuery == null || defaultQuery.isBlank()) {
                throw new IllegalArgumentException("WMI point mapping is blank");
            }
            return new WmiPoint(defaultQuery.trim(), null);
        }
        String trimmed = raw.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            String maybeProperty = trimmed.substring(colon + 1).trim();
            String maybeQuery = trimmed.substring(0, colon).trim();
            if (maybeQuery.toUpperCase().contains("SELECT")
                    && maybeProperty.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return new WmiPoint(maybeQuery, maybeProperty);
            }
        }
        return new WmiPoint(trimmed, null);
    }
}
