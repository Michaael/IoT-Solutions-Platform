package com.ispf.server.platform.settings;

public record PlatformRuntimeSettingView(
        String id,
        String envVar,
        String propertyKey,
        String type,
        String value,
        String defaultValue,
        String source,
        /** When set, the OS env still defines a baseline that this value overrides. */
        String environmentValue,
        boolean overridesEnvironment,
        boolean sensitive,
        boolean editable,
        boolean hotReloadable,
        boolean restartRequired
) {
}
