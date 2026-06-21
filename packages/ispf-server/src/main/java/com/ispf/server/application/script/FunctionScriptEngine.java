package com.ispf.server.application.script;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.function.ApplicationFunctionRuntime;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes JSON step scripts for application-deployed functions (REQ-PF-01).
 */
@Component
public class FunctionScriptEngine {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowInstanceCancelService workflowCancelService;

    public FunctionScriptEngine(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WorkflowInstanceCancelService workflowCancelService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.workflowCancelService = workflowCancelService;
    }

    public DataRecord execute(
            String sourceBody,
            DataRecord input,
            DataSchema outputSchema,
            ScriptExecutionContext context
    ) {
        try {
            JsonNode root = objectMapper.readTree(sourceBody);
            JsonNode steps = root.get("steps");
            if (steps == null || !steps.isArray()) {
                throw new IllegalArgumentException("Script must contain steps array");
            }
            Map<String, Object> inputMap = input != null && input.rowCount() > 0
                    ? new LinkedHashMap<>(input.firstRow())
                    : new LinkedHashMap<>();
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("input", inputMap);
            return executeSteps(steps, vars, outputSchema, context, true);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Script execution failed: " + ex.getMessage(), ex);
        }
    }

    private DataRecord executeSteps(
            JsonNode steps,
            Map<String, Object> vars,
            DataSchema outputSchema,
            ScriptExecutionContext context,
            boolean requireReturn
    ) {
        for (JsonNode step : steps) {
            DataRecord early = executeStep(step, vars, outputSchema, context);
            if (early != null) {
                return early;
            }
        }
        if (requireReturn) {
            throw new IllegalStateException("Script must end with return step");
        }
        return null;
    }

    private DataRecord executeStep(
            JsonNode step,
            Map<String, Object> vars,
            DataSchema outputSchema,
            ScriptExecutionContext context
    ) {
        String type = step.path("type").asText();
        return switch (type) {
            case "selectOne" -> {
                String var = step.path("var").asText();
                String sql = step.path("sql").asText();
                List<Object> params = resolveParams(step.get("params"), vars);
                List<Map<String, Object>> rows = normalizeRows(jdbcTemplate.queryForList(sql, params.toArray()));
                vars.put(var, rows.isEmpty() ? null : rows.get(0));
                yield null;
            }
            case "selectMany" -> {
                String var = step.path("var").asText();
                String sql = step.path("sql").asText();
                List<Object> params = resolveParams(step.get("params"), vars);
                vars.put(var, normalizeRows(jdbcTemplate.queryForList(sql, params.toArray())));
                yield null;
            }
            case "exec" -> {
                String sql = step.path("sql").asText();
                List<Object> params = resolveParams(step.get("params"), vars);
                jdbcTemplate.update(sql, params.toArray());
                yield null;
            }
            case "setVar" -> {
                String varName = step.path("var").asText();
                if (step.has("expression")) {
                    vars.put(varName, evaluateSetVarExpression(step.path("expression").asText(), vars));
                } else {
                    vars.put(varName, resolveStepValue(step.get("value"), vars));
                }
                yield null;
            }
            case "buildRecord" -> {
                vars.put(step.path("var").asText(), resolveFields(step.get("fields"), vars));
                yield null;
            }
            case "map" -> {
                vars.put(step.path("var").asText(), mapCollection(step, vars));
                yield null;
            }
            case "when", "if" -> {
                if (evaluateWhenCondition(step, vars)) {
                    JsonNode thenSteps = step.get("then");
                    if (thenSteps != null && thenSteps.isArray()) {
                        DataRecord result = executeSteps(thenSteps, vars, outputSchema, context, false);
                        if (result != null) {
                            yield result;
                        }
                    }
                } else {
                    JsonNode elseSteps = step.get("else");
                    if (elseSteps != null && elseSteps.isArray()) {
                        DataRecord result = executeSteps(elseSteps, vars, outputSchema, context, false);
                        if (result != null) {
                            yield result;
                        }
                    }
                }
                yield null;
            }
            case "invoke_function" -> {
                Map<String, Object> nestedInput = resolveInputObject(step.get("input"), vars);
                DataRecord nestedOutput = context.invokeFunction(
                        step.path("objectPath").asText(),
                        step.path("functionName").asText(),
                        nestedInput
                );
                Map<String, Object> nestedRow = ApplicationFunctionRuntime.rowAsMap(nestedOutput);
                if (!"OK".equals(ApplicationFunctionRuntime.errorCode(nestedRow))) {
                    yield toOutputRecord(outputSchema, nestedRow);
                }
                String var = step.path("var").asText("");
                if (!var.isBlank()) {
                    vars.put(var, nestedRow);
                }
                yield null;
            }
            case "cancel_workflows" -> {
                List<String> statusIn = readStringList(step.get("statusIn"));
                Map<String, Object> detail = resolveInputObject(step.get("detail"), vars);
                String detailJson;
                try {
                    detailJson = objectMapper.writeValueAsString(detail);
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to serialize cancel detail", ex);
                }
                int cancelled = workflowCancelService.cancelByWorkflowPath(
                        step.path("workflowPath").asText(),
                        statusIn,
                        step.path("reason").asText("cancelled"),
                        detailJson,
                        "application-script"
                );
                Map<String, Object> cancelResult = Map.of("cancelledCount", cancelled);
                String var = step.path("var").asText("");
                if (!var.isBlank()) {
                    vars.put(var, cancelResult);
                }
                yield null;
            }
            case "failIfNull" -> {
                String var = step.path("var").asText();
                if (resolvePath(vars, var) == null) {
                    yield wireError(outputSchema, step);
                }
                yield null;
            }
            case "failIfNotEquals" -> {
                Object actual = resolvePath(vars, step.path("var").asText());
                String expected = step.path("equals").asText();
                if (!Objects.equals(String.valueOf(actual), expected)) {
                    yield wireError(outputSchema, step);
                }
                yield null;
            }
            case "return" -> toOutputRecord(outputSchema, resolveFields(step.get("fields"), vars));
            default -> throw new IllegalArgumentException("Unknown script step type: " + type);
        };
    }

