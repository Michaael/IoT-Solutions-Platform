package com.ispf.server.application.function;

import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.application.script.FunctionScriptValidator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

    public Optional<ApplicationFunctionHandler.DeployedFunction> findLatest(String objectPath, String functionName) {
        List<ApplicationFunctionHandler.DeployedFunction> rows = jdbcTemplate.query("""
                SELECT id, app_id, object_path, function_name, version,
                       source_type, source_body, input_schema_json, output_schema_json
                FROM %s
                WHERE object_path = ? AND function_name = ?
                ORDER BY deployed_at DESC
                LIMIT 1
                """.formatted(functionsTable),
                (rs, rowNum) -> new ApplicationFunctionHandler.DeployedFunction(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("app_id"),
                        rs.getString("object_path"),
                        rs.getString("function_name"),
                        rs.getString("version"),
                        rs.getString("source_type"),
                        rs.getString("source_body"),
                        rs.getString("input_schema_json"),
                        rs.getString("output_schema_json")
                ),
                objectPath,
                functionName
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
