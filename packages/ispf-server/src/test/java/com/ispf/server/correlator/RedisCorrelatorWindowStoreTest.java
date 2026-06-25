package com.ispf.server.correlator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCorrelatorWindowStoreTest {

    @Test
    void hitsKeySanitizesObjectPath() {
        assertThat(RedisCorrelatorWindowStore.hitsKey("corr-1", "root.platform.devices.d1"))
                .isEqualTo("ispf:corr:hits:corr-1:root.platform.devices.d1");
        assertThat(RedisCorrelatorWindowStore.hitsKey("corr-1", "root:alt:path"))
                .isEqualTo("ispf:corr:hits:corr-1:root_alt_path");
    }
}
