package com.ispf.server.ai.agent;

import com.ispf.server.bootstrap.FixtureModelBootstrap;
import com.ispf.server.bootstrap.LabModelBootstrap;
import com.ispf.server.bootstrap.MiniTecPaths;
import com.ispf.server.bootstrap.PipelineScadaPaths;
import com.ispf.server.bootstrap.TankFarmPaths;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.dashboard.DashboardLayouts;

/**
 * Curated multi-step recipes for the tree-first agent (referenced from system prompt).
 * <p>
 * Never use {@link String#formatted(String, Object...)} here — playbooks contain {@code %}, JSON, and paths
 * that break format strings. Use string concatenation only.
 */
public final class AgentPlaybooks {

    public static final String SNMP_DEVICE_PATH = FixtureModelBootstrap.SNMP_LOCALHOST_PATH;
    public static final String SNMP_DASHBOARD_PATH = "root.platform.dashboards.snmp-host-monitoring";
    public static final String SNMP_MODEL = FixtureModelBootstrap.SNMP_AGENT_MODEL;
    public static final String SNMP_DRIVER_ID = "snmp";

    public static final String VIRT_CLUSTER_FOLDER = "root.platform.devices.virt-cluster";
    public static final String VIRT_CLUSTER_HUB = VIRT_CLUSTER_FOLDER + ".hub";
    public static final String VIRT_CLUSTER_DEV_01 = VIRT_CLUSTER_FOLDER + ".dev-01";
    public static final String VIRT_CLUSTER_DEV_02 = VIRT_CLUSTER_FOLDER + ".dev-02";
    public static final String VIRT_CLUSTER_DEV_03 = VIRT_CLUSTER_FOLDER + ".dev-03";
    public static final String VIRT_CLUSTER_OVERVIEW = "root.platform.dashboards.virt-cluster-overview";
    public static final String VIRT_CLUSTER_DETAIL = "root.platform.dashboards.virt-cluster-detail";
    public static final String VIRT_CLUSTER_ALERT = "root.platform.alert-rules.virt-cluster-error";
    public static final String VIRT_CLUSTER_LAB_CONFIG =
            "{\"profile\":\"lab\",\"sineAmplitude\":\"10\",\"sawtoothAmplitude\":\"5\","
                    + "\"triangleAmplitude\":\"5\",\"periodSec\":\"30\"}";

    private AgentPlaybooks() {
    }

    public static String snmpLocalhostMonitoring() {
        return """
                ## SNMP localhost — мониторинг ресурсов хоста
                
                Цель: устройство SNMP на 127.0.0.1:161, метрики CPU/RAM/сеть, дашборд.
                
                Шаги:
                1. search_context query="snmp localhost monitoring dashboard" (once)
                2. get_object path="""
                + SNMP_DEVICE_PATH
                + """
                 — если OK, устройство уже есть; иначе create_object:
                   parentPath=root.platform.devices, name=snmp-localhost, type=DEVICE,
                   displayName=SNMP localhost, templateId="""
                + SNMP_MODEL
                + ", driverId="
                + SNMP_DRIVER_ID
                + """
                , autoStartDriver=false
                3. set_variable path="""
                + SNMP_DEVICE_PATH
                + " name=driverConfigJson value="
                + FixtureModelBootstrap.SNMP_DRIVER_CONFIG
                + """
                
                4. set_variable path="""
                + SNMP_DEVICE_PATH
                + " name=driverPointMappingsJson value="
                + FixtureModelBootstrap.SNMP_POINT_MAPPINGS
                + """
                
                5. configure_driver devicePath="""
                + SNMP_DEVICE_PATH
                + " driverId="
                + SNMP_DRIVER_ID
                + """
                 autoStart=true (или driver_control action=start)
                6. list_variables path="""
                + SNMP_DEVICE_PATH
                + """
                 — показать метрики
                7. get_object path="""
                + SNMP_DASHBOARD_PATH
                + """
                 — дашборд; если нет — create_object parentPath=root.platform.dashboards,
                   name=snmp-host-monitoring, type=DASHBOARD, templateId=dashboard-v1
                8. set_dashboard_layout path="""
                + SNMP_DASHBOARD_PATH
                + """
                 template=snmp-host-monitoring
                9. finish: summary + result.devicePath + result.dashboardPath
                """;
    }

    public static String snmpDashboardLayoutHint() {
        return "Use dashboard layout compatible with selectionKey=device and variables: "
                + "sysName, sysUpTime, hrMemorySize, hrProcessorLoad, hrSystemProcesses, "
                + "hrSystemNumUsers, ifNumber, ifInOctets, ifOutOctets, status.online. "
                + "Reference length: " + DashboardLayouts.SNMP_HOST_MONITORING_DASHBOARD.length() + " chars.";
    }

    public static String dashboardLayoutEditing() {
        return AgentDashboardGuide.referenceText()
                + "\n\n### Быстрое редактирование (SNMP пример)\n\n"
                + "1. get_dashboard_layout path="
                + SNMP_DASHBOARD_PATH
                + " — прочитать текущий layout\n"
                + "2a. set_dashboard_layout path="
                + SNMP_DASHBOARD_PATH
                + " template=snmp-host-monitoring — восстановить эталон\n"
                + "2b. add_dashboard_widget path="
                + SNMP_DASHBOARD_PATH
                + " widget={...} — добавить/заменить один виджет по id\n"
                + "3. finish\n\n"
                + snmpDashboardLayoutHint()
                + "\nНе вызывай search_context больше 1–2 раз подряд — используй get_dashboard_layout.\n";
    }

