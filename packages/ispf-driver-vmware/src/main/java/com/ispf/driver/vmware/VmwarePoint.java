package com.ispf.driver.vmware;

/**
 * Point mapping: vSphere property path (e.g. {@code about.version}) or {@code connected}.
 */
public record VmwarePoint(String vsphereProperty, boolean connectedStatus) {

    public static VmwarePoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("VMware point mapping is blank");
        }
        String trimmed = raw.trim();
        if ("connected".equalsIgnoreCase(trimmed)) {
            return new VmwarePoint(null, true);
        }
        return new VmwarePoint(trimmed, false);
    }

    public boolean isConnectedPoint() {
        return connectedStatus;
    }

    public String propertyPath() {
        if (connectedStatus) {
            return "connected";
        }
        return vsphereProperty;
    }
}
