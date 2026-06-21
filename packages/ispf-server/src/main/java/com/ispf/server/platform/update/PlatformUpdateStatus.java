package com.ispf.server.platform.update;

import java.time.Instant;

public record PlatformUpdateStatus(
        boolean checkEnabled,
        boolean applyEnabled,
        String currentVersion,
        String latestVersion,
        boolean updateAvailable,
        String releaseName,
        String releaseUrl,
        String releaseNotes,
        Instant publishedAt,
        Instant checkedAt,
        String checkError,
        String applyState,
        String applyMessage,
        Instant applyStartedAt
) {
    public static PlatformUpdateStatus idle(
            boolean checkEnabled,
            boolean applyEnabled,
            String currentVersion
    ) {
        return new PlatformUpdateStatus(
                checkEnabled,
                applyEnabled,
                currentVersion,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                "IDLE",
                null,
                null
        );
    }
}
