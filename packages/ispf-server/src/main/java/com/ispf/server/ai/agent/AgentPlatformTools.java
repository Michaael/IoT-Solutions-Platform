package com.ispf.server.ai.agent;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingTargetKind;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.expression.BindingExpressionValidator;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.HaystackExportService;
import com.ispf.server.platform.time.PlatformTimeZoneResolver;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent tools for platform schedules, binding rules, timezone, semantic export.
 */
final class AgentPlatformTools {

    private AgentPlatformTools() {
    }

    static List<PlatformAgentTool> all(
            ScheduleObjectService scheduleObjectService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            PlatformTimeZoneResolver timeZoneResolver,
            HaystackExportService haystackExportService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return List.of(
                listPlatformSchedulesTool(scheduleObjectService, objectManager, objectAccessService, tenantScopeService),
                configurePlatformScheduleTool(scheduleObjectService, objectAccessService, tenantScopeService),
                listBindingRulesTool(bindingRulesService, objectManager, objectAccessService, tenantScopeService),
                configurePlatformContextRuleTool(
                        bindingRulesService,
                        bindingDependencyIndex,
                        bindingRuleEngine,
                        objectManager,
                        objectAccessService,
                        tenantScopeService
                ),
                resolveTimezoneTool(timeZoneResolver, objectAccessService, tenantScopeService),
                exportHaystackTool(haystackExportService, objectAccessService, tenantScopeService)
        );
    }

    private static PlatformAgentTool listPlatformSchedulesTool(
            ScheduleObjectService scheduleObjectService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_platform_schedules";
            }