    public static String dashboardGuide() {
        return AgentDashboardGuide.referenceText();
    }

    public static String snmpIfMibExtension() {
        return """
                ## IF-MIB — дополнительные метрики интерфейса (follow-up)
                
                Устройство: """
                + SNMP_DEVICE_PATH
                + "\nДашборд: "
                + SNMP_DASHBOARD_PATH
                + """
                
                
                Шаги (без search_context):
                1. set_variable path="""
                + SNMP_DEVICE_PATH
                + """
                 name=driverPointMappingsJson value=<JSON ниже>
                2. configure_driver devicePath="""
                + SNMP_DEVICE_PATH
                + """
                 driverId=snmp autoStart=true
                3. driver_control devicePath="""
                + SNMP_DEVICE_PATH
                + """
                 action=poll
                4. add_dashboard_widget path="""
                + SNMP_DASHBOARD_PATH
                + """
                 для ifDescr, ifSpeed, ifOperStatus, ifInErrors, ifOutErrors
                5. list_variables path="""
                + SNMP_DEVICE_PATH
                + """
                
                6. finish
                
                driverPointMappingsJson:
                """
                + FixtureModelBootstrap.SNMP_POINT_MAPPINGS
                + "\n";
    }

    public static String virtualMeterLab() {
        return """
                ## Virtual driver — meter profile (lab-training / MES)
                
                Цель: симулятор налива без железа.
                
                Предпочтительно: create_virtual_device profile=meter (один вызов — модель, конфиг, старт драйвера).
                
                Или вручную:
                1. create_object parentPath=root.platform.devices name=virtual-meter type=DEVICE
                   displayName=Virtual meter, templateId="""
                + LabModelBootstrap.VIRTUAL_UNIFIED_MODEL
                + """
                , driverId=virtual, autoStartDriver=false
                2. set_variable path=... name=driverConfigJson value="""
                + VirtualDeviceProfileCatalog.METER_DRIVER_CONFIG
                + """
                
                3. set_variable path=... name=driverPointMappingsJson value="""
                + VirtualDeviceProfileCatalog.METER_POINT_MAPPINGS
                + """
                
                4. configure_driver devicePath=... driverId=virtual autoStart=true
                   (после set_variable configure_driver подхватывает driverConfigJson с устройства)
                5. list_variables — meterLiters, flowRate, filling (count>0 обязательно перед finish)
                6. finish только после list_variables
                """;
    }

    public static String virtualPumpStation() {
        return """
                ## Virtual pump station — насосная станция (SCADA + мониторинг)
                
                Цель: виртуальные устройства с телеметрией (вибрация, температура, расход, давление),
                SCADA mimic с bindings, дашборд мониторинга.
                
                **RELATIVE модели:** mixin вливает variables/events/functions в существующий объект.
                
                0. list_objects parentPath=root.platform.devices — найти или выбрать родительскую папку (имя из ответа, не выдумывать)
                1. list_relative_models targetObjectType=DEVICE + list_virtual_profiles — modelName/profile только из ответа
                2. Если нужна новая папка проекта: create_object parentPath=<из list_objects> name=<уникальное из контекста> type=CUSTOM
                3. Устройства — **вариант A:** create_object parentPath=<существующий parent> … → apply_relative_model modelName=<из list_relative_models>
                   **вариант B:** create_virtual_device parentPath=<существующий> profile=<из list_virtual_profiles>
                4. list_variables на КАЖДОМ устройстве — variableName для SCADA/dashboard только из этого списка
                5. SCADA: save_mimic_diagram bindings с objectPath/variableName из list_variables
                6. Dashboard: set_dashboard_layout layoutJson= с columns=84, rowHeight=8;
                   scada-mimic: w=84 h=63; value/chart: w=28 h=14; НЕ w=4 h=2 (устаревшая сетка 12×72)
                """;
    }

    public static String relativeModelsGuide() {
        return """
                ## RELATIVE models — mixins для variables / events / functions
                
                Каталог: root.platform.relative-models. Тип ModelType.RELATIVE.
                
                | Инструмент | Назначение |
                |------------|------------|
                | list_relative_models | Каталог mixin-моделей (virtual-lab-v1, snmp-agent-v1, …) |
                | get_object_model | Схема: variables[], events[], functions[] |
                | apply_relative_model | Прикрепить mixin к существующему objectPath |
                
                Workflow:
                1. create_object DEVICE (можно без templateId — только driver schema от provisionDriver)
                2. apply_relative_model modelName=virtual-lab-v1 objectPath=...
                   — добавляет sineWave, driverConfigJson, events, functions на тот же path
                3. configure_driver + driver_control start
                4. list_variables — проверка
                
                Альтернатива: create_object с templateId=virtual-lab-v1 (тот же apply при создании).
                Несколько mixin можно наслаивать, если модели не конфликтуют.
                
                Не путать с create_variable — mixin даёт целую схему + binding rules разом.
                """;
    }

