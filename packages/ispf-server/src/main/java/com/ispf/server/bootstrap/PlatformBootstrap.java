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
                ObjectType.PLATFORM,
                "Platform",
                "IoT Solutions Platform Framework",
                null
        );
        tree.register(platform);

        PlatformObject tenants = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.tenant",
                ObjectType.TENANT,
                "Tenants",
                "Multi-tenant namespaces (root.tenant.{id}.platform.*)",
                null
        );
        tree.register(tenants);

        PlatformObject devices = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices",
                ObjectType.DEVICES,
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
                "Legacy model catalog (see typed catalogs)",
                null
        );
        tree.register(models);

        registerCatalogFolder(tree, "root.platform.relative-models", "Relative Models", "Mixin blueprints for existing objects");
        registerCatalogFolder(tree, "root.platform.instance-types", "Instance Types", "Blueprints for new object instances");
        registerCatalogFolder(tree, "root.platform.absolute-models", "Absolute Models", "Singleton object blueprints");
        registerCatalogFolder(tree, "root.platform.instances", "Instances", "Singleton absolute model instances");

        PlatformObject dashboards = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.dashboards",
                ObjectType.DASHBOARDS,
                "Dashboards",
                "HMI dashboards and operator screens",
                null
        );
        tree.register(dashboards);

        PlatformObject reports = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.reports",
                ObjectType.REPORTS,
                "Reports",
                "SQL reports (REQ-PF-12)",
                null
        );
        tree.register(reports);

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
                ObjectType.WORKFLOWS,
                "Workflows",
                "BPMN automation workflows",
                null
        );
        tree.register(workflows);

        PlatformObject alertRules = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.alert-rules",
                ObjectType.ALERT_RULES,
                "Alert Rules",
                "CEL rules that publish events on variable changes",
                null
        );
        tree.register(alertRules);

        PlatformObject correlators = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.correlators",
                ObjectType.CORRELATORS,
                "Correlators",
                "Event patterns that trigger workflows",
                null
        );
        tree.register(correlators);

        PlatformObject applications = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.applications",
                ObjectType.APPLICATIONS,
                "Applications",
                "Deployed application bundles (functions, reports, schedules)",
                "app-folder-v1"
        );
        tree.register(applications);

        PlatformObject operatorApps = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.operator-apps",
                ObjectType.OPERATOR_APPS,
                "Operator Apps",
                "Operator HMI — набор дашбордов для ?mode=operator&app=<id>",
                null
        );
        tree.register(operatorApps);

        PlatformObject federation = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.federation",
                ObjectType.AGENT,
                "Federation",
                "Remote ISPF sites — peer registry and cross-site object proxy (PF-13 spike)",
                null
        );
        tree.register(federation);

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

    private static void registerCatalogFolder(ObjectTree tree, String path, String displayName, String description) {
        if (tree.findByPath(path).isPresent()) {
            return;
        }
        tree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                ObjectType.MODEL,
                displayName,
                description,
                null
        ));
    }
}