            @Override
            public String description() {
                return "List platform schedule objects under root.platform.schedules.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                var auth = context.authentication();
                String root = ScheduleObjectService.SCHEDULES_ROOT;
                if (!tenantScopeService.isPathVisible(root, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + root);
                }
                objectAccessService.requireRead(root, auth);
                try {
                    scheduleObjectService.ensureCatalog();
                    List<Map<String, Object>> rows = new ArrayList<>();
                    if (objectManager.tree().findByPath(root).isPresent()) {
                        for (PlatformObject child : objectManager.tree().childrenOf(root)) {
                            if (child.type() != ObjectType.SCHEDULE) {
                                continue;
                            }
                            try {
                                ScheduleObjectService.ScheduleView view = scheduleObjectService.getByPath(child.path());
                                rows.add(Map.of(
                                        "path", view.path(),
                                        "scheduleId", view.scheduleId(),
                                        "displayName", view.displayName(),
                                        "enabled", view.enabled(),
                                        "intervalMs", view.intervalMs(),
                                        "objectPath", view.objectPath() != null ? view.objectPath() : "",
                                        "functionName", view.functionName() != null ? view.functionName() : ""
                                ));
                            } catch (RuntimeException ignored) {
                                // skip broken schedule nodes
                            }
                        }
                    }
                    return Map.of("status", "OK", "count", rows.size(), "schedules", rows);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool configurePlatformScheduleTool(
            ScheduleObjectService scheduleObjectService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_platform_schedule";
            }

            @Override
            public String description() {
                return "Create or update platform schedule. Args: scheduleId, intervalMs, objectPath, functionName, "
                        + "optional path (for update), displayName, description, enabled.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                try {
                    ScheduleObjectService.ScheduleView saved;
                    if (!path.isBlank()) {
                        var auth = context.authentication();
                        if (!tenantScopeService.isPathVisible(path, auth)) {
                            return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                        }
                        objectAccessService.requireWrite(path, auth);
                        saved = scheduleObjectService.update(
                                path,
                                optionalString(arguments, "displayName"),
                                optionalString(arguments, "description"),
                                boolArg(arguments, "enabled", null),
                                longArg(arguments, "intervalMs"),
                                optionalString(arguments, "objectPath"),
                                optionalString(arguments, "functionName")
                        );
                    } else {
                        String scheduleId = stringArg(arguments, "scheduleId");
                        if (scheduleId.isBlank()) {
                            return Map.of("status", "ERROR", "error", "scheduleId or path is required");
                        }
                        saved = scheduleObjectService.create(
                                scheduleId,
                                stringArg(arguments, "displayName"),
                                stringArg(arguments, "description"),
                                boolArg(arguments, "enabled", true),
                                longArg(arguments, "intervalMs"),
                                stringArg(arguments, "objectPath"),
                                stringArg(arguments, "functionName")
                        );
                    }
                    return Map.of("status", "OK", "schedule", scheduleRow(saved));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool listBindingRulesTool(
            BindingRulesService bindingRulesService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_binding_rules";
            }

            @Override
            public String description() {
                return "List platform/binding rules on object. Arg: path.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireRead(path, auth);
                try {
                    objectManager.require(path);
                    List<Map<String, Object>> rows = bindingRulesService.listRules(path).stream()
                            .map(AgentPlatformTools::ruleRow)
                            .toList();
                    return Map.of("status", "OK", "path", path, "count", rows.size(), "rules", rows);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool configurePlatformContextRuleTool(
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "configure_platform_context_rule";
            }

            @Override
            public String description() {
                return "Dashboard platform rule (ADR-0019): write @dashboardContext or fire event. "
                        + "Args: path (DASHBOARD), id, expression, optional condition, contextPath (e.g. params.mode), "
                        + "targetKind (context|event), eventName (for event), onContextChange (bool, default true), order.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String id = stringArg(arguments, "id");
                String expression = stringArg(arguments, "expression");
                if (path.isBlank() || id.isBlank() || expression.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path, id, expression are required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    PlatformObject node = objectManager.require(path);
                    if (node.type() != ObjectType.DASHBOARD) {
                        return Map.of("status", "ERROR", "error", "Platform context rules require DASHBOARD object: " + path);
                    }
                    BindingExpressionValidator.validateOrThrow(expression);
                    String condition = optionalString(arguments, "condition");
                    if (condition != null && !condition.isBlank()) {
                        BindingExpressionValidator.validateOrThrow(condition);
                    }
                    String targetKind = BindingTargetKind.normalize(stringArg(arguments, "targetKind"));
                    if (BindingTargetKind.EVENT.equals(targetKind) && stringArg(arguments, "eventName").isBlank()) {
                        return Map.of("status", "ERROR", "error", "eventName is required for targetKind=event");
                    }
                    String contextPath = optionalString(arguments, "contextPath");
                    if (BindingTargetKind.CONTEXT.equals(targetKind) && (contextPath == null || contextPath.isBlank())) {
                        return Map.of("status", "ERROR", "error", "contextPath is required for targetKind=context");
                    }
                    boolean onContextChange = boolArg(arguments, "onContextChange", true);
                    BindingActivators activators = new BindingActivators(
                            false,
                            List.of(BindingVariableRef.local("@dashboardContext")),
                            null,
                            0,
                            false,
                            onContextChange
                    );
                    BindingTarget target = switch (targetKind) {
                        case BindingTargetKind.EVENT -> new BindingTarget(
                                BindingTargetKind.EVENT,
                                null,
                                "value",
                                null,
                                stringArg(arguments, "eventName")
                        );
                        case BindingTargetKind.CONTEXT -> new BindingTarget(
                                BindingTargetKind.CONTEXT,
                                null,
                                "value",
                                contextPath,
                                null
                        );
                        default -> new BindingTarget(
                                BindingTargetKind.VARIABLE,
                                stringArg(arguments, "targetVariable"),
                                "value",
                                null,
                                null
                        );
                    };
                    BindingRule rule = new BindingRule(
                            id,
                            optionalString(arguments, "name"),
                            boolArg(arguments, "enabled", true),
                            intArg(arguments, "order", 10),
                            activators,
                            condition != null ? condition : "",
                            expression,
                            target
                    );
                    BindingRule saved = bindingRulesService.upsertRule(path, rule);
                    bindingDependencyIndex.rebuild(path);
                    bindingRuleEngine.runRulesForObject(path);
                    return Map.of("status", "OK", "path", path, "rule", ruleRow(saved));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool resolveTimezoneTool(
            PlatformTimeZoneResolver timeZoneResolver,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "resolve_timezone";
            }

            @Override
            public String description() {
                return "Resolve effective IANA timezone for device/object path. Arg: objectPath.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(objectPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + objectPath);
                }
                objectAccessService.requireRead(objectPath, auth);
                String timeZone = timeZoneResolver.resolve(objectPath);
                return Map.of("status", "OK", "objectPath", objectPath, "timeZone", timeZone);
            }
        };
    }

    private static PlatformAgentTool exportHaystackTool(
            HaystackExportService haystackExportService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "export_haystack";
            }

            @Override
            public String description() {
                return "Export Haystack JSON for object subtree. Args: optional rootPath (default root), includePoints (bool).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String rootPath = stringArg(arguments, "rootPath");
                if (rootPath.isBlank()) {
                    rootPath = "root";
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(rootPath, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + rootPath);
                }
                objectAccessService.requireRead(rootPath, auth);
                try {
                    boolean includePoints = boolArg(arguments, "includePoints", true);
                    Map<String, Object> export = haystackExportService.exportSubtree(rootPath, includePoints);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "OK");
                    response.put("rootPath", rootPath);
                    response.put("export", export);
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    static Map<String, Object> ruleRow(BindingRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.id());
        row.put("name", rule.name());
        row.put("enabled", rule.enabled());
        row.put("order", rule.order());
        row.put("condition", rule.condition());
        row.put("expression", rule.expression());
        row.put("targetKind", rule.target().kind());
        row.put("targetVariable", rule.target().variableName());
        row.put("contextPath", rule.target().path());
        row.put("eventName", rule.target().eventName());
        row.put("onContextChange", rule.activators().onContextChange());
        return row;
    }

    private static Map<String, Object> scheduleRow(ScheduleObjectService.ScheduleView view) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", view.path());
        row.put("scheduleId", view.scheduleId());
        row.put("displayName", view.displayName());
        row.put("enabled", view.enabled());
        row.put("intervalMs", view.intervalMs());
        row.put("objectPath", view.objectPath());
        row.put("functionName", view.functionName());
        return row;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static boolean boolArg(Map<String, Object> args, String key, Boolean fallback) {
        Object raw = args != null ? args.get(key) : null;
        if (raw == null) {
            return fallback != null && fallback;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback != null && fallback;
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object raw = args != null ? args.get(key) : null;
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return fallback;
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object raw = args != null ? args.get(key) : null;
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }
}
