package com.ispf.server.automation;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.SystemObjectStructureService;
import com.ispf.server.alert.AlertRule;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.persistence.AlertRuleRepository;
import com.ispf.server.correlator.CorrelatorWindowStore;
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
    private final SystemObjectStructureService structureService;
    private final AlertRuleRepository legacyAlertRuleRepository;
    private final EventCorrelatorRepository legacyCorrelatorRepository;
    private final CorrelatorWindowStore correlatorWindowStore;
    private final AutomationRuleIndex ruleIndex;
    private final AutomationIndexRefresh indexRefresh;

    public AutomationTreeService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService,
            AlertRuleRepository legacyAlertRuleRepository,
            EventCorrelatorRepository legacyCorrelatorRepository,
            CorrelatorWindowStore correlatorWindowStore,
            AutomationRuleIndex ruleIndex,
            AutomationIndexRefresh indexRefresh
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
        this.legacyAlertRuleRepository = legacyAlertRuleRepository;
        this.legacyCorrelatorRepository = legacyCorrelatorRepository;
        this.correlatorWindowStore = correlatorWindowStore;
        this.ruleIndex = ruleIndex;
        this.indexRefresh = indexRefresh;
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
        structureService.ensureAlertRuleStructure(path);
    }

    @Transactional
    public void ensureCorrelatorStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.CORRELATOR) {
            throw new IllegalArgumentException("Not a correlator object: " + path);
        }
        structureService.ensureCorrelatorStructure(path);
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
                        entity.isEnabled(), entity.isEdgeTrigger(), 0, false, entity.getLastConditionMet());
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
                        0,
                        CorrelatorActionType.valueOf(entity.getActionType()), entity.getActionTarget(),
                        "",
                        entity.isEnabled(), entity.getLastTriggeredAt());
            }
        }
        for (Map.Entry<String, String> entry : correlatorIdMap.entrySet()) {
            correlatorWindowStore.remapCorrelatorId(entry.getKey(), entry.getValue());
        }
        legacyCorrelatorRepository.deleteAll();
        indexRefresh.scheduleFullRebuild();
    }

    public List<AlertRule> listAlertRules() {
        List<AlertRule> rules = new ArrayList<>();
        collectAlertRules(objectManager.tree().childrenOf(ALERT_RULES_ROOT), rules);
        return rules;
    }

    private void collectAlertRules(List<PlatformObject> nodes, List<AlertRule> rules) {
        for (PlatformObject node : nodes) {
            if (node.type() == ObjectType.ALERT) {
                rules.add(toAlertRule(node));
            } else {
                collectAlertRules(objectManager.tree().childrenOf(node.path()), rules);
            }
        }
    }

    public List<AlertRule> findEnabledAlertRules(String targetObjectPath, String watchVariable) {
        return ruleIndex.findAlertRules(targetObjectPath, watchVariable);
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
            boolean edgeTrigger,
            int delaySeconds,
            boolean sustainWhileTrue
    ) {
        String path = uniqueRulePath(name);
        createAlertRuleNode(path, name, targetObjectPath, watchVariable, conditionExpr, eventName,
                payloadVariable, enabled, edgeTrigger, delaySeconds, sustainWhileTrue, null);
        AlertRule rule = getAlertRule(path);
        indexRefresh.afterAlertRuleCreated(rule);
        return rule;
    }

    @Transactional
    public AlertRule updateAlertRule(String path, String name, String targetObjectPath, String watchVariable,
            String conditionExpr, String eventName, String payloadVariable, Boolean enabled, Boolean edgeTrigger,
            Integer delaySeconds, Boolean sustainWhileTrue) {
        AlertRule previous = getAlertRule(path);
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
        if (delaySeconds != null) {
            setInteger(path, "delaySeconds", delaySeconds);
        }
        if (sustainWhileTrue != null) {
            setBoolean(path, "sustainWhileTrue", sustainWhileTrue);
        }
        objectManager.persistNodeTree(path);
        AlertRule rule = getAlertRule(path);
        indexRefresh.afterAlertRuleUpdated(previous, rule);
        return rule;
    }

    @Transactional
    public void setAlertRuleConditionTrueSince(String path, Instant conditionTrueSince) {
        setRuntimeString(path, "conditionTrueSince", conditionTrueSince != null ? conditionTrueSince.toString() : "");
        objectManager.persistNodeTree(path);
    }

    @Transactional
    public void clearAlertRuleConditionTrueSince(String path) {
        setAlertRuleConditionTrueSince(path, null);
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

    @Transactional
    public void resetAlertRuleRuntimeState(String path) {
        setAlertRuleLastConditionMet(path, false);
        setAlertRuleLastFiredAt(path, null);
        clearAlertRuleConditionTrueSince(path);
        setAlertRuleLastWatchValue(path, null);
    }

    @Transactional(readOnly = true)
    public Double getAlertRuleLastWatchValue(String path) {
        return readString(objectManager.require(path), "lastWatchValue")
                .filter(value -> !value.isBlank())
                .map(Double::parseDouble)
                .orElse(null);
    }

    @Transactional
    public void setAlertRuleLastWatchValue(String path, Double value) {
        setRuntimeString(path, "lastWatchValue", value != null ? Double.toString(value) : "");
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
        return ruleIndex.findCorrelatorsForEvent(eventName);
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
            int sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
            boolean enabled
    ) {
        String path = uniqueCorrelatorPath(name);
        createCorrelatorNode(path, name, targetObjectPath, patternType, eventName, secondEventName,
                windowSeconds, minOccurrences, cooldownSeconds, sequenceGapSeconds, actionType, actionTarget,
                payloadFilterExpr, enabled, null);
        EventCorrelator correlator = getCorrelator(path);
        indexRefresh.afterCorrelatorCreated(correlator);
        return correlator;
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
            Integer sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
            Boolean enabled
    ) {
        EventCorrelator previous = getCorrelator(path);
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
        if (sequenceGapSeconds != null) {
            setInteger(path, "sequenceGapSeconds", sequenceGapSeconds);
        }
        if (actionType != null) {
            setString(path, "actionType", actionType.name());
        }
        if (actionTarget != null) {
            setString(path, "actionTarget", actionTarget);
        }
        if (payloadFilterExpr != null) {
            setString(path, "payloadFilterExpr", payloadFilterExpr);
        }
        if (enabled != null) {
            setBoolean(path, "enabled", enabled);
        }
        objectManager.persistNodeTree(path);
        EventCorrelator correlator = getCorrelator(path);
        indexRefresh.afterCorrelatorUpdated(previous, correlator);
        return correlator;
    }

    @Transactional
    public void setCorrelatorLastTriggeredAt(String path, Instant triggeredAt) {
        setRuntimeString(path, "lastTriggeredAt", triggeredAt != null ? triggeredAt.toString() : "");
        objectManager.persistNodeTree(path);
    }

    @Transactional
    public void deleteAlertRule(String path) {
        AlertRule rule = getAlertRule(path);
        objectManager.delete(path);
        indexRefresh.afterAlertRuleDeleted(rule);
    }

    @Transactional
    public void deleteCorrelator(String path) {
        EventCorrelator correlator = getCorrelator(path);
        correlatorWindowStore.clearCorrelator(path);
        objectManager.delete(path);
        indexRefresh.afterCorrelatorDeleted(correlator);
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
                true,
                0,
                false
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
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                "",
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
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                "",
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
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                "",
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
            int delaySeconds,
            boolean sustainWhileTrue,
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
        setInteger(path, "delaySeconds", delaySeconds);
        setBoolean(path, "sustainWhileTrue", sustainWhileTrue);
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
            int sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
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
        setInteger(path, "sequenceGapSeconds", sequenceGapSeconds);
        setString(path, "actionType", actionType.name());
        setString(path, "actionTarget", actionTarget);
        setString(path, "payloadFilterExpr", payloadFilterExpr != null ? payloadFilterExpr : "");
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
                readInteger(node, "delaySeconds").orElse(0),
                readBoolean(node, "sustainWhileTrue").orElse(false),
                readInteger(node, "rateLimitSeconds").orElse(0),
                readBoolean(node, "lastConditionMet").orElse(null),
                parseInstant(readString(node, "conditionTrueSince").orElse("")),
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
                readInteger(node, "sequenceGapSeconds").orElse(0),
                CorrelatorActionType.valueOf(actionRaw),
                readString(node, "actionTarget").orElse(""),
                blankToNull(readString(node, "payloadFilterExpr").orElse(null)),
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
        objectManager.upsertSystemVariable(
                path,
                variable,
                STRING_VALUE,
                DataRecord.single(STRING_VALUE, Map.of("value", value != null ? value : ""))
        );
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