    public static String mesReferenceLifecycle() {
        return """
                ## MES reference (mes-reference bundle)
                
                Reference appId: mes-reference. BFF on root.platform.devices.demo-sensor-01:
                1. list_functions objectPath=root.platform.devices.demo-sensor-01 appId=mes-reference
                2. get_function objectPath=... functionName=mes_listOrders
                3. invoke_bff objectPath=... functionName=mes_listOrders
                4. invoke_bff ... functionName=mes_startFilling inputRows=[{"orderNo":"DO-1001"}]
                5. invoke_bff ... functionName=mes_completeFilling inputRows=[{"orderNo":"DO-1001"}]
                
                For patterns use get_example_bundle appId=mes-reference sections=functions,objects.
                Device rack: root.platform.devices.mes-rack-01. Alert mesRackOverTemp at temperature > 85.
                """;
    }

    public static String modbusTcpDevice() {
        return """
                ## Modbus TCP device skeleton
                
                1. create_object parentPath=root.platform.devices name=modbus-tcp-01 type=DEVICE
                   templateId=device-v1, driverId=modbus-tcp, autoStartDriver=false
                2. set_variable name=driverConfigJson value={"host":"127.0.0.1","port":"502","unitId":"1"}
                3. set_variable name=driverPointMappingsJson value={"temperature":"40001"}
                4. configure_driver driverId=modbus-tcp autoStart=true
                5. finish
                """;
    }

    public static String virtualClusterMonitoring() {
        return """
                ## Virtual cluster — полный проект (3 устройства, hub, alert, dashboards, operator UI)
                
                Цель: кластер виртуальных устройств lab-профиля, ERROR когда все 3 sine > 0,
                overview + drill-down detail с графиками, авто-открытие в Operator UI.
                НЕ откладывай на «настройку в UI» — все шаги через инструменты.
                
                0. get_automation_schema topic=all (once)
                
                1. create_object parentPath=root.platform.devices name=virt-cluster type=CUSTOM
                   displayName=Virtual cluster folder
                2. Для dev-01, dev-02, dev-03:
                   create_object parentPath="""
                + VIRT_CLUSTER_FOLDER
                + """
                 name=dev-0N type=DEVICE displayName=Virt cluster dev-0N
                   templateId="""
                + LabModelBootstrap.VIRTUAL_LAB_MODEL
                + """
                 driverId=virtual autoStartDriver=false
                   set_variable path=... name=driverConfigJson value="""
                + VIRT_CLUSTER_LAB_CONFIG
                + """
                
                   set_variable path=... name=driverPointMappingsJson value="""
                + LabModelBootstrap.LAB_POINT_MAPPINGS
                + """
                
                   configure_driver devicePath=... driverId=virtual autoStart=true
                   Для каждого устройства и переменных sineWave, sawtoothWave, triangleWave:
                   configure_variable_history path=... name=<var> historyEnabled=true
                
                3. create_object parentPath="""
                + VIRT_CLUSTER_FOLDER
                + """
                 name=hub type=CUSTOM displayName=Cluster hub
                4. create_variable path="""
                + VIRT_CLUSTER_HUB
                + " name=member1Sine valueType=DOUBLE writable=false\n"
                + "   create_variable path="
                + VIRT_CLUSTER_HUB
                + " name=member2Sine valueType=DOUBLE writable=false\n"
                + "   create_variable path="
                + VIRT_CLUSTER_HUB
                + " name=member3Sine valueType=DOUBLE writable=false\n"
                + "   create_variable path="
                + VIRT_CLUSTER_HUB
                + " name=clusterError valueType=BOOLEAN writable=false\n"
                + "   create_binding_rule path="
                + VIRT_CLUSTER_HUB
                + " id=member1-sine targetVariable=member1Sine remoteObjectPath="
                + VIRT_CLUSTER_DEV_01
                + " remoteVariableName=sineWave expression=refAt(\""
                + VIRT_CLUSTER_DEV_01
                + "\", sineWave)\n"
                + "   create_binding_rule path="
                + VIRT_CLUSTER_HUB
                + " id=member2-sine targetVariable=member2Sine remoteObjectPath="
                + VIRT_CLUSTER_DEV_02
                + " remoteVariableName=sineWave expression=refAt(\""
                + VIRT_CLUSTER_DEV_02
                + "\", sineWave)\n"
                + "   create_binding_rule path="
                + VIRT_CLUSTER_HUB
                + " id=member3-sine targetVariable=member3Sine remoteObjectPath="
                + VIRT_CLUSTER_DEV_03
                + " remoteVariableName=sineWave expression=refAt(\""
                + VIRT_CLUSTER_DEV_03
                + "\", sineWave)\n"
                + "   create_binding_rule path="
                + VIRT_CLUSTER_HUB
                + " id=cluster-error targetVariable=clusterError expression="
                + "self.member1Sine[\"value\"] > 0 && self.member2Sine[\"value\"] > 0 && self.member3Sine[\"value\"] > 0\n"
                + """
                
                5. configure_alert name=virt-cluster-error
                   targetObjectPath="""
                + VIRT_CLUSTER_HUB
                + """
                 watchVariable=clusterError
                   conditionExpr=self.clusterError["value"] == true
                   eventName=virtClusterError enabled=true edgeTrigger=true
                
                6. create_object parentPath=root.platform.dashboards name=virt-cluster-overview type=DASHBOARD
                   displayName=Virtual cluster overview templateId=dashboard-v1
                7. set_dashboard_layout path="""
                + VIRT_CLUSTER_OVERVIEW
                + """
                 template=virtual-cluster-overview
                8. create_object parentPath=root.platform.dashboards name=virt-cluster-detail type=DASHBOARD
                   displayName=Virtual cluster detail templateId=dashboard-v1
                9. set_dashboard_layout path="""
                + VIRT_CLUSTER_DETAIL
                + """
                 template=virtual-cluster-detail
                   (detail widgets use selectionKey=device — открывается после клика по строке в overview)
                
                10. configure_operator_ui appId=platform title=Platform HMI
                    defaultDashboard="""
                + VIRT_CLUSTER_OVERVIEW
                + """
                    plus dashboards list: overview path and """
                + VIRT_CLUSTER_DETAIL
                + """
                    detail path (configure_operator_ui dashboards argument)
                
                11. driver_control poll each device; list_variables on hub (clusterError)
                12. finish: summary + paths (folder, devices, hub, dashboards, alert, operator default)
                """;
    }

