package com.ispf.server.bootstrap;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.mimic.MimicService;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.workflow.WorkflowService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the generic mini-TEC digital twin: objects, dashboards, automation, operator UI.
 */
@Component
public class MiniTecPlatformBootstrap {

    private final MiniTecModelBootstrap modelBootstrap;
    private final ObjectTemplateService templateService;
    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final MimicService mimicService;
    private final WorkflowService workflowService;
    private final AutomationTreeService automationTreeService;
    private final DriverRuntimeService driverRuntimeService;
    private final OperatorAppUiService operatorAppUiService;
    private final ApplicationDataService applicationDataService;
    private final BootstrapProperties bootstrapProperties;

    public MiniTecPlatformBootstrap(
            MiniTecModelBootstrap modelBootstrap,
            ObjectTemplateService templateService,
            ObjectManager objectManager,
            DashboardService dashboardService,
            MimicService mimicService,
            WorkflowService workflowService,
            AutomationTreeService automationTreeService,
            DriverRuntimeService driverRuntimeService,
            OperatorAppUiService operatorAppUiService,
            ApplicationDataService applicationDataService,
            BootstrapProperties bootstrapProperties
    ) {
        this.modelBootstrap = modelBootstrap;
        this.templateService = templateService;
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.mimicService = mimicService;
        this.workflowService = workflowService;
        this.automationTreeService = automationTreeService;
        this.driverRuntimeService = driverRuntimeService;
        this.operatorAppUiService = operatorAppUiService;
        this.applicationDataService = applicationDataService;
        this.bootstrapProperties = bootstrapProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 22)
    public void onReady() throws Exception {
        if (!bootstrapProperties.isFixturesEnabled()) {
            return;
        }
        modelBootstrap.ensureMiniTecModels();
        registerApplication();
        ensureSqlSchema();
        ensureFolder();
        ensureDevice("gpu-01", "ГПУ-1", MiniTecModelBootstrap.GPU_MODEL, 1);
        ensureDevice("gpu-02", "ГПУ-2", MiniTecModelBootstrap.GPU_MODEL, 2);
        ensureDevice("gpu-03", "ГПУ-3", MiniTecModelBootstrap.GPU_MODEL, 3);
        ensureDevice("grpb", "ГРПБ", MiniTecModelBootstrap.GRPB_MODEL, 0);
        ensureDevice("rumb-10kv", "РУМБ 10/0.4 кВ", MiniTecModelBootstrap.RUMB_MODEL, 0);
        ensureDevice("dgu", "ДГУ", MiniTecModelBootstrap.DGU_MODEL, 0);
        ensureDevice("load-module", "Нагрузочный модуль", MiniTecModelBootstrap.LOAD_MODEL, 0);
        ensureHub();
        ensureMimics();
        ensureDashboards();
        ensureWorkflows();
        ensureAutomation();
        ensureOperatorUi();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 5)
    public void startDriversAfterBootstrap() {
        if (!bootstrapProperties.isFixturesEnabled()) {
            return;
        }
        startDrivers();
    }

    private void ensureSqlSchema() {
        try {
            applicationDataService.migrate(
                    MiniTecPaths.APP_ID,
                    "1.0.0",
                    List.of(new ApplicationDataService.MigrationScript(
                            "tec_schema",
                            """
                            CREATE TABLE IF NOT EXISTS tec_daily_journal (
                              journal_date DATE PRIMARY KEY,
                              total_energy_kwh DOUBLE PRECISION NOT NULL DEFAULT 0,
                              total_reactive_kvarh DOUBLE PRECISION NOT NULL DEFAULT 0,
                              total_running_hours DOUBLE PRECISION NOT NULL DEFAULT 0,
                              start_count INTEGER NOT NULL DEFAULT 0
                            );
                            CREATE TABLE IF NOT EXISTS tec_consumer_load (
                              consumer_code VARCHAR(64) PRIMARY KEY,
                              consumer_name VARCHAR(256) NOT NULL,
                              nominal_load_kw INTEGER NOT NULL
                            );
                            INSERT INTO tec_consumer_load (consumer_code, consumer_name, nominal_load_kw)
                            SELECT 'consumer-1', 'Потребитель 1', 2430
                            WHERE NOT EXISTS (SELECT 1 FROM tec_consumer_load WHERE consumer_code = 'consumer-1');
                            INSERT INTO tec_consumer_load (consumer_code, consumer_name, nominal_load_kw)
                            SELECT 'consumer-2', 'Потребитель 2', 1200
                            WHERE NOT EXISTS (SELECT 1 FROM tec_consumer_load WHERE consumer_code = 'consumer-2');
                            INSERT INTO tec_consumer_load (consumer_code, consumer_name, nominal_load_kw)
                            SELECT 'consumer-3', 'Резервная нагрузка', 500
                            WHERE NOT EXISTS (SELECT 1 FROM tec_consumer_load WHERE consumer_code = 'consumer-3');
                            """
                    ))
            );
        } catch (Exception ignored) {
            // schema may already exist
        }
    }

