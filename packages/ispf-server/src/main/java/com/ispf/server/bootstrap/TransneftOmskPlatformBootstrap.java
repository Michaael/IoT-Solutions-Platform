package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.mimic.MimicService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seeds the Transneft Omsk RDP SCADA mimic demo (devices, mimic, dashboard).
 */
@Component
public class TransneftOmskPlatformBootstrap {

    private record TankSeed(int number, double initialLevelMm, double rateBiasMmPerHour) {
    }

    private static final TankSeed[] YELLOW_TANKS = {
            new TankSeed(11, 7850, -430),
            new TankSeed(12, 6210, 85),
            new TankSeed(13, 5480, -120),
            new TankSeed(14, 9020, 40),
            new TankSeed(15, 3100, -210),
            new TankSeed(16, 6740, 15),
            new TankSeed(17, 4890, -55),
    };

    private static final TankSeed[] BLUE_TANKS = {
            new TankSeed(18, 8120, 220),
            new TankSeed(19, 4550, -180),
            new TankSeed(20, 7360, 95),
            new TankSeed(21, 5920, -60),
            new TankSeed(22, 2680, 130),
            new TankSeed(23, 8410, -90),
            new TankSeed(24, 5030, 45),
    };

    private final TransneftOmskModelBootstrap modelBootstrap;
    private final ObjectTemplateService templateService;
    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final MimicService mimicService;
    private final DriverRuntimeService driverRuntimeService;
    private final ApplicationDataService applicationDataService;
    private final BootstrapProperties bootstrapProperties;

    public TransneftOmskPlatformBootstrap(
            TransneftOmskModelBootstrap modelBootstrap,
            ObjectTemplateService templateService,
            ObjectManager objectManager,
            DashboardService dashboardService,
            MimicService mimicService,
            DriverRuntimeService driverRuntimeService,
            ApplicationDataService applicationDataService,
            BootstrapProperties bootstrapProperties
    ) {
        this.modelBootstrap = modelBootstrap;
        this.templateService = templateService;
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.mimicService = mimicService;
        this.driverRuntimeService = driverRuntimeService;
        this.applicationDataService = applicationDataService;
        this.bootstrapProperties = bootstrapProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 23)
    public void onReady() {
        if (!bootstrapProperties.isFixturesEnabled()) {
            return;
        }
        modelBootstrap.ensureTransneftModels();
        registerApplication();
        ensureFolder();
        for (TankSeed tank : allTanks()) {
            ensureTank(tank);
        }
        ensureHub();
        ensureMimic(TransneftOmskPaths.MIMIC_RDP, "РДП Омск — резервуарный парк", TransneftOmskMimicDocument.DIAGRAM_JSON);
        ensureDashboard(
                TransneftOmskPaths.DASHBOARD_RDP,
                "РДП Омск — мнемосхема",
                TransneftOmskDashboardLayouts.RDP_MIMIC
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 4)
    public void startDriversAfterBootstrap() {
        if (!bootstrapProperties.isFixturesEnabled()) {
            return;
        }
        startDrivers();
    }

    private List<TankSeed> allTanks() {
        List<TankSeed> tanks = new ArrayList<>();
        for (TankSeed tank : YELLOW_TANKS) {
            tanks.add(tank);
        }
        for (TankSeed tank : BLUE_TANKS) {
            tanks.add(tank);
        }
        return tanks;
    }

    private void registerApplication() {
        try {
            applicationDataService.register(
                    TransneftOmskPaths.APP_ID,
                    TransneftOmskPaths.DISPLAY_NAME,
                    "",
                    "app_transneft_omsk"
            );
        } catch (Exception ignored) {
            // already registered
        }
    }

    private void ensureFolder() {
        if (objectManager.tree().findByPath(TransneftOmskPaths.FOLDER).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    TransneftOmskPaths.PLANT_FOLDER_NAME,
                    ObjectType.CUSTOM,
                    TransneftOmskPaths.DISPLAY_NAME,
                    "Transneft Omsk RDP tank farm SCADA demo",
                    null
            );
        }
    }

    private void ensureTank(TankSeed tank) {
        String name = "tank-" + tank.number();
        String path = TransneftOmskPaths.tank(tank.number());
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    TransneftOmskPaths.FOLDER,
                    name,
                    ObjectType.DEVICE,
                    "Резервуар №" + tank.number(),
                    "",
                    TransneftOmskModelBootstrap.TANK_MODEL
            );
        }
        templateService.applyTemplate(path, TransneftOmskModelBootstrap.TANK_MODEL);
        setStringVar(
                path,
                "driverConfigJson",
                TransneftOmskModelBootstrap.tankDriverConfig(tank.number(), tank.initialLevelMm(), tank.rateBiasMmPerHour())
        );
    }

    private void ensureHub() {
        String path = TransneftOmskPaths.RDP_HUB;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    TransneftOmskPaths.FOLDER,
                    "rdp-hub",
                    ObjectType.CUSTOM,
                    "РДП — коллектор",
                    "",
                    TransneftOmskModelBootstrap.RDP_HUB_MODEL
            );
        }
        templateService.applyTemplate(path, TransneftOmskModelBootstrap.RDP_HUB_MODEL);
        setStringVar(path, "driverConfigJson", TransneftOmskModelBootstrap.RDP_HUB_DRIVER_CONFIG);
    }

    private void setStringVar(String path, String name, String value) {
        PlatformObject node = objectManager.require(path);
        node.setVariableValue(
                name,
                DataRecord.single(DataSchema.builder("v").field("value", FieldType.STRING).build(), Map.of("value", value))
        );
        objectManager.persistNodeTree(path);
    }

    private void ensureMimic(String path, String title, String diagramJson) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            int dot = path.lastIndexOf('.');
            objectManager.create(path.substring(0, dot), path.substring(dot + 1), ObjectType.MIMIC, title, "", "mimic-v1");
        }
        mimicService.ensureMimicStructure(path);
        mimicService.updateTitle(path, title);
        mimicService.saveDiagram(path, diagramJson);
        mimicService.getMimic(path);
    }

    private void ensureDashboard(String path, String title, String layout) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            int dot = path.lastIndexOf('.');
            objectManager.create(path.substring(0, dot), path.substring(dot + 1), ObjectType.DASHBOARD, title, "", "dashboard-v1");
        }
        dashboardService.ensureDashboardStructure(path);
        dashboardService.updateTitle(path, title);
        dashboardService.saveLayout(path, layout);
        dashboardService.updateRefreshInterval(path, 3000);
    }

    private void startDrivers() {
        List<String> paths = new ArrayList<>();
        paths.add(TransneftOmskPaths.RDP_HUB);
        for (TankSeed tank : allTanks()) {
            paths.add(TransneftOmskPaths.tank(tank.number()));
        }
        for (String path : paths) {
            try {
                driverRuntimeService.start(path);
            } catch (Exception ignored) {
                // driver may already run
            }
        }
    }
}
