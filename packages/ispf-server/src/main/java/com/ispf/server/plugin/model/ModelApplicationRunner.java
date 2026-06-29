package com.ispf.server.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.FixtureModelBootstrap;
import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.server.dashboard.DashboardDemoRulesBootstrap;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.workflow.WorkflowDefinitions;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelVariableDefinition;
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
    private final ModelBindingRulesMerger bindingRulesMerger;
    private final ModelApplicationService modelApplicationService;
    private final SystemObjectStructureService structureService;
    private final DeviceProvisioningService deviceProvisioningService;
    private final DashboardDemoRulesBootstrap dashboardDemoRulesBootstrap;

    public ModelApplicationRunner(
            ModelEngine modelEngine,
            ModelRegistry modelRegistry,
            ObjectManager objectManager,
            ModelBindingRulesMerger bindingRulesMerger,
            ModelApplicationService modelApplicationService,
            SystemObjectStructureService structureService,
            DeviceProvisioningService deviceProvisioningService,
            DashboardDemoRulesBootstrap dashboardDemoRulesBootstrap
    ) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
        this.objectManager = objectManager;
        this.bindingRulesMerger = bindingRulesMerger;
        this.modelApplicationService = modelApplicationService;
        this.structureService = structureService;
        this.deviceProvisioningService = deviceProvisioningService;
        this.dashboardDemoRulesBootstrap = dashboardDemoRulesBootstrap;
    }

    public void restoreAttachments() {
        modelApplicationService.restoreAttachments();
    }

    private void applyModelWithRules(ModelDefinition model, String path) {
        modelApplicationService.applyModelWithRules(model, path, model.parameters());
    }

    public void applyDemoModels() {
        ensurePlatformDemoNodes();
        modelRegistry.findByName(FixtureModelBootstrap.MQTT_SENSOR_MODEL).ifPresent(model -> {
            String devicePath = "root.platform.devices.demo-sensor-01";
            applyModelWithRules(model, devicePath);
            deviceProvisioningService.provisionDriver(devicePath, "virtual", 2000, false);
            objectManager.persistNodeTree(devicePath);
        });

        modelRegistry.findByName("dashboard-v1").ifPresent(model -> {
            String path = "root.platform.dashboards.demo-sensor";
            structureService.ensureDashboardStructure(path);
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
            structureService.ensureDashboardStructure(path);
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
            structureService.ensureWorkflowStructure(path);
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
            workflow.setVariableValue(
                    "operatorAppId",
                    DataRecord.single(
                            DataSchema.builder("operatorAppId").field("value", FieldType.STRING).build(),
                            Map.of("value", "platform")
                    )
            );
            objectManager.persistNodeTree(path);
        });

        modelRegistry.findByName(FixtureModelBootstrap.VENDOR_SENSOR_EXT_MODEL).ifPresent(model -> {
            String path = FixtureModelBootstrap.VENDOR_SENSOR_DEMO_PATH;
            if (objectManager.tree().findByPath(path).isEmpty()) {
                modelApplicationService.instantiateWithRules(
                        model.id(),
                        "root.platform.devices",
                        "vendor-sensor-demo",
                        Map.of()
                );
            } else {
                applyModelWithRules(model, path);
            }
            objectManager.persistNodeTree(path);
        });

        ensureMeterMqttBusDevice();
    }

    /** Idempotent demo platform rules — runs on every startup (prod has fixtures disabled). */
    public void ensureDashboardDemoRules() {
        dashboardDemoRulesBootstrap.ensureDemoRules();
    }

    private void ensureMeterMqttBusDevice() {
        modelRegistry.findByName(FixtureModelBootstrap.MQTT_METER_BUS_MODEL).ifPresent(model -> {
            String path = FixtureModelBootstrap.MQTT_METER_BUS_PATH;
            if (objectManager.tree().findByPath(path).isEmpty()) {
                objectManager.create(
                        "root.platform.devices",
                        "mqtt-meter-bus",
                        ObjectType.DEVICE,
                        "MQTT Meter Bus",
                        "Subscribes to MQTT topic meter and auto-creates Meters instances from JSON payloads",
                        null
                );
            }
            applyModelWithRules(model, path);
            ensureMeterBusDriverVariables(path);
            objectManager.persistNodeTree(path);
        });
    }

    private void ensureMeterBusDriverVariables(String path) {
        structureService.ensureDeviceDriverStructure(path);
        PlatformObject device = objectManager.require(path);
        DataSchema stringSchema = DataSchema.builder("stringValue").field("value", FieldType.STRING).build();
        String mappings = readVariableStringField(device, "driverPointMappingsJson", "value");
        if (!mappings.contains("\"ingress\"") || !mappings.contains("meter")) {
            device.setVariableValue(
                    "driverPointMappingsJson",
                    DataRecord.single(stringSchema, Map.of("value", FixtureModelBootstrap.METER_INGRESS_POINT_MAPPINGS))
            );
        }
        String config = readVariableStringField(device, "driverConfigJson", "value");
        if (!config.contains("ingressVariable") || !config.contains("lastIngress")
                || !config.contains("ingressPayloadLanes")) {
            device.setVariableValue(
                    "driverConfigJson",
                    DataRecord.single(stringSchema, Map.of("value", FixtureModelBootstrap.METER_INGRESS_DRIVER_CONFIG))
            );
        }
        if (device.getVariable("driverId").isPresent()) {
            device.setVariableValue(
                    "driverId",
                    DataRecord.single(stringSchema, Map.of("value", "mqtt"))
            );
        }
        if (device.getVariable("driverPollIntervalMs").isPresent()) {
            device.setVariableValue(
                    "driverPollIntervalMs",
                    DataRecord.single(
                            DataSchema.builder("integerValue").field("value", FieldType.INTEGER).build(),
                            Map.of("value", 5000)
                    )
            );
        }
    }

    private void ensurePlatformDemoNodes() {
        ensureNode("root.platform.devices", ObjectType.DEVICES, "Devices", "Connected devices");
        ensureNode("root.platform.devices.demo-sensor-01", ObjectType.DEVICE, "Demo Sensor 01", "Simulated MQTT temperature sensor (fixture)", FixtureModelBootstrap.MQTT_SENSOR_MODEL);
        ensureNode("root.platform.dashboards", ObjectType.DASHBOARDS, "Dashboards", "HMI dashboards");
        ensureNode("root.platform.dashboards.demo-sensor", ObjectType.DASHBOARD, "Demo Sensor Dashboard", "Live HMI for demo MQTT temperature sensor", "dashboard-v1");
        ensureNode("root.platform.dashboards.snmp-host-monitoring", ObjectType.DASHBOARD, "SNMP Host Monitoring", "System monitoring dashboard for SNMP agents (Windows/Linux)", "dashboard-v1");
        ensureNode("root.platform.reports", ObjectType.REPORTS, "Reports", "SQL reports (REQ-PF-12)");
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
        modelRegistry.findByName(FixtureModelBootstrap.SNMP_AGENT_MODEL).ifPresent(model -> {
            if (objectManager.tree().findByPath(FixtureModelBootstrap.SNMP_LOCALHOST_PATH).isEmpty()) {
                modelApplicationService.instantiateWithRules(
                        model.id(),
                        "root.platform.devices",
                        "snmp-localhost",
                        Map.of()
                );
            } else {
                syncSnmpAgentDevice(model, FixtureModelBootstrap.SNMP_LOCALHOST_PATH);
            }
            objectManager.persistNodeTree(FixtureModelBootstrap.SNMP_LOCALHOST_PATH);
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
            } else {
                target.addVariable(new Variable(
                        varDef.name(),
                        varDef.schema(),
                        varDef.readable(),
                        varDef.writable(),
                        varDef.defaultValue(),
                        varDef.historyEnabled(),
                        varDef.historyRetentionDays()
                ));
                changed = true;
            }
        }
        if (!model.bindingRules().isEmpty()) {
            bindingRulesMerger.mergeModelRules(objectPath, model, model.parameters());
            changed = true;
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
                DataRecord.single(stringSchema, Map.of("value", FixtureModelBootstrap.SNMP_POINT_MAPPINGS))
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
                        DataRecord.single(stringSchema, Map.of("value", FixtureModelBootstrap.SNMP_DRIVER_CONFIG))
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
