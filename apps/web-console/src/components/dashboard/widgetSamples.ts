import type { DashboardWidget, WidgetType } from "../../types/dashboard";

/** Well-known platform paths used in sample widgets (see PlatformBootstrap / DashboardLayouts). */
export const WIDGET_SAMPLE_PATHS = {
  device: "root.platform.devices.demo-sensor-01",
  devices: "root.platform.devices",
  labDevice: "root.platform.devices.lab-userA-01",
  demoDashboard: "root.platform.dashboards.demo-sensor",
  snmpDashboard: "root.platform.dashboards.snmp-host-monitoring",
  report: "root.platform.reports.ready-items",
} as const;

const DEVICE = WIDGET_SAMPLE_PATHS.device;
const DEVICES = WIDGET_SAMPLE_PATHS.devices;
const LAB = WIDGET_SAMPLE_PATHS.labDevice;

function j(value: unknown): string {
  return JSON.stringify(value);
}

function sampleId(type: WidgetType, index: number): string {
  return `sample-${type}-${Date.now()}-${index}`;
}

function sampleBase(index: number) {
  return {
    id: "",
    title: "Виджет",
    x: 0,
    y: index * 2,
    w: 3,
    h: 2,
    objectPath: DEVICE,
    variableName: "",
    valueField: "value",
    modelHintPath: DEVICE,
    sampleTemplate: true,
  };
}

function miniValue(
  id: string,
  title: string,
  patch: Partial<DashboardWidget> = {}
): DashboardWidget {
  return {
    id,
    type: "value",
    title,
    x: 0,
    y: 0,
    w: 3,
    h: 2,
    objectPath: DEVICE,
    variableName: "temperature",
    valueField: "value",
    unitField: "unit",
    decimals: 1,
    modelHintPath: DEVICE,
    sampleTemplate: true,
    ...patch,
  } as DashboardWidget;
}

function miniIndicator(id: string, title: string): DashboardWidget {
  return {
    id,
    type: "indicator",
    title,
    x: 0,
    y: 0,
    w: 3,
    h: 2,
    objectPath: DEVICE,
    variableName: "alarmActive",
    valueField: "value",
    trueLabel: "Активна",
    falseLabel: "Норма",
    trueColor: "#f85149",
    falseColor: "#3fb950",
    modelHintPath: DEVICE,
    sampleTemplate: true,
  };
}

function miniFunction(id: string, title: string): DashboardWidget {
  return {
    id,
    type: "function",
    title,
    x: 0,
    y: 0,
    w: 3,
    h: 2,
    objectPath: DEVICE,
    variableName: "alarmActive",
    functionName: "acknowledgeAlarm",
    buttonLabel: "Подтвердить",
    confirmMessage: "Сбросить тревогу?",
    modelHintPath: DEVICE,
    sampleTemplate: true,
  };
}

