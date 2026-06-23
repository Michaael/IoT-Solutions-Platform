package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exhaustive widget property reference for the tree-first agent.
 * Source of truth: {@code apps/web-console/src/types/dashboard.ts}.
 */
public final class AgentWidgetPropertiesGuide {

    private AgentWidgetPropertiesGuide() {
    }

    public static String referenceText() {
        return """
                ## Свойства виджетов — полный справочник
                
                Источник типов: dashboard.ts. Перед созданием виджета: get_widget_catalog type=<type>.
                Имена переменных — только из list_variables / describe_variables на целевом объекте.
                
                ### Корень layout (переменная DASHBOARD.layout)
                
                | Поле | Тип | По умолчанию | Описание |
                |------|-----|--------------|----------|
                | columns | int | 12 | Ширина сетки в колонках |
                | rowHeight | int | 72 | Высота одной строки сетки (px) |
                | theme | string | — | Визуальная тема, напр. "btop" (компактный SNMP-стиль) |
                | widgets | array | [] | Массив объектов виджетов |
                
                ### Общие поля каждого виджета (DashboardWidgetBase)
                
                | Поле | Обяз. | Описание |
                |------|-------|----------|
                | id | да | Уникальный id внутри layout; add_dashboard_widget заменяет виджет с тем же id |
                | type | да | Один из 42 типов (value, chart, object-table, …) |
                | title | да | Заголовок карточки |
                | x, y, w, h | да | Позиция и размер на сетке columns×строки |
                | objectPath | * | Статический путь DEVICE/CUSTOM — для одного объекта |
                | selectionKey | * | Имя слота выбора; путь берётся из selection[key] после клика в таблице/дереве |
                | contextPathKey | — | Путь из session.params[key], если selectionKey пуст |
                | paramKey | — | Значение из session.params (label, breadcrumbs) |
                | modelHintPath | — | Только для редактора: образец объекта для dropdown переменных |
                | variableName | ** | Имя переменной на objectPath/выбранном объекте |
                | valueField | — | Поле строки DataRecord; по умолчанию "value" |
                | stylesJson | — | JSON-**строка** стилей элементов (card, title, value, table, chart…) |
                
                * objectPath ИЛИ selectionKey (для object-variable виджетов). Оба можно: при непустом selection приоритет у selection.
                ** обязателен для виджетов, читающих переменную (value, chart, gauge…).
                
                ### valueField — какое поле читать
                
                | valueField | Когда |
                |------------|-------|
                | value | Число, строка, bool — основное значение (температура, CPU%, sysName) |
                | raw | Сырое значение (sysUpTime SNMP ticks) |
                | online | Поле online у переменной status (индикатор связи) |
                | unit | Единица измерения из записи (редко; чаще unitField на виджете) |
                
                Для status-badge на driverStatus: valueField="value". Для online-индикатора: variableName=status, valueField=online.
                
                ### JSON-поля внутри виджета (всегда STRING, не вложенный объект!)
                
                | Поле | Формат строки |
                |------|----------------|
                | columnsJson | [{"variable":"sysName","label":"Имя"},{"variable":"status","label":"On","field":"online"}] |
                | variablesJson | ["temperature","alarmActive"] — имена переменных |
                | fieldsJson | function-form/input-form: [{"name":"x","label":"X","type":"number"}] |
                | childrenJson | [виджет, виджет, …] — вложенные виджеты panel/composite |
                | tabsJson | [{"id":"t1","label":"Tab","children":[...]}] |
                | slidesJson | [{"title":"…","children":[...]}] |
                | stepsJson | [{"id":"s1","label":"Шаг 1","children":[...]}] |
                | itemsJson | nav-menu: [{"label":"Overview","dashboardPath":"root…"}] |
                | eventNamesJson | ["thresholdExceeded","virtClusterError"] |
                | contextSelectionJson | {"device":"root.platform.devices.foo"} |
                | contextParamsJson | {"clusterPath":"root…"} |
                | rowParamsJson | произвольные params при клике строки |
                | cardParamsJson | params при клике карточки |
                | inputJson | function: статические input rows [{...}] |
                | htmlJson | HTML-фрагмент |
                | textJson | JSON для динамического текста label |
                | stylesJson | {"value":{"fontSize":"0.82rem"},"meta":{"display":"none"}} |
                
                В tool arguments (add_dashboard_widget) columnsJson передаётся как строка внутри widget-объекта.
                
                ### object-variable виджеты (читают переменные объекта)
                
                **value** [req: variableName]
                unit, unitField (взять unit из переменной), decimals (точность).
                Пример: temperature + valueField=value + decimals=1 + unit=°C.
                
                **toggle** [req: variableName] — запись bool; trueLabel, falseLabel.
                
                **indicator** [req: variableName] — лампа bool; trueLabel, falseLabel, trueColor, falseColor.
                
                **chart** [req: variableName] — historian; ОБЯЗАТЕЛЬНО historyEnabled=true на переменной.
                chartStyle: line|area; chartType: line|area|bar|candlestick|bubble|radar|range;
                historyRange: live|1h|6h|24h|7d|all (default live); maxPoints (default ~120);
                color, decimals, unit, unitField.
                
                **sparkline** — как chart, компактный; historyRange, maxPoints, color, decimals.
                
                **gauge** [req: variableName] — радиальная шкала.
                minValue/maxValue (числа) ИЛИ minVariable/maxVariable (другие переменные того же объекта); unit, decimals.
                
                **linear-gauge**, **liquid-gauge** — как gauge, горизонтальная / «жидкость».
                
                **progress** [req: currentVariable, maxVariable] — два имени переменных на одном объекте; unit, decimals.
                НЕ variableName — используй currentVariable + maxVariable.
                
                **status-badge** — variableName (default status), valueField (default value).
                
                **pie-chart** [req: variableName] — переменная типа RECORD_LIST; labelField, decimals.
                
                **history-table** [req: variableName] — таблица за 5 мин + среднее; historyEnabled обязателен; decimals.
                
                **timer** — mode: countdown|elapsed; durationSeconds; variableName (для elapsed).
                
                **spreadsheet** [req: variableName] — RECORD_LIST таблица; editable: true|false.
                
                **gantt-chart** [req: variableName] — RECORD_LIST; labelField, startField, endField.
                
                **network-graph** — nodesVariable, edgesVariable (RECORD_LIST), labelField.
                
                **svg-widget** [req: svgUrl] — clickAction: function|toggle; functionName; toggleVariable.
                
                ### object-only (функции и формы на объекте)
                
                **function** [req: functionName] — objectPath|selectionKey; buttonLabel, confirmMessage, inputJson.
                variableName на виджете опционален (контекст объекта для invoke).
                
                **function-form** [req: functionName] — fieldsJson массив полей:
                name, label, type: text|number|select, optionsFrom (parentPath для select), staticOptions, defaultValue.
                
                **variable-editor** — variablesJson: ["var1","var2"] или пусто = все переменные объекта.
                
                **input-form** — fieldsJson: name, label, type: text|number|textarea|select|slider|checkbox|radio|datetime|time;
                variableName (куда писать), optionsFrom, min, max, step, defaultValue; buttonLabel.
                
                ### parent-catalog (список детей папки)
                
                **object-table** [req: parentPath]
                columnsJson (variable + label + optional field); selectionKey (публикатор выбора);
                rowTargetDashboard, rowOpenMode: navigate|modal, rowSelectionKey, rowParamsJson.
                parentPath = папка (root.platform.devices), НЕ конкретное устройство.
                
                **card-grid** [req: parentPath]
                variablesJson — какие переменные на карточке; cardTargetDashboard, cardOpenMode, cardSelectionKey, cardParamsJson.
                
                **map** [req: parentPath]
                latVariable (переменная с geo на child), latField/lonField (default lat/lon);
                labelVariable, zoom, centerLat, centerLon, tileUrl, mapStyleUrl;
                rowTargetDashboard, rowOpenMode, rowSelectionKey, rowParamsJson — как у таблицы.
                
                **object-tree** [req: parentPath] — selectionKey, maxDepth.
                
                ### navigation & external
                
                **dashboard-link** [req: targetDashboardPath]
                openMode: navigate|modal; buttonLabel, modalTitle, confirmMessage;
                contextSelectionJson, contextParamsJson — передать в session при открытии.
                
                **sub-dashboard** — targetDashboardPath или targetDashboardPathKey (из params); inheritContext: true передаёт selection.
                
                **report** [req: reportPath] — путь REPORT в дереве; emptyMessage.
                
                **event-feed** — objectPathPrefix, eventNamesJson, payloadFilterExpr, maxItems.
                
                **work-queue** — operatorId, maxItems.
                
                ### session / static
                
                **label** — text или textJson; paramKey для подстановки из session.
                **breadcrumbs** — pathKey (ключ в selection/params), separator.
                **context-list** — отладка: показывает selection и params (без полей).
                **image** — imageUrl, alt.
                **html-snippet** — htmlJson.
                
                ### composition (вложенные виджеты)
                
                **panel** — childrenJson, collapsible, variant: simple.
                **tab-panel** — tabsJson: [{id, label, children: [widgets]}].
                **drawer-panel** — childrenJson, drawerLabel.
                **carousel** — slidesJson, autoplayMs.
                **steps-panel** — stepsJson, activeStepKey.
                **composite-widget** — childrenJson (плоский список вложенных).
                **nav-menu** — itemsJson: [{label, dashboardPath}].
                
                ### Типичные ошибки свойств
                
                - progress: писать variableName вместо currentVariable + maxVariable
                - object-table: objectPath вместо parentPath
                - columnsJson как массив в JSON tool args — должна быть escaped string
                - chart без historyEnabled → пустой график
                - gauge: только minValue без maxValue
                - function-form fieldsJson: type "string" — неверно, нужно "text"
                - pie-chart/gantt/spreadsheet: variableName должна быть RECORD_LIST
                - status online: забыть valueField=online при variableName=status
                - unit и unitField: unit — статическая подпись; unitField — читать поле unit из переменной
                - Дублировать id виджетов в одном layout
                - w>12 или x+w>12 — виджет обрежется сеткой
                
                ### Размеры сетки (рекомендации)
                
                - value/indicator/toggle: w=2–4, h=2
                - chart: w=6–12, h=4–6
                - object-table: w=12, h=4–6
                - function-form: w=4–6, h=4
                - snmp btop theme: rowHeight=52, компактные h=1–2 для метрик
                """;
    }

