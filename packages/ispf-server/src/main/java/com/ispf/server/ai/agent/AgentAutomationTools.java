package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.expression.BindingExpressionValidator;
import com.ispf.server.alert.AlertRule;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FW-48: automation, bindings, and operator UI tools for end-to-end project builds.
 */
final class AgentAutomationTools {

    private AgentAutomationTools() {
    }

    static List<PlatformAgentTool> all(
            AutomationTreeService automationTreeService,
            OperatorAppUiService operatorAppUiService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            AgentRecipeCatalog recipeCatalog,
            ObjectMapper objectMapper
    ) {
        return List.of(
                configureAlertTool(automationTreeService, objectManager, objectAccessService, tenantScopeService),
                configureCorrelatorTool(automationTreeService, objectManager, objectAccessService, tenantScopeService),
                listAutomationTool(automationTreeService, objectAccessService, tenantScopeService),
                getAutomationSchemaTool(recipeCatalog),
                searchPlatformRecipesTool(recipeCatalog),
                createVariableTool(objectManager, objectAccessService, tenantScopeService, objectMapper),
                createBindingRuleTool(
                        objectManager,
                        objectAccessService,
                        tenantScopeService,
                        bindingRulesService,
                        bindingDependencyIndex,
                        bindingRuleEngine
                ),
                configureVariableHistoryTool(objectManager, objectAccessService, tenantScopeService),
                configureOperatorUiTool(operatorAppUiService)
        );
    }

    private static PlatformAgentTool configureAlertTool(
            AutomationTreeService automationTreeService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_alert";
            }

