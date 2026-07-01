package com.ispf.server.ai.agent;

import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.dashboard.DashboardWidgetPlacement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step-by-step dashboard workflow and anti-patterns for the tree-first agent.
 * <p>
 * Complements {@link AgentWidgetCatalog} (types/fields) and playbooks (recipes).
 */
public final class AgentDashboardGuide {

    private AgentDashboardGuide() {
    }

    public static String referenceText() {
        return """
                ## Дашборды — как работать (обязательный порядок)
                
                Дашборд = объект type=DASHBOARD, templateId=dashboard-v1, parentPath=root.platform.dashboards.
                Виджеты хранятся **только** в переменной `layout` (JSON-строка: {columns, rowHeight, widgets[]}).
                
                ### Алгоритм создания
                
                1. **Устройство и данные готовы** — драйвер запущен, list_variables path=<device> показывает имена переменных.
                2. **create_object** parentPath=root.platform.dashboards name=<slug> type=DASHBOARD templateId=dashboard-v1
                3. **Выбрать способ layout** (см. ниже) — НЕ собирать дашборд из 10+ вызовов add_dashboard_widget.
                4. Опционально: set_variable path=<dashboard> name=title value="..."
                5. Опционально: configure_operator_ui defaultDashboard + dashboards[] для Operator HMI
                6. finish с dashboardPath и подсказкой где открыть в UI
                
                ### Три способа задать layout
                
                | Сценарий | Инструмент | Пример |
                |----------|------------|--------|
                | Готовый эталон (SNMP, demo, virt-cluster) | set_dashboard_layout template=... | template=snmp-host-monitoring |
                | Один датчик, статический objectPath | template=demo-sensor или empty + 1–3 виджета | objectPath на DEVICE |
                | Добавить 1–2 виджета к существующему | add_dashboard_widget | после get_dashboard_layout |
                | Полностью свой экран | get_dashboard_layout template=empty → правка → set_dashboard_layout layoutJson=... | редко |
                
                Шаблоны template=: """
                + String.join(", ", DashboardService.layoutTemplateNames())
                + """
                
                Перед правкой незнакомого дашборда: get_dashboard_layout path=... (прочитать widgets[]).
                Перед новым типом виджета: get_widget_catalog type=<type>.
                
                ### Привязка данных (критично)
                
                **value / indicator / chart / sparkline / gauge / progress / status-badge:**
                - Один объект: `objectPath` = полный путь DEVICE/CUSTOM + `variableName` + `valueField` (обычно "value")
                - Несколько объектов на экране: `object-table` с `parentPath` + `selectionKey` (напр. "device");
                  потребители с **тем же** selectionKey, objectPath у них НЕ нужен
                - НЕ ставь parentPath на value/chart — parentPath только у object-table, card-grid, map, object-tree
                
                **object-table:**
                - `parentPath` — папка, чьих **детей** показывать (напр. root.platform.devices)
                - `columnsJson` — **строка** JSON-массива: [{"variable":"sysName","label":"Имя"},...]
                - `selectionKey` — при клике публикует путь строки в контекст дашборда
                - Drill-down: `rowTargetDashboard` + `rowOpenMode` navigate|modal на overview;
                  detail-дашборд: виджеты только с selectionKey=device (путь приходит из клика)
                
                **selectionKey — это имя слота, не путь.** Совпадение строки связывает таблицу и виджеты.
                Таблица "device" + виджет "order" = НЕ работают.
                
                ### Данные на экране
                
                Дашборд НЕ опрашивает SNMP/Modbus. Цепочка: драйвер poll → переменные на сервере → виджет читает API.
                variableName в layout должен **точно** совпадать с list_variables (регистр важен).
                chart/sparkline/history-table: сначала configure_variable_history historyEnabled=true на переменной.
                SNMP сетевые графики: ifInOctetsRate / ifOutOctetsRate (не сырые Counter32 ifInOctets).
                
                ### Частые ошибки (НЕ ДЕЛАТЬ)
                
                - set_variable name=widgets или name=layout вручную — только set_dashboard_layout / add_dashboard_widget
                - Выдумывать variableName без list_variables
                - 5+ подряд add_dashboard_widget для целого экрана — взять template=
                - columnsJson/fieldsJson/stylesJson как вложенный объект в tool args — это строки внутри widget JSON
                - objectPath="root.platform.devices" на value (нужен конкретный DEVICE, не папка)
                - selectionKey на виджете без object-table-источника с тем же ключом
                - chart без historyEnabled → пустой график
                - create_object DASHBOARD без templateId=dashboard-v1
                - Просить пользователя «настроить дашборд в UI» когда есть инструменты
                
                ### Минимальные примеры widget (add_dashboard_widget)
                
                Статический value:
                {"id":"temp","type":"value","title":"Температура","x":0,"y":0,"w":28,"h":14,
                 "objectPath":"root.platform.devices.demo-sensor-01","variableName":"temperature","valueField":"value","decimals":1}
                
                Таблица устройств + выбор:
                {"id":"dev-table","type":"object-table","title":"Устройства","x":0,"y":0,"w":84,"h":28,
                 "parentPath":"root.platform.devices","selectionKey":"device",
                 "columnsJson":"[{\\"variable\\":\\"sysName\\",\\"label\\":\\"Имя\\"}]"}
                
                Потребитель выбора (после клика в таблице):
                {"id":"cpu","type":"value","title":"CPU","x":0,"y":28,"w":21,"h":14,
                 "selectionKey":"device","variableName":"hrProcessorLoad","valueField":"value","unit":"%"}
                
                SCADA mimic (полный экран):
                {"id":"mimic","type":"scada-mimic","title":"Мнемосхема","x":0,"y":0,"w":84,"h":63,
                 "mimicPath":"root.platform.mimics.tank-farm","panEnabled":true}
                
                Grid: columns=84, rowHeight=8. Позиция x,y и размер w,h — в ячейках fine grid (1/4 ширины ≈ w=21, 2 строки ≈ h=14).
                НЕ используй старую сетку 12×72 — виджеты с w=4,h=2 будут крошечными. У каждого виджета уникальный id.
                """;
    }

    public static Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dashboardRoot", "root.platform.dashboards");
        summary.put("model", "dashboard-v1");
        summary.put("layoutVariable", "layout");
        summary.put("layoutShape", Map.of(
                "columns", DashboardWidgetPlacement.DEFAULT_COLUMNS,
                "rowHeight", DashboardWidgetPlacement.DEFAULT_ROW_HEIGHT,
                "widgets", "array"
        ));
        summary.put("templates", DashboardService.layoutTemplateNames());
        summary.put("tools", List.of(
                "get_widget_catalog",
                "get_dashboard_layout",
                "set_dashboard_layout",
                "add_dashboard_widget"
        ));
        summary.put("workflow", List.of(
                "list_variables on target device(s) first",
                "create_object DASHBOARD under root.platform.dashboards",
                "prefer set_dashboard_layout template= over many add_dashboard_widget",
                "match selectionKey between object-table and consumers",
                "configure_variable_history before chart widgets"
        ));
        summary.put("antiPatterns", List.of(
                "set_variable name=widgets or manual layout variable",
                "invent variableName without list_variables",
                "parentPath on value/chart widgets",
                "mismatched selectionKey strings",
                "many add_dashboard_widget calls for full screen"
        ));
        return summary;
    }
}
