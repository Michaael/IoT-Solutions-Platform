package com.ispf.server.application.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationDataService {

    private final ApplicationDataStore store;
    private final ApplicationSchemaSession schemaSession;

    public ApplicationDataService(ApplicationDataStore store, ApplicationSchemaSession schemaSession) {
        this.store = store;
        this.schemaSession = schemaSession;
    }

    public Map<String, Object> register(String appId, String displayName, String tablePrefix, String schemaName) {
        store.registerApp(appId, displayName, tablePrefix, schemaName);
        Map<String, Object> app = store.findApp(appId).orElseThrow();
        return Map.of(
                "appId", appId,
                "displayName", displayName,
                "tablePrefix", app.get("table_prefix") != null ? app.get("table_prefix") : "",
                "schemaName", app.get("schema_name")
        );
    }

    @Transactional
    public Map<String, Object> migrate(String appId, String version, List<MigrationScript> scripts) {
        Map<String, Object> app = ensureApp(appId);
        String schemaName = String.valueOf(app.get("schema_name"));
        String tablePrefix = app.get("table_prefix") != null ? String.valueOf(app.get("table_prefix")) : "";

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (MigrationScript script : scripts) {
            if (store.isMigrationApplied(appId, version, script.id())) {
                skipped.add(script.id());
                continue;
            }
            ApplicationSchemaSupport.validateMigrationSql(script.sql(), tablePrefix);
            schemaSession.runInSchema(schemaName, () -> {
                for (String statement : splitStatements(script.sql())) {
                    if (!statement.isBlank()) {
                        store.executeSql(statement);
                    }
                }
            });
            store.recordMigration(appId, version, script.id(), script.sql());
            applied.add(script.id());
        }

        return Map.of(
                "appId", appId,
                "version", version,
                "applied", applied,
                "skipped", skipped
        );
    }

    @Transactional
    public Map<String, Object> seed(String appId, String profile) {
        Map<String, Object> app = ensureApp(appId);
        String schemaName = String.valueOf(app.get("schema_name"));

        List<SeedScript> seeds = ApplicationSeedProfiles.scripts(profile);
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (SeedScript seed : seeds) {
            if (store.isSeedApplied(appId, profile, seed.id())) {
                skipped.add(seed.id());
                continue;
            }
            schemaSession.runInSchema(schemaName, () -> {
                for (String statement : splitStatements(seed.sql())) {
                    if (!statement.isBlank()) {
                        store.executeSql(statement);
                    }
                }
            });
            store.recordSeed(appId, profile, seed.id());
            applied.add(seed.id());
        }

        return Map.of(
                "appId", appId,
                "profile", profile,
                "applied", applied,
                "skipped", skipped
        );
    }

    public Map<String, Object> status(String appId) {
        Map<String, Object> app = ensureApp(appId);
        List<Map<String, Object>> migrations = store.listMigrations(appId);
        String currentVersion = migrations.isEmpty()
                ? ""
                : String.valueOf(migrations.get(migrations.size() - 1).get("version"));
        return Map.of(
                "appId", appId,
                "schemaName", app.get("schema_name"),
                "currentVersion", currentVersion,
                "applied", migrations
        );
    }

    private Map<String, Object> ensureApp(String appId) {
        return store.findApp(appId).orElseThrow(() -> new IllegalArgumentException("Unknown application: " + appId));
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.endsWith(";")) {
                current.append(line, 0, line.lastIndexOf(';')).append('\n');
                statements.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        if (current.length() > 0) {
            statements.add(current.toString().trim());
        }
        return statements;
    }

    public record MigrationScript(String id, String sql) {
    }

    public record SeedScript(String id, String sql) {
    }
}