    public static String groundTruthGuide() {
        return """
                ## Ground truth — опирайся на дерево, не на выдуманные имена
                
                Документация (playbooks, recipes, briefing) ≠ состояние платформы. Каждый шаг создания/изменения
                должен ссылаться на объекты, уже возвращённые инструментами в этом ходе.
                
                **Порядок (обязателен для ВСЕХ типов объектов):**
                1. list_objects parent=<точная папка> — root.platform.workflows, root.platform.mimics, … (НЕ parent=root)
                2. create_object parentPath=<та же папка> name=… type=WORKFLOW|MIMIC|DASHBOARD|DEVICE|…
                3. configure/save на path из результата create_object — save_workflow_bpmn, save_mimic_diagram, set_dashboard_layout, configure_driver, …
                
                Примеры:
                - WORKFLOW: list_objects workflows → create_object WORKFLOW → save_workflow_bpmn → update_workflow_status → run_workflow
                - MIMIC: list_objects mimics → create_object MIMIC → save_mimic_diagram
                - DASHBOARD: list_objects dashboards → create_object DASHBOARD → set_dashboard_layout
                - DEVICE: list_objects devices → create_object / create_virtual_device → configure_driver → list_variables
                
                Дополнительно перед create:
                - search_objects / get_object — если пользователь назвал объект
                - list_relative_models + list_virtual_profiles — modelName/profile из ответа
                - list_variables path=<существующий DEVICE> — имена переменных для виджетов
                
                **Переиспользование:** если list_objects показал папку/устройство — работай с этим path.
                Если create_object вернул «Object exists» — get_object + list_variables, не дублируй.
                
                **Recipes:** search_platform_recipes даёт последовательность; подставляй пути из list_objects,
                не копируй примерные имена из catalog буквально.
                """;
    }

    public static String projectBlueprintGuide() {
        return "## Project blueprint (8 layers, end-to-end)\n\n"
                + "Используй как каркас для новых решений. Не перескакивай через слои.\n\n"
                + "0. **Ground truth**: list_objects / search_objects / list_relative_models — зафиксируй реальные paths "
                + "и modelName из ответов tools (см. groundTruthGuide). Playbook-пути — только примеры.\n"
                + "1. **Intent + scope**: зафиксируй цель, бизнес-события, naming/path policy на основе существующего дерева.\n"
                + "2. **Model strategy**: выбери INSTANCE vs RELATIVE vs ABSOLUTE "
                + "(см. get_automation_schema topic=instanceTypes).\n"
                + "3. **Source layer**: устройства/драйверы/телеметрия (DEVICE, configure_driver, list_variables).\n"
                + "4. **Aggregation layer**: CUSTOM hub + create_variable + create_binding_rule (refAt/CEL).\n"
                + "5. **Alert layer**: configure_alert на hub/device переменных.\n"
                + "6. **Correlation layer**: configure_correlator для pattern/action цепочек.\n"
                + "7. **Operator layer**: DASHBOARD + MIMIC + REPORT + configure_operator_ui.\n"
                + "8. **Validation layer**: poll/list_variables/get_mimic_diagram/list_automation и только потом finish.\n\n"
                + "Alert + correlator chain (референсный паттерн):\n"
                + "telemetry -> hub computed variable -> configure_alert emits event -> configure_correlator consumes pattern\n"
                + "-> FIRE_EVENT / RUN_WORKFLOW / OPEN_OPERATOR_REPORT.\n\n"
                + "Полный пример проекта: см. playbook virtualClusterMonitoring() и "
                + "get_automation_schema topic=platformMaster.\n";
    }

