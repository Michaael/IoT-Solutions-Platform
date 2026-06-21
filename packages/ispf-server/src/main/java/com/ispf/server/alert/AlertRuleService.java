package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.event.EventService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertRuleService {

    private final AutomationTreeService automationTreeService;
    private final ObjectManager objectManager;
    private final ExpressionEngine expressionEngine;
    private final EventService eventService;

    public AlertRuleService(
            AutomationTreeService automationTreeService,
            ObjectManager objectManager,
            ExpressionEngine expressionEngine,
            EventService eventService
    ) {
        this.automationTreeService = automationTreeService;
        this.objectManager = objectManager;
        this.expressionEngine = expressionEngine;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<AlertRule> list() {
        return automationTreeService.listAlertRules();
    }

    @Transactional(readOnly = true)
    public AlertRule get(String id) {
        return automationTreeService.getAlertRule(id);
    }

    @Transactional
    public AlertRule create(CreateAlertRuleRequest request) {
        validateRule(request.objectPath(), request.eventName(), request.conditionExpr());
        return automationTreeService.createAlertRule(
                request.name(),
                request.objectPath(),
                request.watchVariable(),
                request.conditionExpr(),
                request.eventName(),
                request.payloadVariable(),
                request.enabled(),
                request.edgeTrigger()
        );
    }

    @Transactional
    public AlertRule update(String id, UpdateAlertRuleRequest request) {
        if (request.objectPath() != null || request.eventName() != null || request.conditionExpr() != null) {
            AlertRule current = get(id);
            validateRule(
                    request.objectPath() != null ? request.objectPath() : current.objectPath(),
                    request.eventName() != null ? request.eventName() : current.eventName(),
                    request.conditionExpr() != null ? request.conditionExpr() : current.conditionExpr()
            );
        }
        return automationTreeService.updateAlertRule(
                id,
                request.name(),
                request.objectPath(),
                request.watchVariable(),
                request.conditionExpr(),
                request.eventName(),
                request.payloadVariable(),
                request.enabled(),
                request.edgeTrigger()
        );
    }

    @Transactional
    public void delete(String id) {
        automationTreeService.deleteAlertRule(id);
    }

    @Transactional
    public void processVariableChange(String objectPath, String variableName) {
        List<AlertRule> rules = automationTreeService.findEnabledAlertRules(objectPath, variableName);
        if (rules.isEmpty()) {
            return;
        }

        PlatformObject node = objectManager.require(objectPath);
        for (AlertRule rule : rules) {
            if (!node.events().containsKey(rule.eventName())) {
                continue;
            }
            boolean conditionMet = evaluateCondition(rule.conditionExpr(), node);
            boolean shouldFire = conditionMet;
            if (rule.edgeTrigger()) {
                shouldFire = conditionMet && !Boolean.TRUE.equals(rule.lastConditionMet());
            }
            automationTreeService.setAlertRuleLastConditionMet(rule.id(), conditionMet);
            if (shouldFire) {
                if (rule.rateLimitSeconds() > 0) {
                    if (rule.lastFiredAt() != null
                            && rule.lastFiredAt().plusSeconds(rule.rateLimitSeconds()).isAfter(java.time.Instant.now())) {
                        continue;
                    }
                    automationTreeService.setAlertRuleLastFiredAt(rule.id(), java.time.Instant.now());
                }
                DataRecord payload = resolvePayload(node, rule.payloadVariable());
                eventService.fire(objectPath, rule.eventName(), payload);
            }
        }
    }

    @Transactional
    public void ensureDemoRules() {
        automationTreeService.ensureDemoAlertRule();
    }

    private DataRecord resolvePayload(PlatformObject node, String payloadVariable) {
        if (payloadVariable == null || payloadVariable.isBlank()) {
            return null;
        }
        return node.getVariable(payloadVariable)
                .flatMap(v -> v.value())
                .orElse(null);
    }

    private boolean evaluateCondition(String expression, PlatformObject node) {
        try {
            Object result = expressionEngine.evaluate(expression, node);
            if (result instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (ExpressionException e) {
            return false;
        }
    }

    private void validateRule(String objectPath, String eventName, String conditionExpr) {
        PlatformObject node = objectManager.require(objectPath);
        if (!node.events().containsKey(eventName)) {
            throw new IllegalArgumentException("Unknown event on object: " + eventName);
        }
        expressionEngine.compile(conditionExpr);
    }

    public record CreateAlertRuleRequest(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            boolean enabled,
            boolean edgeTrigger
    ) {
    }

    public record UpdateAlertRuleRequest(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            Boolean enabled,
            Boolean edgeTrigger
    ) {
    }
}
