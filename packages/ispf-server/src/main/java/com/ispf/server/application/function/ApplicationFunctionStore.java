package com.ispf.server.application.function;

import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.application.script.FunctionScriptValidator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApplicationFunctionStore {

    private final JdbcTemplate jdbcTemplate;
    private final FunctionScriptValidator scriptValidator;
    private final String functionsTable;

    public ApplicationFunctionStore(
            JdbcTemplate jdbcTemplate,
            FunctionScriptValidator scriptValidator,
            PlatformSqlCatalog platformSqlCatalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scriptValidator = scriptValidator;
        this.functionsTable = platformSqlCatalog.table("application_functions");
    }

    public void deploy(ApplicationFunctionHandler.DeployedFunction function) {
        if ("script".equals(function.sourceType())) {
            scriptValidator.validate(function.sourceBody());
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM %s
                WHERE app_id = ? AND object_path = ? AND function_name = ? AND version = ?
                """.formatted(functionsTable),
                Integer.class,
                function.appId(),
                function.objectPath(),
                function.functionName(),
                function.version()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET source_type = ?, source_body = ?, input_schema_json = ?,
                        output_schema_json = ?, deployed_at = ?
                    WHERE app_id = ? AND object_path = ? AND function_name = ? AND version = ?
                    """.formatted(functionsTable),
                    function.sourceType(),
                    function.sourceBody(),
                    function.inputSchemaJson(),
                    function.outputSchemaJson(),
                    Timestamp.from(Instant.now()),
                    function.appId(),
                    function.objectPath(),
                    function.functionName(),
                    function.version()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, app_id, object_path, function_name, version,
                    source_type, source_body, input_schema_json, output_schema_json, deployed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(functionsTable),
                function.id(),
                function.appId(),
                function.objectPath(),
                function.functionName(),
                function.version(),
                function.sourceType(),
                function.sourceBody(),
                function.inputSchemaJson(),
                function.outputSchemaJson(),
                Timestamp.from(Instant.now())
        );
    }

    public List<ApplicationFunctionHandler.DeployedFunction> listLatestByApp(String appId) {
        List<ApplicationFunctionHandler.DeployedFunction> rows = jdbcTemplate.query("""
                SELECT id, app_id, object_path, function_name, version,
                       source_type, source_body, input_schema_json, output_schema_json
                FROM %s
                WHERE app_id = ?
                ORDER BY object_path, function_name, deployed_at DESC
                """.formatted(functionsTable),
                this::mapDeployedFunction,
                appId
        );
        Map<String, ApplicationFunctionHandler.DeployedFunction> latest = new LinkedHashMap<>();
        for (ApplicationFunctionHandler.DeployedFunction row : rows) {
            String key = row.objectPath() + "\0" + row.functionName();
            latest.putIfAbsent(key, row);
        }
        return List.copyOf(latest.values());
    }

    public List<ApplicationFunctionHandler.DeployedFunction> listLatestByObjectPath(String objectPath) {
        List<ApplicationFunctionHandler.DeployedFunction> rows = jdbcTemplate.query("""
                SELECT id, app_id, object_path, function_name, version,
                       source_type, source_body, input_schema_json, output_schema_json
                FROM %s
                WHERE object_path = ?
                ORDER BY function_name, deployed_at DESC
                """.formatted(functionsTable),
                this::mapDeployedFunction,
                objectPath
        );
        Map<String, ApplicationFunctionHandler.DeployedFunction> latest = new LinkedHashMap<>();
        for (ApplicationFunctionHandler.DeployedFunction row : rows) {
            latest.putIfAbsent(row.functionName(), row);
        }
        return List.copyOf(latest.values());
    }

    public Optional<ApplicationFunctionHandler.DeployedFunction> findByVersion(
            String appId,
            String objectPath,
            String functionName,
            String version
    ) {
        List<ApplicationFunctionHandler.DeployedFunction> rows = jdbcTemplate.query("""
                SELECT id, app_id, object_path, function_name, version,
                       source_type, source_body, input_schema_json, output_schema_json
                FROM %s
                WHERE app_id = ? AND object_path = ? AND function_name = ? AND version = ?
                """.formatted(functionsTable),
                this::mapDeployedFunction,
                appId,
                objectPath,
                functionName,
                version
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> listVersions(String appId, String objectPath, String functionName) {
        return jdbcTemplate.query("""
                SELECT version, source_type, deployed_at
                FROM %s
                WHERE app_id = ? AND object_path = ? AND function_name = ?
                ORDER BY deployed_at DESC
                """.formatted(functionsTable),
                (rs, rowNum) -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("version", rs.getString("version"));
                    row.put("sourceType", rs.getString("source_type"));
                    row.put("deployedAt", rs.getTimestamp("deployed_at"));
                    return row;
                },
                appId,
                objectPath,
                functionName
        );
    }

    public void activateVersion(String appId, String objectPath, String functionName, String version) {
        int updated = jdbcTemplate.update("""
                UPDATE %s SET deployed_at = ?
                WHERE app_id = ? AND object_path = ? AND function_name = ? AND version = ?
                """.formatted(functionsTable),
                Timestamp.from(Instant.now()),
                appId,
                objectPath,
                functionName,
                version
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Function version not found: " + functionName + "@" + version);
        }
    }

    public Optional<ApplicationFunctionHandler.DeployedFunction> findLatest(String objectPath, String functionName) {
        Optional<ApplicationFunctionHandler.DeployedFunction> direct = findLatestDirect(objectPath, functionName);
        if (direct.isPresent()) {
            return direct;
        }
        return findLatestByTreeFunctionPath(objectPath);
    }

    private Optional<ApplicationFunctionHandler.DeployedFunction> findLatestDirect(String objectPath, String functionName) {
        List<ApplicationFunctionHandler.DeployedFunction> rows = jdbcTemplate.query("""
                SELECT id, app_id, object_path, function_name, version,
                       source_type, source_body, input_schema_json, output_schema_json
                FROM %s
                WHERE object_path = ? AND function_name = ?
                ORDER BY deployed_at DESC
                LIMIT 1
                """.formatted(functionsTable),
                this::mapDeployedFunction,
                objectPath,
                functionName
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<ApplicationFunctionHandler.DeployedFunction> findLatestByTreeFunctionPath(String treeFunctionPath) {
        TreeFunctionRef ref = TreeFunctionRef.parse(treeFunctionPath);
        if (ref == null) {
            return Optional.empty();
        }
        List<ApplicationFunctionHandler.DeployedFunction> rows = jdbcTemplate.query("""
                SELECT id, app_id, object_path, function_name, version,
                       source_type, source_body, input_schema_json, output_schema_json
                FROM %s
                WHERE app_id = ? AND function_name = ?
                ORDER BY deployed_at DESC
                LIMIT 1
                """.formatted(functionsTable),
                this::mapDeployedFunction,
                ref.appId(),
                ref.functionName()
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    record TreeFunctionRef(String appId, String functionName) {
        static TreeFunctionRef parse(String path) {
            if (path == null || !path.contains(".applications.") || !path.contains(".functions.")) {
                return null;
            }
            int appsIdx = path.indexOf(".applications.");
            int funcIdx = path.indexOf(".functions.");
            if (funcIdx <= appsIdx) {
                return null;
            }
            String appId = path.substring(appsIdx + ".applications.".length(), funcIdx);
            String functionName = path.substring(funcIdx + ".functions.".length());
            if (appId.isBlank() || functionName.isBlank() || functionName.contains(".")) {
                return null;
            }
            return new TreeFunctionRef(appId, functionName);
        }
    }

    public int countDeployedVersions() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s".formatted(functionsTable),
                Integer.class
        );
        return count != null ? count : 0;
    }

    public int countDistinctFunctions() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM (
                            SELECT DISTINCT app_id, object_path, function_name
                            FROM %s
                        ) t
                        """.formatted(functionsTable),
                Integer.class
        );
        return count != null ? count : 0;
    }

    private ApplicationFunctionHandler.DeployedFunction mapDeployedFunction(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new ApplicationFunctionHandler.DeployedFunction(
                UUID.fromString(rs.getString("id")),
                rs.getString("app_id"),
                rs.getString("object_path"),
                rs.getString("function_name"),
                rs.getString("version"),
                rs.getString("source_type"),
                rs.getString("source_body"),
                rs.getString("input_schema_json"),
                rs.getString("output_schema_json")
        );
    }
}
