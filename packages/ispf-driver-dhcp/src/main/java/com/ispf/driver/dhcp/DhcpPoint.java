package com.ispf.driver.dhcp;

import java.util.Locale;

/**
 * Point mapping: {@code serverIp} or {@code lease} status from DHCP DISCOVER probe.
 */
public record DhcpPoint(Kind kind) {

    public enum Kind {
        SERVER_IP,
        LEASE
    }

    public static DhcpPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("DHCP point mapping is blank");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "serverip", "server_ip", "server-ip" -> new DhcpPoint(Kind.SERVER_IP);
            case "lease", "lease_status", "lease-status" -> new DhcpPoint(Kind.LEASE);
            default -> throw new IllegalArgumentException("Unknown DHCP point mapping: " + raw);
        };
    }
}
