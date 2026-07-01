package com.ispf.server.dashboard;

/**
 * Default dashboard layout JSON for demo and new dashboards.
 */
public final class DashboardLayouts {

    public static final String DEMO_SENSOR_DASHBOARD = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": [
                {
                  "id": "temp-value",
                  "type": "value",
                  "title": "Температура",
                  "x": 0,
                  "y": 0,
                  "w": 28,
                  "h": 14,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "unitField": "unit"
                },
                {
                  "id": "threshold-value",
                  "type": "value",
                  "title": "Порог",
                  "x": 28,
                  "y": 0,
                  "w": 21,
                  "h": 14,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "threshold",
                  "valueField": "value",
                  "unit": "°C"
                },
                {
                  "id": "alarm-indicator",
                  "type": "indicator",
                  "title": "Тревога",
                  "x": 49,
                  "y": 0,
                  "w": 21,
                  "h": 14,
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
                  "x": 70,
                  "y": 0,
                  "w": 14,
                  "h": 14,
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
                  "y": 14,
                  "w": 56,
                  "h": 28,
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
                  "x": 56,
                  "y": 14,
                  "w": 28,
                  "h": 14,
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
                  "y": 42,
                  "w": 28,
                  "h": 14,
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
              "columns": 84,
              "rowHeight": 8,
              "theme": "btop",
              "widgets": [
                {
                  "id": "btop-host",
                  "type": "value",
                  "title": "host",
                  "x": 0,
                  "y": 0,
                  "w": 28,
                  "h": 7,
                  "selectionKey": "device",
                  "variableName": "sysName",
                  "valueField": "value",
                  "stylesJson": "{\\"value\\":{\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.95rem\\",\\"fontWeight\\":\\"700\\",\\"color\\":\\"#e6edf3\\"},\\"card\\":{\\"backgroundColor\\":\\"transparent\\",\\"border\\":\\"none\\",\\"padding\\":\\"2px 6px\\"},\\"title\\":{\\"color\\":\\"#58a6ff\\",\\"fontSize\\":\\"0.62rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.14em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "btop-uptime",
                  "type": "value",
                  "title": "up",
                  "x": 28,
                  "y": 0,
                  "w": 21,
                  "h": 7,
                  "selectionKey": "device",
                  "variableName": "sysUpTime",
                  "valueField": "raw",
                  "stylesJson": "{\\"value\\":{\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.78rem\\",\\"color\\":\\"#8b949e\\"},\\"card\\":{\\"backgroundColor\\":\\"transparent\\",\\"border\\":\\"none\\",\\"padding\\":\\"2px 6px\\"},\\"title\\":{\\"color\\":\\"#6e7681\\",\\"fontSize\\":\\"0.62rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.14em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "btop-users",
                  "type": "value",
                  "title": "usr",
                  "x": 49,
                  "y": 0,
                  "w": 7,
                  "h": 7,
                  "selectionKey": "device",
                  "variableName": "hrSystemNumUsers",
                  "valueField": "value",
                  "decimals": 0,
                  "stylesJson": "{\\"value\\":{\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.82rem\\",\\"fontWeight\\":\\"700\\",\\"color\\":\\"#ffa657\\"},\\"card\\":{\\"backgroundColor\\":\\"transparent\\",\\"border\\":\\"none\\",\\"padding\\":\\"2px 4px\\"},\\"title\\":{\\"color\\":\\"#6e7681\\",\\"fontSize\\":\\"0.58rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.1em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "btop-proc-count",
                  "type": "value",
                  "title": "prc",
                  "x": 56,
                  "y": 0,
                  "w": 7,
                  "h": 7,
                  "selectionKey": "device",
                  "variableName": "hrSystemProcesses",
                  "valueField": "value",
                  "decimals": 0,
                  "stylesJson": "{\\"value\\":{\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.82rem\\",\\"fontWeight\\":\\"700\\",\\"color\\":\\"#7ee787\\"},\\"card\\":{\\"backgroundColor\\":\\"transparent\\",\\"border\\":\\"none\\",\\"padding\\":\\"2px 4px\\"},\\"title\\":{\\"color\\":\\"#6e7681\\",\\"fontSize\\":\\"0.58rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.1em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "btop-if-count",
                  "type": "value",
                  "title": "net",
                  "x": 63,
                  "y": 0,
                  "w": 7,
                  "h": 7,
                  "selectionKey": "device",
                  "variableName": "ifNumber",
                  "valueField": "value",
                  "decimals": 0,
                  "stylesJson": "{\\"value\\":{\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.82rem\\",\\"fontWeight\\":\\"700\\",\\"color\\":\\"#56d4dd\\"},\\"card\\":{\\"backgroundColor\\":\\"transparent\\",\\"border\\":\\"none\\",\\"padding\\":\\"2px 4px\\"},\\"title\\":{\\"color\\":\\"#6e7681\\",\\"fontSize\\":\\"0.58rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.1em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "btop-online",
                  "type": "indicator",
                  "title": "lnk",
                  "x": 70,
                  "y": 0,
                  "w": 7,
                  "h": 7,
                  "selectionKey": "device",
                  "variableName": "status",
                  "valueField": "online",
                  "trueLabel": "●",
                  "falseLabel": "○",
                  "trueColor": "#3fb950",
                  "falseColor": "#484f58",
                  "stylesJson": "{\\"card\\":{\\"backgroundColor\\":\\"transparent\\",\\"border\\":\\"none\\",\\"padding\\":\\"2px 4px\\"},\\"title\\":{\\"color\\":\\"#6e7681\\",\\"fontSize\\":\\"0.58rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.1em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "btop-driver",
                  "type": "status-badge",
                  "title": "drv",
                  "x": 77,
                  "y": 0,
                  "w": 7,
                  "h": 7,
                  "selectionKey": "device",
                  "variableName": "driverStatus",
                  "valueField": "value",
                  "stylesJson": "{\\"card\\":{\\"backgroundColor\\":\\"transparent\\",\\"border\\":\\"none\\",\\"padding\\":\\"2px 4px\\"},\\"title\\":{\\"color\\":\\"#6e7681\\",\\"fontSize\\":\\"0.58rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.1em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"badge\\":{\\"fontSize\\":\\"0.62rem\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"meta\\":{\\"display\\":\\"none\\"}}"
                },
                {
                  "id": "btop-cpu",
                  "type": "chart",
                  "title": "cpu",
                  "x": 0,
                  "y": 7,
                  "w": 42,
                  "h": 35,
                  "selectionKey": "device",
                  "variableName": "hrProcessorLoad",
                  "valueField": "value",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#7ee787",
                  "decimals": 0,
                  "unit": "%",
                  "stylesJson": "{\\"card\\":{\\"backgroundColor\\":\\"#0a0e14\\",\\"border\\":\\"1px solid #1f2937\\",\\"borderRadius\\":\\"4px\\",\\"padding\\":\\"6px 8px\\"},\\"title\\":{\\"color\\":\\"#7ee787\\",\\"fontSize\\":\\"0.65rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.12em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"value\\":{\\"color\\":\\"#7ee787\\",\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.82rem\\",\\"fontWeight\\":\\"700\\"}}"
                },
                {
                  "id": "btop-mem",
                  "type": "chart",
                  "title": "mem",
                  "x": 42,
                  "y": 7,
                  "w": 42,
                  "h": 35,
                  "selectionKey": "device",
                  "variableName": "hrMemorySize",
                  "valueField": "value",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#ffa657",
                  "decimals": 0,
                  "unit": "KB",
                  "stylesJson": "{\\"card\\":{\\"backgroundColor\\":\\"#0a0e14\\",\\"border\\":\\"1px solid #1f2937\\",\\"borderRadius\\":\\"4px\\",\\"padding\\":\\"6px 8px\\"},\\"title\\":{\\"color\\":\\"#ffa657\\",\\"fontSize\\":\\"0.65rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.12em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"value\\":{\\"color\\":\\"#ffa657\\",\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.82rem\\",\\"fontWeight\\":\\"700\\"}}"
                },
                {
                  "id": "btop-net-down",
                  "type": "chart",
                  "title": "net ↓",
                  "x": 0,
                  "y": 42,
                  "w": 42,
                  "h": 28,
                  "selectionKey": "device",
                  "variableName": "ifInOctetsRate",
                  "valueField": "value",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#56d4dd",
                  "decimals": 1,
                  "unit": "B/s",
                  "stylesJson": "{\\"card\\":{\\"backgroundColor\\":\\"#0a0e14\\",\\"border\\":\\"1px solid #1f2937\\",\\"borderRadius\\":\\"4px\\",\\"padding\\":\\"6px 8px\\"},\\"title\\":{\\"color\\":\\"#56d4dd\\",\\"fontSize\\":\\"0.65rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.12em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"value\\":{\\"color\\":\\"#56d4dd\\",\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.82rem\\",\\"fontWeight\\":\\"700\\"}}"
                },
                {
                  "id": "btop-net-up",
                  "type": "chart",
                  "title": "net ↑",
                  "x": 42,
                  "y": 42,
                  "w": 42,
                  "h": 28,
                  "selectionKey": "device",
                  "variableName": "ifOutOctetsRate",
                  "valueField": "value",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#d2a8ff",
                  "decimals": 1,
                  "unit": "B/s",
                  "stylesJson": "{\\"card\\":{\\"backgroundColor\\":\\"#0a0e14\\",\\"border\\":\\"1px solid #1f2937\\",\\"borderRadius\\":\\"4px\\",\\"padding\\":\\"6px 8px\\"},\\"title\\":{\\"color\\":\\"#d2a8ff\\",\\"fontSize\\":\\"0.65rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.12em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"value\\":{\\"color\\":\\"#d2a8ff\\",\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.82rem\\",\\"fontWeight\\":\\"700\\"}}"
                },
                {
                  "id": "btop-proc",
                  "type": "object-table",
                  "title": "proc",
                  "x": 0,
                  "y": 70,
                  "w": 84,
                  "h": 28,
                  "parentPath": "root.platform.devices",
                  "selectionKey": "device",
                  "columnsJson": "[{\\"variable\\":\\"hrProcessorLoad\\",\\"label\\":\\"cpu%\\"},{\\"variable\\":\\"hrSystemProcesses\\",\\"label\\":\\"prc\\"},{\\"variable\\":\\"hrMemorySize\\",\\"label\\":\\"mem KB\\"},{\\"variable\\":\\"driverStatus\\",\\"label\\":\\"drv\\"}]",
                  "stylesJson": "{\\"card\\":{\\"backgroundColor\\":\\"#0a0e14\\",\\"border\\":\\"1px solid #1f2937\\",\\"borderRadius\\":\\"4px\\",\\"padding\\":\\"6px 8px\\"},\\"title\\":{\\"color\\":\\"#58a6ff\\",\\"fontSize\\":\\"0.65rem\\",\\"fontWeight\\":\\"700\\",\\"letterSpacing\\":\\"0.12em\\",\\"fontFamily\\":\\"Consolas, monospace\\"},\\"table\\":{\\"fontFamily\\":\\"Consolas, monospace\\",\\"fontSize\\":\\"0.72rem\\"},\\"body\\":{\\"maxHeight\\":\\"100%\\",\\"overflowY\\":\\"auto\\"}}"
                }
              ]
            }
            """;

    public static final String VIRTUAL_CLUSTER_OVERVIEW = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": [
                {
                  "id": "cluster-error",
                  "type": "indicator",
                  "title": "Cluster ERROR",
                  "x": 0,
                  "y": 0,
                  "w": 21,
                  "h": 14,
                  "objectPath": "root.platform.devices.virt-cluster.hub",
                  "variableName": "clusterError",
                  "valueField": "value",
                  "trueLabel": "ERROR",
                  "falseLabel": "OK",
                  "trueColor": "#f85149",
                  "falseColor": "#3fb950"
                },
                {
                  "id": "cluster-devices",
                  "type": "object-table",
                  "title": "Virtual cluster devices",
                  "x": 0,
                  "y": 14,
                  "w": 84,
                  "h": 35,
                  "parentPath": "root.platform.devices.virt-cluster",
                  "selectionKey": "device",
                  "rowTargetDashboard": "root.platform.dashboards.virt-cluster-detail",
                  "rowOpenMode": "navigate",
                  "columnsJson": "[{\\"variable\\":\\"sineWave\\",\\"label\\":\\"sine\\"},{\\"variable\\":\\"sawtoothWave\\",\\"label\\":\\"saw\\"},{\\"variable\\":\\"triangleWave\\",\\"label\\":\\"tri\\"},{\\"variable\\":\\"status\\",\\"label\\":\\"online\\",\\"field\\":\\"online\\"}]"
                }
              ]
            }
            """;

