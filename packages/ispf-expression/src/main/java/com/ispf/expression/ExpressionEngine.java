package com.ispf.expression;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Expression engine based on Google CEL.
 */
public class ExpressionEngine {

    private final CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("self", SimpleType.DYN)
            .addVar("parent", SimpleType.DYN)
            .addVar("context", SimpleType.DYN)
            .build();

    private final CelCompiler payloadCompiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("payload", SimpleType.DYN)
            .build();

    private final CelRuntime runtime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

    private final ConcurrentHashMap<String, CompiledExpression> compiledCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PayloadCompiledExpression> payloadCompiledCache = new ConcurrentHashMap<>();

    public CompiledExpression compile(String expression) {
        return compiledCache.computeIfAbsent(expression, this::compileUncached);
    }

    private CompiledExpression compileUncached(String expression) {
        try {
            CelAbstractSyntaxTree ast = compiler.compile(expression).getAst();
            return new CompiledExpression(expression, ast, runtime);
        } catch (CelValidationException e) {
            throw new ExpressionException("Invalid expression: " + expression, e);
        }
    }

    public Object evaluate(String expression, PlatformObject platformObject) {
        return compile(expression).evaluate(platformObject, null, Map.of());
    }

    public Object evaluate(String expression, PlatformObject platformObject, Map<String, Object> context) {
        return compile(expression).evaluate(platformObject, null, context != null ? context : Map.of());
    }

    /**
     * Evaluates an alert condition, ensuring {@code watchVariable} is present in {@code self}
     * even when the async handler runs slightly after the triggering telemetry write.
     */
    public Object evaluateAlertCondition(String expression, PlatformObject platformObject, String watchVariable) {
        return compile(expression).evaluate(platformObject, watchVariable);
    }

    public Object evaluateWithPayload(String expression, Map<String, Object> payload) {
        return payloadCompiledCache.computeIfAbsent(expression, this::compilePayloadUncached)
                .evaluate(payload);
    }

    private PayloadCompiledExpression compilePayloadUncached(String expression) {
        try {
            CelAbstractSyntaxTree ast = payloadCompiler.compile(expression).getAst();
            return new PayloadCompiledExpression(expression, ast, runtime);
        } catch (CelValidationException e) {
            throw new ExpressionException("Invalid expression: " + expression, e);
        }
    }

    public static final class CompiledExpression {
        private final String source;
        private final CelAbstractSyntaxTree ast;
        private final CelRuntime runtime;
        private volatile CelRuntime.Program program;

        CompiledExpression(String source, CelAbstractSyntaxTree ast, CelRuntime runtime) {
            this.source = source;
            this.ast = ast;
            this.runtime = runtime;
        }

        public String source() {
            return source;
        }

        public Object evaluate(PlatformObject platformObject) {
            return evaluate(platformObject, null, Map.of());
        }

        Object evaluate(PlatformObject platformObject, String watchVariable) {
            return evaluate(platformObject, watchVariable, Map.of());
        }

        Object evaluate(PlatformObject platformObject, String watchVariable, Map<String, Object> context) {
            try {
                return program().eval(buildBindings(platformObject, watchVariable, context));
            } catch (Exception e) {
                throw new ExpressionException("Evaluation failed: " + source, e);
            }
        }

        private CelRuntime.Program program() throws Exception {
            CelRuntime.Program cached = program;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (program == null) {
                    program = runtime.createProgram(ast);
                }
                return program;
            }
        }
    }

    private static final class PayloadCompiledExpression {
        private final String source;
        private final CelAbstractSyntaxTree ast;
        private final CelRuntime runtime;
        private volatile CelRuntime.Program program;

        PayloadCompiledExpression(String source, CelAbstractSyntaxTree ast, CelRuntime runtime) {
            this.source = source;
            this.ast = ast;
            this.runtime = runtime;
        }

        Object evaluate(Map<String, Object> payload) {
            try {
                CelRuntime.Program program = program();
                Map<String, Object> bindings = new HashMap<>();
                bindings.put("payload", payload != null ? payload : Map.of());
                return program.eval(bindings);
            } catch (Exception e) {
                throw new ExpressionException("Evaluation failed: " + source, e);
            }
        }

        private CelRuntime.Program program() throws Exception {
            CelRuntime.Program cached = program;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                if (program == null) {
                    program = runtime.createProgram(ast);
                }
                return program;
            }
        }
    }

    private static Map<String, Object> buildBindings(PlatformObject platformObject) {
        return buildBindings(platformObject, null, Map.of());
    }

    private static Map<String, Object> buildBindings(PlatformObject platformObject, String watchVariable) {
        return buildBindings(platformObject, watchVariable, Map.of());
    }

    private static Map<String, Object> buildBindings(
            PlatformObject platformObject,
            String watchVariable,
            Map<String, Object> context
    ) {
        Map<String, Object> self = new HashMap<>();
        for (Variable variable : platformObject.variables().values()) {
            Optional<DataRecord> value = variable.value();
            if (value.isPresent() && value.get().rowCount() > 0) {
                self.put(variable.name(), normalizeRow(value.get().firstRow()));
            }
        }
        if (watchVariable != null && !watchVariable.isBlank()) {
            platformObject.getVariable(watchVariable)
                    .flatMap(Variable::value)
                    .filter(record -> record.rowCount() > 0)
                    .ifPresent(record -> self.put(watchVariable, normalizeRow(record.firstRow())));
        }
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("self", self);
        bindings.put("parent", Map.of());
        bindings.put("context", context != null ? context : Map.of());
        return bindings;
    }

    private static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = HashMap.newHashMap(row.size());
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            normalized.put(entry.getKey(), normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value;
    }
}
