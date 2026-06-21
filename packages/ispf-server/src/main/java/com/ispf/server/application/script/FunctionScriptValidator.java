package com.ispf.server.application.script;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FunctionScriptValidator {

    private static final Set<String> KNOWN_STEPS = Set.of(
            "selectOne",
            "selectMany",
            "exec",
            "setVar",
            "buildRecord",
            "map",
            "when",
            "if",
            "invoke_function",
            "cancel_workflows",
            "failIfNull",
            "failIfNotEquals",
            "return"
    );

    private final ObjectMapper objectMapper;

    public FunctionScriptValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String sourceBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(sourceBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Script body must be valid JSON: " + ex.getMessage());
        }

        JsonNode steps = root.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            throw new IllegalArgumentException("Script must contain a non-empty steps array");
        }
        validateSteps(steps, true);
    }

    private void validateSteps(JsonNode steps, boolean requireReturn) {
        boolean hasReturn = false;
        for (JsonNode step : steps) {
            String type = step.path("type").asText("");
            if (type.isBlank()) {
                throw new IllegalArgumentException("Each script step must have a type");
            }
            if (!KNOWN_STEPS.contains(type)) {
                throw new IllegalArgumentException("Unknown script step type: " + type);
            }
            if ("return".equals(type)) {
                hasReturn = true;
            }
            validateStep(type, step);
        }
        if (requireReturn && !hasReturn) {
            throw new IllegalArgumentException("Script must include at least one return step");
        }
    }

    private void validateStep(String type, JsonNode step) {
        switch (type) {
            case "selectOne", "selectMany" -> require(step, "var", "sql");
            case "exec" -> require(step, "sql");
            case "setVar" -> {
                require(step, "var");
                if (!step.has("value") && !step.has("expression")) {
                    throw new IllegalArgumentException("setVar step requires value or expression");
                }
            }
            case "buildRecord" -> {
                require(step, "var");
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("buildRecord step requires fields object");
                }
            }
            case "map" -> {
                require(step, "var", "source");
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("map step requires fields object");
                }
            }
            case "when", "if" -> {
                if (!step.has("var") && !step.has("notNull")) {
                    throw new IllegalArgumentException(type + " step requires var or notNull");
                }
                JsonNode thenSteps = step.get("then");
                if (thenSteps == null || !thenSteps.isArray() || thenSteps.isEmpty()) {
                    throw new IllegalArgumentException(type + " step requires non-empty then array");
                }
                validateSteps(thenSteps, false);
                JsonNode elseSteps = step.get("else");
                if (elseSteps != null && elseSteps.isArray() && !elseSteps.isEmpty()) {
                    validateSteps(elseSteps, false);
                }
            }
            case "invoke_function" -> require(step, "objectPath", "functionName");
            case "cancel_workflows" -> require(step, "workflowPath");
            case "failIfNull" -> require(step, "var");
            case "failIfNotEquals" -> require(step, "var", "equals");
            case "return" -> {
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("return step requires fields object");
                }
            }
            default -> {
            }
        }
    }

    private static void require(JsonNode step, String... fields) {
        for (String field : fields) {
            if (!step.has(field) || step.get(field).asText("").isBlank()) {
                throw new IllegalArgumentException("Step " + step.path("type").asText() + " requires " + field);
            }
        }
    }
}
