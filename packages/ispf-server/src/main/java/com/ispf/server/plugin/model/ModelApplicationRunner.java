package com.ispf.server.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import com.ispf.core.object.ObjectType;
import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.workflow.WorkflowDefinitions;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

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
    }

    private void ensurePlatformDemoNodes() {
        ensureNode("root.platform.devices", ObjectType.CUSTOM, "Devices", "Connected devices");
        ensureNode("root.platform.devices.demo-sensor-01", ObjectType.DEVICE, "Demo Sensor 01", "Simulated MQTT temperature sensor", "mqtt-sensor-v1");
        ensureNode("root.platform.dashboards", ObjectType.CUSTOM, "Dashboards", "HMI dashboards");
        ensureNode("root.platform.dashboards.demo-sensor", ObjectType.DASHBOARD, "Demo Sensor Dashboard", "Live HMI for demo MQTT temperature sensor", "dashboard-v1");
        ensureNode("root.platform.dashboards.snmp-host-monitoring", ObjectType.DASHBOARD, "SNMP Host Monitoring", "System monitoring dashboard for SNMP agents (Windows/Linux)", "dashboard-v1");
        ensureNode("root.platform.workflows", ObjectType.CUSTOM, "Workflows", "BPMN automation workflows");
        ensureNode("root.platform.workflows.demo-alarm-handler", ObjectType.WORKFLOW, "Demo Alarm Handler", "Triggers when demo sensor alarm becomes active", "workflow-v1");
    }

    private void ensureNode(String path, ObjectType type, String displayName, String description) {
        ensureNode(path, type, displayName, description, null);
    }

    private void ensureNode(String path, ObjectType type, String displayName, String description, String modelId) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        int lastDot = path.lastIndexOf('.');
        String parentPath = path.substring(0, lastDot);
        String name = path.substring(lastDot + 1);
        objectManager.create(parentPath, name, type, displayName, description, modelId);
    }

    public void ensureSnmpLocalhostDevice() {
        if (objectManager.tree().findByPath(ModelBootstrap.SNMP_LOCALHOST_PATH).isPresent()) {
            return;
        }
        modelRegistry.findByName(ModelBootstrap.SNMP_AGENT_MODEL).ifPresent(model -> {
            modelEngine.instantiateModel(
                    model.id(),
                    "root.platform.devices",
                    "snmp-localhost",
                    Map.of()
            );
            objectManager.persistNodeTree(ModelBootstrap.SNMP_LOCALHOST_PATH);
        });
    }
}