    public static String instanceTypesGuide() {
        return """
                ## INSTANCE vs RELATIVE vs ABSOLUTE (decision tree)

                1) Нужен готовый шаблон "создать экземпляр из каталога"?
                   -> **INSTANCE**
                   tools: list_instance_types -> instantiate_instance_type

                2) Нужно обогатить уже существующий objectPath (добавить variables/events/functions)?
                   -> **RELATIVE**
                   tools: list_relative_models -> apply_relative_model

                3) Нужна строгая каноническая структура по абсолютной модели (без mixin-слоёв)?
                   -> **ABSOLUTE**
                   tools: list_absolute_models -> ensure_absolute_instance

                Quick flow:
                - Сначала покажи каталоги: list_instance_types, list_relative_models, list_absolute_models.
                - Затем выбери один путь и НЕ смешивай INSTANCE/ABSOLUTE вслепую на одном объекте.
                - После инстанцирования/применения модели: configure_driver + list_variables (verification).
                """;
    }

    public static String objectTypesMatrixGuide() {
        return "## ObjectType creation matrix\n\n"
                + "| ObjectType | Parent path | Typical template/model | Primary tools |\n"
                + "|------------|-------------|------------------------|---------------|\n"
                + "| DEVICE | root.platform.devices | snmp-agent-v1 / virtual-lab-v1 / absolute model | "
                + "create_object, create_virtual_device, configure_driver |\n"
                + "| CUSTOM | root.platform.devices.* or root.platform.instances | (custom hub / instance type) | "
                + "create_object, create_variable, create_binding_rule |\n"
                + "| DASHBOARD | root.platform.dashboards | dashboard-v1 | "
                + "create_object, set_dashboard_layout, add_dashboard_widget |\n"
                + "| MIMIC | root.platform.mimics | mimic-v1 | "
                + "create_object, save_mimic_diagram, get_mimic_diagram |\n"
                + "| ALERT | " + AutomationTreeService.ALERT_RULES_ROOT + " | alert-rule-v1 | configure_alert |\n"
                + "| CORRELATOR | " + AutomationTreeService.CORRELATORS_ROOT + " | correlator-v1 | configure_correlator |\n"
                + "| WORKFLOW | root.platform.workflows | workflow-v1 | "
                + "create_object, save_workflow_bpmn, run_workflow |\n"
                + "| REPORT | root.platform.reports | report-v1 / tree-variables-report-v1 | configure_report, run_report |\n";
    }

    public static String platformObjectTypesGuide() {
        return AgentObjectTreeGuide.referenceText();
    }

    public static String miniTecReference() {
        return """
                ## Мини-ТЭЦ (эталон) — цифровой двойник (3×ГПУ, ГРПБ, РУМБ, ДГУ, нагрузочный модуль)
                
                Преднастроено bootstrap-ом при старте сервера. Smoke-check:
                1. list_variables path="""
                + MiniTecPaths.STATION_HUB
                + """
                 — totalGenPowerKw, gridFrequencyHz, alarmLatched
                2. driver_control action=poll path="""
                + MiniTecPaths.GPU_01
                + """
                3. Operator UI: ?mode=operator&app=mini-tec&dashboard="""
                + MiniTecPaths.DASHBOARD_OVERVIEW
                + """
                
                Пути:
                - Папка: """
                + MiniTecPaths.FOLDER
                + """
                - ГПУ: gpu-01..03, ГРПБ: grpb, РУМБ: rumb-10kv, ДГУ: dgu, нагрузка: load-module
                - Hub: """
                + MiniTecPaths.STATION_HUB
                + """
                - Дашборды: mini-tec-overview, mini-tec-gpu-detail, mini-tec-grpb, mini-tec-protections
                - Модели: mini-tec-gpu-v1, mini-tec-grpb-v1, mini-tec-rumb-v1, mini-tec-dgu-v1, mini-tec-load-module-v1
                - Virtual driver profiles: tec-gpu, tec-grpb, tec-rumb, tec-dgu, tec-load
                - Bundle redeploy: POST /api/v1/applications/mini-tec/deploy with examples/mini-tec/bundle.json
                """;
    }