    public static Map<String, Object> fieldSemantics() {
        Map<String, Object> semantics = new LinkedHashMap<>();
        semantics.put("objectPath", "Static DEVICE/CUSTOM path; not a folder");
        semantics.put("parentPath", "Folder whose children are listed (object-table, card-grid, map, object-tree)");
        semantics.put("selectionKey", "Slot name in dashboard selection; must match publisher widget");
        semantics.put("valueField", "DataRecord field: value (default), raw, online, unit");
        semantics.put("unitField", "Read unit from variable record field (e.g. temperature.unit)");
        semantics.put("variableName", "Exact variable name from list_variables");
        semantics.put("modelHintPath", "Editor-only sample path; does not affect runtime");
        semantics.put("historyRange", "chart/sparkline: live|1h|6h|24h|7d|all");
        semantics.put("openMode", "navigate|modal for dashboard-link and row/card clicks");
        semantics.put("jsonEmbeddedFields", jsonEmbeddedFields());
        return semantics;
    }

    public static List<String> jsonEmbeddedFields() {
        return List.of(
                "columnsJson", "variablesJson", "fieldsJson", "childrenJson", "tabsJson",
                "slidesJson", "stepsJson", "itemsJson", "eventNamesJson", "contextSelectionJson",
                "contextParamsJson", "rowParamsJson", "cardParamsJson", "inputJson", "htmlJson",
                "textJson", "stylesJson", "demoPreviewJson"
        );
    }

