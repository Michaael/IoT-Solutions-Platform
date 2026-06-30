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
 * Seeds pipeline SCADA demo: 15 РД-029 screen forms, devices, dashboards.
 */
@Component
public class PipelineScadaPlatformBootstrap {

    private record TankSeed(int number, double initialLevelMm, double rateBiasMmPerHour) {
    }

    private record FormSeed(String mimicPath, String dashboardPath, String title, String diagramJson) {
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

    private static final FormSeed[] FORMS = {
            new FormSeed(PipelineScadaPaths.MIMIC_MT_TERRITORIAL, "root.platform.dashboards.pipeline-mt-territorial", "Территориальная схема МТ", PipelineScadaMimicDocuments.MT_TERRITORIAL),
            new FormSeed(PipelineScadaPaths.MIMIC_MT_SCHEME, "root.platform.dashboards.pipeline-mt-scheme", "Схема МТ", PipelineScadaMimicDocuments.MT_SCHEME),
            new FormSeed(PipelineScadaPaths.MIMIC_RP_OIL_PLACEMENT, "root.platform.dashboards.pipeline-rp-oil-placement", "Размещение нефти в РП", PipelineScadaMimicDocuments.RP_OIL_PLACEMENT),
            new FormSeed(PipelineScadaPaths.MIMIC_RP, PipelineScadaPaths.DASHBOARD, "Экранная форма РП", PipelineScadaMimicDocuments.RP),
            new FormSeed(PipelineScadaPaths.MIMIC_RP_URDO, "root.platform.dashboards.pipeline-rp-urdo", "ЭФ РП со слоем УРДО", PipelineScadaMimicDocuments.RP_URDO),
            new FormSeed(PipelineScadaPaths.MIMIC_SIKN, "root.platform.dashboards.pipeline-sikn", "Экранная форма СИКН", PipelineScadaMimicDocuments.SIKN),
            new FormSeed(PipelineScadaPaths.MIMIC_PSP, "root.platform.dashboards.pipeline-psp", "Экранная форма ПСП", PipelineScadaMimicDocuments.PSP),
            new FormSeed(PipelineScadaPaths.MIMIC_NPS, "root.platform.dashboards.pipeline-nps", "Экранная форма НПС", PipelineScadaMimicDocuments.NPS),
            new FormSeed(PipelineScadaPaths.MIMIC_LU_MT, "root.platform.dashboards.pipeline-lu-mt", "Экранная форма ЛУ МТ", PipelineScadaMimicDocuments.LU_MT),
            new FormSeed(PipelineScadaPaths.MIMIC_LU_NAV, "root.platform.dashboards.pipeline-lu-nav", "Панель навигации по ЛУ МТ", PipelineScadaMimicDocuments.LU_NAV),
            new FormSeed(PipelineScadaPaths.MIMIC_SEA_TERMINAL, "root.platform.dashboards.pipeline-sea-terminal", "Морской терминал", PipelineScadaMimicDocuments.SEA_TERMINAL),
            new FormSeed(PipelineScadaPaths.MIMIC_PIER, "root.platform.dashboards.pipeline-pier", "Причал", PipelineScadaMimicDocuments.PIER),
            new FormSeed(PipelineScadaPaths.MIMIC_MT_STOP_PANEL, "root.platform.dashboards.pipeline-mt-stop-panel", "Панель остановки МТ", PipelineScadaMimicDocuments.MT_STOP_PANEL),
            new FormSeed(PipelineScadaPaths.MIMIC_MT_SECTION_PANEL, "root.platform.dashboards.pipeline-mt-section-panel", "Панель управления ЛЧ МТ", PipelineScadaMimicDocuments.MT_SECTION_PANEL),
            new FormSeed(PipelineScadaPaths.MIMIC_NPS_PANEL, "root.platform.dashboards.pipeline-nps-panel", "Панель управления НПС", PipelineScadaMimicDocuments.NPS_PANEL),
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

    public PipelineScadaPlatformBootstrap(
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
    @Order(Ordered.HIGHEST_PRECEDENCE + 24)
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
        for (FormSeed form : FORMS) {
            ensureMimic(form.mimicPath(), form.title(), form.diagramJson());
            ensureDashboard(
                    form.dashboardPath(),
                    form.title(),
                    PipelineScadaDashboardLayouts.hmiLayout(form.mimicPath(), form.title())
            );
        }
        ensureMimicAlias();
        ensureOperatorUi();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 3)
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
                    PipelineScadaPaths.APP_ID,
                    PipelineScadaPaths.DISPLAY_NAME,
                    "",
                    "app_pipeline_scada"
            );
        } catch (Exception ignored) {
            // already registered
        }
    }

    private void ensureFolder() {
        if (objectManager.tree().findByPath(PipelineScadaPaths.FOLDER).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    PipelineScadaPaths.PLANT_FOLDER_NAME,
                    ObjectType.CUSTOM,
                    PipelineScadaPaths.DISPLAY_NAME,
                    "Pipeline SCADA demo (РД-029)",
                    null
            );
        }
    }

    private void ensureTank(TankSeed tank) {
        String name = "tank-" + tank.number();
        String path = PipelineScadaPaths.tank(tank.number());
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    PipelineScadaPaths.FOLDER,
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
        String path = PipelineScadaPaths.MANIFOLD_HUB;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    PipelineScadaPaths.FOLDER,
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

    /** Backward-compatible alias: tank-farm-demo → same RP diagram. */
    private void ensureMimicAlias() {
        ensureMimic(
                PipelineScadaPaths.MIMIC_TANK_FARM_DEMO,
                "Резервуарный парк (демо, устар.)",
                PipelineScadaMimicDocuments.RP
        );
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
        List<Map<String, String>> dashboards = new ArrayList<>();
        for (FormSeed form : FORMS) {
            dashboards.add(Map.of("path", form.dashboardPath(), "title", form.title()));
        }
        operatorAppUiService.saveUi(
                PipelineScadaPaths.APP_ID,
                PipelineScadaPaths.DISPLAY_NAME,
                PipelineScadaPaths.DASHBOARD,
                dashboards
        );
    }

    private void startDrivers() {
        List<String> paths = new ArrayList<>();
        paths.add(PipelineScadaPaths.MANIFOLD_HUB);
        for (TankSeed tank : allTanks()) {
            paths.add(PipelineScadaPaths.tank(tank.number()));
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