    public static String scadaMimicGuide() {
        return """
                ## SCADA mimic (MIMIC objects + scada-mimic widget)
                
                search_context topic=scada for diagramJson v2, editor tools, REST API.
                
                ### Concepts
                - Object type MIMIC at root.platform.mimics.* (template mimic-v1)
                - Dashboard widget scada-mimic: mimicPath OR inline diagramJson; prefer reusable MIMIC object
                - Bindings: objectPath + variableName + valueField + transform (number|bool|string)
                - diagramJson version 2 only; symbols in apps/web-console/src/scada/symbols/
                
                ### Anonymization policy (demos)
                Demo diagrams must NOT contain real company names, personal data, or geo-specific labels.
                Use generic titles (Резервуар No11, Коллектор, ST-1...). Never use transneft-* paths or filenames.
                
                ### Bootstrap demos (ispf.bootstrap.fixtures-enabled=true)
                
                **tank-farm-demo** (appId=tank-farm-demo):
                - Devices: """
                + TankFarmPaths.FOLDER
                + """
                 (tank-11..24, manifold-hub)
                - Mimic: """
                + TankFarmPaths.MIMIC
                + """
                - Dashboard: """
                + TankFarmPaths.DASHBOARD
                + """
                - Models: tank-farm-tank-v1, tank-farm-hub-v1; virtual profiles: tank-farm-tank, tank-farm-hub
                - Java bootstrap: TankFarmPlatformBootstrap, TankFarmMimicDocument
                - Re-export JSON: cd apps/web-console && npx tsx src/scada/templates/exportTankFarmMimic.ts
                - TS builder: apps/web-console/src/scada/templates/buildTankFarmMimic.ts
                
                **pipeline-scada** (appId=pipeline-scada, РД-029 screen forms):
                - Devices: """
                + PipelineScadaPaths.FOLDER
                + """
                - Main HMI: """
                + PipelineScadaPaths.DASHBOARD
                + """
                 -> mimic """
                + PipelineScadaPaths.MIMIC_RP
                + """
                - 15 mimics pipeline-* (MT, RP, SIKN, PSP, NPS, LU, sea terminal, pier, panels...)
                - """
                + PipelineScadaPaths.MIMIC_TANK_FARM_DEMO
                + """
                 deprecated alias to RP diagram when pipeline-scada bootstrap runs after tank-farm
                - Re-export: cd apps/web-console && npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts
                
                **mini-tec-single-line**:
                - Mimic: root.platform.mimics.mini-tec-single-line
                - Dashboard: root.platform.dashboards.mini-tec-single-line
                
                ### Smoke-check (tank-farm)
                1. list_variables path="""
                + TankFarmPaths.tank(11)
                + """
                 levelMm, rateMmPerHour
                2. driver_control action=poll path="""
                + TankFarmPaths.tank(11)
                + """
                3. Operator: ?mode=operator&app=tank-farm-demo&dashboard="""
                + TankFarmPaths.DASHBOARD
                + """
                
                ### Agent workflow: new mimic on tree
                1. list_mimic_symbols — pick symbolId values (tank.vertical, valve.gate, label, pipe.segment, …)
                2. create_object parentPath=root.platform.mimics type=MIMIC templateId=mimic-v1 name=<id>
                3. **save_mimic_diagram** path=<mimic> elements=[...] — REQUIRED; must include at least one symbol.
                   Shorthand element: {id, symbolId, layerId:"layer-default", x, y, bindings:{slot:{objectPath,variableName,valueField,transform}}}
                   Or full diagramJson v2 document. Use add_mimic_elements to append more symbols later.
                   NEVER set_variable name=diagram — diagram variable stores JSON string; use save_mimic_diagram only.
                4. get_mimic_diagram path=<mimic> — verify elementCount > 0 before finish
                5. create_object parentPath=root.platform.dashboards type=DASHBOARD → add_dashboard_widget type=scada-mimic mimicPath=<path>
                6. Bind symbols to device variables (list_variables first)
                
                Minimal example (one tank + label):
                  save_mimic_diagram path=root.platform.mimics.demo-tank elements=[
                    {"id":"lbl","symbolId":"label","layerId":"layer-default","x":80,"y":40,"props":{"text":"Резервуар 1"}},
                    {"id":"t1","symbolId":"tank.vertical","layerId":"layer-default","x":100,"y":80,
                     "bindings":{"fillLevel":{"objectPath":"root.platform.devices.demo-sensor","variableName":"level","valueField":"value","transform":"number"}}}
                  ]
                """;
    }

    public static String widgetCatalogGuide() {
        return AgentWidgetCatalog.referenceText();
    }

    public static String widgetPropertiesGuide() {
        return AgentWidgetPropertiesGuide.referenceText();
    }

