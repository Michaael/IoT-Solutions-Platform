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
import com.ispf.server.operator.OperatorAppUiService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seeds the anonymized tank-farm SCADA mimic demo (devices, mimic, dashboard).
 */
@Component
public class TankFarmPlatformBootstrap {

    private record TankSeed(int number, double initialLevelMm, double rateBiasMmPerHour) {
    }

    private static final TankSeed[] TYPE_A_TANKS = {
            new TankSeed(11, 1662, -430),
            new TankSeed(12, 1667, 85),
            new TankSeed(13, 1481, -120),
            new TankSeed(14, 1597, 40),
            new TankSeed(15, 1620, -210),
            new TankSeed(16, 6352, 15),
            new TankSeed(17, 1762, -55),
    };

    private static final TankSeed[] TYPE_B_TANKS = {
            new TankSeed(18, 11726, 220),
            new TankSeed(19, 1702, -180),
            new TankSeed(20, 5858, 95),
            new TankSeed(21, 4393, -60),
            new TankSeed(22, 1712, 130),
            new TankSeed(23, 1280, -90),
            new TankSeed(24, 1230, 45),
    };

    private final TankFarmModelBootstrap modelBootstrap;
    private final ObjectTemplateService templateService;
    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final MimicService mimicService;
    private final DriverRuntimeService driverRuntimeService;
    private final ApplicationDataService applicationDataService;
    private final OperatorAppUiService operatorAppUiService;
    private final BootstrapProperties bootstrapProperties;

    public TankFarmPlatformBootstrap(
            TankFarmModelBootstrap modelBootstrap,
            ObjectTemplateService templateService,
            ObjectManager objectManager,
            DashboardService dashboardService,
            MimicService mimicService,
            DriverRuntimeService driverRuntimeService,
            ApplicationDataService applicationDataService,
            OperatorAppUiService operatorAppUiService,
            BootstrapProperties bootstrapProperties
    ) {
        this.modelBootstrap = modelBootstrap;
        this.templateService = templateService;
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.mimicService = mimicService;
        this.driverRuntimeService = driverRuntimeService;
        this.applicationDataService = applicationDataService;
        this.operatorAppUiService = operatorAppUiService;
        this.bootstrapProperties = bootstrapProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 23)
    public void onReady() throws Exception {
        if (!bootstrapProperties.isFixturesEnabled()) {
            return;
        }
        modelBootstrap.ensureTankFarmModels();
        registerApplication();
        ensureFolder();
        for (TankSeed tank : allTanks()) {
            ensureTank(tank);
        }
        ensureHub();
        ensureMimic(TankFarmPaths.MIMIC, "Резервуарный парк (демо)", TankFarmMimicDocument.DIAGRAM_JSON);
        ensureDashboard(
                TankFarmPaths.DASHBOARD,
                "Мнемосхема резервуарного парка",
                TankFarmDashboardLayouts.HMI_LAYOUT
        );
        ensureOperatorUi();
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
        for (TankSeed tank : TYPE_A_TANKS) {
            tanks.add(tank);
        }
        for (TankSeed tank : TYPE_B_TANKS) {
            tanks.add(tank);
        }
        return tanks;
    }

    private void registerApplication() {
        try {
            applicationDataService.register(
                    TankFarmPaths.APP_ID,
                    TankFarmPaths.DISPLAY_NAME,
                    "",
                    "app_tank_farm_demo"
            );
        } catch (Exception ignored) {
            // already registered
        }
    }

    private void ensureFolder() {
        if (objectManager.tree().findByPath(TankFarmPaths.FOLDER).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    TankFarmPaths.PLANT_FOLDER_NAME,
                    ObjectType.CUSTOM,
                    TankFarmPaths.DISPLAY_NAME,
                    "Anonymized tank farm SCADA demo",
                    null
            );
        }
    }

    private void ensureTank(TankSeed tank) {
        String name = "tank-" + tank.number();
        String path = TankFarmPaths.tank(tank.number());
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    TankFarmPaths.FOLDER,
                    name,
                    ObjectType.DEVICE,
                    "Резервуар №" + tank.number(),
                    "",
                    TankFarmModelBootstrap.TANK_MODEL
            );
        }
        templateService.applyTemplate(path, TankFarmModelBootstrap.TANK_MODEL);
        setStringVar(
                path,
                "driverConfigJson",
                TankFarmModelBootstrap.tankDriverConfig(tank.number(), tank.initialLevelMm(), tank.rateBiasMmPerHour())
        );
    }

    private void ensureHub() {
        String path = TankFarmPaths.MANIFOLD_HUB;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    TankFarmPaths.FOLDER,
                    "manifold-hub",
                    ObjectType.CUSTOM,
                    "Коллектор магистрали",
                    "",
                    TankFarmModelBootstrap.MANIFOLD_HUB_MODEL
            );
        }
        templateService.applyTemplate(path, TankFarmModelBootstrap.MANIFOLD_HUB_MODEL);
        setStringVar(path, "driverConfigJson", TankFarmModelBootstrap.MANIFOLD_HUB_DRIVER_CONFIG);
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

    private void ensureOperatorUi() throws Exception {
        operatorAppUiService.saveUi(
                TankFarmPaths.APP_ID,
                TankFarmPaths.DISPLAY_NAME,
                TankFarmPaths.DASHBOARD,
                List.of(Map.of(
                        "path", TankFarmPaths.DASHBOARD,
                        "title", "Мнемосхема резервуарного парка"
                ))
        );
    }

    private void startDrivers() {
        List<String> paths = new ArrayList<>();
        paths.add(TankFarmPaths.MANIFOLD_HUB);
        for (TankSeed tank : allTanks()) {
            paths.add(TankFarmPaths.tank(tank.number()));
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
