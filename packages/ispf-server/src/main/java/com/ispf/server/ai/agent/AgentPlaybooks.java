package com.ispf.server.ai.agent;

import com.ispf.server.bootstrap.LabModelBootstrap;
import com.ispf.server.bootstrap.MiniTecPaths;
import com.ispf.server.dashboard.DashboardLayouts;
import com.ispf.server.plugin.model.ModelBootstrap;

/**
 * Curated multi-step recipes for the tree-first agent (referenced from system prompt).
 * <p>
 * Never use {@link String#formatted(String, Object...)} here — playbooks contain {@code %}, JSON, and paths
 * that break format strings. Use string concatenation only.
 */
public final class AgentPlaybooks {

    public static final String SNMP_DEVICE_PATH = ModelBootstrap.SNMP_LOCALHOST_PATH;
    public static final String SNMP_DASHBOARD_PATH = "root.platform.dashboards.snmp-host-monitoring";
    public static final String SNMP_MODEL = ModelBootstrap.SNMP_AGENT_MODEL;
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
                + ModelBootstrap.SNMP_DRIVER_CONFIG
                + """
                
                4. set_variable path="""
                + SNMP_DEVICE_PATH
                + " name=driverPointMappingsJson value="
                + ModelBootstrap.SNMP_POINT_MAPPINGS
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
                + ModelBootstrap.SNMP_POINT_MAPPINGS
                + "\n";
    }

    public static String virtualMeterLab() {
        return """
                ## Virtual driver — meter profile (lab-training / MES)
                
                Цель: симулятор налива без железа.
                
                Шаги:
                1. create_object parentPath=root.platform.devices name=virtual-meter type=DEVICE
                   displayName=Virtual meter, templateId=device-v1, driverId=virtual, autoStartDriver=false
                2. set_variable name=driverConfigJson value={"profile":"meter","litersPerSecond":"120","filling":"true"}
                3. configure_driver driverId=virtual autoStart=true
                4. list_variables — meterLiters, flowRate, filling
                5. finish
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

    public static String widgetCatalogGuide() {
        return AgentWidgetCatalog.referenceText();
    }

    public static String widgetPropertiesGuide() {
        return AgentWidgetPropertiesGuide.referenceText();
    }
}
