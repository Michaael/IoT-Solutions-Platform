package com.ispf.server.object;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.expression.BindingEvaluator;
import com.ispf.server.application.binding.ApplicationSqlBindingService;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends binding evaluation with {@code sqlBinding(appId, bindingName)} expressions on variables.
 */
@Component
@Primary
public class ServerBindingEvaluator extends BindingEvaluator {

    private static final Pattern SQL_BINDING = Pattern.compile(
            "sqlBinding\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)"
    );

    private final ApplicationSqlBindingService sqlBindingService;
    private final BindingEvaluator delegate = new BindingEvaluator();

    public ServerBindingEvaluator(@Lazy ApplicationSqlBindingService sqlBindingService) {
        this.sqlBindingService = sqlBindingService;
    }

    @Override
    public List<String> evaluateBindingsReturningChanges(
            PlatformObject platformObject,
            BindingEvaluationContext context
    ) {
        List<String> changed = new ArrayList<>();
        for (Variable variable : platformObject.variables().values()) {
            variable.bindingExpression().ifPresent(expr -> {
                Matcher matcher = SQL_BINDING.matcher(expr.trim());
                if (!matcher.matches()) {
                    return;
                }
                String appId = matcher.group(1);
                String bindingName = matcher.group(2);
                sqlBindingService.refreshBinding(appId, platformObject.path(), matcher.group(2));
                platformObject.getVariable(variable.name()).ifPresent(updated -> changed.add(variable.name()));
            });
        }
        changed.addAll(delegate.evaluateBindingsReturningChanges(platformObject, context));
        return changed;
    }
}
