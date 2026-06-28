package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.expression.BindingExpressionEvaluator;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class BindingRuleEngine {

    private static final int MAX_PASSES = 8;
    private static final int MAX_DEPTH = 16;
    private static final ThreadLocal<Integer> ACTIVATION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;
    private final BindingExpressionEvaluator expressionEvaluator;
    private final ExpressionEngine expressionEngine;
    private final BindingEvaluationContext evaluationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final BindingRuleAsyncExecutor asyncExecutor;
    private final BindingInvokeAuditService bindingAuditService;
    private final ObjectEntityMapper entityMapper;

    public BindingRuleEngine(
            ObjectManager objectManager,
            BindingRulesService bindingRulesService,
            BindingExpressionEvaluator expressionEvaluator,
            ExpressionEngine expressionEngine,
            BindingEvaluationContext evaluationContext,
            ApplicationEventPublisher eventPublisher,
            BindingRuleAsyncExecutor asyncExecutor,
            BindingInvokeAuditService bindingAuditService,
            ObjectEntityMapper entityMapper
    ) {
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
        this.expressionEvaluator = expressionEvaluator;
        this.expressionEngine = expressionEngine;
        this.evaluationContext = evaluationContext;
        this.eventPublisher = eventPublisher;
        this.asyncExecutor = asyncExecutor;
        this.bindingAuditService = bindingAuditService;
        this.entityMapper = entityMapper;
    }

    public void onStartup(String objectPath) {
        runRules(objectPath, Trigger.startup(), false);
    }

    public void onVariableChanged(String ruleObjectPath, String changedObjectPath, String changedVariable) {
        if (BindingRulesConstants.isReservedVariable(changedVariable)
                || BindingStateVariables.BINDING_STATE.equals(changedVariable)) {
            return;
        }
        runRules(ruleObjectPath, Trigger.variableChange(changedObjectPath, changedVariable), true);
    }

    public void onEvent(String objectPath, String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return;
        }
        runRules(objectPath, Trigger.event(eventName), false);
    }

    public void onPeriodic(String objectPath, String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return;
        }
        runRules(objectPath, Trigger.periodic(ruleId), false);
    }

    public void runRulesForObject(String objectPath) {
        runRules(objectPath, Trigger.manual(), false);
    }

    private void runRules(String objectPath, Trigger trigger, boolean guardDepth) {
        if (guardDepth) {
            int depth = ACTIVATION_DEPTH.get();
            if (depth >= MAX_DEPTH) {
                return;
            }
            ACTIVATION_DEPTH.set(depth + 1);
        }
        try {
            Set<String> changedVariables = new LinkedHashSet<>();
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                boolean changedInPass = evaluatePass(objectPath, trigger, changedVariables);
                if (!changedInPass) {
                    break;
                }
                trigger = Trigger.manual();
            }
            for (String variableName : changedVariables) {
                if (!BindingRulesConstants.isReservedVariable(variableName)) {
                    eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(objectPath, variableName));
                }
            }
        } finally {
            if (guardDepth) {
                ACTIVATION_DEPTH.set(Math.max(0, ACTIVATION_DEPTH.get() - 1));
            }
        }
    }

    private boolean evaluatePass(String objectPath, Trigger trigger, Set<String> changedVariables) {
        PlatformObject object = objectManager.require(objectPath);
        List<BindingRule> rules = bindingRulesService.listRules(objectPath).stream()
                .filter(BindingRule::enabled)
                .sorted(Comparator.comparingInt(BindingRule::order))
                .toList();
        boolean changed = false;
        for (BindingRule rule : rules) {
            if (!matchesTrigger(objectPath, rule, trigger)) {
                continue;
            }
            if (rule.activators().async()) {
                scheduleAsyncRule(objectPath, rule, trigger);
                continue;
            }
            if (evaluateRule(object, rule, changedVariables, trigger)) {
                changed = true;
            }
        }
        return changed;
    }

    private void scheduleAsyncRule(String objectPath, BindingRule rule, Trigger trigger) {
        asyncExecutor.schedule(objectPath, rule, () -> runSingleAsyncRule(objectPath, rule, trigger));
    }

    private void runSingleAsyncRule(String objectPath, BindingRule rule, Trigger trigger) {
        if (!matchesTrigger(objectPath, rule, trigger)) {
            return;
        }
        PlatformObject object = objectManager.require(objectPath);
        Set<String> changedVariables = new LinkedHashSet<>();
        if (evaluateRule(object, rule, changedVariables, trigger)) {
            for (String variableName : changedVariables) {
                if (!BindingRulesConstants.isReservedVariable(variableName)) {
                    eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(objectPath, variableName));
                }
            }
        }
    }

    private boolean matchesTrigger(String objectPath, BindingRule rule, Trigger trigger) {
        return switch (trigger.kind()) {
            case STARTUP -> rule.activators().onStartup();
            case MANUAL -> true;
            case VARIABLE_CHANGE -> rule.activators().matchesVariableChange(
                    objectPath,
                    trigger.changedObjectPath(),
                    trigger.changedVariable()
            );
            case EVENT -> rule.activators().matchesEvent(trigger.eventName());
            case PERIODIC -> rule.id().equals(trigger.ruleId()) && rule.activators().hasPeriodicSchedule();
        };
    }

    private boolean evaluateRule(
            PlatformObject object,
            BindingRule rule,
            Set<String> changedVariables,
            Trigger trigger
    ) {
        long start = System.nanoTime();
        boolean success = true;
        boolean changed = false;
        String error = null;
        DataRecord previous = null;
        DataRecord next = null;
        try {
            if (!conditionPasses(object, rule.condition())) {
                return false;
            }
            Variable target = object.getVariable(rule.target().variableName()).orElse(null);
            if (target == null) {
                success = false;
                error = "Unknown target variable: " + rule.target().variableName();
                return false;
            }
            Optional<DataRecord> computed = expressionEvaluator.evaluate(
                    object,
                    rule.target().variableName(),
                    rule.expression(),
                    target.schema(),
                    evaluationContext
            );
            if (computed.isEmpty()) {
                success = false;
                error = "Expression returned empty";
                return false;
            }
            DataRecord nextValue = computed.get();
            previous = target.value().orElse(null);
            next = nextValue;
            if (!BindingExpressionEvaluator.recordsEqual(previous, nextValue)) {
                target.setComputedValue(nextValue);
                objectManager.persistBindingRuleTarget(object.path(), target);
                changedVariables.add(rule.target().variableName());
                changed = true;
                return true;
            }
            return false;
        } catch (RuntimeException ex) {
            success = false;
            error = ex.getMessage();
            throw ex;
        } finally {
            bindingAuditService.recordCel(
                    object.path(),
                    rule,
                    trigger.kind().name(),
                    success,
                    changed,
                    error,
                    System.nanoTime() - start,
                    entityMapper.auditDiff(previous, next)
            );
        }
    }

    private boolean conditionPasses(PlatformObject object, String condition) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        try {
            Object result = expressionEngine.evaluate(condition, object);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (ExpressionException ignored) {
            return false;
        }
    }

    private record Trigger(
            Kind kind,
            String changedObjectPath,
            String changedVariable,
            String eventName,
            String ruleId
    ) {
        enum Kind { STARTUP, VARIABLE_CHANGE, MANUAL, EVENT, PERIODIC }

        static Trigger startup() {
            return new Trigger(Kind.STARTUP, null, null, null, null);
        }

        static Trigger manual() {
            return new Trigger(Kind.MANUAL, null, null, null, null);
        }

        static Trigger variableChange(String changedObjectPath, String changedVariable) {
            return new Trigger(Kind.VARIABLE_CHANGE, changedObjectPath, changedVariable, null, null);
        }

        static Trigger event(String eventName) {
            return new Trigger(Kind.EVENT, null, null, eventName, null);
        }

        static Trigger periodic(String ruleId) {
            return new Trigger(Kind.PERIODIC, null, null, null, ruleId);
        }
    }
}
