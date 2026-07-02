package com.ispf.server.platform.settings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * UI/API overrides in {@code runtime-settings.properties} must beat OS environment variables
 * (Spring env normally wins over imported files).
 */
public final class RuntimeSettingsOverrideEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "ispfRuntimeSettingsOverride";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path settingsFile = resolveSettingsFile(environment);
        if (!Files.isRegularFile(settingsFile)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(settingsFile)) {
            properties.load(reader);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read runtime settings override file: " + settingsFile, ex);
        }
        if (properties.isEmpty()) {
            return;
        }
        Map<String, Object> source = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            source.put(name, properties.getProperty(name));
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, source));
    }

    static Path resolveSettingsFile(ConfigurableEnvironment environment) {
        String dataDir = System.getenv("ISPF_DATA_DIR");
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = environment.getProperty("ispf.license.data-dir", "./data");
        }
        return Path.of(dataDir, "runtime-settings.properties").toAbsolutePath().normalize();
    }
}
