package com.ispf.server.dashboard;

/**
 * Default dashboard layout JSON for demo and new dashboards.
 */
public final class DashboardLayouts {

    public static final String DEMO_SENSOR_DASHBOARD = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": [
                {
                  "id": "temp-value",
                  "type": "value",
                  "title": "Температура",
                  "x": 0,
                  "y": 0,
                  "w": 4,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "unitField": "unit"
                },
                {
                  "id": "threshold-value",
                  "type": "value",
                  "title": "Порог",
                  "x": 4,
                  "y": 0,
                  "w": 3,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "threshold",
                  "valueField": "value",
                  "unit": "°C"
                },
                {
                  "id": "alarm-indicator",
                  "type": "indicator",
                  "title": "Тревога",
                  "x": 7,
                  "y": 0,
                  "w": 3,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "alarmActive",
                  "valueField": "value",
                  "trueLabel": "Активна",
                  "falseLabel": "Норма"
                },
                {
                  "id": "online-indicator",
                  "type": "indicator",
                  "title": "Связь",
                  "x": 10,
                  "y": 0,
                  "w": 2,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "status",
                  "valueField": "online",
                  "trueLabel": "Онлайн",
                  "falseLabel": "Офлайн"
                },
                {
                  "id": "temp-trend",
                  "type": "chart",
                  "title": "Тренд температуры",
                  "x": 0,
                  "y": 2,
                  "w": 8,
                  "h": 4,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "unitField": "unit",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#2f81f7",
                  "decimals": 1
                },
                {
                  "id": "temp-sparkline",
                  "type": "sparkline",
                  "title": "Спарклайн",
                  "x": 8,
                  "y": 2,
                  "w": 4,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "maxPoints": 40,
                  "color": "#3fb950",
                  "decimals": 1
                },
                {
                  "id": "ack-alarm-fn",
                  "type": "function",
                  "title": "Сброс тревоги",
                  "x": 0,
                  "y": 6,
                  "w": 4,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "alarmActive",
                  "functionName": "acknowledgeAlarm",
                  "buttonLabel": "Подтвердить тревогу",
                  "confirmMessage": "Подтвердить сброс активной тревоги?"
                }
              ]
            }
            """;

    public static final String SNMP_HOST_MONITORING_DASHBOARD = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": [
                {
                  "id": "device-table",
                  "type": "object-table",
                  "title": "Устройства",
                  "x": 0,
                  "y": 0,
                  "w": 12,
                  "h": 3,
                  "parentPath": "root.platform.devices",
                  "selectionKey": "device",
                  "columnsJson": "[{\\"variable\\":\\"sysName\\",\\"label\\":\\"Имя хоста\\"},{\\"variable\\":\\"driverStatus\\",\\"label\\":\\"Драйвер\\"},{\\"variable\\":\\"sysUpTime\\",\\"label\\":\\"Uptime\\"}]"
                },
                {
                  "id": "online-indicator",
                  "type": "indicator",
                  "title": "Связь",
                  "x": 0,
                  "y": 3,
                  "w": 2,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "status",
                  "valueField": "online",
                  "trueLabel": "Онлайн",
                  "falseLabel": "Офлайн",
                  "trueColor": "#3fb950"
                },
                {
                  "id": "hostname-value",
                  "type": "value",
                  "title": "Имя хоста",
                  "x": 2,
                  "y": 3,
                  "w": 3,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "sysName",
                  "valueField": "value"
                },
                {
                  "id": "uptime-value",
                  "type": "value",
                  "title": "Uptime",
                  "x": 5,
                  "y": 3,
                  "w": 3,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "sysUpTime",
                  "valueField": "raw"
                },
                {
                  "id": "memory-value",
                  "type": "value",
                  "title": "Память",
                  "x": 8,
                  "y": 3,
                  "w": 2,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "hrMemorySize",
                  "valueField": "value",
                  "decimals": 0,
                  "unit": "KB"
                },
                {
                  "id": "processes-value",
                  "type": "value",
                  "title": "Процессы",
                  "x": 10,
                  "y": 3,
                  "w": 2,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "hrSystemProcesses",
                  "valueField": "value",
                  "decimals": 0
                },
                {
                  "id": "sysdescr-value",
                  "type": "value",
                  "title": "Описание ОС",
                  "x": 0,
                  "y": 5,
                  "w": 6,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "sysDescr",
                  "valueField": "value",
                  "stylesJson": "{\\"value\\":{\\"fontSize\\":\\"0.82rem\\",\\"whiteSpace\\":\\"normal\\",\\"overflowY\\":\\"auto\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "location-value",
                  "type": "value",
                  "title": "Расположение",
                  "x": 6,
                  "y": 5,
                  "w": 3,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "sysLocation",
                  "valueField": "value"
                },
                {
                  "id": "contact-value",
                  "type": "value",
                  "title": "Контакт",
                  "x": 9,
                  "y": 5,
                  "w": 3,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "sysContact",
                  "valueField": "value"
                },
                {
                  "id": "users-value",
                  "type": "value",
                  "title": "Пользователи",
                  "x": 0,
                  "y": 7,
                  "w": 2,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "hrSystemNumUsers",
                  "valueField": "value",
                  "decimals": 0
                },
                {
                  "id": "interfaces-value",
                  "type": "value",
                  "title": "Интерфейсы",
                  "x": 2,
                  "y": 7,
                  "w": 2,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "ifNumber",
                  "valueField": "value",
                  "decimals": 0
                },
                {
                  "id": "driver-status-badge",
                  "type": "status-badge",
                  "title": "Драйвер",
                  "x": 4,
                  "y": 7,
                  "w": 2,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "driverStatus",
                  "valueField": "value"
                },
                {
                  "id": "uptime-sparkline",
                  "type": "sparkline",
                  "title": "Uptime (тренд)",
                  "x": 6,
                  "y": 7,
                  "w": 3,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "sysUpTime",
                  "valueField": "value",
                  "maxPoints": 60,
                  "color": "#3fb950",
                  "decimals": 0
                },
                {
                  "id": "processes-sparkline",
                  "type": "sparkline",
                  "title": "Процессы (тренд)",
                  "x": 9,
                  "y": 7,
                  "w": 3,
                  "h": 2,
                  "selectionKey": "device",
                  "variableName": "hrSystemProcesses",
                  "valueField": "value",
                  "maxPoints": 60,
                  "color": "#2f81f7",
                  "decimals": 0
                },
                {
                  "id": "uptime-chart",
                  "type": "chart",
                  "title": "Uptime — история",
                  "x": 0,
                  "y": 9,
                  "w": 8,
                  "h": 4,
                  "selectionKey": "device",
                  "variableName": "sysUpTime",
                  "valueField": "value",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#2f81f7",
                  "decimals": 0
                },
                {
                  "id": "memory-chart",
                  "type": "chart",
                  "title": "Память (KB) — история",
                  "x": 8,
                  "y": 9,
                  "w": 4,
                  "h": 4,
                  "selectionKey": "device",
                  "variableName": "hrMemorySize",
                  "valueField": "value",
                  "chartStyle": "line",
                  "maxPoints": 120,
                  "color": "#d29922",
                  "decimals": 0
                }
              ]
            }
            """;

    public static final String EMPTY_DASHBOARD = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": []
            }
            """;

    private DashboardLayouts() {
    }
}
