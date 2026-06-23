package com.ispf.server.dashboard;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ObjectMapper objectMapper;

    public DashboardService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureDashboardStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DASHBOARD) {
            throw new IllegalArgumentException("Not a dashboard object: " + path);
        }
        if (node.getVariable("layout").isPresent()) {
            return;
        }
        String templateId = node.templateId().orElse("dashboard-v1");
        resolveModel(templateId).ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    private Optional<ModelDefinition> resolveModel(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return modelRegistry.findByName("dashboard-v1");
        }
        return modelRegistry.findById(templateId)
                .or(() -> modelRegistry.findByName(templateId));
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
            var widgets = (tools.jackson.databind.node.ArrayNode) root.get("widgets");
            String widgetId = widget.containsKey("id") ? String.valueOf(widget.get("id")) : null;
            if (widgetId != null && !widgetId.isBlank()) {
                for (int i = widgets.size() - 1; i >= 0; i--) {
                    var existing = widgets.get(i);
                    if (existing.isObject() && widgetId.equals(existing.path("id").asString(null))) {
                        widgets.remove(i);
                    }
                }
            }
            widgets.add(objectMapper.valueToTree(normalized));
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
            return Map.of("columns", 12, "rowHeight", 72, "widgets", java.util.List.of());
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
}
