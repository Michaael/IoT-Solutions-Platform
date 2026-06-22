package com.ispf.server.ai.agent;

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
        return """
                ## Редактирование дашборда (layout)
                
                Виджеты хранятся ТОЛЬКО в переменной layout (JSON-строка с полем widgets[]).
                Нет отдельной переменной widgets.
                
                Быстрый путь:
                1. get_dashboard_layout path="""
                + SNMP_DASHBOARD_PATH
                + """
                 — прочитать текущий layout
                2a. set_dashboard_layout path="""
                + SNMP_DASHBOARD_PATH
                + """
                 template=snmp-host-monitoring — восстановить эталон
                2b. add_dashboard_widget path="""
                + SNMP_DASHBOARD_PATH
                + """
                 widget={...} — добавить один виджет
                3. finish
                
                Пример виджета CPU (selectionKey=device, unit — символ процента):
                {"id":"cpu-value","type":"value","title":"CPU","x":0,"y":0,"w":3,"h":2,
                 "selectionKey":"device","variableName":"hrProcessorLoad","valueField":"value","unit":"pct"}
                
                Не вызывай search_context больше 1–2 раз подряд — используй get_dashboard_layout.
                """;
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
}
