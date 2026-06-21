package com.ispf.server.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import com.ispf.core.object.ObjectType;
import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.workflow.WorkflowDefinitions;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelVariableDefinition;
import com.ispf.plugin.model.ModelBindingDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applies built-in models to demo objects after bootstrap.
 */
@Component
public class ModelApplicationRunner {

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;
    private final ObjectManager objectManager;

    public ModelApplicationRunner(
            ModelEngine modelEngine,
            ModelRegistry modelRegistry,
            ObjectManager objectManager
    ) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
        this.objectManager = objectManager;
    }

    public void applyDemoModels() {
        ensurePlatformDemoNodes();
        modelRegistry.findByName("mqtt-sensor-v1").ifPresent(model -> {
            String path = "root.platform.devices.demo-sensor-01";
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });

        modelRegistry.findByName("dashboard-v1").ifPresent(model -> {
            String path = "root.platform.dashboards.demo-sensor";
            modelEngine.applyModel(model.id(), path);
            PlatformObject dashboard = objectManager.require(path);
            dashboard.setVariableValue(
                    "title",
                    DataRecord.single(
                            DataSchema.builder("title").field("value", FieldType.STRING).build(),
                            Map.of("value", "Demo Sensor Dashboard")
                    )
            );
            dashboard.setVariableValue(
                    "layout",
                    DataRecord.single(
                            DataSchema.builder("layout").field("value", FieldType.STRING).build(),
                            Map.of("value", DashboardLayouts.DEMO_SENSOR_DASHBOARD.trim())
                    )
            );
            objectManager.persistNodeTree(path);
        });

        modelRegistry.findByName("dashboard-v1").ifPresent(model -> {
            String path = "root.platform.dashboards.snmp-host-monitoring";
            modelEngine.applyModel(model.id(), path);
            PlatformObject dashboard = objectManager.require(path);
            dashboard.setVariableValue(
                    "title",
                    DataRecord.single(
                            DataSchema.builder("title").field("value", FieldType.STRING).build(),
                            Map.of("value", "SNMP Host Monitoring")
                    )
            );
            dashboard.setVariableValue(
                    "refreshIntervalMs",
                    DataRecord.single(
                            DataSchema.builder("refreshIntervalMs").field("value", FieldType.INTEGER).build(),
                            Map.of("value", 10000)
                    )
            );
            dashboard.setVariableValue(
                    "layout",
                    DataRecord.single(
                            DataSchema.builder("layout").field("value", FieldType.STRING).build(),
                            Map.of("value", DashboardLayouts.SNMP_HOST_MONITORING_DASHBOARD.trim())
                    )
            );
            objectManager.persistNodeTree(path);
        });

        modelRegistry.findByName("workflow-v1").ifPresent(model -> {
            String path = "root.platform.workflows.demo-alarm-handler";
            modelEngine.applyModel(model.id(), path);
            PlatformObject workflow = objectManager.require(path);
            workflow.setVariableValue(
                    "title",
                    DataRecord.single(
                            DataSchema.builder("title").field("value", FieldType.STRING).build(),
                            Map.of("value", "Demo Alarm Handler")
                    )
            );
            workflow.setVariableValue(
                    "status",
                    DataRecord.single(
                            DataSchema.builder("status").field("value", FieldType.STRING).build(),
                            Map.of("value", WorkflowLifecycleStatus.ACTIVE.name())
                    )
            );
            workflow.setVariableValue(
                    "bpmnXml",
                    DataRecord.single(
                            DataSchema.builder("bpmnXml").field("value", FieldType.STRING).build(),
                            Map.of("value", WorkflowDefinitions.DEMO_ALARM_HANDLER.trim())
                    )
            );
            workflow.setVariableValue(
                    "triggerJson",
                    DataRecord.single(
                            DataSchema.builder("triggerJson").field("value", FieldType.STRING).build(),
                            Map.of("value", WorkflowDefinitions.DEMO_ALARM_TRIGGER.trim())
                    )
            );
            objectManager.persistNodeTree(path);
        });

        modelRegistry.findByName("vendor-sensor-ext-v1").ifPresent(model -> {
            String path = "root.platform.devices.vendor-sensor-demo";
            if (objectManager.tree().findByPath(path).isEmpty()) {
                modelEngine.instantiateModel(model.id(), "root.platform.devices", "vendor-sensor-demo", Map.of());
            } else {
                modelEngine.applyModel(model.id(), path);
            }
            objectManager.persistNodeTree(path);
        });
    }

    private void ensurePlatformDemoNodes() {
        ensureNode("root.platform.devices", ObjectType.DEVICES, "Devices", "Connected devices");
        ensureNode("root.platform.devices.demo-sensor-01", ObjectType.DEVICE, "Demo Sensor 01", "Simulated MQTT temperature sensor", "mqtt-sensor-v1");
        ensureNode("root.platform.dashboards", ObjectType.DASHBOARDS, "Dashboards", "HMI dashboards");
        ensureNode("root.platform.dashboards.demo-sensor", ObjectType.DASHBOARD, "Demo Sensor Dashboard", "Live HMI for demo MQTT temperature sensor", "dashboard-v1");
        ensureNode("root.platform.dashboards.snmp-host-monitoring", ObjectType.DASHBOARD, "SNMP Host Monitoring", "System monitoring dashboard for SNMP agents (Windows/Linux)", "dashboard-v1");
        ensureNode("root.platform.workflows", ObjectType.WORKFLOWS, "Workflows", "BPMN automation workflows");
        ensureNode("root.platform.workflows.demo-alarm-handler", ObjectType.WORKFLOW, "Demo Alarm Handler", "Triggers when demo sensor alarm becomes active", "workflow-v1");
        ensureNode("root.platform.alert-rules", ObjectType.ALERT_RULES, "Alert Rules", "CEL rules that publish events on variable changes");
        ensureNode("root.platform.correlators", ObjectType.CORRELATORS, "Correlators", "Event patterns that trigger workflows");
    }

    private void ensureNode(String path, ObjectType type, String displayName, String description) {
        ensureNode(path, type, displayName, description, null);
    }

    private void ensureNode(String path, ObjectType type, String displayName, String description, String modelId) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.reconcileType(path, type);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        String parentPath = path.substring(0, lastDot);
        String name = path.substring(lastDot + 1);
        objectManager.create(parentPath, name, type, displayName, description, modelId);
    }

    public void ensureSnmpLocalhostDevice() {
        modelRegistry.findByName(ModelBootstrap.SNMP_AGENT_MODEL).ifPresent(model -> {
            if (objectManager.tree().findByPath(ModelBootstrap.SNMP_LOCALHOST_PATH).isEmpty()) {
                modelEngine.instantiateModel(
                        model.id(),
                        "root.platform.devices",
                        "snmp-localhost",
                        Map.of()
                );
            } else {
                syncSnmpAgentDevice(model, ModelBootstrap.SNMP_LOCALHOST_PATH);
            }
            objectManager.persistNodeTree(ModelBootstrap.SNMP_LOCALHOST_PATH);
        });
    }

    /**
     * Aligns per-variable history metadata on all objects instantiated from a model.
     */
    public void syncAllModelBackedVariableMetadata() {
        for (PlatformObject node : objectManager.tree().all()) {
            Optional<String> templateId = node.templateId();
            if (templateId.isEmpty() || templateId.get().isBlank()) {
                continue;
            }
            Optional<ModelDefinition> model = modelRegistry.findById(templateId.get());
            if (model.isEmpty()) {
                model = modelRegistry.findByName(templateId.get());
            }
            if (model.isEmpty()) {
                continue;
            }
            if (syncModelVariableMetadata(model.get(), node.path())) {
                objectManager.persistNodeTree(node.path());
            }
        }
    }

    /**
     * Adds variables and OID mappings missing on demo SNMP devices created before HOST-RESOURCES-MIB support.
     */
    private void syncSnmpAgentDevice(ModelDefinition model, String devicePath) {
        if (syncModelVariableMetadata(model, devicePath)) {
            // in-memory updates only; caller persists
        }
        ensureSnmpDriverMappings(objectManager.require(devicePath));
    }

    private boolean syncModelVariableMetadata(ModelDefinition model, String objectPath) {
        PlatformObject target = objectManager.require(objectPath);
        boolean changed = false;
        for (ModelVariableDefinition varDef : model.variables()) {
            Optional<Variable> existingOpt = target.getVariable(varDef.name());
            if (existingOpt.isPresent()) {
                Variable existing = existingOpt.get();
                if (existing.historyEnabled() != varDef.historyEnabled()
                        || !Objects.equals(
                        existing.historyRetentionDays().orElse(null),
                        varDef.historyRetentionDays()
                )) {
                    target.addVariable(existing.withHistorySettings(
                            varDef.historyEnabled(),
                            varDef.historyRetentionDays()
                    ));
                    changed = true;
                }
                String expectedBinding = model.bindingFor(varDef.name());
                if (!Objects.equals(existing.bindingExpression().orElse(null), expectedBinding)) {
                    target.addVariable(new Variable(
                            existing.name(),
                            existing.schema(),
                            existing.readable(),
                            existing.writable(),
                            expectedBinding,
                            existing.value().orElse(varDef.defaultValue()),
                            existing.historyEnabled(),
                            existing.historyRetentionDays().orElse(null)
                    ));
                    changed = true;
                }
            } else {
                target.addVariable(new Variable(
                        varDef.name(),
                        varDef.schema(),
                        varDef.readable(),
                        varDef.writable(),
                        model.bindingFor(varDef.name()),
                        varDef.defaultValue(),
                        varDef.historyEnabled(),
                        varDef.historyRetentionDays()
                ));
                changed = true;
            }
        }
        return changed;
    }

    private void ensureSnmpDriverMappings(PlatformObject device) {
        Set<String> requiredMappingKeys = Set.of(
                "sysName", "sysDescr", "sysUpTime", "sysLocation", "sysContact",
                "hrMemorySize", "hrSystemProcesses", "hrSystemNumUsers", "ifNumber",
                "ifInOctets", "ifOutOctets", "hrProcessorLoad"
        );
        String currentMappings = readVariableStringField(device, "driverPointMappingsJson", "value");
        boolean complete = requiredMappingKeys.stream().allMatch(currentMappings::contains);
        if (complete) {
            return;
        }
        DataSchema stringSchema = DataSchema.builder("stringValue").field("value", FieldType.STRING).build();
        device.setVariableValue(
                "driverPointMappingsJson",
                DataRecord.single(stringSchema, Map.of("value", ModelBootstrap.SNMP_POINT_MAPPINGS))
        );
        if (device.getVariable("driverId").isPresent()) {
            device.setVariableValue(
                    "driverId",
                    DataRecord.single(stringSchema, Map.of("value", "snmp"))
            );
        }
        if (device.getVariable("driverConfigJson").isPresent()) {
            String config = readVariableStringField(device, "driverConfigJson", "value");
            if (config.isBlank() || config.equals("{}")) {
                device.setVariableValue(
                        "driverConfigJson",
                        DataRecord.single(stringSchema, Map.of("value", ModelBootstrap.SNMP_DRIVER_CONFIG))
                );
            }
        }
    }

    private static String readVariableStringField(PlatformObject device, String variableName, String field) {
        return device.getVariable(variableName)
                .flatMap(Variable::value)
                .filter(record -> record.rowCount() > 0)
                .map(record -> record.get(field, 0))
                .map(String::valueOf)
                .orElse("");
    }
}
