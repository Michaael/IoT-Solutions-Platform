package com.ispf.server.platform.settings;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.object.bus.ObjectChangeEventBus;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformRuntimeSettingsService {

    private final Environment environment;
    private final PlatformRuntimeSettingsStore store;
    private final ObjectChangeProperties objectChangeProperties;
    private final RuntimeTelemetryProperties runtimeTelemetryProperties;
    private final ObjectChangeEventBus objectChangeEventBus;

    public PlatformRuntimeSettingsService(
            Environment environment,
            PlatformRuntimeSettingsStore store,
            ObjectChangeProperties objectChangeProperties,
            RuntimeTelemetryProperties runtimeTelemetryProperties,
            ObjectChangeEventBus objectChangeEventBus
    ) {
        this.environment = environment;
        this.store = store;
        this.objectChangeProperties = objectChangeProperties;
        this.runtimeTelemetryProperties = runtimeTelemetryProperties;
        this.objectChangeEventBus = objectChangeEventBus;
    }

    public PlatformRuntimeSettingsResponse snapshot() {
        Map<String, String> fileOverrides = store.readOverrides();
        Map<String, List<PlatformRuntimeSettingView>> grouped = new LinkedHashMap<>();
        for (PlatformRuntimeSettingDefinition definition : PlatformRuntimeSettingsCatalog.all()) {
            grouped.computeIfAbsent(definition.sectionId(), ignored -> new ArrayList<>())
                    .add(toView(definition, fileOverrides));
        }
        List<PlatformRuntimeSettingsSectionView> sections = new ArrayList<>();
        for (Map.Entry<String, String> entry : PlatformRuntimeSettingsCatalog.SECTION_TITLES.entrySet()) {
            List<PlatformRuntimeSettingView> settings = grouped.get(entry.getKey());
            if (settings == null || settings.isEmpty()) {
                continue;
            }
            sections.add(new PlatformRuntimeSettingsSectionView(entry.getKey(), entry.getValue(), List.copyOf(settings)));
        }
        return new PlatformRuntimeSettingsResponse(store.settingsFile().toString(), sections);
    }

    public PlatformRuntimeSettingsPatchResult patch(PlatformRuntimeSettingsPatchRequest request) {
        Map<String, PlatformRuntimeSettingDefinition> definitions = PlatformRuntimeSettingsCatalog.byId();
        Map<String, String> overrides = new LinkedHashMap<>(store.readOverrides());
        List<String> appliedLive = new ArrayList<>();
        List<String> skippedEnvLocked = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        boolean restartRequired = false;

        for (Map.Entry<String, String> entry : request.values().entrySet()) {
            PlatformRuntimeSettingDefinition definition = definitions.get(entry.getKey());
            if (definition == null) {
                errors.add("Unknown setting: " + entry.getKey());
                continue;
            }
            if (definition.sensitive()) {
                errors.add("Sensitive setting cannot be updated via API: " + entry.getKey());
                continue;
            }
            String normalized;
            try {
                normalized = normalizeValue(definition, entry.getValue());
            } catch (IllegalArgumentException ex) {
                errors.add(entry.getKey() + ": " + ex.getMessage());
                continue;
            }
            overrides.put(definition.propertyKey(), normalized);
            if (definition.hotReloadable()) {
                applyLive(definition, normalized);
                appliedLive.add(entry.getKey());
            } else {
                restartRequired = true;
            }
        }

        store.writeOverrides(overrides);
        return new PlatformRuntimeSettingsPatchResult(restartRequired, appliedLive, skippedEnvLocked, errors);
    }

    private PlatformRuntimeSettingView toView(
            PlatformRuntimeSettingDefinition definition,
            Map<String, String> fileOverrides
    ) {
        String envValue = environmentValue(definition);
        String fileValue = fileOverrides.get(definition.propertyKey());
        boolean overridesEnvironment = envValue != null && fileValue != null;
        String source;
        String rawValue;
        if (fileValue != null) {
            source = overridesEnvironment ? "override" : "file";
            rawValue = fileValue;
        } else if (envValue != null) {
            source = "environment";
            rawValue = envValue;
        } else {
            source = "default";
            rawValue = environment.getProperty(definition.propertyKey(), definition.defaultValue());
        }
        boolean editable = !definition.sensitive();
        String displayValue = definition.sensitive() && rawValue != null && !rawValue.isBlank()
                ? "********"
                : rawValue;
        String displayEnvValue = null;
        if (envValue != null) {
            displayEnvValue = definition.sensitive() && !envValue.isBlank() ? "********" : envValue;
        }
        return new PlatformRuntimeSettingView(
                definition.id(),
                definition.envVar(),
                definition.propertyKey(),
                definition.type().name().toLowerCase(Locale.ROOT),
                displayValue,
                definition.defaultValue(),
                source,
                displayEnvValue,
                overridesEnvironment,
                definition.sensitive(),
                editable,
                definition.hotReloadable(),
                !definition.hotReloadable()
        );
    }

    private String environmentValue(PlatformRuntimeSettingDefinition definition) {
        String value = System.getenv(definition.envVar());
        if (value == null || value.isBlank()) {
            value = environment.getProperty(definition.envVar());
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String normalizeValue(PlatformRuntimeSettingDefinition definition, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
        String trimmed = value.trim();
        return switch (definition.type()) {
            case BOOLEAN -> {
                if ("true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)) {
                    yield "true";
                }
                if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
                    yield "false";
                }
                throw new IllegalArgumentException("expected boolean");
            }
            case INTEGER -> {
                try {
                    yield String.valueOf(Integer.parseInt(trimmed));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("expected integer");
                }
            }
            case DURATION, STRING -> trimmed;
        };
    }

    private void applyLive(PlatformRuntimeSettingDefinition definition, String value) {
        switch (definition.id()) {
            case "runtime-telemetry.coalesce-ms" ->
                    runtimeTelemetryProperties.setCoalesceMs(Long.parseLong(value));
            case "object-change.coalesce-telemetry" ->
                    objectChangeProperties.setCoalesceTelemetryUpdates(Boolean.parseBoolean(value));
            case "object-change.worker-threads" ->
                    objectChangeProperties.setWorkerThreads(Integer.parseInt(value));
            case "object-change.telemetry-workers" ->
                    objectChangeProperties.setTelemetryWorkerThreads(Integer.parseInt(value));
            case "object-change.automation-workers" ->
                    objectChangeProperties.setAutomationWorkerThreads(Integer.parseInt(value));
            case "object-change.elastic-scale-up-threshold" ->
                    objectChangeProperties.setElasticScaleUpQueueThreshold(Integer.parseInt(value));
            case "object-change.elastic-scale-down-steps" ->
                    objectChangeProperties.setElasticScaleDownSteps(Integer.parseInt(value));
            case "object-change.elastic-scale-check-ms" ->
                    objectChangeProperties.setElasticScaleCheckIntervalMs(Integer.parseInt(value));
            case "object-change.worker-threads-min" ->
                    objectChangeProperties.setWorkerThreadsMin(Integer.parseInt(value));
            case "object-change.worker-threads-max" ->
                    objectChangeProperties.setWorkerThreadsMax(Integer.parseInt(value));
            case "object-change.telemetry-workers-min" ->
                    objectChangeProperties.setTelemetryWorkerThreadsMin(Integer.parseInt(value));
            case "object-change.telemetry-workers-max" ->
                    objectChangeProperties.setTelemetryWorkerThreadsMax(Integer.parseInt(value));
            case "object-change.automation-workers-min" ->
                    objectChangeProperties.setAutomationWorkerThreadsMin(Integer.parseInt(value));
            case "object-change.automation-workers-max" ->
                    objectChangeProperties.setAutomationWorkerThreadsMax(Integer.parseInt(value));
            default -> throw new IllegalStateException("Missing hot reload handler for " + definition.id());
        }
        if (definition.id().startsWith("object-change.")) {
            objectChangeEventBus.applyRuntimeTuning();
        }
    }
}