    public static Map<String, Object> propertiesForType(String type) {
        if (type == null || type.isBlank()) {
            return Map.of();
        }
        String key = type.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> spec = TYPE_SPECS.get(key);
        if (spec == null) {
            return Map.of("error", "Unknown widget type: " + type);
        }
        return spec;
    }

    public static Map<String, Map<String, Object>> allTypeSpecs() {
        return TYPE_SPECS;
    }

    private static Map<String, Object> spec(
            String binding,
            List<String> required,
            List<Map<String, String>> fields,
            String notes,
            String example
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("binding", binding);
        map.put("required", required);
        map.put("commonOptional", COMMON_GRID_FIELDS);
        map.put("fields", fields);
        if (notes != null && !notes.isBlank()) {
            map.put("notes", notes);
        }
        if (example != null && !example.isBlank()) {
            map.put("example", example);
        }
        return map;
    }

    private static Map<String, String> f(String name, String type, boolean required, String description) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("type", type);
        row.put("required", String.valueOf(required));
        row.put("description", description);
        return row;
    }

    private static List<Map<String, String>> basePathFields() {
        return List.of(
                f("objectPath", "string", false, "Static object path"),
                f("selectionKey", "string", false, "Selection slot name"),
                f("contextPathKey", "string", false, "Path from session.params"),
                f("modelHintPath", "string", false, "Editor hint only")
        );
    }

    private static List<Map<String, String>> varFields(boolean variableRequired) {
        List<Map<String, String>> fields = new ArrayList<>(basePathFields());
        fields.add(f("variableName", "string", variableRequired, "Variable on resolved object path"));
        fields.add(f("valueField", "string", false, "DataRecord field; default value"));
        fields.add(f("stylesJson", "string", false, "Per-element CSS JSON string"));
        return fields;
    }

    private static final List<String> COMMON_GRID_FIELDS = List.of(
            "id", "type", "title", "x", "y", "w", "h"
    );

    private static final Map<String, Map<String, Object>> TYPE_SPECS = buildTypeSpecs();

    private static Map<String, Map<String, Object>> buildTypeSpecs() {
        Map<String, Map<String, Object>> specs = new LinkedHashMap<>();

        List<Map<String, String>> valueFields = new ArrayList<>(varFields(true));
        valueFields.add(f("unit", "string", false, "Static unit label"));
        valueFields.add(f("unitField", "string", false, "Field name for unit from variable"));
        valueFields.add(f("decimals", "integer", false, "Decimal places"));
        specs.put("value", spec("object-variable", List.of("variableName"), valueFields,
                "Single metric display", "{\"type\":\"value\",\"variableName\":\"temperature\",\"valueField\":\"value\",\"decimals\":1}"));

        List<Map<String, String>> chartFields = new ArrayList<>(varFields(true));
        chartFields.add(f("chartStyle", "enum", false, "line|area"));
        chartFields.add(f("chartType", "enum", false, "line|area|bar|candlestick|bubble|radar|range"));
        chartFields.add(f("historyRange", "enum", false, "live|1h|6h|24h|7d|all"));
        chartFields.add(f("maxPoints", "integer", false, "Points in live mode"));
        chartFields.add(f("color", "string", false, "Hex color"));
        chartFields.add(f("decimals", "integer", false, "Decimal places"));
        chartFields.add(f("unit", "string", false, "Static unit"));
        chartFields.add(f("unitField", "string", false, "Unit from variable"));
        specs.put("chart", spec("object-variable", List.of("variableName"), chartFields,
                "Requires configure_variable_history historyEnabled=true", null));
        specs.put("sparkline", spec("object-variable", List.of("variableName"), chartFields,
                "Compact chart; historyEnabled required", null));

        List<Map<String, String>> indicatorFields = new ArrayList<>(varFields(true));
        indicatorFields.add(f("trueLabel", "string", false, "Label when true"));
        indicatorFields.add(f("falseLabel", "string", false, "Label when false"));
        indicatorFields.add(f("trueColor", "string", false, "Hex color when true"));
        indicatorFields.add(f("falseColor", "string", false, "Hex color when false"));
        specs.put("indicator", spec("object-variable", List.of("variableName"), indicatorFields, null, null));
        specs.put("toggle", spec("object-variable", List.of("variableName"), indicatorFields,
                "Writable boolean", null));

        List<Map<String, String>> gaugeFields = new ArrayList<>(varFields(true));
        gaugeFields.add(f("minValue", "number", false, "Static min"));
        gaugeFields.add(f("maxValue", "number", false, "Static max"));
        gaugeFields.add(f("minVariable", "string", false, "Variable for min"));
        gaugeFields.add(f("maxVariable", "string", false, "Variable for max"));
        gaugeFields.add(f("unit", "string", false, "Unit label"));
        gaugeFields.add(f("decimals", "integer", false, "Decimal places"));
        specs.put("gauge", spec("object-variable", List.of("variableName"), gaugeFields, null, null));
        specs.put("linear-gauge", spec("object-variable", List.of("variableName"), gaugeFields, null, null));
        specs.put("liquid-gauge", spec("object-variable", List.of("variableName"), gaugeFields, null, null));

        List<Map<String, String>> progressFields = new ArrayList<>(basePathFields());
        progressFields.add(f("currentVariable", "string", true, "Numerator variable name"));
        progressFields.add(f("maxVariable", "string", true, "Denominator variable name"));
        progressFields.add(f("unit", "string", false, "Unit label"));
        progressFields.add(f("decimals", "integer", false, "Decimal places"));
        specs.put("progress", spec("object-variable", List.of("currentVariable", "maxVariable"), progressFields,
                "Uses currentVariable+maxVariable, NOT variableName", null));

        specs.put("status-badge", spec("object-variable", List.of(), varFields(false),
                "Default variableName=status", null));

        List<Map<String, String>> pieFields = new ArrayList<>(varFields(true));
        pieFields.add(f("labelField", "string", false, "Label column in RECORD_LIST rows"));
        pieFields.add(f("decimals", "integer", false, "Decimal places"));
        specs.put("pie-chart", spec("object-variable", List.of("variableName"), pieFields,
                "variableName must be RECORD_LIST", null));

        List<Map<String, String>> histFields = new ArrayList<>(varFields(true));
        histFields.add(f("decimals", "integer", false, "Decimal places"));
        specs.put("history-table", spec("object-variable", List.of("variableName"), histFields,
                "Last 5 min; historyEnabled required", null));

        List<Map<String, String>> timerFields = new ArrayList<>(basePathFields());
        timerFields.add(f("mode", "enum", false, "countdown|elapsed"));
        timerFields.add(f("durationSeconds", "integer", false, "For countdown"));
        timerFields.add(f("variableName", "string", false, "For elapsed mode"));
        specs.put("timer", spec("object-variable", List.of(), timerFields, null, null));

        List<Map<String, String>> sheetFields = new ArrayList<>(varFields(true));
        sheetFields.add(f("editable", "boolean", false, "Allow inline edit"));
        specs.put("spreadsheet", spec("object-variable", List.of("variableName"), sheetFields,
                "RECORD_LIST variable", null));

        List<Map<String, String>> ganttFields = new ArrayList<>(varFields(true));
        ganttFields.add(f("labelField", "string", false, "Row label field"));
        ganttFields.add(f("startField", "string", false, "Start time field"));
        ganttFields.add(f("endField", "string", false, "End time field"));
        specs.put("gantt-chart", spec("object-variable", List.of("variableName"), ganttFields,
                "RECORD_LIST variable", null));

        List<Map<String, String>> netFields = new ArrayList<>(basePathFields());
        netFields.add(f("nodesVariable", "string", false, "RECORD_LIST nodes"));
        netFields.add(f("edgesVariable", "string", false, "RECORD_LIST edges"));
        netFields.add(f("labelField", "string", false, "Node label field"));
        specs.put("network-graph", spec("object-variable", List.of(), netFields, null, null));

        List<Map<String, String>> svgFields = new ArrayList<>(basePathFields());
        svgFields.add(f("svgUrl", "string", true, "SVG asset URL"));
        svgFields.add(f("clickAction", "enum", false, "function|toggle"));
        svgFields.add(f("functionName", "string", false, "When clickAction=function"));
        svgFields.add(f("toggleVariable", "string", false, "When clickAction=toggle"));
        svgFields.add(f("confirmMessage", "string", false, "Confirm dialog"));
        specs.put("svg-widget", spec("object-variable", List.of("svgUrl"), svgFields, null, null));

        List<Map<String, String>> fnFields = new ArrayList<>(basePathFields());
        fnFields.add(f("functionName", "string", true, "Tree/BFF function name"));
        fnFields.add(f("buttonLabel", "string", false, "Button text"));
        fnFields.add(f("confirmMessage", "string", false, "Confirm before invoke"));
        fnFields.add(f("inputJson", "string", false, "Static input rows JSON"));
        specs.put("function", spec("object-only", List.of("functionName"), fnFields, null, null));

        List<Map<String, String>> formFields = new ArrayList<>(basePathFields());
        formFields.add(f("functionName", "string", true, "Function to invoke"));
        formFields.add(f("buttonLabel", "string", false, "Submit button"));
        formFields.add(f("confirmMessage", "string", false, "Confirm dialog"));
        formFields.add(f("fieldsJson", "string", false, "Form fields array JSON string"));
        specs.put("function-form", spec("object-only", List.of("functionName"), formFields,
                "fieldsJson: [{name,label,type:text|number|select,optionsFrom,defaultValue}]", null));

        List<Map<String, String>> varEdFields = new ArrayList<>(basePathFields());
        varEdFields.add(f("variablesJson", "string", false, "Array of variable names; empty=all"));
        specs.put("variable-editor", spec("object-only", List.of(), varEdFields, null, null));

        List<Map<String, String>> inputFormFields = new ArrayList<>(basePathFields());
        inputFormFields.add(f("fieldsJson", "string", false, "Input fields JSON"));
        inputFormFields.add(f("buttonLabel", "string", false, "Submit button"));
        specs.put("input-form", spec("object-only", List.of(), inputFormFields,
                "fieldsJson types: text|number|textarea|select|slider|checkbox|radio|datetime|time", null));

        List<Map<String, String>> tableFields = new ArrayList<>();
        tableFields.add(f("parentPath", "string", true, "Folder path for child objects"));
        tableFields.add(f("columnsJson", "string", false, "Columns array JSON string"));
        tableFields.add(f("selectionKey", "string", false, "Publishes row path on click"));
        tableFields.add(f("rowTargetDashboard", "string", false, "Dashboard path on row click"));
        tableFields.add(f("rowOpenMode", "enum", false, "navigate|modal"));
        tableFields.add(f("rowSelectionKey", "string", false, "Selection key in target dashboard"));
        tableFields.add(f("rowParamsJson", "string", false, "Params JSON on row click"));
        tableFields.add(f("modelHintPath", "string", false, "Editor hint"));
        tableFields.add(f("stylesJson", "string", false, "Table styles"));
        specs.put("object-table", spec("parent-catalog", List.of("parentPath"), tableFields,
                "parentPath is folder; columns use variable names on each child", null));

        List<Map<String, String>> cardFields = new ArrayList<>();
        cardFields.add(f("parentPath", "string", true, "Folder path"));
        cardFields.add(f("variablesJson", "string", false, "Variable names on cards"));
        cardFields.add(f("cardTargetDashboard", "string", false, "Dashboard on card click"));
        cardFields.add(f("cardOpenMode", "enum", false, "navigate|modal"));
        cardFields.add(f("cardSelectionKey", "string", false, "Selection key on click"));
        cardFields.add(f("cardParamsJson", "string", false, "Params on click"));
        specs.put("card-grid", spec("parent-catalog", List.of("parentPath"), cardFields, null, null));

        List<Map<String, String>> mapFields = new ArrayList<>();
        mapFields.add(f("parentPath", "string", true, "Folder with geo objects"));
        mapFields.add(f("latVariable", "string", false, "Variable with coordinates"));
        mapFields.add(f("latField", "string", false, "Lat field in record"));
        mapFields.add(f("lonField", "string", false, "Lon field in record"));
        mapFields.add(f("labelVariable", "string", false, "Marker label variable"));
        mapFields.add(f("zoom", "number", false, "Map zoom"));
        mapFields.add(f("centerLat", "number", false, "Center latitude"));
        mapFields.add(f("centerLon", "number", false, "Center longitude"));
        mapFields.add(f("tileUrl", "string", false, "Raster tile URL"));
        mapFields.add(f("mapStyleUrl", "string", false, "MapLibre style URL"));
        mapFields.add(f("rowTargetDashboard", "string", false, "Dashboard on marker click"));
        mapFields.add(f("rowOpenMode", "enum", false, "navigate|modal"));
        mapFields.add(f("rowSelectionKey", "string", false, "Selection key"));
        mapFields.add(f("rowParamsJson", "string", false, "Params on click"));
        specs.put("map", spec("parent-catalog", List.of("parentPath"), mapFields, null, null));

        List<Map<String, String>> treeFields = new ArrayList<>();
        treeFields.add(f("parentPath", "string", true, "Subtree root"));
        treeFields.add(f("selectionKey", "string", false, "Publishes selection on click"));
        treeFields.add(f("maxDepth", "integer", false, "Max tree depth"));
        specs.put("object-tree", spec("parent-catalog", List.of("parentPath"), treeFields, null, null));

        List<Map<String, String>> linkFields = new ArrayList<>();
        linkFields.add(f("targetDashboardPath", "string", true, "Dashboard to open"));
        linkFields.add(f("openMode", "enum", false, "navigate|modal"));
        linkFields.add(f("buttonLabel", "string", false, "Button text"));
        linkFields.add(f("modalTitle", "string", false, "Modal title"));
        linkFields.add(f("confirmMessage", "string", false, "Confirm dialog"));
        linkFields.add(f("contextSelectionJson", "string", false, "Initial selection JSON"));
        linkFields.add(f("contextParamsJson", "string", false, "Initial params JSON"));
        specs.put("dashboard-link", spec("external", List.of("targetDashboardPath"), linkFields, null, null));

        List<Map<String, String>> subFields = new ArrayList<>();
        subFields.add(f("targetDashboardPath", "string", false, "Embedded dashboard"));
        subFields.add(f("targetDashboardPathKey", "string", false, "Path from session.params"));
        subFields.add(f("inheritContext", "boolean", false, "Pass selection to child"));
        specs.put("sub-dashboard", spec("external", List.of(), subFields, null, null));

        List<Map<String, String>> reportFields = new ArrayList<>();
        reportFields.add(f("reportPath", "string", true, "REPORT object path"));
        reportFields.add(f("parametersJson", "string", false, "Static run parameters JSON"));
        reportFields.add(f("contextParamsJson", "string", false, "Map reportParam to session.params key"));
        reportFields.add(f("emptyMessage", "string", false, "Text when no rows"));
        reportFields.add(f("showCsv", "boolean", false, "CSV export button (default true)"));
        reportFields.add(f("showPdf", "boolean", false, "PDF when YARG template"));
        reportFields.add(f("showXlsx", "boolean", false, "XLSX when YARG template"));
        reportFields.add(f("showHtml", "boolean", false, "HTML when YARG template"));
        reportFields.add(f("showTruncatedWarning", "boolean", false, "Banner when rows truncated"));
        specs.put("report", spec("external", List.of("reportPath"), reportFields, null, null));

        List<Map<String, String>> eventFields = new ArrayList<>();
        eventFields.add(f("objectPathPrefix", "string", false, "Filter events by path prefix"));
        eventFields.add(f("eventNamesJson", "string", false, "Event names array JSON"));
        eventFields.add(f("payloadFilterExpr", "string", false, "Client filter expression"));
        eventFields.add(f("maxItems", "integer", false, "Max events shown"));
        specs.put("event-feed", spec("external", List.of(), eventFields, null, null));

        List<Map<String, String>> wqFields = new ArrayList<>();
        wqFields.add(f("operatorId", "string", false, "Operator filter"));
        wqFields.add(f("maxItems", "integer", false, "Max tasks"));
        specs.put("work-queue", spec("external", List.of(), wqFields, null, null));

        List<Map<String, String>> labelFields = new ArrayList<>();
        labelFields.add(f("text", "string", false, "Static text"));
        labelFields.add(f("textJson", "string", false, "Dynamic text JSON"));
        labelFields.add(f("paramKey", "string", false, "Value from session.params"));
        specs.put("label", spec("session", List.of(), labelFields, null, null));

        List<Map<String, String>> crumbFields = new ArrayList<>();
        crumbFields.add(f("pathKey", "string", false, "Key in selection/params"));
        crumbFields.add(f("separator", "string", false, "Path separator"));
        specs.put("breadcrumbs", spec("session", List.of(), crumbFields, null, null));

        specs.put("context-list", spec("session", List.of(), List.of(),
                "Debug widget: shows session selection and params", null));

        List<Map<String, String>> imageFields = new ArrayList<>();
        imageFields.add(f("imageUrl", "string", false, "Image URL"));
        imageFields.add(f("alt", "string", false, "Alt text"));
        specs.put("image", spec("static", List.of(), imageFields, null, null));

        List<Map<String, String>> htmlFields = new ArrayList<>();
        htmlFields.add(f("htmlJson", "string", false, "HTML content"));
        specs.put("html-snippet", spec("static", List.of(), htmlFields, null, null));

        List<Map<String, String>> compFields = new ArrayList<>();
        compFields.add(f("childrenJson", "string", false, "Nested widgets array JSON"));
        specs.put("composite-widget", spec("composition", List.of(), compFields, null, null));

        List<Map<String, String>> panelFields = new ArrayList<>(compFields);
        panelFields.add(f("collapsible", "boolean", false, "Collapsible panel"));
        panelFields.add(f("variant", "enum", false, "simple"));
        specs.put("panel", spec("composition", List.of(), panelFields, null, null));

        List<Map<String, String>> tabFields = new ArrayList<>();
        tabFields.add(f("tabsJson", "string", false, "Tabs with children JSON"));
        specs.put("tab-panel", spec("composition", List.of(), tabFields, null, null));

        List<Map<String, String>> drawerFields = new ArrayList<>(compFields);
        drawerFields.add(f("drawerLabel", "string", false, "Drawer title"));
        specs.put("drawer-panel", spec("composition", List.of(), drawerFields, null, null));

        List<Map<String, String>> carouselFields = new ArrayList<>();
        carouselFields.add(f("slidesJson", "string", false, "Slides with children"));
        carouselFields.add(f("autoplayMs", "integer", false, "Autoplay interval"));
        specs.put("carousel", spec("composition", List.of(), carouselFields, null, null));

        List<Map<String, String>> stepsFields = new ArrayList<>();
        stepsFields.add(f("stepsJson", "string", false, "Wizard steps JSON"));
        stepsFields.add(f("activeStepKey", "string", false, "Current step id"));
        specs.put("steps-panel", spec("composition", List.of(), stepsFields, null, null));

        List<Map<String, String>> navFields = new ArrayList<>();
        navFields.add(f("itemsJson", "string", false, "[{label,dashboardPath}]"));
        specs.put("nav-menu", spec("composition", List.of(), navFields, null, null));

        return Map.copyOf(specs);
    }
}