/** Build a new widget pre-filled with a characteristic example configuration. */
export function buildSampleWidget(type: WidgetType, index: number): DashboardWidget {
  const base = sampleBase(index);
  base.id = sampleId(type, index);

  switch (type) {
    case "value":
      return {
        ...base,
        type: "value",
        title: "Температура",
        variableName: "temperature",
        unitField: "unit",
        decimals: 1,
      };
    case "indicator":
      return {
        ...base,
        type: "indicator",
        title: "Тревога",
        variableName: "alarmActive",
        trueLabel: "Активна",
        falseLabel: "Норма",
        trueColor: "#f85149",
        falseColor: "#3fb950",
      };
    case "toggle":
      return {
        ...base,
        type: "toggle",
        title: "Режим",
        variableName: "alarmActive",
        trueLabel: "Вкл",
        falseLabel: "Выкл",
      };
    case "chart":
      return {
        ...base,
        type: "chart",
        title: "Тренд температуры",
        w: 6,
        h: 4,
        variableName: "temperature",
        unitField: "unit",
        chartStyle: "area",
        chartType: "line",
        maxPoints: 120,
        color: "#2f81f7",
        decimals: 1,
        demoPreviewJson: j([
          { t: 1, v: 21.2 },
          { t: 2, v: 21.8 },
          { t: 3, v: 22.1 },
          { t: 4, v: 22.4 },
          { t: 5, v: 23.0 },
          { t: 6, v: 22.7 },
          { t: 7, v: 22.3 },
          { t: 8, v: 21.9 },
        ]),
      };
    case "sparkline":
      return {
        ...base,
        type: "sparkline",
        title: "Спарклайн",
        w: 4,
        h: 2,
        variableName: "temperature",
        maxPoints: 40,
        color: "#3fb950",
        decimals: 1,
        demoPreviewJson: j([20.1, 20.4, 20.8, 21.2, 21.0, 21.5, 21.3]),
      };
    case "function":
      return {
        ...base,
        type: "function",
        title: "Сброс тревоги",
        variableName: "alarmActive",
        functionName: "acknowledgeAlarm",
        buttonLabel: "Подтвердить тревогу",
        confirmMessage: "Подтвердить сброс активной тревоги?",
      };
    case "function-form":
      return {
        ...base,
        type: "function-form",
        title: "Добавить строку",
        w: 4,
        h: 4,
        objectPath: LAB,
        modelHintPath: LAB,
        functionName: "appendTableRow",
        buttonLabel: "Добавить",
        fieldsJson: j([
          { name: "int", label: "Число", type: "number", defaultValue: "1" },
          { name: "string", label: "Текст", type: "text", defaultValue: "пример" },
        ]),
      };
    case "progress":
      return {
        ...base,
        type: "progress",
        title: "Заполнение",
        w: 6,
        h: 2,
        objectPath: DEVICE,
        currentVariable: "temperature",
        maxVariable: "threshold",
        unit: "°C",
        decimals: 1,
        modelHintPath: DEVICE,
      };
    case "object-table":
      return {
        ...base,
        type: "object-table",
        title: "Устройства",
        w: 8,
        h: 5,
        objectPath: "",
        parentPath: DEVICES,
        modelHintPath: DEVICES,
        columnsJson: j([
          { variable: "temperature", label: "Температура" },
          { variable: "status", label: "Статус" },
        ]),
        selectionKey: "device",
        rowSelectionKey: "device",
        rowTargetDashboard: WIDGET_SAMPLE_PATHS.demoDashboard,
        rowOpenMode: "modal",
        rowParamsJson: j({ source: "object-table" }),
      };
    case "event-feed":
      return {
        ...base,
        type: "event-feed",
        title: "Лента событий",
        w: 8,
        h: 4,
        objectPath: "",
        objectPathPrefix: DEVICE,
        eventNamesJson: j(["thresholdExceeded"]),
        maxItems: 15,
        demoPreviewJson: j([
          {
            id: "demo-event-1",
            eventName: "thresholdExceeded",
            level: "WARNING",
            objectPath: DEVICE,
            timestamp: new Date().toISOString(),
            payload: { rows: [{ value: 42.5, unit: "°C", threshold: 40 }] },
          },
          {
            id: "demo-event-2",
            eventName: "thresholdExceeded",
            level: "INFO",
            objectPath: DEVICE,
            timestamp: new Date(Date.now() - 120_000).toISOString(),
            payload: { rows: [{ value: 38.1, unit: "°C" }] },
          },
        ]),
      };
    case "work-queue":
      return {
        ...base,
        type: "work-queue",
        title: "Очередь задач",
        w: 4,
        h: 5,
        objectPath: "",
        operatorId: "operator",
        maxItems: 10,
        demoPreviewJson: j([
          {
            id: "demo-task-1",
            title: "Проверить датчик",
            status: "OPEN",
            instructions: "Сверить показания температуры с эталоном",
            workflowPath: "root.platform.workflows.example",
          },
          {
            id: "demo-task-2",
            title: "Подтвердить тревогу",
            status: "CLAIMED",
            instructions: "Вызвать acknowledgeAlarm на устройстве",
            workflowPath: "root.platform.workflows.example",
          },
        ]),
      };
    case "status-badge":
      return {
        ...base,
        type: "status-badge",
        title: "Связь",
        variableName: "status",
        valueField: "online",
      };
    case "gauge":
      return {
        ...base,
        type: "gauge",
        title: "Уровень",
        w: 4,
        h: 3,
        variableName: "temperature",
        minValue: 0,
        maxValue: 50,
        unit: "°C",
        decimals: 1,
      };
    case "card-grid":
      return {
        ...base,
        type: "card-grid",
        title: "Карточки устройств",
        w: 6,
        h: 4,
        objectPath: "",
        parentPath: DEVICES,
        modelHintPath: DEVICES,
        variablesJson: j(["temperature", "alarmActive"]),
        cardSelectionKey: "device",
        cardTargetDashboard: WIDGET_SAMPLE_PATHS.demoDashboard,
        cardOpenMode: "modal",
      };
    case "dashboard-link":
      return {
        ...base,
        type: "dashboard-link",
        title: "Детальный экран",
        w: 3,
        h: 2,
        objectPath: "",
        targetDashboardPath: WIDGET_SAMPLE_PATHS.demoDashboard,
        openMode: "modal",
        buttonLabel: "Открыть дашборд",
        modalTitle: "Демо датчик",
        contextSelectionJson: j({ device: DEVICE }),
        contextParamsJson: j({ source: "dashboard-link" }),
      };
    case "report":
      return {
        ...base,
        type: "report",
        title: "SQL-отчёт",
        w: 6,
        h: 4,
        objectPath: "",
        reportPath: WIDGET_SAMPLE_PATHS.report,
        emptyMessage: "Нет строк — проверьте reportPath",
      };
    case "pie-chart":
      return {
        ...base,
        type: "pie-chart",
        title: "Распределение",
        w: 4,
        h: 4,
        objectPath: LAB,
        modelHintPath: LAB,
        variableName: "table",
        labelField: "string",
        valueField: "int",
        decimals: 0,
        demoPreviewJson: j([
          { name: "Готово", value: 45 },
          { name: "В работе", value: 30 },
          { name: "Ожидание", value: 25 },
        ]),
      };
    case "history-table":
      return {
        ...base,
        type: "history-table",
        title: "История (5 мин)",
        w: 4,
        h: 4,
        variableName: "temperature",
        decimals: 2,
      };
    case "variable-editor":
      return {
        ...base,
        type: "variable-editor",
        title: "Параметры датчика",
        w: 4,
        h: 5,
        variablesJson: j(["temperature", "threshold", "alarmActive"]),
      };
    case "svg-widget":
      return {
        ...base,
        type: "svg-widget",
        title: "Кнопка",
        w: 2,
        h: 2,
        svgUrl: "/lab-assets/button.svg",
        clickAction: "toggle",
        toggleVariable: "alarmActive",
        objectPath: DEVICE,
      };
    case "composite-widget":
      return {
        ...base,
        type: "composite-widget",
        title: "Композит",
        w: 6,
        h: 3,
        childrenJson: j([
          miniValue("composite-temp", "Температура", { w: 4, h: 2 }),
          miniIndicator("composite-alarm", "Тревога"),
        ]),
      };
    case "sub-dashboard":
      return {
        ...base,
        type: "sub-dashboard",
        title: "Вложенный дашборд",
        w: 8,
        h: 6,
        objectPath: "",
        targetDashboardPath: WIDGET_SAMPLE_PATHS.demoDashboard,
        inheritContext: true,
      };
    case "panel":
      return {
        ...base,
        type: "panel",
        title: "Панель метрик",
        w: 6,
        h: 4,
        variant: "simple",
        collapsible: true,
        childrenJson: j([
          miniValue("panel-temp", "Температура", { w: 4, h: 2 }),
          miniIndicator("panel-alarm", "Тревога"),
        ]),
      };
    case "tab-panel":
      return {
        ...base,
        type: "tab-panel",
        title: "Вкладки",
        w: 8,
        h: 5,
        objectPath: "",
        tabsJson: j([
          {
            id: "metrics",
            label: "Метрики",
            children: [miniValue("tab-temp", "Температура", { w: 4, h: 2 })],
          },
          {
            id: "actions",
            label: "Действия",
            children: [miniFunction("tab-fn", "Сброс")],
          },
        ]),
      };
    case "map":
      return {
        ...base,
        type: "map",
        title: "Карта устройств",
        w: 8,
        h: 6,
        objectPath: "",
        parentPath: DEVICES,
        modelHintPath: DEVICES,
        latVariable: "coordinates",
        latField: "latitude",
        lonField: "longitude",
        selectionKey: "device",
        rowSelectionKey: "device",
        rowTargetDashboard: WIDGET_SAMPLE_PATHS.demoDashboard,
        zoom: 10,
        centerLat: 55.75,
        centerLon: 37.62,
        tileUrl: "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        tileAttribution: "© OpenStreetMap contributors",
      };
    case "label":
      return {
        ...base,
        type: "label",
        title: "Подпись",
        w: 4,
        h: 1,
        objectPath: "",
        text: "Температура зоны A — демо подпись",
        paramKey: "zoneLabel",
      };
    case "image":
      return {
        ...base,
        type: "image",
        title: "Схема",
        w: 3,
        h: 3,
        objectPath: "",
        imageUrl: "/lab-assets/fan.svg",
        alt: "Пример SVG-изображения",
      };
    case "html-snippet":
      return {
        ...base,
        type: "html-snippet",
        title: "HTML блок",
        w: 4,
        h: 3,
        objectPath: "",
        htmlJson:
          "<p><strong>Статус:</strong> норма</p><p class=\"hint\">htmlJson — произвольная разметка</p>",
      };
    case "object-tree":
      return {
        ...base,
        type: "object-tree",
        title: "Дерево устройств",
        w: 4,
        h: 5,
        objectPath: "",
        parentPath: DEVICES,
        modelHintPath: DEVICES,
        selectionKey: "device",
        maxDepth: 2,
      };
    case "breadcrumbs":
      return {
        ...base,
        type: "breadcrumbs",
        title: "Путь",
        w: 6,
        h: 1,
        objectPath: "",
        pathKey: "device",
        separator: " › ",
      };
    case "timer":
      return {
        ...base,
        type: "timer",
        title: "Таймер",
        w: 3,
        h: 2,
        objectPath: "",
        mode: "countdown",
        durationSeconds: 120,
      };
    case "context-list":
      return {
        ...base,
        type: "context-list",
        title: "Контекст сессии",
        w: 4,
        h: 4,
        objectPath: "",
      };
    case "linear-gauge":
      return {
        ...base,
        type: "linear-gauge",
        title: "Линейная шкала",
        w: 6,
        h: 2,
        variableName: "temperature",
        minValue: 0,
        maxValue: 50,
        unit: "°C",
        decimals: 1,
      };
    case "input-form":
      return {
        ...base,
        type: "input-form",
        title: "Форма ввода",
        w: 4,
        h: 4,
        fieldsJson: j([
          {
            name: "threshold",
            label: "Порог °C",
            type: "number",
            variableName: "threshold",
            defaultValue: "40",
          },
          {
            name: "note",
            label: "Комментарий",
            type: "textarea",
            defaultValue: "Пример поля без привязки",
          },
        ]),
        buttonLabel: "Применить",
      };
    case "drawer-panel":
      return {
        ...base,
        type: "drawer-panel",
        title: "Выдвижная панель",
        w: 4,
        h: 3,
        objectPath: "",
        drawerLabel: "Детали",
        childrenJson: j([miniValue("drawer-temp", "Температура", { w: 4, h: 2 })]),
      };
    case "carousel":
      return {
        ...base,
        type: "carousel",
        title: "Карусель",
        w: 6,
        h: 4,
        objectPath: "",
        autoplayMs: 8000,
        slidesJson: j([
          {
            id: "slide-1",
            label: "Температура",
            children: [miniValue("carousel-temp", "Температура", { w: 4, h: 2 })],
          },
          {
            id: "slide-2",
            label: "Тревога",
            children: [miniIndicator("carousel-alarm", "Тревога")],
          },
        ]),
      };
    case "steps-panel":
      return {
        ...base,
        type: "steps-panel",
        title: "Шаги",
        w: 8,
        h: 5,
        objectPath: "",
        stepsJson: j([
          {
            id: "step-1",
            label: "1. Обзор",
            children: [miniValue("step1-temp", "Температура", { w: 4, h: 2 })],
          },
          {
            id: "step-2",
            label: "2. Проверка",
            children: [miniIndicator("step2-alarm", "Тревога")],
          },
          {
            id: "step-3",
            label: "3. Действие",
            children: [miniFunction("step3-fn", "Сброс")],
          },
        ]),
      };
    case "gantt-chart":
      return {
        ...base,
        type: "gantt-chart",
        title: "Гантт",
        w: 8,
        h: 4,
        objectPath: LAB,
        modelHintPath: LAB,
        variableName: "table",
        labelField: "string",
        startField: "int",
        endField: "int",
        demoPreviewJson: j([
          { label: "Задача A", start: 0, end: 3 },
          { label: "Задача B", start: 2, end: 6 },
          { label: "Задача C", start: 5, end: 9 },
        ]),
      };
    case "network-graph":
      return {
        ...base,
        type: "network-graph",
        title: "Граф сети",
        w: 6,
        h: 5,
        objectPath: LAB,
        modelHintPath: LAB,
        nodesVariable: "nodes",
        edgesVariable: "edges",
        labelField: "name",
        demoPreviewJson: j({
          nodes: ["Шлюз", "Датчик 1", "Датчик 2", "ПЛК"],
          edges: 5,
        }),
      };
    case "spreadsheet":
      return {
        ...base,
        type: "spreadsheet",
        title: "Таблица данных",
        w: 8,
        h: 5,
        objectPath: LAB,
        modelHintPath: LAB,
        variableName: "table",
        editable: true,
        demoPreviewJson: j([
          { int: 1, string: "строка A" },
          { int: 2, string: "строка B" },
          { int: 3, string: "строка C" },
        ]),
      };
    case "liquid-gauge":
      return {
        ...base,
        type: "liquid-gauge",
        title: "Жидкий gauge",
        w: 3,
        h: 3,
        variableName: "temperature",
        minValue: 0,
        maxValue: 50,
        decimals: 1,
      };
    case "nav-menu":
      return {
        ...base,
        type: "nav-menu",
        title: "Навигация",
        w: 4,
        h: 2,
        objectPath: "",
        itemsJson: j([
          {
            label: "Демо датчик",
            dashboardPath: WIDGET_SAMPLE_PATHS.demoDashboard,
            openMode: "modal",
          },
          {
            label: "SNMP мониторинг",
            dashboardPath: WIDGET_SAMPLE_PATHS.snmpDashboard,
            openMode: "navigate",
          },
        ]),
      };
    case "mini-tec-sld":
      return {
        ...base,
        type: "mini-tec-sld",
        title: "Однолинейная схема Мини-ТЭЦ",
        w: 12,
        h: 5,
        objectPath: "",
      };
    default:
      return { ...base, type: "value", title: "Значение", variableName: "temperature", decimals: 1 };
  }
}
