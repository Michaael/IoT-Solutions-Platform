package com.ispf.driver.cwmp;

/**
 * Point mapping: TR-069 parameter name from last Inform response, or {@code connected}.
 */
public record CwmpPoint(String tr069Parameter, boolean connectedStatus) {

    public static CwmpPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("CWMP point mapping is blank");
        }
        String trimmed = raw.trim();
        if ("connected".equalsIgnoreCase(trimmed)) {
            return new CwmpPoint(null, true);
        }
        return new CwmpPoint(trimmed, false);
    }

    public boolean isConnectedPoint() {
        return connectedStatus;
    }

    public String parameterName() {
        if (connectedStatus) {
            return "connected";
        }
        return tr069Parameter;
    }
}