    public static final String VIRTUAL_CLUSTER_DETAIL = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": [
                {
                  "id": "sine-value",
                  "type": "value",
                  "title": "Sine",
                  "x": 0,
                  "y": 0,
                  "w": 28,
                  "h": 14,
                  "selectionKey": "device",
                  "variableName": "sineWave",
                  "valueField": "value",
                  "decimals": 2
                },
                {
                  "id": "saw-value",
                  "type": "value",
                  "title": "Sawtooth",
                  "x": 28,
                  "y": 0,
                  "w": 28,
                  "h": 14,
                  "selectionKey": "device",
                  "variableName": "sawtoothWave",
                  "valueField": "value",
                  "decimals": 2
                },
                {
                  "id": "tri-value",
                  "type": "value",
                  "title": "Triangle",
                  "x": 56,
                  "y": 0,
                  "w": 28,
                  "h": 14,
                  "selectionKey": "device",
                  "variableName": "triangleWave",
                  "valueField": "value",
                  "decimals": 2
                },
                {
                  "id": "sine-chart",
                  "type": "chart",
                  "title": "Sine trend",
                  "x": 0,
                  "y": 14,
                  "w": 84,
                  "h": 28,
                  "selectionKey": "device",
                  "variableName": "sineWave",
                  "valueField": "value",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#2f81f7",
                  "decimals": 2
                },
                {
                  "id": "saw-chart",
                  "type": "chart",
                  "title": "Sawtooth trend",
                  "x": 0,
                  "y": 42,
                  "w": 42,
                  "h": 28,
                  "selectionKey": "device",
                  "variableName": "sawtoothWave",
                  "valueField": "value",
                  "chartStyle": "line",
                  "maxPoints": 120,
                  "color": "#ffa657",
                  "decimals": 2
                },
                {
                  "id": "tri-chart",
                  "type": "chart",
                  "title": "Triangle trend",
                  "x": 42,
                  "y": 42,
                  "w": 42,
                  "h": 28,
                  "selectionKey": "device",
                  "variableName": "triangleWave",
                  "valueField": "value",
                  "chartStyle": "line",
                  "maxPoints": 120,
                  "color": "#3fb950",
                  "decimals": 2
                }
              ]
            }
            """;

    public static final String MONITORING_OVERVIEW = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": [
                {
                  "id": "devices-table",
                  "type": "object-table",
                  "title": "Devices overview",
                  "x": 0,
                  "y": 0,
                  "w": 84,
                  "h": 35,
                  "parentPath": "root.platform.devices",
                  "selectionKey": "device",
                  "columnsJson": "[{\\"variable\\":\\"status\\",\\"label\\":\\"online\\",\\"field\\":\\"online\\"}]"
                },
                {
                  "id": "primary-value",
                  "type": "value",
                  "title": "Primary metric",
                  "x": 0,
                  "y": 35,
                  "w": 28,
                  "h": 14,
                  "selectionKey": "device",
                  "variableName": "temperature",
                  "valueField": "value"
                }
              ]
            }
            """;

    public static final String SCADA_FACILITY_OVERVIEW = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": [
                {
                  "id": "facility-mimic",
                  "type": "scada-mimic",
                  "title": "Facility mimic",
                  "x": 0,
                  "y": 0,
                  "w": 84,
                  "h": 63,
                  "mimicPath": "root.platform.mimics.facility-overview"
                },
                {
                  "id": "facility-devices",
                  "type": "object-table",
                  "title": "Facility devices",
                  "x": 0,
                  "y": 63,
                  "w": 84,
                  "h": 28,
                  "parentPath": "root.platform.devices",
                  "selectionKey": "device",
                  "columnsJson": "[{\\"variable\\":\\"status\\",\\"label\\":\\"online\\",\\"field\\":\\"online\\"}]"
                }
              ]
            }
            """;

    public static final String EMPTY_DASHBOARD = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": []
            }
            """;

    private DashboardLayouts() {
    }
}