    private boolean evaluateWhenCondition(JsonNode step, Map<String, Object> vars) {
        if (step.has("notNull")) {
            String var = step.path("var").asText();
            boolean expectNotNull = step.path("notNull").asBoolean(true);
            boolean isNull = resolvePath(vars, var) == null;
            return expectNotNull != isNull;
        }
        if (step.has("equals")) {
            Object actual = resolvePath(vars, step.path("var").asText());
            String expected = step.path("equals").asText();
            return Objects.equals(String.valueOf(actual), expected);
        }
        if (step.has("notEquals")) {
            Object actual = resolvePath(vars, step.path("var").asText());
            String expected = step.path("notEquals").asText();
            return !Objects.equals(String.valueOf(actual), expected);
        }
        if (step.has("gt") || step.has("lt") || step.has("gte") || step.has("lte")) {
            Object actual = resolvePath(vars, step.path("var").asText());
            double left = toNumber(actual);
            double right = toNumber(step.has("gt") ? step.path("gt").asText()
                    : step.has("lt") ? step.path("lt").asText()
                    : step.has("gte") ? step.path("gte").asText()
                    : step.path("lte").asText());
            if (step.has("gt")) {
                return left > right;
            }
            if (step.has("lt")) {
                return left < right;
            }
            if (step.has("gte")) {
                return left >= right;
            }
            return left <= right;
        }
        if (step.has("var")) {
            Object value = resolvePath(vars, step.path("var").asText());
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.doubleValue() != 0;
            }
            return value != null && !"false".equalsIgnoreCase(String.valueOf(value)) && !String.valueOf(value).isBlank();
        }
        return false;
    }

    private static DataRecord toOutputRecord(DataSchema outputSchema, Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (FieldDefinition field : outputSchema.fields()) {
            Object value = row.get(field.name());
            if (value == null && !field.nullable()) {
                value = defaultValue(field.type());
            } else {
                value = coerceFieldValue(field, value);
            }
            normalized.put(field.name(), value);
        }
        return DataRecord.single(outputSchema, normalized);
    }

    private static Object coerceFieldValue(FieldDefinition field, Object value) {
        if (value == null || field.type() != FieldType.BOOLEAN) {
            return value;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        return value;
    }

    private static Object defaultValue(com.ispf.core.model.FieldType type) {
        return switch (type) {
            case BOOLEAN -> false;
            case INTEGER, LONG -> 0L;
            case DOUBLE -> 0.0;
            case RECORD_LIST -> List.of();
            default -> "";
        };
    }

    private static DataRecord wireError(DataSchema outputSchema, JsonNode step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("error_code", step.path("error_code").asText(step.path("code").asText("ERROR")));
        row.put("error_message", step.path("error_message").asText(step.path("message").asText("Script validation failed")));
        return toOutputRecord(outputSchema, row);
    }

    private List<Map<String, Object>> mapCollection(JsonNode step, Map<String, Object> vars) {
        Object source = resolveStepValue(step.get("source"), vars);
        if (!(source instanceof List<?> list)) {
            throw new IllegalArgumentException("map source must be a list");
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object element : list) {
            Map<String, Object> scoped = new LinkedHashMap<>(vars);
            if (element instanceof Map<?, ?> row) {
                scoped.put("item", normalizeRow(toStringKeyMap(row)));
            } else {
                scoped.put("item", element);
            }
            mapped.add(resolveFields(step.get("fields"), scoped));
        }
        return mapped;
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    private Map<String, Object> resolveFields(JsonNode fieldsNode, Map<String, Object> vars) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (fieldsNode == null || !fieldsNode.isObject()) {
            return row;
        }
        fieldsNode.forEachEntry((key, value) ->
                row.put(key, resolveStepValue(value, vars))
        );
        return row;
    }

    private Map<String, Object> resolveInputObject(JsonNode inputNode, Map<String, Object> vars) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (inputNode == null || !inputNode.isObject()) {
            return input;
        }
        inputNode.forEachEntry((key, value) ->
                input.put(key, resolveStepValue(value, vars))
        );
        return input;
    }

    private Object resolveStepValue(JsonNode valueNode, Map<String, Object> vars) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isObject() || valueNode.isArray()) {
            return objectMapper.convertValue(valueNode, Object.class);
        }
        String text = valueNode.asText();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        if (text.matches("-?\\d+")) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (text.matches("-?\\d+(\\.\\d+)?")) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return resolveValue(text, vars);
    }

    private List<Object> resolveParams(JsonNode paramsNode, Map<String, Object> vars) {
        List<Object> params = new ArrayList<>();
        if (paramsNode == null || !paramsNode.isArray()) {
            return params;
        }
        for (JsonNode node : paramsNode) {
            params.add(resolveStepValue(node, vars));
        }
        return params;
    }

    private Object resolveValue(String token, Map<String, Object> vars) {
        if (token == null) {
            return null;
        }
        if (token.startsWith("${") && token.endsWith("}")) {
            return resolvePath(vars, token.substring(2, token.length() - 1));
        }
        if (token.startsWith("$")) {
            return resolvePath(vars, token.substring(1));
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    private Object resolvePath(Map<String, Object> vars, String path) {
        String[] parts = path.split("\\.");
        Object current = vars;
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private static List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        return rows.stream().map(FunctionScriptEngine::normalizeRow).toList();
    }

    private static List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
    }

    private static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key.toLowerCase(), value));
        return normalized;
    }

    private static final Pattern ADD_EXPRESSION = Pattern.compile("^(.+?)\\s*\\+\\s*(.+)$");
    private static final Pattern COMPARE_EXPRESSION = Pattern.compile("^(.+?)\\s*(==|!=|<=|>=|<|>)\\s*(.+)$");

    private Object evaluateSetVarExpression(String expression, Map<String, Object> vars) {
        String trimmed = expression.trim();
        Matcher compare = COMPARE_EXPRESSION.matcher(trimmed);
        if (compare.matches()) {
            Object left = resolveExpressionOperand(compare.group(1).trim(), vars);
            Object right = resolveExpressionOperand(compare.group(3).trim(), vars);
            return evaluateComparison(left, right, compare.group(2));
        }
        Matcher add = ADD_EXPRESSION.matcher(trimmed);
        if (add.matches()) {
            Object left = resolveExpressionOperand(add.group(1).trim(), vars);
            Object right = resolveExpressionOperand(add.group(2).trim(), vars);
            if (left instanceof String || right instanceof String) {
                return String.valueOf(left) + String.valueOf(right);
            }
            return toNumber(left) + toNumber(right);
        }
        return resolveValue(trimmed, vars);
    }

    private static boolean evaluateComparison(Object left, Object right, String operator) {
        if ("==".equals(operator)) {
            return Objects.equals(String.valueOf(left), String.valueOf(right));
        }
        if ("!=".equals(operator)) {
            return !Objects.equals(String.valueOf(left), String.valueOf(right));
        }
        double leftNum = toNumber(left);
        double rightNum = toNumber(right);
        return switch (operator) {
            case "<" -> leftNum < rightNum;
            case ">" -> leftNum > rightNum;
            case "<=" -> leftNum <= rightNum;
            case ">=" -> leftNum >= rightNum;
            default -> false;
        };
    }

    private Object resolveExpressionOperand(String token, Map<String, Object> vars) {
        if (token.matches("-?\\d+(\\.\\d+)?")) {
            return Double.parseDouble(token);
        }
        return resolveValue(token, vars);
    }

    private static double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
