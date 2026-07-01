package com.ispf.server.dashboard;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.dashboard.DashboardContextConstants;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    private static final DataSchema LAYOUT_SCHEMA = DataSchema.builder("layout")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema TITLE_SCHEMA = DataSchema.builder("title")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema REFRESH_SCHEMA = DataSchema.builder("refreshIntervalMs")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema CONTEXT_SCHEMA = DataSchema.builder("dashboardContext")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;
    private final ObjectMapper objectMapper;

    public DashboardService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureDashboardStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        structureService.ensureDashboardStructure(path);
        ensureDashboardContextVariable(path);
    }

    @Transactional
    public void ensureDashboardContextVariable(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable(DashboardContextConstants.VARIABLE).isPresent()) {
            return;
        }
        objectManager.upsertSystemVariable(
                path,
                DashboardContextConstants.VARIABLE,
                CONTEXT_SCHEMA,
                DataRecord.single(CONTEXT_SCHEMA, Map.of("value", DashboardContextSupport.EMPTY_JSON))
        );
    }

    public DashboardContextView getContext(String path) {
        ensureDashboardStructure(path);
        PlatformObject node = objectManager.require(path);
        String json = readContextJson(node);
        Map<String, Object> context = DashboardContextSupport.parseContextJson(json, objectMapper);
        return new DashboardContextView(path, context, json);
    }

    @Transactional
    public DashboardContextView saveContext(String path, Map<String, Object> contextPatch, String updatedBy) {
        ensureDashboardStructure(path);
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        String currentJson = readContextJson(node);
        Map<String, Object> merged = DashboardContextSupport.parseContextJson(currentJson, objectMapper);
        if (contextPatch != null) {
            mergeContextPatch(merged, contextPatch);
        }
        if (updatedBy != null && !updatedBy.isBlank()) {
            merged.put("updatedBy", updatedBy);
        }
        merged.put("updatedAt", java.time.Instant.now().toString());
        String nextJson = DashboardContextSupport.toJson(merged, objectMapper);
        objectManager.upsertSystemVariable(
                path,
                DashboardContextConstants.VARIABLE,
                CONTEXT_SCHEMA,
                DataRecord.single(CONTEXT_SCHEMA, Map.of("value", nextJson))
        );
        PlatformObject updated = objectManager.require(path);
        String finalJson = readContextJson(updated);
        Map<String, Object> finalContext = DashboardContextSupport.parseContextJson(finalJson, objectMapper);
        return new DashboardContextView(path, finalContext, finalJson);
    }

    public DashboardView getDashboard(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        String title = readString(node, "title").orElse(node.displayName());
        int refreshIntervalMs = readInteger(node, "refreshIntervalMs").orElse(5000);
        String layoutJson = readString(node, "layout").orElse(DashboardLayouts.EMPTY_DASHBOARD);
        Object layout = parseLayout(layoutJson);
        return new DashboardView(path, title, refreshIntervalMs, layout, layoutJson);
    }

    @Transactional
    public DashboardView saveLayout(String path, String layoutJson) {
        layoutJson = DashboardWidgetNormalizer.normalizeLayoutJson(layoutJson, objectMapper);
        validateLayoutJson(layoutJson);
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        objectManager.setVariableValue(
                path,
                "layout",
                DataRecord.single(LAYOUT_SCHEMA, Map.of("value", layoutJson))
        );
        return getDashboard(path);
    }

    @Transactional
    public DashboardView updateTitle(String path, String title) {
        objectManager.setVariableValue(
                path,
                "title",
                DataRecord.single(TITLE_SCHEMA, Map.of("value", title))
        );
        return getDashboard(path);
    }

    @Transactional
    public DashboardView updateRefreshInterval(String path, int refreshIntervalMs) {
        objectManager.setVariableValue(
                path,
                "refreshIntervalMs",
                DataRecord.single(REFRESH_SCHEMA, Map.of("value", refreshIntervalMs))
        );
        return getDashboard(path);
    }

    public String resolveTemplateLayout(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template is required");
        }
        return switch (template.trim().toLowerCase()) {
            case "snmp-host-monitoring", "snmp" -> DashboardLayouts.SNMP_HOST_MONITORING_DASHBOARD.trim();
            case "demo-sensor", "demo" -> DashboardLayouts.DEMO_SENSOR_DASHBOARD.trim();
            case "virtual-cluster-overview", "virt-cluster-overview" ->
                    DashboardLayouts.VIRTUAL_CLUSTER_OVERVIEW.trim();
            case "virtual-cluster-detail", "virt-cluster-detail" ->
                    DashboardLayouts.VIRTUAL_CLUSTER_DETAIL.trim();
            case "empty" -> DashboardLayouts.EMPTY_DASHBOARD.trim();
            default -> throw new IllegalArgumentException(
                    "Unknown template: " + template + ". Use snmp-host-monitoring, demo-sensor, "
                            + "virtual-cluster-overview, virtual-cluster-detail, or empty."
            );
        };
    }

    @Transactional
    public DashboardView applyTemplateLayout(String path, String template) {
        return saveLayout(path, resolveTemplateLayout(template));
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public DashboardView addWidget(String path, Map<String, Object> widget) {
        if (widget == null || widget.isEmpty()) {
            throw new IllegalArgumentException("widget object is required");
        }
        Map<String, Object> normalized = DashboardWidgetNormalizer.normalizeWidget(widget, objectMapper);
        DashboardView current = getDashboard(path);
        try {
            var root = objectMapper.readTree(current.layoutJson());
            if (!root.isObject() || !root.has("widgets") || !root.get("widgets").isArray()) {
                throw new IllegalArgumentException("Dashboard layout must contain a widgets array");
            }
            int columns = root.path("columns").asInt(DashboardWidgetPlacement.DEFAULT_COLUMNS);
            int rowHeight = root.path("rowHeight").asInt(DashboardWidgetPlacement.DEFAULT_ROW_HEIGHT);
            List<Map<String, Object>> existingWidgets = new java.util.ArrayList<>();
            var widgets = (tools.jackson.databind.node.ArrayNode) root.get("widgets");
            String widgetId = widget.containsKey("id") ? String.valueOf(widget.get("id")) : null;
            for (int i = widgets.size() - 1; i >= 0; i--) {
                var existing = widgets.get(i);
                if (!existing.isObject()) {
                    continue;
                }
                if (widgetId != null && !widgetId.isBlank() && widgetId.equals(existing.path("id").asString(null))) {
                    widgets.remove(i);
                    continue;
                }
                existingWidgets.add(objectMapper.convertValue(existing, Map.class));
            }
            Map<String, Object> placed = DashboardWidgetPlacement.prepareNewWidget(
                    normalized,
                    existingWidgets,
                    columns,
                    rowHeight
            );
            widgets.add(objectMapper.valueToTree(placed));
            return saveLayout(path, objectMapper.writeValueAsString(root));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid layout JSON", e);
        }
    }

    public static List<String> layoutTemplateNames() {
        return List.of(
                "snmp-host-monitoring",
                "demo-sensor",
                "virtual-cluster-overview",
                "virtual-cluster-detail",
                "empty"
        );
    }

    private void validateLayoutJson(String layoutJson) {
        try {
            objectMapper.readTree(layoutJson);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid layout JSON", e);
        }
    }

    private Object parseLayout(String layoutJson) {
        try {
            return objectMapper.readValue(layoutJson, Object.class);
        } catch (JacksonException e) {
            return Map.of(
                    "columns", DashboardWidgetPlacement.DEFAULT_COLUMNS,
                    "rowHeight", DashboardWidgetPlacement.DEFAULT_ROW_HEIGHT,
                    "widgets", java.util.List.of()
            );
        }
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    private static Optional<Integer> readInteger(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                    return Integer.parseInt(String.valueOf(value));
                });
    }

    public record DashboardView(
            String path,
            String title,
            int refreshIntervalMs,
            Object layout,
            String layoutJson
    ) {
    }

    public record DashboardContextView(
            String path,
            Map<String, Object> context,
            String contextJson
    ) {
    }

    @SuppressWarnings("unchecked")
    private static void mergeContextPatch(Map<String, Object> target, Map<String, Object> patch) {
        if (patch.containsKey("selection") && patch.get("selection") instanceof Map<?, ?> selection) {
            Map<String, Object> current = (Map<String, Object>) target.computeIfAbsent(
                    "selection",
                    ignored -> new LinkedHashMap<>()
            );
            current.putAll((Map<String, Object>) selection);
        }
        if (patch.containsKey("params") && patch.get("params") instanceof Map<?, ?> params) {
            Map<String, Object> current = (Map<String, Object>) target.computeIfAbsent(
                    "params",
                    ignored -> new LinkedHashMap<>()
            );
            current.putAll((Map<String, Object>) params);
        }
        if (patch.containsKey("widgets") && patch.get("widgets") instanceof Map<?, ?> widgets) {
            Map<String, Object> current = (Map<String, Object>) target.computeIfAbsent(
                    "widgets",
                    ignored -> new LinkedHashMap<>()
            );
            current.putAll((Map<String, Object>) widgets);
        }
    }

    private static String readContextJson(PlatformObject node) {
        return node.getVariable(DashboardContextConstants.VARIABLE)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .orElse(DashboardContextSupport.EMPTY_JSON);
    }
}
