package com.ispf.server.bootstrap;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.bootstrap.FixtureModelBootstrap;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.security.PlatformUserService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds the platform with default objects. Device structure comes from Models plugin.
 */
@Component
public class PlatformBootstrap {

    private final BootstrapProperties bootstrapProperties;

    public PlatformBootstrap(BootstrapProperties bootstrapProperties) {
        this.bootstrapProperties = bootstrapProperties;
    }

    public void initialize(ObjectTree tree) {
        initializeCatalog(tree);
        if (bootstrapProperties.isFixturesEnabled()) {
            initializeFixtures(tree);
        }
    }

    private void initializeCatalog(ObjectTree tree) {
        register(tree, PlatformCatalogSortOrder.PLATFORM_ROOT, ObjectType.PLATFORM, null);
        register(tree, "root.tenant", ObjectType.TENANT, null);

        register(tree, PlatformUserService.SECURITY_ROOT, ObjectType.SECURITY, "security-folder-v1");
        register(tree, "root.platform.devices", ObjectType.DEVICES, null);
        register(tree, "root.platform.alert-rules", ObjectType.ALERT_RULES, null);
        register(tree, "root.platform.operator-apps", ObjectType.OPERATOR_APPS, "app-folder-v1");
        register(tree, "root.platform.dashboards", ObjectType.DASHBOARDS, null);
        register(tree, "root.platform.mimics", ObjectType.MIMICS, null);

        registerCatalogFolder(tree, "root.platform.relative-models");
        registerCatalogFolder(tree, "root.platform.absolute-models");
        registerCatalogFolder(tree, "root.platform.instance-types");
        register(tree, "root.platform.reports", ObjectType.REPORTS, null);
        register(tree, "root.platform.correlators", ObjectType.CORRELATORS, null);
        register(tree, "root.platform.workflows", ObjectType.WORKFLOWS, null);

        register(tree, "root.platform.applications", ObjectType.APPLICATIONS, "app-folder-v1");
        registerCatalogFolder(tree, "root.platform.instances");
        register(tree, FederationPaths.FEDERATION_ROOT, ObjectType.AGENT, null);
    }

    private void initializeFixtures(ObjectTree tree) {
        PlatformObject demoDevice = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.demo-sensor-01",
                ObjectType.DEVICE,
                "Demo Sensor 01",
                """
                        Sample MQTT temperature device for learning the platform. \
                        Fixture model mqtt-sensor-v1 is applied when platform fixtures are enabled.""",
                FixtureModelBootstrap.MQTT_SENSOR_MODEL
        );
        tree.register(demoDevice);

        PlatformObject demoDashboard = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.dashboards.demo-sensor",
                ObjectType.DASHBOARD,
                "Demo Sensor Dashboard",
                "Live HMI widgets bound to demo-sensor-01 variables. Open in Dashboard editor or operator mode when linked from an Operator App.",
                "dashboard-v1"
        );
        tree.register(demoDashboard);

        PlatformObject snmpDashboard = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.dashboards.snmp-host-monitoring",
                ObjectType.DASHBOARD,
                "SNMP Host Monitoring",
                "System monitoring dashboard for SNMP agents (Windows/Linux). Requires snmp-localhost or compatible SNMP device under Devices.",
                "dashboard-v1"
        );
        tree.register(snmpDashboard);

        PlatformObject demoWorkflow = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.workflows.demo-alarm-handler",
                ObjectType.WORKFLOW,
                "Demo Alarm Handler",
                "Example BPMN workflow triggered when the demo sensor alarm becomes active. Link an Operator App to surface user tasks in the work queue.",
                "workflow-v1"
        );
        tree.register(demoWorkflow);
    }

    private static void register(ObjectTree tree, String path, ObjectType type, String templateId) {
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        int sortOrder = PlatformCatalogSortOrder.forPath(path)
                .orElseThrow(() -> new IllegalStateException("Missing default sort order: " + path));
        tree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                type,
                entry.displayName(),
                entry.description(),
                templateId,
                sortOrder
        ));
    }

    private static void registerCatalogFolder(ObjectTree tree, String path) {
        if (tree.findByPath(path).isPresent()) {
            return;
        }
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        int sortOrder = PlatformCatalogSortOrder.forPath(path)
                .orElseThrow(() -> new IllegalStateException("Missing default sort order: " + path));
        tree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                ObjectType.MODEL,
                entry.displayName(),
                entry.description(),
                null,
                sortOrder
        ));
    }
}
