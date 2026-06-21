package com.ispf.server.platform.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformVersionSupportTest {

    @Test
    void detectsUpdateFromSnapshotToRelease() {
        assertTrue(PlatformVersionSupport.isUpdateAvailable("0.1.0-SNAPSHOT", "0.1.0"));
        assertTrue(PlatformVersionSupport.isUpdateAvailable("0.1.0-SNAPSHOT", "0.1.1"));
    }

    @Test
    void ignoresSameOrOlderRelease() {
        assertFalse(PlatformVersionSupport.isUpdateAvailable("0.1.1", "0.1.1"));
        assertFalse(PlatformVersionSupport.isUpdateAvailable("0.2.0", "0.1.9"));
    }

    @Test
    void normalizesTagPrefix() {
        assertTrue(PlatformVersionSupport.isUpdateAvailable("0.1.0", "v0.1.1"));
    }
}
