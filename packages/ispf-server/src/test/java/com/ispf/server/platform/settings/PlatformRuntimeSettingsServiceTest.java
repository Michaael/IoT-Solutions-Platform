package com.ispf.server.platform.settings;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.object.bus.ObjectChangeEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformRuntimeSettingsServiceTest {

    @Mock
    private PlatformRuntimeSettingsStore store;

    @Mock
    private ObjectChangeEventBus objectChangeEventBus;

    private MockEnvironment environment;
    private PlatformRuntimeSettingsService service;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        service = new PlatformRuntimeSettingsService(
                environment,
                store,
                new ObjectChangeProperties(),
                new RuntimeTelemetryProperties(),
                objectChangeEventBus
        );
    }

    @Test
    void fileOverrideIsEditableEvenWhenPropertyExistsInEnvironment() {
        when(store.settingsFile()).thenReturn(Path.of("./data/runtime-settings.properties"));
        environment.setProperty("ISPF_AI_ENABLED", "true");
        when(store.readOverrides()).thenReturn(Map.of("ispf.ai.enabled", "false"));

        PlatformRuntimeSettingsResponse response = service.snapshot();
        PlatformRuntimeSettingView aiEnabled = findSetting(response, "ai.enabled");

        assertThat(aiEnabled).isNotNull();
        assertThat(aiEnabled.value()).isEqualTo("false");
        assertThat(aiEnabled.source()).isEqualTo("override");
        assertThat(aiEnabled.overridesEnvironment()).isTrue();
        assertThat(aiEnabled.environmentValue()).isEqualTo("true");
        assertThat(aiEnabled.editable()).isTrue();
    }

    @Test
    void patchPersistsOverrideInsteadOfSkippingEnvironmentLockedSetting() {
        when(store.readOverrides()).thenReturn(Map.of());

        PlatformRuntimeSettingsPatchResult result = service.patch(
                new PlatformRuntimeSettingsPatchRequest(Map.of("object-change.elastic-scale-up-threshold", "77"))
        );

        assertThat(result.skippedEnvLocked()).isEmpty();
        assertThat(result.errors()).isEmpty();
        assertThat(result.appliedLive()).containsExactly("object-change.elastic-scale-up-threshold");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(store).writeOverrides(captor.capture());
        assertThat(captor.getValue())
                .containsEntry("ispf.object-change.elastic-scale-up-queue-threshold", "77");
    }

    private static PlatformRuntimeSettingView findSetting(PlatformRuntimeSettingsResponse response, String id) {
        return response.sections().stream()
                .flatMap(section -> section.settings().stream())
                .filter(setting -> setting.id().equals(id))
                .findFirst()
                .orElse(null);
    }
}
