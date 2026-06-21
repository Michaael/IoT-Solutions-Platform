package com.ispf.server.platform.update;

import org.springframework.boot.info.BuildProperties;

import java.util.Optional;

public final class PlatformVersionSupport {

    private PlatformVersionSupport() {
    }

    public static String currentVersion(Optional<BuildProperties> buildProperties) {
        return buildProperties
                .map(BuildProperties::getVersion)
                .filter(version -> !version.isBlank())
                .orElse("0.1.0-SNAPSHOT");
    }

    public static String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return "0.0.0";
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int plus = normalized.indexOf('+');
        if (plus >= 0) {
            normalized = normalized.substring(0, plus);
        }
        return normalized;
    }

    public static int compare(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("[.-]");
        String[] rightParts = normalizeVersion(right).split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = partValue(leftParts, index);
            int rightValue = partValue(rightParts, index);
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    public static boolean isSnapshot(String version) {
        return normalizeVersion(version).toUpperCase().contains("SNAPSHOT");
    }

    public static boolean isUpdateAvailable(String currentVersion, String latestVersion) {
        if (latestVersion == null || latestVersion.isBlank()) {
            return false;
        }
        if (isSnapshot(currentVersion)) {
            String base = normalizeVersion(currentVersion).replaceAll("(?i)-SNAPSHOT$", "");
            return compare(base, latestVersion) <= 0;
        }
        return compare(currentVersion, latestVersion) < 0;
    }

    private static int partValue(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String part = parts[index];
        if (part.isBlank()) {
            return 0;
        }
        StringBuilder digits = new StringBuilder();
        for (int charIndex = 0; charIndex < part.length(); charIndex++) {
            char value = part.charAt(charIndex);
            if (Character.isDigit(value)) {
                digits.append(value);
            } else {
                break;
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