    private void registerApplication() {
        try {
            applicationDataService.register(
                    MiniTecPaths.APP_ID,
                    MiniTecPaths.DISPLAY_NAME,
                    "",
                    "app_mini_tec"
            );
        } catch (Exception ignored) {
            // already registered
        }
    }

    private void ensureFolder() {
        if (objectManager.tree().findByPath(MiniTecPaths.FOLDER).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    MiniTecPaths.PLANT_FOLDER_NAME,
                    ObjectType.CUSTOM,
                    MiniTecPaths.DISPLAY_NAME,
                    "Gas piston mini power plant reference digital twin",
                    null
            );
        }
    }

    private void ensureDevice(String name, String displayName, String model, int unitIndex) {
        String path = MiniTecPaths.FOLDER + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(MiniTecPaths.FOLDER, name, ObjectType.DEVICE, displayName, "", model);
        }
        templateService.applyTemplate(path, model);
        if (unitIndex > 0) {
            String config = String.format(MiniTecModelBootstrap.GPU_DRIVER_CONFIG_TEMPLATE, "1480", unitIndex);
            setStringVar(path, "driverConfigJson", config);
        }
    }

    private void ensureHub() {
        String path = MiniTecPaths.STATION_HUB;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(MiniTecPaths.FOLDER, "station-hub", ObjectType.CUSTOM, "Станционный hub", "", MiniTecModelBootstrap.HUB_MODEL);
        }
        templateService.applyTemplate(path, MiniTecModelBootstrap.HUB_MODEL);
    }

    private void setStringVar(String path, String name, String value) {
        PlatformObject node = objectManager.require(path);
        node.setVariableValue(
                name,
                DataRecord.single(DataSchema.builder("v").field("value", FieldType.STRING).build(), Map.of("value", value))
        );
        objectManager.persistNodeTree(path);
    }

    private void ensureMimics() {
        ensureMimic(MiniTecPaths.MIMIC_SINGLE_LINE, "Однолинейная схема Мини-ТЭЦ", MiniTecMimicDocument.DIAGRAM_JSON);
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

    private void ensureDashboards() {
        ensureDashboard(MiniTecPaths.DASHBOARD_OVERVIEW, "Станционная сводка", MiniTecDashboardLayouts.OVERVIEW);
        ensureDashboard(MiniTecPaths.DASHBOARD_SINGLE_LINE, "Однолинейная схема", MiniTecDashboardLayouts.SINGLE_LINE);
        ensureDashboard(MiniTecPaths.DASHBOARD_GPU_DETAIL, "ГПУ — детально", MiniTecDashboardLayouts.GPU_DETAIL);
        ensureDashboard(MiniTecPaths.DASHBOARD_GRPB, "ГРПБ", MiniTecDashboardLayouts.GRPB);
        ensureDashboard(MiniTecPaths.DASHBOARD_RUMB, "РУМБ 10/0.4 кВ", MiniTecDashboardLayouts.RUMB);
        ensureDashboard(MiniTecPaths.DASHBOARD_DGU, "ДГУ", MiniTecDashboardLayouts.DGU);
        ensureDashboard(MiniTecPaths.DASHBOARD_LOAD, "Нагрузочный модуль", MiniTecDashboardLayouts.LOAD_MODULE);
        ensureDashboard(MiniTecPaths.DASHBOARD_PROTECTIONS, "Защиты", MiniTecDashboardLayouts.PROTECTIONS);
        ensureDashboard(MiniTecPaths.DASHBOARD_EXPLOITATION, "Эксплуатация", MiniTecDashboardLayouts.EXPLOITATION);
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

    private void ensureWorkflows() throws Exception {
        ensureWorkflow(MiniTecPaths.WORKFLOW_GAS_TRIP, MiniTecWorkflowDefinitions.GAS_EMERGENCY_TRIP);
        ensureWorkflow(MiniTecPaths.WORKFLOW_LOAD_UNLOAD, MiniTecWorkflowDefinitions.LOAD_AUTO_UNLOAD);
        ensureWorkflow(MiniTecPaths.WORKFLOW_GPU_START, MiniTecWorkflowDefinitions.GPU_START_SEQUENCE);
        ensureWorkflow(MiniTecPaths.WORKFLOW_ACK, MiniTecWorkflowDefinitions.ACK_PROTECTION);
    }

    private void ensureWorkflow(String path, String bpmn) throws Exception {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            int dot = path.lastIndexOf('.');
            objectManager.create(path.substring(0, dot), path.substring(dot + 1), ObjectType.WORKFLOW, path.substring(dot + 1), "", "workflow-v1");
        }
        workflowService.ensureWorkflowStructure(path);
        workflowService.saveBpmn(path, bpmn);
        workflowService.updateStatus(path, WorkflowLifecycleStatus.ACTIVE);
    }

    private void ensureAutomation() {
        ensureAlert("Mini-TEC GPU overload", MiniTecPaths.GPU_01, "protOverload",
                "self.protOverload[\"value\"] == true", "gpuProtOverload", "protOverload");
        ensureAlert("Mini-TEC bus undervoltage", MiniTecPaths.STATION_HUB, "busUndervoltage",
                "self.busUndervoltage[\"value\"] == true", "busProtUndervoltage", "busUndervoltage");
        ensureAlert("Mini-TEC GRPB gas leak", MiniTecPaths.GRPB, "gasLeak",
                "self.gasLeak[\"value\"] == true", "grpbGasLeak", "gasLeak");
        ensureAlert("Mini-TEC GRPB fire", MiniTecPaths.GRPB, "fireAlarm",
                "self.fireAlarm[\"value\"] == true", "grpbFire", "fireAlarm");
        ensureAlert("Mini-TEC station underpower", MiniTecPaths.STATION_HUB, "stationUnderpower",
                "self.stationUnderpower[\"value\"] == true", "stationUnderpower", "stationUnderpower");

        ensureCorrelator("Mini-TEC latch alarm", MiniTecPaths.STATION_HUB, "gpuProtOverload",
                CorrelatorActionType.SET_VARIABLE, "alarmLatched=true");
        ensureCorrelator("Mini-TEC gas trip workflow", MiniTecPaths.GRPB, "grpbFire",
                CorrelatorActionType.RUN_WORKFLOW, MiniTecPaths.WORKFLOW_GAS_TRIP);
        ensureCorrelator("Mini-TEC load auto unload", MiniTecPaths.STATION_HUB, "stationUnderpower",
                CorrelatorActionType.RUN_WORKFLOW, MiniTecPaths.WORKFLOW_LOAD_UNLOAD);
    }

    private void ensureAlert(String name, String objectPath, String watch, String condition, String event, String payload) {
        String path = AutomationTreeService.rulePathForName(name);
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        automationTreeService.createAlertRule(name, objectPath, watch, condition, event, payload, true, true, 0, false, null, null);
    }

    private void ensureCorrelator(String name, String objectPath, String event, CorrelatorActionType action, String target) {
        String path = AutomationTreeService.correlatorPathForName(name);
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        automationTreeService.createCorrelator(
                name, objectPath, CorrelatorPatternType.COUNT, event, null,
                0, 1, 60, 0, action, target, null, true
        );
    }

    private void ensureOperatorUi() throws Exception {
        List<Map<String, String>> dashboards = List.of(
                entry(MiniTecPaths.DASHBOARD_OVERVIEW, "Сводка"),
                entry(MiniTecPaths.DASHBOARD_SINGLE_LINE, "Однолинейная"),
                entry(MiniTecPaths.DASHBOARD_GPU_DETAIL, "ГПУ"),
                entry(MiniTecPaths.DASHBOARD_GRPB, "ГРПБ"),
                entry(MiniTecPaths.DASHBOARD_RUMB, "РУМБ"),
                entry(MiniTecPaths.DASHBOARD_DGU, "ДГУ"),
                entry(MiniTecPaths.DASHBOARD_LOAD, "Нагрузочный модуль"),
                entry(MiniTecPaths.DASHBOARD_PROTECTIONS, "Защиты"),
                entry(MiniTecPaths.DASHBOARD_EXPLOITATION, "Эксплуатация")
        );
        operatorAppUiService.saveUi(
                MiniTecPaths.APP_ID,
                MiniTecPaths.DISPLAY_NAME,
                MiniTecPaths.DASHBOARD_OVERVIEW,
                dashboards,
                miniTecAlarmBarConfig()
        );
    }

    private Map<String, Object> miniTecAlarmBarConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("soundEnabled", true);
        config.put("soundUrl", "/sounds/alarm.wav");
        config.put("minLevel", "WARNING");
        config.put("position", "top");
        config.put("rules", List.of(
                alarmRule(
                        "grpb-fire",
                        List.of("grpbFire", "fireAlarm"),
                        MiniTecPaths.GRPB,
                        "ERROR",
                        "Пожар: {{eventName}}",
                        "#450a0a", "#fee2e2", "#ef4444",
                        MiniTecPaths.DASHBOARD_GRPB,
                        "acknowledgeAlarm"
                ),
                alarmRule(
                        "grpb-gas",
                        List.of("grpbGasLeak", "gasLeak"),
                        MiniTecPaths.GRPB,
                        "ERROR",
                        "Утечка газа: {{eventName}}",
                        "#431407", "#ffedd5", "#f97316",
                        MiniTecPaths.DASHBOARD_SINGLE_LINE,
                        "acknowledgeAlarm"
                ),
                alarmRule(
                        "gpu-overload",
                        List.of("gpuProtOverload", "overloadTrip"),
                        MiniTecPaths.GPU_01,
                        "WARNING",
                        "Перегрузка: {{eventName}}",
                        "#422006", "#fef3c7", "#f59e0b",
                        MiniTecPaths.DASHBOARD_GPU_DETAIL,
                        null
                ),
                alarmRule(
                        "open-report",
                        List.of("openOperatorReport"),
                        null,
                        "INFO",
                        "Открыть отчёт",
                        "#1e3a5f", "#e0f2fe", "#38bdf8",
                        null,
                        null
                )
        ));
        return config;
    }

    private static Map<String, Object> alarmRule(
            String id,
            List<String> eventNames,
            String objectPathPrefix,
            String minLevel,
            String title,
            String background,
            String text,
            String border,
            String dashboardPath,
            String acknowledgeFunction
    ) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", id);
        rule.put("eventNames", eventNames);
        if (objectPathPrefix != null) {
            rule.put("objectPathPrefix", objectPathPrefix);
        }
        rule.put("minLevel", minLevel);
        rule.put("title", title);
        rule.put("fields", List.of(
                Map.of("label", "Событие", "source", "eventName"),
                Map.of("label", "Объект", "source", "objectPath"),
                Map.of("label", "Время", "source", "timestamp")
        ));
        rule.put("colors", Map.of(
                "background", background,
                "text", text,
                "border", border,
                "accent", border
        ));
        Map<String, Object> actions = new LinkedHashMap<>();
        if (dashboardPath != null) {
            actions.put("dashboardPath", dashboardPath);
        }
        actions.put("selectionKey", "devicePath");
        actions.put("reportFromPayload", "reportPath");
        if (acknowledgeFunction != null) {
            actions.put("acknowledgeFunction", acknowledgeFunction);
        }
        rule.put("actions", actions);
        rule.put("persistUntilDismiss", true);
        return rule;
    }

    private static Map<String, String> entry(String path, String title) {
        return Map.of("path", path, "title", title);
    }

    private void startDrivers() {
        for (String path : List.of(
                MiniTecPaths.GPU_01, MiniTecPaths.GPU_02, MiniTecPaths.GPU_03,
                MiniTecPaths.GRPB, MiniTecPaths.RUMB, MiniTecPaths.DGU, MiniTecPaths.LOAD_MODULE
        )) {
            try {
                driverRuntimeService.start(path);
            } catch (Exception ex) {
                // driver may already run
            }
        }
    }
}