    public static String reportsGuide() {
        return """
                ## Reports (REQ-PF-12)

                Tree-first REPORT objects under root.platform.reports.*.

                ### Tools (use these — do not invent REST paths)
                - list_reports — catalog under root.platform.reports
                - get_report_schema path=... — definition, columns, exportFormats, yargPlaceholders
                - run_report path=... parameters={} — preview rows before wiring UI
                - configure_report — create/update SQL or tree-variables report
                - create_object parentPath=root.platform.reports type=REPORT (prefer configure_report)
                - add_dashboard_widget type=report reportPath=...
                - configure_operator_ui — manifest screens with report
                - get_automation_schema topic=report — this guide

                ### Report types
                - **sql** (report-v1): dataSourcePath → root.platform.data-sources.*, SELECT query, ? parameters, columns
                - **tree-variables** (tree-variables-report-v1): devicePathPattern (glob), variableName, columns

                ### Lab virtual reports (bootstrap)
                - root.platform.reports.lab-all-devices-table — variable table, columns devicepath/int/string
                - root.platform.reports.lab-virtual-status — variable status, columns devicepath/online/lastseen
                - root.platform.reports.lab-virtual-sine — sineWave snapshot
                - root.platform.reports.lab-virtual-waves-sum — sumWaves
                - root.platform.reports.lab-table-corrective — opened from table action when int sum > 100
                - Pattern for lab devices: root.platform.devices.lab-*

                ### configure_report examples
                tree-variables:
                  reportId=lab-device-status reportType=tree-variables title="Device status"
                  devicePathPattern=root.platform.devices.lab-* variableName=status
                  columns=[{field:devicepath,label:"Device path"},{field:online,label:Online},{field:lastseen,label:"Last seen"}]
                sql:
                  reportId=ready-items reportType=sql dataSourcePath=root.platform.data-sources.demo
                  query="SELECT item_code, status FROM demo_item WHERE status = ?"
                  parameters=["status"] columns=[{field:item_code,label:Code},{field:status,label:Status}]

                ### Dashboard widget type=report
                - reportPath (required)
                - parametersJson — static run params
                - contextParamsJson — {reportParam: sessionParamKey}
                - Export buttons in widget are optional; user exports via Report Builder too

                ### YARG templates (PDF/XLSX/HTML)
                - Upload .xls/.docx in Report Builder → Шаблон YARG (agent cannot upload binary files)
                - Named range **Band1** on the data row in Excel
                - Placeholders must match report column **field** names in UPPERCASE:
                  Excel (.xls): ${DEVICEPATH} or ${Band1.DEVICEPATH} (server rewrites Band1. for Excel)
                  Word (.docx): ${Band1.DEVICEPATH}
                - get_report_schema returns yargPlaceholders for exact field names
                - Column field names in report definition MUST match template placeholders
                - Without template: CSV + table XLSX/HTML; with .xls template: styled PDF/XLSX

                ### Bundle manifest
                reports[] with reportId, title, query OR reportType=tree-variables + devicePathPattern + variableName

                ### Playbook: report on dashboard
                1. list_reports or get_report_schema path=...
                2. run_report to verify data
                3. add_dashboard_widget widget={type:report, reportPath, title}
                4. finish — tell user Report Builder path for template/export

                ### Playbook: new tree-variables report for devices
                1. list_variables on sample device to pick variableName
                2. configure_report reportType=tree-variables ...
                3. run_report preview
                4. optional: add_dashboard_widget or configure_operator_ui
                """;
    }

    public static String platformMasterGuide() {
        return """
                ## Platform master index (agent tools by area)
                
                **Discovery:** search_context, list_drivers, get_driver_help, list_examples, get_example_bundle, list_object_models
                
                **Models (RELATIVE mixins):** list_relative_models, get_object_model, apply_relative_model
                — enrich DEVICE/CUSTOM with variables, events, functions (virtual-lab-v1, virtual-unified-v1, …)
                
                **Object tree:** list_objects, get_object, create_object, delete_object, search_objects, search_by_haystack_tags
                list_variables, describe_variables, set_variable, create_variable
                
                **Devices/drivers:** configure_driver, driver_control, configure_variable_history, create_virtual_device
                
                **Bindings/rules:** create_binding_rule, list_binding_rules, configure_platform_context_rule, create_binding_rule refAt/CEL
                
                **Dashboards/HMI:** get_dashboard_layout, set_dashboard_layout template=, add_dashboard_widget, get_widget_catalog
                configure_operator_ui, configure_platform_context_rule (drill-down, visibility)
                
                **SCADA mimic:** list_mimic_symbols → create_object MIMIC → save_mimic_diagram elements[] → get_mimic_diagram
                → add_dashboard_widget type=scada-mimic mimicPath=...
                
                **Reports:** list_reports, get_report_schema, run_report, configure_report, add_dashboard_widget type=report
                
                **Automation:** configure_alert, configure_correlator, list_automation, get_automation_schema
                
                **Workflows:** create_object WORKFLOW → save_workflow_bpmn → update_workflow_status ACTIVE → run_workflow
                list_workflow_instances, signal_workflow_instance, cancel_workflow_instance
                
                **Applications (REST D):** register_application → application_data_migrate → deploy_app_binding / deploy_app_function
                validate_bundle → dry_run_deploy → import_package; export_application_bundle, rollback_application_deploy
                
                **Schedules:** list_platform_schedules, configure_platform_schedule (intervalMs + invoke function)
                
                **Functions/events:** list_functions, get_function, invoke_bff, invoke_tree_function, fire_event, list_events
                
                **Tree functions (script + Java):** get_function_template → deploy_tree_function → invoke_tree_function
                Application BFF: deploy_app_function sourceType=script only
                
                **Semantic/timezone:** export_haystack, resolve_timezone, search_by_haystack_tags
                
                Always finish end-to-end with tools — never defer to manual UI when a tool exists.
                get_automation_schema topic=<area> for detailed field reference.
                """;
    }

    public static String workflowGuide() {
        return """
                ## Workflows (BPMN on tree)
                
                ### Tools
                - create_object parentPath=root.platform.workflows type=WORKFLOW templateId=workflow-v1
                - get_workflow path=... — bpmnXml, status, instanceState
                - save_workflow_bpmn path=... bpmnXml=<BPMN 2.0 XML>
                - update_workflow_status path=... status=ACTIVE|INACTIVE|DRAFT
                - run_workflow path=... triggerObjectPath=... (optional)
                - list_workflow_instances path=...
                - signal_workflow_instance instanceId=... signal=...
                - cancel_workflow_instance instanceId=... reason=...
                - list_work_queue — operator open user tasks
                
                ### Agent workflow
                1. create_object WORKFLOW
                2. save_workflow_bpmn with start → serviceTask/userTask → end
                3. update_workflow_status ACTIVE
                4. run_workflow to test; list_workflow_instances for instanceId
                5. configure_correlator action=RUN_WORKFLOW for event-driven start (optional)
                6. add_dashboard_widget type=work-queue for operator tasks (optional)
                
                BPMN service tasks can invoke tree functions (see WORKFLOWS.md in search_context topic=workflows).
                """;
    }

