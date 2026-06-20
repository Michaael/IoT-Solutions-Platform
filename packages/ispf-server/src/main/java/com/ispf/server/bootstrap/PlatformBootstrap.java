package com.ispf.server.bootstrap;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds the platform with default objects. Device structure comes from Models plugin.
 */
@Component
public class PlatformBootstrap {

    public void initialize(ObjectTree tree) {
        PlatformObject platform = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform",
                ObjectType.CUSTOM,
                "Platform",
                "IoT Solutions Platform Framework",
                null
        );
        tree.register(platform);

        PlatformObject devices = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices",
                ObjectType.CUSTOM,
                "Devices",
                "Connected devices",
                null
        );
        tree.register(devices);

        PlatformObject models = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.models",
                ObjectType.MODEL,
                "Models",
                "Model definitions and object templates",
                null
        );
        tree.register(models);

        PlatformObject dashboards = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.dashboards",
                ObjectType.CUSTOM,
                "Dashboards",
                "HMI dashboards and operator screens",
                null
        );
        tree.register(dashboards);

        PlatformObject demoDevice = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.demo-sensor-01",
                ObjectType.DEVICE,
                "Demo Sensor 01",
                "Simulated MQTT temperature sensor — structure from mqtt-sensor-v1 model",
                "mqtt-sensor-v1"
        );
        tree.register(demoDevice);

        PlatformObject demoDashboard = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.dashboards.demo-sensor",
                ObjectType.DASHBOARD,
                "Demo Sensor Dashboard",
                "Live HMI for demo MQTT temperature sensor",
                "dashboard-v1"
        );
        tree.register(demoDashboard);

        PlatformObject snmpDashboard = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.dashboards.snmp-host-monitoring",
                ObjectType.DASHBOARD,
                "SNMP Host Monitoring",
                "System monitoring dashboard for SNMP agents (Windows/Linux)",
                "dashboard-v1"
        );
        tree.register(snmpDashboard);

        PlatformObject workflows = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.workflows",
                ObjectType.CUSTOM,
                "Workflows",
                "BPMN automation workflows",
                null
        );
        tree.register(workflows);

        PlatformObject applications = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.applications",
                ObjectType.CUSTOM,
                "Applications",
                "Deployed application bundles (functions, reports, schedules)",
                "app-folder-v1"
        );
        tree.register(applications);

        PlatformObject operatorApps = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.operator-apps",
                ObjectType.CUSTOM,
                "Operator Apps",
                "Operator HMI — набор дашбордов для ?mode=operator&app=<id>",
                null
        );
        tree.register(operatorApps);

        PlatformObject demoWorkflow = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.workflows.demo-alarm-handler",
                ObjectType.WORKFLOW,
                "Demo Alarm Handler",
                "Triggers when demo sensor alarm becomes active",
                "workflow-v1"
        );
        tree.register(demoWorkflow);
    }
}