            @Override
            public String description() {
                return "Create or update ALERT rule under root.platform.alert-rules. "
                        + "Args: path (existing) OR name (creates under alert-rules), targetObjectPath, watchVariable, "
                        + "conditionExpr (CEL, e.g. self.temperature[\"value\"] > 85), eventName, "
                        + "payloadVariable?, enabled?, edgeTrigger?, delaySeconds?, sustainWhileTrue?. "
                        + "Use get_automation_schema for field names.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                try {
                    String path = stringArg(arguments, "path");
                    String name = stringArg(arguments, "name");
                    if (path.isBlank() && name.isBlank()) {
                        return Map.of("status", "ERROR", "error", "path or name is required");
                    }
                    if (path.isBlank()) {
                        automationTreeService.ensurePlatformFolders();
                        path = AutomationTreeService.rulePathForName(name);
                        if (objectManager.tree().findByPath(path).isEmpty()) {
                            objectAccessService.requireWrite(AutomationTreeService.ALERT_RULES_ROOT, auth);
                            objectManager.create(
                                    AutomationTreeService.ALERT_RULES_ROOT,
                                    AutomationTreeService.slugify(name),
                                    ObjectType.ALERT,
                                    name,
                                    "",
                                    null
                            );
                            automationTreeService.ensureAlertRuleStructure(path);
                        }
                    }
                    if (!tenantScopeService.isPathVisible(path, auth)) {
                        return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                    }
                    objectAccessService.requireWrite(path, auth);
                    automationTreeService.ensureAlertRuleStructure(path);

                    String targetObjectPath = optionalString(arguments, "targetObjectPath");
                    String watchVariable = optionalString(arguments, "watchVariable");
                    String conditionExpr = optionalString(arguments, "conditionExpr");
                    String eventName = optionalString(arguments, "eventName");
                    String payloadVariable = optionalString(arguments, "payloadVariable");
                    Boolean enabled = boolArg(arguments, "enabled", null);
                    Boolean edgeTrigger = boolArg(arguments, "edgeTrigger", null);
                    Integer delaySeconds = intArg(arguments, "delaySeconds", null);
                    Boolean sustainWhileTrue = boolArg(arguments, "sustainWhileTrue", null);

                    if (targetObjectPath == null || watchVariable == null || conditionExpr == null || eventName == null) {
                        AlertRule current = automationTreeService.getAlertRule(path);
                        return Map.of(
                                "status", "OK",
                                "path", path,
                                "alert", alertPreview(current),
                                "hint", "Provide targetObjectPath, watchVariable, conditionExpr, eventName to update"
                        );
                    }

                    String displayName = name.isBlank()
                            ? objectManager.require(path).displayName()
                            : name;
                    AlertRule updated = automationTreeService.updateAlertRule(
                            path,
                            displayName,
                            targetObjectPath,
                            watchVariable,
                            conditionExpr,
                            eventName,
                            payloadVariable != null ? payloadVariable : "",
                            enabled != null ? enabled : true,
                            edgeTrigger != null ? edgeTrigger : true,
                            delaySeconds != null ? delaySeconds : 0,
                            sustainWhileTrue != null ? sustainWhileTrue : false,
                            null,
                            null
                    );
                    return Map.of("status", "OK", "path", path, "alert", alertPreview(updated));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool configureCorrelatorTool(
            AutomationTreeService automationTreeService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_correlator";
            }

            @Override
            public String description() {
                return "Create or update CORRELATOR under root.platform.correlators. "
                        + "Args: path OR name, targetObjectPath, patternType (COUNT|SEQUENCE|EVENT_CHAIN|...), "
                        + "eventName, secondEventName?, windowSeconds?, minOccurrences?, cooldownSeconds?, "
                        + "actionType (FIRE_EVENT|START_WORKFLOW|...), actionTarget?, payloadFilterExpr?, enabled?. "
                        + "Use get_automation_schema for enums and fields.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                try {
                    String path = stringArg(arguments, "path");
                    String name = stringArg(arguments, "name");
                    if (path.isBlank() && name.isBlank()) {
                        return Map.of("status", "ERROR", "error", "path or name is required");
                    }
                    if (path.isBlank()) {
                        automationTreeService.ensurePlatformFolders();
                        path = AutomationTreeService.correlatorPathForName(name);
                        if (objectManager.tree().findByPath(path).isEmpty()) {
                            objectAccessService.requireWrite(AutomationTreeService.CORRELATORS_ROOT, auth);
                            objectManager.create(
                                    AutomationTreeService.CORRELATORS_ROOT,
                                    AutomationTreeService.slugify(name),
                                    ObjectType.CORRELATOR,
                                    name,
                                    "",
                                    null
                            );
                            automationTreeService.ensureCorrelatorStructure(path);
                        }
                    }
                    if (!tenantScopeService.isPathVisible(path, auth)) {
                        return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                    }
                    objectAccessService.requireWrite(path, auth);
                    automationTreeService.ensureCorrelatorStructure(path);

                    String targetObjectPath = optionalString(arguments, "targetObjectPath");
                    String patternTypeRaw = optionalString(arguments, "patternType");
                    String eventName = optionalString(arguments, "eventName");
                    if (targetObjectPath == null || patternTypeRaw == null || eventName == null) {
                        EventCorrelator current = automationTreeService.getCorrelator(path);
                        return Map.of(
                                "status", "OK",
                                "path", path,
                                "correlator", correlatorPreview(current),
                                "hint", "Provide targetObjectPath, patternType, eventName to update"
                        );
                    }

                    CorrelatorPatternType patternType = CorrelatorPatternType.valueOf(
                            patternTypeRaw.trim().toUpperCase(Locale.ROOT)
                    );
                    String secondEventName = optionalString(arguments, "secondEventName");
                    int windowSeconds = intArg(arguments, "windowSeconds", 60);
                    int minOccurrences = intArg(arguments, "minOccurrences", 3);
                    int cooldownSeconds = intArg(arguments, "cooldownSeconds", 30);
                    int sequenceGapSeconds = intArg(arguments, "sequenceGapSeconds", 0);
                    String actionTypeRaw = stringArg(arguments, "actionType");
                    if (actionTypeRaw.isBlank()) {
                        actionTypeRaw = "FIRE_EVENT";
                    }
                    CorrelatorActionType actionType = CorrelatorActionType.valueOf(
                            actionTypeRaw.trim().toUpperCase(Locale.ROOT)
                    );
                    String actionTarget = optionalString(arguments, "actionTarget");
                    String payloadFilterExpr = optionalString(arguments, "payloadFilterExpr");
                    Boolean enabled = boolArg(arguments, "enabled", null);

                    String displayName = name.isBlank()
                            ? objectManager.require(path).displayName()
                            : name;
                    EventCorrelator updated = automationTreeService.updateCorrelator(
                            path,
                            displayName,
                            targetObjectPath,
                            patternType,
                            eventName,
                            secondEventName,
                            windowSeconds,
                            minOccurrences,
                            cooldownSeconds,
                            sequenceGapSeconds,
                            actionType,
                            actionTarget != null ? actionTarget : "",
                            payloadFilterExpr != null ? payloadFilterExpr : "",
                            enabled != null ? enabled : true
                    );
                    return Map.of("status", "OK", "path", path, "correlator", correlatorPreview(updated));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool listAutomationTool(
            AutomationTreeService automationTreeService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_automation";
            }

            @Override
            public String description() {
                return "List alert rules and event correlators on the platform. No args.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                List<Map<String, Object>> alerts = new ArrayList<>();
                for (AlertRule rule : automationTreeService.listAlertRules()) {
                    if (!tenantScopeService.isPathVisible(rule.id(), auth)) {
                        continue;
                    }
                    if (!objectAccessService.canRead(rule.id(), auth)) {
                        continue;
                    }
                    alerts.add(alertPreview(rule));
                }
                List<Map<String, Object>> correlators = new ArrayList<>();
                for (EventCorrelator correlator : automationTreeService.listCorrelators()) {
                    if (!tenantScopeService.isPathVisible(correlator.id(), auth)) {
                        continue;
                    }
                    if (!objectAccessService.canRead(correlator.id(), auth)) {
                        continue;
                    }
                    correlators.add(correlatorPreview(correlator));
                }
                return Map.of(
                        "status", "OK",
                        "alertRulesRoot", AutomationTreeService.ALERT_RULES_ROOT,
                        "correlatorsRoot", AutomationTreeService.CORRELATORS_ROOT,
                        "alertCount", alerts.size(),
                        "correlatorCount", correlators.size(),
                        "alerts", alerts,
                        "correlators", correlators
                );
            }
        };
    }