    public static String applicationLifecycleGuide() {
        return """
                ## Application lifecycle (bundle + REST D)
                
                ### Full bundle path (production)
                1. get_example_bundle appId=mes-reference sections=[manifest,migrations,functions]
                2. validate_bundle appId=... manifest={...}
                3. dry_run_deploy appId=... manifest={...}
                4. import_package appId=... manifest={...}
                5. configure_operator_ui from manifest operatorUi
                
                ### Incremental REST D tools
                - register_application appId displayName tablePrefix schemaName
                - application_data_status appId
                - application_data_migrate appId version scripts=[{id,sql}]
                - application_data_seed appId profile=...
                - deploy_app_binding appId objectPath variable query refreshIntervalMs
                - deploy_app_function appId objectPath functionName sourceType sourceBody
                - list_app_bindings appId
                - export_application_bundle appId
                - rollback_application_deploy appId version
                - pull_application_from_tree appId sections=[dashboards,workflows,...]
                - list_applications
                
                After any deploy: list_variables / invoke_bff to verify tree paths.
                """;
    }

    public static String platformRuleGuide() {
        return """
                ## Platform rules (dashboard context + events, ADR-0019)
                
                ### Dashboard visibility / drill-down
                - configure_platform_context_rule on DASHBOARD object
                - targetKind=context contextPath=params.mode (or selection.*)
                - expression: CEL string result; condition uses context.selection.* and refAt(path,var)
                - onContextChange=true (default)
                
                ### Variable binding (devices, CUSTOM hubs)
                - create_binding_rule path=... targetKind=variable targetVariable=... expression=...
                - Cross-device: remoteObjectPath + remoteVariableName activators
                - refAt(otherPath, varName) in expression for computed values
                
                ### Event side-effects
                - create_binding_rule targetKind=event eventName=... expression=...
                
                ### Inspect
                - list_binding_rules path=...
                - get_automation_schema topic=platform-rule
                
                Pair with object-table rowTargetDashboard + selectionKey for drill-down dashboards.
                """;
    }

    public static String scheduleGuide() {
        return """
                ## Platform schedules (root.platform.schedules)
                
                ### Tools
                - list_platform_schedules
                - configure_platform_schedule scheduleId intervalMs objectPath functionName
                  (create) OR path=... (update)
                
                ### Agent workflow
                1. list_functions on target object — pick functionName
                2. configure_platform_schedule scheduleId=my-poll intervalMs=60000 objectPath=... functionName=...
                3. list_platform_schedules to verify enabled=true
                
                Schedules invoke tree functions periodically. Prefer bundle schedules[] for repeatable deploys.
                """;
    }

    public static String functionsGuide() {
        return """
                ## Object-tree functions (script + Java)
                
                ### Tools
                - get_function_template topic=java|script|comparison — skeleton and rules
                - deploy_tree_function path functionName sourceType sourceBody inputSchema outputSchema
                - invoke_tree_function path functionName inputRows={} — test after deploy
                - list_functions / get_function — inspect existing
                - deploy_app_function — application BFF **script only** (SQL steps, app schema)
                
                ### When to use Java vs script
                | sourceType | Use for |
                |------------|---------|
                | **java** | Typed logic, math, conditions; compiles on save (ObjectJavaFunction) |
                | **script** | SQL (selectOne/exec), readVariable, invoke_function, workflow-style steps |
                | **(none)** | Built-in platform handlers by name (acknowledgeAlarm, calculate, …) |
                
                ### Java deploy example
                get_function_template topic=java
                deploy_tree_function path=root.platform.devices.demo-sensor-01 functionName=checkThreshold
                  sourceType=java
                  inputSchema={fields:[{name:value,type:DOUBLE}]}
                  outputSchema={fields:[{name:alarm,type:BOOLEAN}]}
                  sourceBody=\"\"\"
                  import com.ispf.core.function.ObjectJavaFunction;
                  import com.ispf.core.function.JavaFunctionContext;
                  import com.ispf.core.model.*;
                  import java.util.Map;
                  public class CheckThresholdFn implements ObjectJavaFunction {
                    public DataRecord invoke(DataRecord input, JavaFunctionContext ctx) {
                      double v = ((Number)input.firstRow().get("value")).doubleValue();
                      return DataRecord.single(
                        DataSchema.builder("out").field("alarm", FieldType.BOOLEAN).build(),
                        Map.of("alarm", v > 80));
                    }
                  }
                  \"\"\"
                invoke_tree_function path=... functionName=checkThreshold inputRows=[{value:90}]
                
                ### Script deploy example
                deploy_tree_function sourceType=script sourceBody={"steps":[{"type":"return","fields":{"ok":true}}]}
                
                search_context topic=functions for full OBJECT_FUNCTIONS.md (Java security limits, script steps).
                """;
    }
}
