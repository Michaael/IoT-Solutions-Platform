package com.ispf.server.object;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingDependencyParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BindingRulesService {

    private static final DataSchema RULES_SCHEMA = DataSchema.builder("bindingRulesJson")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public BindingRulesService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    public List<BindingRule> listRules(String objectPath) {
        PlatformObject object = objectManager.require(objectPath);
        return readRules(object);
    }

    public List<BindingRule> saveRules(String objectPath, List<BindingRule> rules) {
        List<BindingRule> normalized = normalizeRules(rules);
        for (BindingRule rule : normalized) {
            validateRule(objectPath, rule);
        }
        writeRules(objectPath, normalized);
        return normalized;
    }

    public BindingRule upsertRule(String objectPath, BindingRule rule) {
        validateRule(objectPath, rule);
        List<BindingRule> rules = new ArrayList<>(listRules(objectPath));
        rules.removeIf(existing -> existing.id().equals(rule.id()));
        rules.add(rule);
        return saveRules(objectPath, rules).stream()
                .filter(r -> r.id().equals(rule.id()))
                .findFirst()
                .orElseThrow();
    }

    public void deleteRule(String objectPath, String ruleId) {
        List<BindingRule> rules = listRules(objectPath).stream()
                .filter(rule -> !rule.id().equals(ruleId))
                .toList();
        writeRules(objectPath, rules);
    }

    public static BindingActivators defaultActivators(String objectPath, String expression) {
        var remoteRefs = BindingDependencyParser.parseRefAtDependencies(expression);
        if (!remoteRefs.isEmpty()) {
            return new BindingActivators(false, new ArrayList<>(remoteRefs), null, 0);
        }
        return BindingActivators.onLocalChange();
    }

    private void validateRule(String objectPath, BindingRule rule) {
        PlatformObject object = objectManager.require(objectPath);
        BindingTarget target = rule.target();
        if (target.isVariable()) {
            if (object.getVariable(target.variableName()).isEmpty()) {
                throw new IllegalArgumentException("Unknown target variable: " + target.variableName());
            }
            return;
        }
        if (target.isContext()) {
            if (object.type() != ObjectType.DASHBOARD) {
                throw new IllegalArgumentException("Context target is only allowed on DASHBOARD objects");
            }
            if (target.path() == null || target.path().isBlank()) {
                throw new IllegalArgumentException("Context target.path is required");
            }
            return;
        }
        if (target.isEvent()) {
            if (target.eventName() == null || target.eventName().isBlank()) {
                throw new IllegalArgumentException("Event target.eventName is required");
            }
        }
    }

    private List<BindingRule> readRules(PlatformObject object) {
        return object.getVariable(BindingRulesConstants.RULES_VARIABLE)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(json -> !json.isBlank())
                .map(this::parseRules)
                .orElse(List.of());
    }

    private List<BindingRule> parseRules(String json) {
        try {
            List<BindingRuleDto> dtos = objectMapper.readValue(json, new TypeReference<>() {});
            return dtos.stream().map(BindingRuleDto::toRule).toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid binding rules JSON: " + e.getMessage());
        }
    }

    private void writeRules(String objectPath, List<BindingRule> rules) {
        try {
            List<BindingRuleDto> dtos = rules.stream().map(BindingRuleDto::from).toList();
            String json = objectMapper.writeValueAsString(dtos);
            DataRecord record = DataRecord.single(RULES_SCHEMA, Map.of("value", json));
            objectManager.upsertSystemVariable(objectPath, BindingRulesConstants.RULES_VARIABLE, RULES_SCHEMA, record);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist binding rules", e);
        }
    }

    private static List<BindingRule> normalizeRules(List<BindingRule> rules) {
        List<BindingRule> normalized = new ArrayList<>();
        for (BindingRule rule : rules) {
            BindingActivators activators = normalizeActivators(rule.activators(), rule.expression());
            normalized.add(new BindingRule(
                    rule.id(),
                    rule.name(),
                    rule.enabled(),
                    rule.order(),
                    activators,
                    rule.condition(),
                    rule.expression(),
                    rule.target()
            ));
        }
        normalized.sort((left, right) -> Integer.compare(left.order(), right.order()));
        return normalized;
    }

    private static BindingActivators normalizeActivators(BindingActivators activators, String expression) {
        String onEvent = activators.onEvent();
        if (onEvent != null) {
            onEvent = onEvent.trim();
            if (onEvent.isBlank()) {
                onEvent = null;
            }
        }
        long periodicMs = Math.max(0L, activators.periodicMs());
        if (onEvent != activators.onEvent() || periodicMs != activators.periodicMs()) {
            activators = new BindingActivators(
                    activators.onStartup(),
                    activators.onVariableChange(),
                    onEvent,
                    periodicMs,
                    activators.async(),
                    activators.onContextChange()
            );
        }
        if (activators.onVariableChange().isEmpty()
                && !activators.onStartup()
                && !activators.onContextChange()
                && !activators.hasPeriodicSchedule()
                && (activators.onEvent() == null || activators.onEvent().isBlank())) {
            activators = defaultActivators("", expression);
        }
        return activators;
    }

    private record BindingRuleDto(
            String id,
            String name,
            boolean enabled,
            int order,
            BindingActivatorsDto activators,
            String condition,
            String expression,
            BindingTargetDto target
    ) {
        static BindingRuleDto from(BindingRule rule) {
            return new BindingRuleDto(
                    rule.id(),
                    rule.name(),
                    rule.enabled(),
                    rule.order(),
                    BindingActivatorsDto.from(rule.activators()),
                    rule.condition(),
                    rule.expression(),
                    new BindingTargetDto(
                            rule.target().kind(),
                            rule.target().variableName(),
                            rule.target().field(),
                            rule.target().path(),
                            rule.target().eventName()
                    )
            );
        }

        BindingRule toRule() {
            return new BindingRule(
                    id,
                    name,
                    enabled,
                    order,
                    activators != null ? activators.toActivators() : null,
                    condition,
                    expression,
                    new BindingTarget(
                            target.kind(),
                            target.variableName(),
                            target.field(),
                            target.path(),
                            target.eventName()
                    )
            );
        }
    }

    private record BindingActivatorsDto(
            boolean onStartup,
            List<BindingVariableRefDto> onVariableChange,
            String onEvent,
            long periodicMs,
            Boolean async,
            Boolean onContextChange
    ) {
        static BindingActivatorsDto from(BindingActivators activators) {
            return new BindingActivatorsDto(
                    activators.onStartup(),
                    activators.onVariableChange().stream().map(BindingVariableRefDto::from).toList(),
                    activators.onEvent(),
                    activators.periodicMs(),
                    activators.async(),
                    activators.onContextChange()
            );
        }

        BindingActivators toActivators() {
            List<BindingVariableRef> refs = onVariableChange != null
                    ? onVariableChange.stream().map(BindingVariableRefDto::toRef).toList()
                    : List.of();
            return new BindingActivators(
                    onStartup,
                    refs,
                    onEvent,
                    periodicMs,
                    async != null && async,
                    onContextChange != null && onContextChange
            );
        }
    }

    private record BindingVariableRefDto(String objectPath, String variableName) {
        static BindingVariableRefDto from(BindingVariableRef ref) {
            return new BindingVariableRefDto(ref.objectPath(), ref.variableName());
        }

        BindingVariableRef toRef() {
            return new BindingVariableRef(objectPath, variableName);
        }
    }

    private record BindingTargetDto(
            String kind,
            String variableName,
            String field,
            String path,
            String eventName
    ) {
    }
}
