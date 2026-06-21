package com.ispf.server.automation;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.alert.AlertRule;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.AlertRuleRepository;
import com.ispf.server.persistence.CorrelatorHitRepository;
import com.ispf.server.persistence.EventCorrelatorRepository;
import com.ispf.server.persistence.entity.AlertRuleEntity;
import com.ispf.server.persistence.entity.EventCorrelatorEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AutomationTreeService {

    public static final String ALERT_RULES_ROOT = "root.platform.alert-rules";
    public static final String CORRELATORS_ROOT = "root.platform.correlators";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema INTEGER_VALUE = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final AlertRuleRepository legacyAlertRuleRepository;
    private final EventCorrelatorRepository legacyCorrelatorRepository;
    private final CorrelatorHitRepository correlatorHitRepository;

    public AutomationTreeService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            AlertRuleRepository legacyAlertRuleRepository,
            EventCorrelatorRepository legacyCorrelatorRepository,
            CorrelatorHitRepository correlatorHitRepository
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.legacyAlertRuleRepository = legacyAlertRuleRepository;
        this.legacyCorrelatorRepository = legacyCorrelatorRepository;
        this.correlatorHitRepository = correlatorHitRepository;
    }

    @Transactional
    public void ensurePlatformFolders() {
        ensureNode(ALERT_RULES_ROOT, ObjectType.ALERT_RULES, "Alert Rules", "CEL rules that publish events on variable changes");
        ensureNode(CORRELATORS_ROOT, ObjectType.CORRELATORS, "Correlators", "Event patterns that trigger workflows");
    }

    @Transactional
    public void ensureAlertRuleStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.ALERT) {
            throw new IllegalArgumentException("Not an alert rule object: " + path);
        }
        if (node.getVariable("targetObjectPath").isPresent()) {
            return;
        }
        modelRegistry.findByName("alert-rule-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    @Transactional
    public void ensureCorrelatorStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.CORRELATOR) {
            throw new IllegalArgumentException("Not a correlator object: " + path);
        }
        if (node.getVariable("patternType").isPresent()) {
            return;
        }
        modelRegistry.findByName("correlator-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    @Transactional
    public void migrateLegacyTables() {
        ensurePlatformFolders();
        Map<String, String> correlatorIdMap = new HashMap<>();
        for (AlertRuleEntity entity : legacyAlertRuleRepository.findAll()) {
            String path = rulePathForName(entity.getName());
            if (objectManager.tree().findByPath(path).isEmpty()) {
                createAlertRuleNode(path, entity.getName(), entity.getObjectPath(), entity.getWatchVariable(),
                        entity.getConditionExpr(), entity.getEventName(), entity.getPayloadVariable(),
                        entity.isEnabled(), entity.isEdgeTrigger(), entity.getLastConditionMet());
            }
        }
        legacyAlertRuleRepository.deleteAll();

        for (EventCorrelatorEntity entity : legacyCorrelatorRepository.findAll()) {
            String path = correlatorPathForName(entity.getName());
            correlatorIdMap.put(entity.getId(), path);
            if (objectManager.tree().findByPath(path).isEmpty()) {
                createCorrelatorNode(path, entity.getName(), entity.getObjectPath(),
                        CorrelatorPatternType.valueOf(entity.getPatternType()),
                        entity.getEventName(), entity.getSecondEventName(),
                        entity.getWindowSeconds(), entity.getMinOccurrences(), entity.getCooldownSeconds(),
                        CorrelatorActionType.valueOf(entity.getActionType()), entity.getActionTarget(),
                        entity.isEnabled(), entity.getLastTriggeredAt());
            }
        }
        for (Map.Entry<String, String> entry : correlatorIdMap.entrySet()) {
            correlatorHitRepository.remapCorrelatorId(entry.getKey(), entry.getValue());
        }
        legacyCorrelatorRepository.deleteAll();
    }

    public List<AlertRule> listAlertRules() {
        List<AlertRule> rules = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() == ObjectType.ALERT && node.path().startsWith(ALERT_RULES_ROOT + ".")) {
                rules.add(toAlertRule(node));
            }
        }
        return rules;
    }

    public List<AlertRule> findEnabledAlertRules(String targetObjectPath, String watchVariable) {
        return listAlertRules().stream()
                .filter(rule -> rule.enabled())
                .filter(rule -> targetObjectPath.equals(rule.objectPath()))
                .filter(rule -> watchVariable.equals(rule.watchVariable()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertRule getAlertRule(String path) {
        return toAlertRule(requireAlertRule(path));
    }

    @Transactional
    public AlertRule createAlertRule(
            String name,
            String targetObjectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            boolean enabled,
            boolean edgeTrigger
    ) {
        String path = uniqueRulePath(name);
        createAlertRuleNode(path, name, targetObjectPath, watchVariable, conditionExpr, eventName,
                payloadVariable, enabled, edgeTrigger, null);
        return getAlertRule(path);
    }

    @Transactional
    public AlertRule updateAlertRule(String path, String name, String targetObjectPath, String watchVariable,
            String conditionExpr, String eventName, String payloadVariable, Boolean enabled, Boolean edgeTrigger) {
        PlatformObject node = requireAlertRule(path);
        if (name != null && !name.isBlank()) {
            objectManager.updateInfo(path, name, node.description());
        }
        if (targetObjectPath != null) {
            setString(path, "targetObjectPath", targetObjectPath);
        }
        if (watchVariable != null) {
            setString(path, "watchVariable", watchVariable);
        }
        if (conditionExpr != null) {
            setString(path, "conditionExpr", conditionExpr);
        }
        if (eventName != null) {
            setString(path, "eventName", eventName);
        }
        if (payloadVariable != null) {
            setString(path, "payloadVariable", payloadVariable);
        }
        if (enabled != null) {
            setBoolean(path, "enabled", enabled);
        }
        if (edgeTrigger != null) {
            setBoolean(path, "edgeTrigger", edgeTrigger);
        }
        objectManager.persistNodeTree(path);
        return getAlertRule(path);
    }

    @Transactional
    public void setAlertRuleLastFiredAt(String path, Instant lastFiredAt) {
        if (objectManager.require(path).getVariable("lastFiredAt").isEmpty()) {
            setString(path, "lastFiredAt", "");
        }
        setRuntimeString(path, "lastFiredAt", lastFiredAt != null ? lastFiredAt.toString() : "");
        objectManager.persistNodeTree(path);
    }

    @Transactional
    public void setAlertRuleLastConditionMet(String path, boolean lastConditionMet) {
        setRuntimeBoolean(path, "lastConditionMet", lastConditionMet);
        objectManager.persistNodeTree(path);
    }

    public List<EventCorrelator> listCorrelators() {
        List<EventCorrelator> correlators = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() == ObjectType.CORRELATOR && node.path().startsWith(CORRELATORS_ROOT + ".")) {
                correlators.add(toCorrelator(node));
            }
        }
        return correlators;
    }

    @Transactional(readOnly = true)
    public EventCorrelator getCorrelator(String path) {
        return toCorrelator(requireCorrelator(path));
    }

    public List<EventCorrelator> findEnabledCorrelatorsForEvent(String eventName) {
        return listCorrelators().stream()
                .filter(c -> c.enabled())
                .filter(c -> correlatorWatchesEvent(c, eventName))
                .toList();
    }

    private static boolean correlatorWatchesEvent(EventCorrelator correlator, String eventName) {
        if (eventName.equals(correlator.eventName())) {
            return true;
        }
        if (correlator.secondEventName() == null || correlator.secondEventName().isBlank()) {
            return false;
        }
        if (correlator.patternType() == CorrelatorPatternType.EVENT_CHAIN) {
            for (String part : correlator.secondEventName().split(",")) {
                if (eventName.equals(part.trim())) {
                    return true;
                }
            }
        }
        return eventName.equals(correlator.secondEventName());
    }

    @Transactional
    public EventCorrelator createCorrelator(
            String name,
            String targetObjectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            int windowSeconds,
            int minOccurrences,
            int cooldownSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            boolean enabled
    ) {
        String path = uniqueCorrelatorPath(name);
        createCorrelatorNode(path, name, targetObjectPath, patternType, eventName, secondEventName,
                windowSeconds, minOccurrences, cooldownSeconds, actionType, actionTarget, enabled, null);
        return getCorrelator(path);
    }

    @Transactional
    public EventCorrelator updateCorrelator(
            String path,
            String name,
            String targetObjectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            Integer windowSeconds,
            Integer minOccurrences,
            Integer cooldownSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            Boolean enabled
    ) {
        PlatformObject node = requireCorrelator(path);
        if (name != null && !name.isBlank()) {
            objectManager.updateInfo(path, name, node.description());
        }
        if (targetObjectPath != null) {
            setString(path, "targetObjectPath", targetObjectPath);
        }
        if (patternType != null) {
            setString(path, "patternType", patternType.name());
        }
        if (eventName != null) {
            setString(path, "eventName", eventName);
        }
        if (secondEventName != null) {
            setString(path, "secondEventName", secondEventName);
        }
        if (windowSeconds != null) {
            setInteger(path, "windowSeconds", windowSeconds);
        }
        if (minOccurrences != null) {
            setInteger(path, "minOccurrences", minOccurrences);
        }
        if (cooldownSeconds != null) {
            setInteger(path, "cooldownSeconds", cooldownSeconds);
        }
        if (actionType != null) {
            setString(path, "actionType", actionType.name());
        }
        if (actionTarget != null) {
            setString(path, "actionTarget", actionTarget);
        }
        if (enabled != null) {
            setBoolean(path, "enabled", enabled);
        }
        objectManager.persistNodeTree(path);
        return getCorrelator(path);
    }

    @Transactional
    public void setCorrelatorLastTriggeredAt(String path, Instant triggeredAt) {
        setRuntimeString(path, "lastTriggeredAt", triggeredAt != null ? triggeredAt.toString() : "");
        objectManager.persistNodeTree(path);
    }

    @Transactional
    public void deleteAlertRule(String path) {
        requireAlertRule(path);
        objectManager.delete(path);
    }

    @Transactional
    public void deleteCorrelator(String path) {
        requireCorrelator(path);
        correlatorHitRepository.deleteByCorrelatorId(path);
        objectManager.delete(path);
    }

    @Transactional
    public void ensureDemoAlertRule() {
        String path = rulePathForName("Temperature threshold exceeded");
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        createAlertRule(
                "Temperature threshold exceeded",
                "root.platform.devices.demo-sensor-01",
                "alarmActive",
                "self.alarmActive[\"value\"] == true",
                "thresholdExceeded",
                "temperature",
                true,
                true
        );
    }

    @Transactional
    public void ensureDemoCorrelators() {
        ensureEscalationCorrelator();
        if (objectManager.tree().findByPath(correlatorPathForName("Alarm handler on threshold event")).isPresent()) {
            return;
        }
        createCorrelator(
                "Alarm handler on threshold event",
                "root.platform.devices.demo-sensor-01",
                CorrelatorPatternType.COUNT,
                "thresholdExceeded",
                null,
                0,
                1,
                120,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                true
        );
        createCorrelator(
                "Threshold then alarm active (sequence demo)",
                "root.platform.devices.demo-sensor-01",
                CorrelatorPatternType.SEQUENCE,
                "thresholdExceeded",
                "alarmActive",
                300,
                1,
                120,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                false
        );
    }

    @Transactional
    public void ensureEscalationCorrelator() {
        String path = correlatorPathForName("Recurring threshold escalation");
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        createCorrelator(
                "Recurring threshold escalation",
                "root.platform.devices.demo-sensor-01",
                CorrelatorPatternType.COUNT,
                "thresholdExceeded",
                null,
                300,
                3,
                120,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                true
        );
    }

    private void createAlertRuleNode(
            String path,
            String displayName,
            String targetObjectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            boolean enabled,
            boolean edgeTrigger,
            Boolean lastConditionMet
    ) {
        ensureParent(path);
        String name = leafName(path);
        objectManager.create(parentPath(path), name, ObjectType.ALERT, displayName,
                "CEL alert rule", "alert-rule-v1");
        ensureAlertRuleStructure(path);
        setString(path, "targetObjectPath", targetObjectPath);
        setString(path, "watchVariable", watchVariable);
        setString(path, "conditionExpr", conditionExpr);
        setString(path, "eventName", eventName);
        setString(path, "payloadVariable", payloadVariable != null ? payloadVariable : "");
        setBoolean(path, "enabled", enabled);
        setBoolean(path, "edgeTrigger", edgeTrigger);
        if (lastConditionMet != null) {
            setRuntimeBoolean(path, "lastConditionMet", lastConditionMet);
        }
        objectManager.persistNodeTree(path);
    }

    private void createCorrelatorNode(
            String path,
            String displayName,
            String targetObjectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            int windowSeconds,
            int minOccurrences,
            int cooldownSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            boolean enabled,
            Instant lastTriggeredAt
    ) {
        ensureParent(path);
        String name = leafName(path);
        objectManager.create(parentPath(path), name, ObjectType.CORRELATOR, displayName,
                "Event correlator", "correlator-v1");
        ensureCorrelatorStructure(path);
        setString(path, "targetObjectPath", targetObjectPath != null ? targetObjectPath : "");
        setString(path, "patternType", patternType.name());
        setString(path, "eventName", eventName);
        setString(path, "secondEventName", secondEventName != null ? secondEventName : "");
        setInteger(path, "windowSeconds", windowSeconds);
        setInteger(path, "minOccurrences", minOccurrences);
        setInteger(path, "cooldownSeconds", cooldownSeconds);
        setString(path, "actionType", actionType.name());
        setString(path, "actionTarget", actionTarget);
        setBoolean(path, "enabled", enabled);
        if (lastTriggeredAt != null) {
            setRuntimeString(path, "lastTriggeredAt", lastTriggeredAt.toString());
        }
        objectManager.persistNodeTree(path);
    }

    private AlertRule toAlertRule(PlatformObject node) {
        Instant createdAt = node.createdAt() != null ? node.createdAt() : Instant.now();
        return new AlertRule(
                node.path(),
                node.displayName(),
                readString(node, "targetObjectPath").orElse(""),
                readString(node, "watchVariable").orElse(""),
                readString(node, "conditionExpr").orElse(""),
                readString(node, "eventName").orElse(""),
                blankToNull(readString(node, "payloadVariable").orElse(null)),
                readBoolean(node, "enabled").orElse(true),
                readBoolean(node, "edgeTrigger").orElse(true),
                readInteger(node, "rateLimitSeconds").orElse(0),
                readBoolean(node, "lastConditionMet").orElse(null),
                parseInstant(readString(node, "lastFiredAt").orElse("")),
                createdAt,
                createdAt
        );
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private EventCorrelator toCorrelator(PlatformObject node) {
        Instant createdAt = node.createdAt() != null ? node.createdAt() : Instant.now();
        String patternRaw = readString(node, "patternType").orElse("COUNT");
        String actionRaw = readString(node, "actionType").orElse("RUN_WORKFLOW");
        String lastTriggeredRaw = readString(node, "lastTriggeredAt").orElse("");
        Instant lastTriggered = lastTriggeredRaw.isBlank() ? null : Instant.parse(lastTriggeredRaw);
        return new EventCorrelator(
                node.path(),
                node.displayName(),
                blankToNull(readString(node, "targetObjectPath").orElse(null)),
                CorrelatorPatternType.valueOf(patternRaw),
                readString(node, "eventName").orElse(""),
                blankToNull(readString(node, "secondEventName").orElse(null)),
                readInteger(node, "windowSeconds").orElse(0),
                readInteger(node, "minOccurrences").orElse(1),
                readInteger(node, "cooldownSeconds").orElse(120),
                CorrelatorActionType.valueOf(actionRaw),
                readString(node, "actionTarget").orElse(""),
                readBoolean(node, "enabled").orElse(true),
                lastTriggered,
                createdAt,
                createdAt
        );
    }

    private PlatformObject requireAlertRule(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.ALERT) {
            throw new IllegalArgumentException("Not an alert rule object: " + path);
        }
        return node;
    }

    private PlatformObject requireCorrelator(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.CORRELATOR) {
            throw new IllegalArgumentException("Not a correlator object: " + path);
        }
        return node;
    }

    private String uniqueRulePath(String name) {
        String base = rulePathForName(name);
        if (objectManager.tree().findByPath(base).isEmpty()) {
            return base;
        }
        int suffix = 2;
        while (objectManager.tree().findByPath(base + "-" + suffix).isPresent()) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private String uniqueCorrelatorPath(String name) {
        String base = correlatorPathForName(name);
        if (objectManager.tree().findByPath(base).isEmpty()) {
            return base;
        }
        int suffix = 2;
        while (objectManager.tree().findByPath(base + "-" + suffix).isPresent()) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    public static String rulePathForName(String name) {
        return ALERT_RULES_ROOT + "." + slugify(name);
    }

    public static String correlatorPathForName(String name) {
        return CORRELATORS_ROOT + "." + slugify(name);
    }

    public static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "node";
        }
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            return "node";
        }
        if (Character.isDigit(slug.charAt(0))) {
            slug = "n-" + slug;
        }
        return slug;
    }

    private void ensureNode(String path, ObjectType type, String displayName, String description) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.reconcileType(path, type);
            return;
        }
        objectManager.create(parentPath(path), leafName(path), type, displayName, description, null);
    }

    private void ensureParent(String path) {
        String parent = parentPath(path);
        if (parent.equals(ALERT_RULES_ROOT)) {
            ensurePlatformFolders();
        } else if (parent.equals(CORRELATORS_ROOT)) {
            ensurePlatformFolders();
        }
    }

    private static String parentPath(String path) {
        int lastDot = path.lastIndexOf('.');
        return path.substring(0, lastDot);
    }

    private static String leafName(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_VALUE, Map.of("value", value != null ? value : "")));
    }

    private void setBoolean(String path, String variable, boolean value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(BOOLEAN_VALUE, Map.of("value", value)));
    }

    private void setInteger(String path, String variable, int value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(INTEGER_VALUE, Map.of("value", value)));
    }

    private void setRuntimeBoolean(String path, String variable, boolean value) {
        objectManager.setSystemVariableValue(path, variable, DataRecord.single(BOOLEAN_VALUE, Map.of("value", value)));
    }

    private void setRuntimeString(String path, String variable, String value) {
        objectManager.setSystemVariableValue(path, variable, DataRecord.single(STRING_VALUE, Map.of("value", value != null ? value : "")));
    }

    private static Optional<String> readString(PlatformObject node, String variable) {
        return node.getVariable(variable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString);
    }

    private static Optional<Boolean> readBoolean(PlatformObject node, String variable) {
        return node.getVariable(variable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(value -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value)));
    }

    private static Optional<Integer> readInteger(PlatformObject node, String variable) {
        return node.getVariable(variable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(value -> ((Number) value).intValue());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