    private static PlatformAgentTool searchPlatformRecipesTool(AgentRecipeCatalog recipeCatalog) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "search_platform_recipes";
            }

            @Override
            public String description() {
                return "Search generated platform recipes. Args: optional query, category, industry, archetype, "
                        + "limit (default 25), offset (default 0).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return recipeCatalog.search(
                        optionalString(arguments, "query"),
                        optionalString(arguments, "category"),
                        optionalString(arguments, "industry"),
                        optionalString(arguments, "archetype"),
                        intArg(arguments, "limit", 25),
                        intArg(arguments, "offset", 0)
                );
            }
        };
    }

    private static PlatformAgentTool getAutomationSchemaTool(AgentRecipeCatalog recipeCatalog) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_automation_schema";
            }

            @Override
            public String description() {
                return "Reference for ALERT/CORRELATOR variables, dashboard templates, widgets, object tree, bindings, "
                        + "operator UI, reports, workflows, SCADA, application lifecycle, schedules, platform rules. "
                        + "Optional arg: topic (alert|correlator|dashboard|widget|object|binding|operator|report|"
                        + "workflow|scada|lifecycle|schedule|platform-rule|projectBlueprint|groundTruth|instanceTypes|"
                        + "objectTypes|relativeModels|recipes|projects|recipe/{id}|all).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String topic = stringArg(arguments, "topic");
                if (topic.isBlank()) {
                    topic = "all";
                }
                final String resolvedTopic = topic;
                String topicLower = topic.toLowerCase(Locale.ROOT);
                int offset = intArg(arguments, "offset", 0);
                int limit = intArg(arguments, "limit", 50);
                if (topicLower.startsWith("recipe/")) {
                    String recipeId = topic.substring("recipe/".length()).trim();
                    if (recipeId.isBlank()) {
                        return Map.of(
                                "status", "ERROR",
                                "topic", topic,
                                "error", "recipe/{id} requires non-empty id"
                        );
                    }
                    return recipeCatalog.findById(recipeId)
                            .<Map<String, Object>>map(recipe -> Map.of(
                                    "status", "OK",
                                    "topic", resolvedTopic,
                                    "recipe", AgentRecipeCatalog.toDetailRow(recipe)
                            ))
                            .orElseGet(() -> Map.of(
                                    "status", "ERROR",
                                    "topic", resolvedTopic,
                                    "error", "Recipe not found: " + recipeId
                            ));
                }
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("status", "OK");
                schema.put("topic", resolvedTopic);
                switch (topicLower) {
                    case "alert" -> schema.put("alert", alertSchema());
                    case "correlator" -> schema.put("correlator", correlatorSchema());
                    case "dashboard" -> schema.put("dashboard", dashboardSchema());
                    case "widget", "widgets" -> schema.put("widgets", AgentWidgetCatalog.catalogResponse("", ""));
                    case "object", "objects" -> schema.put("objects", AgentObjectTreeGuide.summary());
                    case "projectblueprint", "project-blueprint", "blueprint" -> {
                        schema.put("projectBlueprint", AgentPlaybooks.projectBlueprintGuide());
                        schema.put("planSections", AgentPlanSections.guide());
                    }
                    case "groundtruth", "ground-truth", "discovery" ->
                            schema.put("groundTruth", AgentPlaybooks.groundTruthGuide());
                    case "instancetypes", "instance-types", "instance-type", "instance" ->
                            schema.put("instanceTypes", AgentPlaybooks.instanceTypesGuide());
                    case "objecttypes", "object-types", "objecttype", "object-type" -> {
                        schema.put("objectTypes", AgentPlaybooks.objectTypesMatrixGuide());
                        schema.put("objectTypeCoverage", AgentPlaybooks.objectTypeCoverageGuide());
                        schema.put("objectTypeTable", objectTypeGuide());
                    }
                    case "relativemodels", "relative-models", "relative-model" ->
                            schema.put("relativeModels", AgentPlaybooks.relativeModelsGuide());
                    case "binding" -> schema.put("binding", bindingSchema());
                    case "operator" -> schema.put("operator", operatorSchema());
                    case "report", "reports" -> schema.put("reports", AgentPlaybooks.reportsGuide());
                    case "workflow", "workflows" -> schema.put("workflows", AgentPlaybooks.workflowGuide());
                    case "scada", "mimic", "mimics" -> schema.put("scada", AgentPlaybooks.scadaMimicGuide());
                    case "lifecycle", "application" -> schema.put("lifecycle", AgentPlaybooks.applicationLifecycleGuide());
                    case "schedule", "schedules" -> schema.put("schedules", AgentPlaybooks.scheduleGuide());
                    case "function", "functions" -> schema.put("functions", AgentPlaybooks.functionsGuide());
                    case "platform-rule", "platform-rules", "context" -> schema.put("platformRules", AgentPlaybooks.platformRuleGuide());
                    case "recipes" -> schema.put("recipes", recipeCatalog.indexSummary(offset, limit));
                    case "projects" -> schema.put(
                            "projects",
                            recipeCatalog.listProjects(
                                    optionalString(arguments, "industry"),
                                    optionalString(arguments, "archetype"),
                                    offset,
                                    limit
                            )
                    );
                    default -> {
                        schema.put("alert", alertSchema());
                        schema.put("correlator", correlatorSchema());
                        schema.put("dashboard", dashboardSchema());
                        schema.put("widgets", AgentWidgetCatalog.catalogSummary());
                        schema.put("objects", AgentObjectTreeGuide.summary());
                        schema.put("binding", bindingSchema());
                        schema.put("operator", operatorSchema());
                        schema.put("reports", AgentPlaybooks.reportsGuide());
                        schema.put("workflows", AgentPlaybooks.workflowGuide());
                        schema.put("scada", AgentPlaybooks.scadaMimicGuide());
                        schema.put("lifecycle", AgentPlaybooks.applicationLifecycleGuide());
                        schema.put("schedules", AgentPlaybooks.scheduleGuide());
                        schema.put("functions", AgentPlaybooks.functionsGuide());
                        schema.put("platformRules", AgentPlaybooks.platformRuleGuide());
                        schema.put("platformMaster", AgentPlaybooks.platformMasterGuide());
                        schema.put("projectBlueprint", AgentPlaybooks.projectBlueprintGuide());
                        schema.put("planSections", AgentPlanSections.guide());
                        schema.put("groundTruth", AgentPlaybooks.groundTruthGuide());
                        schema.put("instanceTypes", AgentPlaybooks.instanceTypesGuide());
                        schema.put("relativeModels", AgentPlaybooks.relativeModelsGuide());
                        schema.put("objectTypes", AgentPlaybooks.objectTypesMatrixGuide());
                        schema.put("objectTypeTable", objectTypeGuide());
                        schema.put("recipes", recipeCatalog.indexSummary(offset, limit));
                        schema.put(
                                "projects",
                                recipeCatalog.listProjects(
                                        optionalString(arguments, "industry"),
                                        optionalString(arguments, "archetype"),
                                        offset,
                                        limit
                                )
                        );
                    }
                }
                return schema;
            }
        };
    }

    private static PlatformAgentTool createVariableTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_variable";
            }

            @Override
            public String description() {
                return "Create a new variable on an object (for CUSTOM logic, computed fields). "
                        + "Args: path, name, valueType (DOUBLE|BOOLEAN|STRING|INTEGER), "
                        + "initialValue? (map or scalar), "
                        + "writable? (default false), historyEnabled? (default false). "
                        + "Use create_binding_rule for computed values; set_variable only updates writable vars.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                String valueType = stringArg(arguments, "valueType");
                if (path.isBlank() || name.isBlank() || valueType.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path, name, valueType are required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    PlatformObject node = objectManager.require(path);
                    if (node.getVariable(name).isPresent()) {
                        return Map.of("status", "ERROR", "error", "Variable already exists: " + name);
                    }
                    DataSchema schema = schemaForValueType(valueType);
                    boolean writable = boolArg(arguments, "writable", false);
                    boolean historyEnabled = boolArg(arguments, "historyEnabled", false);
                    DataRecord initialValue = null;
                    Object rawInitial = arguments.get("initialValue");
                    if (rawInitial == null) {
                        rawInitial = arguments.get("value");
                    }
                    if (rawInitial != null) {
                        initialValue = toInitialRecord(objectMapper, schema, rawInitial);
                    }
                    Variable created = objectManager.createVariable(
                            path,
                            name,
                            schema,
                            true,
                            writable,
                            initialValue,
                            historyEnabled,
                            null
                    );
                    Map<String, Object> preview = new LinkedHashMap<>();
                    preview.put("name", created.name());
                    preview.put("writable", created.writable());
                    return Map.of("status", "OK", "path", path, "variable", preview);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool createBindingRuleTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_binding_rule";
            }

            @Override
            public String description() {
                return "Create or replace a binding rule on an object. Args: path, id, expression, "
                        + "targetVariable (for kind=variable), optional targetKind (variable|context|event), "
                        + "contextPath (context target), eventName (event target), condition (CEL), "
                        + "remoteObjectPath + remoteVariableName (cross-object activator), "
                        + "onContextChange (bool, dashboard rules), onStartup, order. "
                        + "For dashboard @dashboardContext rules prefer configure_platform_context_rule.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String id = stringArg(arguments, "id");
                String targetVariable = stringArg(arguments, "targetVariable");
                String expression = stringArg(arguments, "expression");
                String targetKind = com.ispf.core.binding.BindingTargetKind.normalize(stringArg(arguments, "targetKind"));
                if (path.isBlank() || id.isBlank() || expression.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path, id, expression are required");
                }
                if (com.ispf.core.binding.BindingTargetKind.VARIABLE.equals(targetKind) && targetVariable.isBlank()) {
                    return Map.of("status", "ERROR", "error", "targetVariable is required for targetKind=variable");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    objectManager.require(path);
                    BindingExpressionValidator.validateOrThrow(expression);
                    String condition = optionalString(arguments, "condition");
                    if (condition != null && !condition.isBlank()) {
                        BindingExpressionValidator.validateOrThrow(condition);
                    }
                    BindingActivators activators = buildActivators(arguments, path);
                    com.ispf.core.binding.BindingTarget target = buildTarget(arguments, targetKind, targetVariable);
                    BindingRule rule = new BindingRule(
                            id,
                            optionalString(arguments, "name"),
                            boolArg(arguments, "enabled", true),
                            intArg(arguments, "order", 0),
                            activators,
                            condition != null ? condition : "",
                            expression,
                            target
                    );
                    BindingRule saved = bindingRulesService.upsertRule(path, rule);
                    bindingDependencyIndex.rebuild(path);
                    bindingRuleEngine.runRulesForObject(path);
                    return Map.of("status", "OK", "path", path, "ruleId", saved.id(), "target", saved.target().variableName());
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }

            private BindingActivators buildActivators(Map<String, Object> arguments, String path) {
                String remotePath = optionalString(arguments, "remoteObjectPath");
                String remoteVar = optionalString(arguments, "remoteVariableName");
                if (remotePath != null && remoteVar != null) {
                    return BindingActivators.onRemoteChange(remotePath, remoteVar);
                }
                if (boolArg(arguments, "onContextChange", false)) {
                    return new BindingActivators(
                            boolArg(arguments, "onStartup", false),
                            List.of(com.ispf.core.binding.BindingVariableRef.local("@dashboardContext")),
                            null,
                            0,
                            false,
                            true
                    );
                }
                return BindingRulesService.defaultActivators(path, stringArg(arguments, "expression"));
            }

            private com.ispf.core.binding.BindingTarget buildTarget(
                    Map<String, Object> arguments,
                    String targetKind,
                    String targetVariable
            ) {
                String field = optionalString(arguments, "targetField");
                if (field == null || field.isBlank()) {
                    field = "value";
                }
                return switch (targetKind) {
                    case com.ispf.core.binding.BindingTargetKind.CONTEXT -> new com.ispf.core.binding.BindingTarget(
                            com.ispf.core.binding.BindingTargetKind.CONTEXT,
                            null,
                            field,
                            stringArg(arguments, "contextPath"),
                            null
                    );
                    case com.ispf.core.binding.BindingTargetKind.EVENT -> new com.ispf.core.binding.BindingTarget(
                            com.ispf.core.binding.BindingTargetKind.EVENT,
                            null,
                            field,
                            null,
                            stringArg(arguments, "eventName")
                    );
                    default -> new com.ispf.core.binding.BindingTarget(targetVariable, field);
                };
            }
        };
    }

    private static PlatformAgentTool configureVariableHistoryTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_variable_history";
            }

            @Override
            public String description() {
                return "Enable or disable historian for an existing variable (required for chart/sparkline widgets). "
                        + "Args: path (object path), name (variable name), historyEnabled (boolean), "
                        + "optional historyRetentionDays (integer).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                if (path.isBlank() || name.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and name are required");
                }
                Boolean historyEnabled = boolArg(arguments, "historyEnabled", null);
                if (historyEnabled == null) {
                    return Map.of("status", "ERROR", "error", "historyEnabled is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    Integer retentionDays = intArg(arguments, "historyRetentionDays", null);
                    Variable updated = objectManager.updateVariableHistory(
                            path,
                            name,
                            historyEnabled,
                            retentionDays
                    );
                    Map<String, Object> preview = new LinkedHashMap<>();
                    preview.put("name", updated.name());
                    preview.put("historyEnabled", updated.historyEnabled());
                    updated.historyRetentionDays().ifPresent(days -> preview.put("historyRetentionDays", days));
                    return Map.of("status", "OK", "path", path, "variable", preview);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool configureOperatorUiTool(OperatorAppUiService operatorAppUiService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_operator_ui";
            }

            @Override
            public String description() {
                return "Set operator HMI default dashboard and menu. Args: appId (default platform), title, "
                        + "defaultDashboard (path), dashboards (list of {path, title}). "
                        + "Creates operator app record if appId is new.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                try {
                    String appId = stringArg(arguments, "appId");
                    if (appId.isBlank()) {
                        appId = "platform";
                    }
                    String title = stringArg(arguments, "title");
                    String defaultDashboard = stringArg(arguments, "defaultDashboard");
                    Object dashboardsRaw = arguments.get("dashboards");
                    if (title.isBlank() || defaultDashboard.isBlank() || !(dashboardsRaw instanceof List<?> list) || list.isEmpty()) {
                        Map<String, Object> current;
                        try {
                            current = operatorAppUiService.getUi(appId);
                        } catch (IllegalArgumentException ex) {
                            return Map.of(
                                    "status", "ERROR",
                                    "error",
                                    "title, defaultDashboard, dashboards[] are required (app not found: " + appId + ")"
                            );
                        }
                        return Map.of("status", "OK", "appId", appId, "ui", current);
                    }
                    List<Map<String, String>> dashboards = new ArrayList<>();
                    for (Object item : (List<?>) dashboardsRaw) {
                        if (item instanceof Map<?, ?> row) {
                            String path = String.valueOf(row.get("path"));
                            String dashTitle = row.containsKey("title")
                                    ? String.valueOf(row.get("title"))
                                    : path;
                            if (!path.isBlank()) {
                                dashboards.add(Map.of("path", path, "title", dashTitle));
                            }
                        }
                    }
                    if (dashboards.isEmpty()) {
                        return Map.of("status", "ERROR", "error", "dashboards must contain at least one {path, title}");
                    }
                    try {
                        operatorAppUiService.getUi(appId);
                    } catch (IllegalArgumentException ex) {
                        operatorAppUiService.createApp(appId, title);
                    }
                    Map<String, Object> saved = operatorAppUiService.saveUi(
                            appId,
                            title,
                            defaultDashboard,
                            dashboards
                    );
                    return Map.of("status", "OK", "appId", appId, "ui", saved);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Map<String, Object> alertPreview(AlertRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", rule.id());
        row.put("name", rule.name());
        row.put("targetObjectPath", rule.objectPath());
        row.put("watchVariable", rule.watchVariable());
        row.put("conditionExpr", rule.conditionExpr());
        row.put("eventName", rule.eventName());
        row.put("enabled", rule.enabled());
        return row;
    }

    private static Map<String, Object> correlatorPreview(EventCorrelator correlator) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", correlator.id());
        row.put("name", correlator.name());
        row.put("targetObjectPath", correlator.objectPath());
        row.put("patternType", correlator.patternType().name());
        row.put("eventName", correlator.eventName());
        row.put("enabled", correlator.enabled());
        return row;
    }

    private static Map<String, Object> alertSchema() {
        return Map.of(
                "parentPath", AutomationTreeService.ALERT_RULES_ROOT,
                "objectType", "ALERT",
                "model", "alert-rule-v1",
                "variables", List.of(
                        "targetObjectPath", "watchVariable", "conditionExpr", "eventName",
                        "payloadVariable", "enabled", "edgeTrigger", "delaySeconds", "sustainWhileTrue"
                ),
                "conditionExample", "self.temperature[\"value\"] > 85",
                "tool", "configure_alert"
        );
    }

    private static Map<String, Object> correlatorSchema() {
        return Map.of(
                "parentPath", AutomationTreeService.CORRELATORS_ROOT,
                "objectType", "CORRELATOR",
                "model", "correlator-v1",
                "patternTypes", List.of("COUNT", "SEQUENCE", "EVENT_CHAIN"),
                "actionTypes", List.of("RUN_WORKFLOW", "FIRE_EVENT", "SET_VARIABLE", "OPEN_OPERATOR_REPORT"),
                "variables", List.of(
                        "targetObjectPath", "patternType", "eventName", "secondEventName",
                        "windowSeconds", "minOccurrences", "cooldownSeconds", "actionType", "actionTarget",
                        "payloadFilterExpr", "enabled"
                ),
                "note", "COUNT correlator counts events per targetObjectPath; cross-device logic use CUSTOM hub + refAt + alert",
                "tool", "configure_correlator"
        );
    }

    private static Map<String, Object> dashboardSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("objectType", "DASHBOARD");
        schema.put("layoutVariable", "layout");
        schema.putAll(AgentDashboardGuide.summary());
        schema.put("fieldSemantics", AgentWidgetPropertiesGuide.fieldSemantics());
        schema.put("widgetTypesDocumented", AgentWidgetPropertiesGuide.allTypeSpecs().size());
        schema.putAll(AgentWidgetCatalog.catalogSummary());
        schema.put("drillDown",
                "object-table: selectionKey + rowTargetDashboard + rowOpenMode=navigate; "
                        + "detail widgets use same selectionKey");
        schema.put("historian",
                "chart/sparkline/history-table require historyEnabled=true; use configure_variable_history");
        schema.put("catalogTool", "get_widget_catalog");
        schema.put("tools", List.of(
                "get_widget_catalog",
                "get_dashboard_layout",
                "set_dashboard_layout",
                "add_dashboard_widget",
                "configure_variable_history"
        ));
        return schema;
    }

    private static Map<String, Object> bindingSchema() {
        return Map.of(
                "refAt", "refAt(\"root.platform.devices.foo\", variableName) or refAt(\"path\", var, field)",
                "cel", "CEL expressions on same object, e.g. self.member1[\"value\"] > 0 && self.member2[\"value\"] > 0",
                "platformBindings", List.of(
                        "refAt", "hysteresis", "counterRate", "unitConvert", "callFunction", "sqlBinding"
                ),
                "tool", "create_binding_rule"
        );
    }

    private static Map<String, Object> operatorSchema() {
        return Map.of(
                "defaultAppId", "platform",
                "rest", "PUT /api/v1/operator-apps/{appId}/ui",
                "fields", List.of("title", "defaultDashboard", "dashboards[{path,title}]"),
                "tool", "configure_operator_ui"
        );
    }

    private static List<Map<String, String>> objectTypeGuide() {
        return List.of(
                Map.of("type", "DEVICE", "use", "Sensors, PLCs, simulators", "keyVars", "driverConfigJson, driverPointMappingsJson, status"),
                Map.of("type", "DASHBOARD", "use", "Operator screens", "keyVars", "title, layout, refreshIntervalMs"),
                Map.of("type", "CUSTOM", "use", "Logic hub, aggregations, refAt bindings", "keyVars", "user-defined via create_variable"),
                Map.of("type", "ALERT", "use", "CEL rules → events", "parent", AutomationTreeService.ALERT_RULES_ROOT),
                Map.of("type", "CORRELATOR", "use", "Event patterns", "parent", AutomationTreeService.CORRELATORS_ROOT),
                Map.of("type", "WORKFLOW", "use", "BPMN automation", "parent", "root.platform.workflows"),
                Map.of("type", "REPORT", "use", "Report definitions", "parent", "root.platform.reports"),
                Map.of("type", "MODEL_CATALOG", "use", "Blueprint definitions", "paths", "root.platform.relative-models, instance-types, absolute-models")
        );
    }

    private static DataSchema schemaForValueType(String valueType) {
        FieldType fieldType = switch (valueType.trim().toUpperCase(Locale.ROOT)) {
            case "BOOLEAN", "BOOL" -> FieldType.BOOLEAN;
            case "STRING", "TEXT" -> FieldType.STRING;
            case "INTEGER", "INT" -> FieldType.INTEGER;
            default -> FieldType.DOUBLE;
        };
        return DataSchema.builder("agentVar")
                .field("value", fieldType)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static DataRecord toInitialRecord(ObjectMapper objectMapper, DataSchema schema, Object raw)
            throws Exception {
        if (raw instanceof Map<?, ?> map) {
            return DataRecord.single(schema, (Map<String, Object>) map);
        }
        if (raw instanceof String text && text.trim().startsWith("{")) {
            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
            return DataRecord.single(schema, parsed);
        }
        return DataRecord.single(schema, Map.of("value", raw));
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static Boolean boolArg(Map<String, Object> args, String key, Boolean defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer intArg(Map<String, Object> args, String key, Integer defaultValue) {
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
