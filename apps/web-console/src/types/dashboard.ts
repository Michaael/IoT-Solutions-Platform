export type WidgetType =
  | "value"
  | "toggle"
  | "indicator"
  | "chart"
  | "sparkline"
  | "function"
  | "function-form"
  | "progress"
  | "object-table"
  | "event-feed"
  | "work-queue"
  | "status-badge"
  | "gauge"
  | "card-grid"
  | "dashboard-link"
  | "report"
  | "pie-chart"
  | "history-table"
  | "variable-editor"
  | "svg-widget"
  | "composite-widget"
  | "sub-dashboard"
  | "panel"
  | "tab-panel"
  | "map"
  | "label"
  | "image"
  | "html-snippet"
  | "object-tree"
  | "breadcrumbs"
  | "timer"
  | "context-list"
  | "linear-gauge"
  | "input-form"
  | "drawer-panel"
  | "carousel"
  | "steps-panel"
  | "gantt-chart"
  | "network-graph"
  | "spreadsheet"
  | "liquid-gauge"
  | "nav-menu";

export type DashboardOpenMode = "navigate" | "modal";

export type ChartStyle = "line" | "area";

export type ChartType = "line" | "area" | "bar" | "candlestick" | "bubble" | "radar" | "range";

/** How chart/sparkline loads historian data. `live` = sliding window + live tail (default). */
export type WidgetHistoryRange = "live" | "1h" | "6h" | "24h" | "7d" | "all";

export const WIDGET_HISTORY_RANGE_OPTIONS: { id: WidgetHistoryRange; label: string }[] = [
  { id: "live", label: "Live — последние N точек" },
  { id: "1h", label: "1 час" },
  { id: "6h", label: "6 часов" },
  { id: "24h", label: "24 часа" },
  { id: "7d", label: "7 дней (avg/1h)" },
  { id: "all", label: "Весь retention (avg/6h)" },
];

export function widgetHistoryRangeLabel(range: WidgetHistoryRange | undefined): string | undefined {
  return WIDGET_HISTORY_RANGE_OPTIONS.find((item) => item.id === (range ?? "live"))?.label;
}

export interface DashboardWidgetBase {
  id: string;
  type: WidgetType;
  title: string;
  x: number;
  y: number;
  w: number;
  h: number;
  objectPath?: string;
  variableName?: string;
  valueField?: string;
  /** When set, objectPath is taken from dashboard selection (e.g. selected order). */
  selectionKey?: string;
  /** Read arbitrary value from session.params */
  paramKey?: string;
  /** Object path from session.params[key] when selectionKey empty */
  contextPathKey?: string;
  /** Editor hint: sample object for variable dropdown when using selectionKey */
  modelHintPath?: string;
  /**
   * Per-element inline styles (JSON object).
   * Keys: card, title, body, value, unit, meta, label, badge, dot, table, chart.
   * Values: camelCase CSS properties, e.g. {"value":{"fontSize":"0.88rem"}}.
   */
  stylesJson?: string;
}

export interface ValueWidget extends DashboardWidgetBase {
  type: "value";
  unit?: string;
  unitField?: string;
  decimals?: number;
}

export interface ToggleWidget extends DashboardWidgetBase {
  type: "toggle";
  trueLabel?: string;
  falseLabel?: string;
}

export interface IndicatorWidget extends DashboardWidgetBase {
  type: "indicator";
  trueLabel?: string;
  falseLabel?: string;
  trueColor?: string;
  falseColor?: string;
}

export interface ChartWidget extends DashboardWidgetBase {
  type: "chart";
  chartStyle?: ChartStyle;
  chartType?: ChartType;
  maxPoints?: number;
  historyRange?: WidgetHistoryRange;
  color?: string;
  decimals?: number;
  unit?: string;
  unitField?: string;
}

export interface SparklineWidget extends DashboardWidgetBase {
  type: "sparkline";
  maxPoints?: number;
  historyRange?: WidgetHistoryRange;
  color?: string;
  decimals?: number;
}

export interface FunctionWidget extends DashboardWidgetBase {
  type: "function";
  functionName: string;
  buttonLabel?: string;
  confirmMessage?: string;
  /** Static JSON input rows for invoke (optional). */
  inputJson?: string;
}

export interface FunctionFormField {
  name: string;
  label: string;
  type: "text" | "number" | "select";
  /** Parent path — options = child object display names / paths */
  optionsFrom?: string;
  staticOptions?: string[];
  defaultValue?: string;
}

export interface FunctionFormWidget extends DashboardWidgetBase {
  type: "function-form";
  functionName: string;
  buttonLabel?: string;
  confirmMessage?: string;
  fieldsJson?: string;
}

export interface ProgressWidget extends DashboardWidgetBase {
  type: "progress";
  currentVariable: string;
  maxVariable: string;
  unit?: string;
  decimals?: number;
}

export interface ObjectTableColumn {
  variable: string;
  label: string;
}

export interface ObjectTableWidget extends DashboardWidgetBase {
  type: "object-table";
  parentPath: string;
  columnsJson?: string;
  /** Writes selected row path into dashboard selection */
  selectionKey?: string;
  /** Open another dashboard when a row is clicked */
  rowTargetDashboard?: string;
  rowOpenMode?: DashboardOpenMode;
  /** Selection key written in target dashboard session */
  rowSelectionKey?: string;
  /** JSON merged into session.params on row click */
  rowParamsJson?: string;
}

export interface EventFeedWidget extends DashboardWidgetBase {
  type: "event-feed";
  objectPathPrefix?: string;
  eventNamesJson?: string;
  /** Client-side filter on payload row, e.g. `count>10 && name contains abc` */
  payloadFilterExpr?: string;
  maxItems?: number;
}

export interface WorkQueueWidget extends DashboardWidgetBase {
  type: "work-queue";
  operatorId?: string;
  maxItems?: number;
}

export interface StatusBadgeWidget extends DashboardWidgetBase {
  type: "status-badge";
}

export interface GaugeWidget extends DashboardWidgetBase {
  type: "gauge";
  minVariable?: string;
  maxVariable?: string;
  minValue?: number;
  maxValue?: number;
  unit?: string;
  decimals?: number;
}

export interface CardGridWidget extends DashboardWidgetBase {
  type: "card-grid";
  parentPath: string;
  variablesJson?: string;
  /** Open another dashboard when a card is clicked */
  cardTargetDashboard?: string;
  cardOpenMode?: DashboardOpenMode;
  /** Copy clicked card path into selection before navigation */
  cardSelectionKey?: string;
  cardParamsJson?: string;
}

export interface DashboardLinkWidget extends DashboardWidgetBase {
  type: "dashboard-link";
  targetDashboardPath: string;
  openMode?: DashboardOpenMode;
  buttonLabel?: string;
  modalTitle?: string;
  confirmMessage?: string;
  contextSelectionJson?: string;
  contextParamsJson?: string;
}

export interface ReportWidget extends DashboardWidgetBase {
  type: "report";
  reportPath: string;
  emptyMessage?: string;
}

export interface PieChartWidget extends DashboardWidgetBase {
  type: "pie-chart";
  labelField?: string;
  decimals?: number;
}

export interface HistoryTableWidget extends DashboardWidgetBase {
  type: "history-table";
  decimals?: number;
}

export interface VariableEditorWidget extends DashboardWidgetBase {
  type: "variable-editor";
  /** JSON array of variable names; empty = all variables on object */
  variablesJson?: string;
}

export interface SvgWidget extends DashboardWidgetBase {
  type: "svg-widget";
  svgUrl: string;
  clickAction?: "function" | "toggle";
  functionName?: string;
  toggleVariable?: string;
  confirmMessage?: string;
}

export interface CompositeWidget extends DashboardWidgetBase {
  type: "composite-widget";
  childrenJson?: string;
}

export interface SubDashboardWidget extends DashboardWidgetBase {
  type: "sub-dashboard";
  targetDashboardPath?: string;
  targetDashboardPathKey?: string;
  inheritContext?: boolean;
}

export interface PanelWidget extends DashboardWidgetBase {
  type: "panel";
  variant?: "simple";
  collapsible?: boolean;
  childrenJson?: string;
}

export interface TabPanelTab {
  id: string;
  label: string;
  children: DashboardWidget[];
}

export interface TabPanelWidget extends DashboardWidgetBase {
  type: "tab-panel";
  tabsJson?: string;
}

export interface MapWidget extends DashboardWidgetBase {
  type: "map";
  parentPath: string;
  latVariable?: string;
  latField?: string;
  lonField?: string;
  labelVariable?: string;
  zoom?: number;
  centerLat?: number;
  centerLon?: number;
  rowTargetDashboard?: string;
  rowOpenMode?: DashboardOpenMode;
  rowSelectionKey?: string;
  rowParamsJson?: string;
}

export interface LabelWidget extends DashboardWidgetBase {
  type: "label";
  text?: string;
  textJson?: string;
}

export interface ImageWidget extends DashboardWidgetBase {
  type: "image";
  imageUrl?: string;
  alt?: string;
}

export interface HtmlSnippetWidget extends DashboardWidgetBase {
  type: "html-snippet";
  htmlJson?: string;
}

export interface ObjectTreeWidget extends DashboardWidgetBase {
  type: "object-tree";
  parentPath: string;
  selectionKey?: string;
  maxDepth?: number;
}

export interface BreadcrumbsWidget extends DashboardWidgetBase {
  type: "breadcrumbs";
  pathKey?: string;
  separator?: string;
}

export interface TimerWidget extends DashboardWidgetBase {
  type: "timer";
  mode?: "countdown" | "elapsed";
  durationSeconds?: number;
  variableName?: string;
}

export interface ContextListWidget extends DashboardWidgetBase {
  type: "context-list";
}

export interface LinearGaugeWidget extends DashboardWidgetBase {
  type: "linear-gauge";
  minVariable?: string;
  maxVariable?: string;
  minValue?: number;
  maxValue?: number;
  unit?: string;
  decimals?: number;
}

export interface InputFormField {
  name: string;
  label: string;
  type: "text" | "number" | "textarea" | "select" | "slider" | "checkbox" | "radio" | "datetime" | "time";
  variableName?: string;
  optionsFrom?: string;
  staticOptions?: string[];
  min?: number;
  max?: number;
  step?: number;
  defaultValue?: string;
}

export interface InputFormWidget extends DashboardWidgetBase {
  type: "input-form";
  fieldsJson?: string;
  buttonLabel?: string;
}

export interface DrawerPanelWidget extends DashboardWidgetBase {
  type: "drawer-panel";
  drawerLabel?: string;
  childrenJson?: string;
}

export interface CarouselWidget extends DashboardWidgetBase {
  type: "carousel";
  slidesJson?: string;
  autoplayMs?: number;
}

export interface StepsPanelWidget extends DashboardWidgetBase {
  type: "steps-panel";
  stepsJson?: string;
  activeStepKey?: string;
}

export interface GanttChartWidget extends DashboardWidgetBase {
  type: "gantt-chart";
  labelField?: string;
  startField?: string;
  endField?: string;
}

export interface NetworkGraphWidget extends DashboardWidgetBase {
  type: "network-graph";
  nodesVariable?: string;
  edgesVariable?: string;
  labelField?: string;
}

export interface SpreadsheetWidget extends DashboardWidgetBase {
  type: "spreadsheet";
  variableName: string;
  editable?: boolean;
}

export interface LiquidGaugeWidget extends DashboardWidgetBase {
  type: "liquid-gauge";
  minVariable?: string;
  maxVariable?: string;
  minValue?: number;
  maxValue?: number;
  decimals?: number;
}

export interface NavMenuWidget extends DashboardWidgetBase {
  type: "nav-menu";
  itemsJson?: string;
}

export type DashboardWidget =
  | ValueWidget
  | ToggleWidget
  | IndicatorWidget
  | ChartWidget
  | SparklineWidget
  | FunctionWidget
  | FunctionFormWidget
  | ProgressWidget
  | ObjectTableWidget
  | EventFeedWidget
  | WorkQueueWidget
  | StatusBadgeWidget
  | GaugeWidget
  | CardGridWidget
  | DashboardLinkWidget
  | ReportWidget
  | PieChartWidget
  | HistoryTableWidget
  | VariableEditorWidget
  | SvgWidget
  | CompositeWidget
  | SubDashboardWidget
  | PanelWidget
  | TabPanelWidget
  | MapWidget
  | LabelWidget
  | ImageWidget
  | HtmlSnippetWidget
  | ObjectTreeWidget
  | BreadcrumbsWidget
  | TimerWidget
  | ContextListWidget
  | LinearGaugeWidget
  | InputFormWidget
  | DrawerPanelWidget
  | CarouselWidget
  | StepsPanelWidget
  | GanttChartWidget
  | NetworkGraphWidget
  | SpreadsheetWidget
  | LiquidGaugeWidget
  | NavMenuWidget;

export interface DashboardLayout {
  columns: number;
  rowHeight: number;
  /** Visual theme id, e.g. "btop" → class dashboard-theme-btop on grid host */
  theme?: string;
  widgets: DashboardWidget[];
}

export interface DashboardView {
  path: string;
  title: string;
  refreshIntervalMs: number;
  layout: DashboardLayout;
  layoutJson: string;
}

export const WIDGET_TYPES: Array<{ type: WidgetType; label: string }> = [
  { type: "value", label: "Значение" },
  { type: "indicator", label: "Индикатор" },
  { type: "toggle", label: "Переключатель" },
  { type: "chart", label: "График / тренд" },
  { type: "sparkline", label: "Спарклайн" },
  { type: "function", label: "Функция (кнопка)" },
  { type: "function-form", label: "Функция (форма)" },
  { type: "progress", label: "Прогресс" },
  { type: "object-table", label: "Таблица объектов" },
  { type: "event-feed", label: "Лента событий" },
  { type: "work-queue", label: "Очередь задач" },
  { type: "status-badge", label: "Статус (badge)" },
  { type: "gauge", label: "Шкала / gauge" },
  { type: "card-grid", label: "Карточки объектов" },
  { type: "dashboard-link", label: "Переход / модальный дашборд" },
  { type: "report", label: "SQL-отчёт" },
  { type: "pie-chart", label: "Круговая диаграмма" },
  { type: "history-table", label: "Таблица истории (5 мин)" },
  { type: "variable-editor", label: "Редактор переменных" },
  { type: "svg-widget", label: "SVG (lab)" },
  { type: "composite-widget", label: "Композитный виджет" },
  { type: "sub-dashboard", label: "Сабдашборд" },
  { type: "panel", label: "Панель" },
  { type: "tab-panel", label: "Вкладки" },
  { type: "map", label: "Карта" },
  { type: "label", label: "Метка" },
  { type: "image", label: "Изображение" },
  { type: "html-snippet", label: "HTML" },
  { type: "object-tree", label: "Дерево объектов" },
  { type: "breadcrumbs", label: "Хлебные крошки" },
  { type: "timer", label: "Таймер" },
  { type: "context-list", label: "Контекст" },
  { type: "linear-gauge", label: "Линейная шкала" },
  { type: "input-form", label: "Форма ввода" },
  { type: "drawer-panel", label: "Выдвижная панель" },
  { type: "carousel", label: "Карусель" },
  { type: "steps-panel", label: "Шаги" },
  { type: "gantt-chart", label: "Гантт" },
  { type: "network-graph", label: "Граф сети" },
  { type: "spreadsheet", label: "Таблица (RECORD_LIST)" },
  { type: "liquid-gauge", label: "Жидкий gauge" },
  { type: "nav-menu", label: "Меню навигации" },
];

export function emptyLayout(): DashboardLayout {
  return { columns: 12, rowHeight: 72, widgets: [] };
}

export function parseLayoutJson(raw: string | undefined | null): DashboardLayout {
  if (!raw) {
    return emptyLayout();
  }
  try {
    const parsed = JSON.parse(raw) as DashboardLayout;
    return {
      columns: parsed.columns ?? 12,
      rowHeight: parsed.rowHeight ?? 72,
      theme: typeof parsed.theme === "string" ? parsed.theme : undefined,
      widgets: Array.isArray(parsed.widgets) ? parsed.widgets : [],
    };
  } catch {
    return emptyLayout();
  }
}

export function layoutToJson(layout: DashboardLayout): string {
  return JSON.stringify(layout, null, 2);
}

export function newWidget(type: WidgetType, index: number): DashboardWidget {
  const base = {
    id: `widget-${Date.now()}-${index}`,
    title: "Виджет",
    x: 0,
    y: index * 2,
    w: 3,
    h: 2,
    objectPath: "",
    variableName: "",
    valueField: "value",
  };
  switch (type) {
    case "toggle":
      return { ...base, type: "toggle", trueLabel: "Вкл", falseLabel: "Выкл" };
    case "indicator":
      return { ...base, type: "indicator", trueLabel: "Да", falseLabel: "Нет" };
    case "chart":
      return {
        ...base,
        type: "chart",
        w: 6,
        h: 4,
        chartStyle: "area",
        maxPoints: 120,
        color: "#2f81f7",
        decimals: 1,
      };
    case "sparkline":
      return {
        ...base,
        type: "sparkline",
        w: 4,
        h: 2,
        maxPoints: 40,
        color: "#3fb950",
        decimals: 1,
      };
    case "function":
      return {
        ...base,
        type: "function",
        w: 3,
        h: 2,
        functionName: "acknowledgeAlarm",
        buttonLabel: "Выполнить",
      };
    case "function-form":
      return {
        ...base,
        type: "function-form",
        w: 4,
        h: 4,
        functionName: "assign",
        buttonLabel: "Выполнить",
        fieldsJson: "[]",
      };
    case "progress":
      return {
        ...base,
        type: "progress",
        w: 6,
        h: 2,
        currentVariable: "actualLiters",
        maxVariable: "plannedLiters",
        unit: "л",
        decimals: 0,
        selectionKey: "order",
      };
    case "object-table":
      return {
        ...base,
        type: "object-table",
        w: 8,
        h: 5,
        parentPath: "",
        columnsJson: "[]",
        selectionKey: "order",
      };
    case "event-feed":
      return {
        ...base,
        type: "event-feed",
        w: 12,
        h: 4,
        objectPathPrefix: "",
        eventNamesJson: "[]",
        maxItems: 20,
      };
    case "work-queue":
      return {
        ...base,
        type: "work-queue",
        w: 4,
        h: 5,
        operatorId: "operator",
        maxItems: 10,
      };
    case "status-badge":
      return {
        ...base,
        type: "status-badge",
        w: 3,
        h: 2,
        variableName: "status",
        selectionKey: "order",
      };
    case "gauge":
      return {
        ...base,
        type: "gauge",
        w: 4,
        h: 3,
        variableName: "levelM3",
        minVariable: "minLevelM3",
        maxVariable: "maxLevelM3",
        unit: "м³",
        decimals: 1,
      };
    case "card-grid":
      return {
        ...base,
        type: "card-grid",
        w: 6,
        h: 4,
        parentPath: "",
        variablesJson: '["levelM3","qualityOk"]',
      };
    case "dashboard-link":
      return {
        ...base,
        type: "dashboard-link",
        w: 3,
        h: 2,
        targetDashboardPath: "",
        openMode: "navigate",
        buttonLabel: "Открыть дашборд",
      };
    case "report":
      return {
        ...base,
        type: "report",
        w: 6,
        h: 4,
        reportPath: "",
        emptyMessage: "Нет строк",
      };
    case "pie-chart":
      return {
        ...base,
        type: "pie-chart",
        w: 4,
        h: 4,
        labelField: "name",
        valueField: "value",
        decimals: 1,
      };
    case "history-table":
      return {
        ...base,
        type: "history-table",
        w: 4,
        h: 4,
        decimals: 2,
      };
    case "variable-editor":
      return {
        ...base,
        type: "variable-editor",
        w: 4,
        h: 5,
        variablesJson: "[]",
      };
    case "svg-widget":
      return {
        ...base,
        type: "svg-widget",
        w: 2,
        h: 2,
        svgUrl: "/lab-assets/button.svg",
        clickAction: "function",
        functionName: "",
      };
    case "composite-widget":
      return {
        ...base,
        type: "composite-widget",
        w: 4,
        h: 3,
        childrenJson: "[]",
      };
    case "sub-dashboard":
      return {
        ...base,
        type: "sub-dashboard",
        w: 8,
        h: 6,
        targetDashboardPath: "",
        inheritContext: true,
      };
    case "panel":
      return {
        ...base,
        type: "panel",
        w: 6,
        h: 4,
        variant: "simple",
        collapsible: true,
        childrenJson: "[]",
      };
    case "tab-panel":
      return {
        ...base,
        type: "tab-panel",
        w: 8,
        h: 5,
        tabsJson: "[]",
      };
    case "map":
      return {
        ...base,
        type: "map",
        w: 8,
        h: 6,
        parentPath: "",
        latVariable: "coordinates",
        latField: "latitude",
        lonField: "longitude",
        selectionKey: "device",
        zoom: 10,
        centerLat: 55.75,
        centerLon: 37.62,
      };
    case "label":
      return { ...base, type: "label", w: 4, h: 1, text: "Текст" };
    case "image":
      return { ...base, type: "image", w: 3, h: 3, imageUrl: "" };
    case "html-snippet":
      return { ...base, type: "html-snippet", w: 4, h: 3, htmlJson: "<p>HTML</p>" };
    case "object-tree":
      return {
        ...base,
        type: "object-tree",
        w: 4,
        h: 5,
        parentPath: "",
        selectionKey: "device",
      };
    case "breadcrumbs":
      return { ...base, type: "breadcrumbs", w: 6, h: 1, pathKey: "device" };
    case "timer":
      return {
        ...base,
        type: "timer",
        w: 3,
        h: 2,
        mode: "elapsed",
        durationSeconds: 60,
      };
    case "context-list":
      return { ...base, type: "context-list", w: 4, h: 4 };
    case "linear-gauge":
      return {
        ...base,
        type: "linear-gauge",
        w: 6,
        h: 2,
        minValue: 0,
        maxValue: 100,
        decimals: 0,
      };
    case "input-form":
      return {
        ...base,
        type: "input-form",
        w: 4,
        h: 4,
        fieldsJson: "[]",
        buttonLabel: "Применить",
      };
    case "drawer-panel":
      return {
        ...base,
        type: "drawer-panel",
        w: 4,
        h: 3,
        drawerLabel: "Открыть",
        childrenJson: "[]",
      };
    case "carousel":
      return { ...base, type: "carousel", w: 6, h: 4, slidesJson: "[]" };
    case "steps-panel":
      return { ...base, type: "steps-panel", w: 8, h: 5, stepsJson: "[]" };
    case "gantt-chart":
      return {
        ...base,
        type: "gantt-chart",
        w: 8,
        h: 4,
        labelField: "name",
        startField: "start",
        endField: "end",
      };
    case "network-graph":
      return {
        ...base,
        type: "network-graph",
        w: 6,
        h: 5,
        nodesVariable: "nodes",
        edgesVariable: "edges",
      };
    case "spreadsheet":
      return {
        ...base,
        type: "spreadsheet",
        w: 8,
        h: 5,
        variableName: "table",
        editable: true,
      };
    case "liquid-gauge":
      return {
        ...base,
        type: "liquid-gauge",
        w: 3,
        h: 3,
        minValue: 0,
        maxValue: 100,
        decimals: 0,
      };
    case "nav-menu":
      return { ...base, type: "nav-menu", w: 4, h: 2, itemsJson: "[]" };
    default:
      return { ...base, type: "value", decimals: 1 };
  }
}

export function readFieldValue(
  row: Record<string, unknown> | undefined,
  field?: string
): unknown {
  if (!row) return undefined;
  if (!field) return row.value ?? row.raw ?? Object.values(row)[0];
  return row[field];
}

export function mergeWidgetLayout(
  widgets: DashboardWidget[],
  positions: Array<{ id: string; x: number; y: number; w: number; h: number }>
): DashboardWidget[] {
  const byId = new Map(positions.map((item) => [item.id, item]));
  return widgets.map((widget) => {
    const position = byId.get(widget.id);
    if (!position) return widget;
    return { ...widget, x: position.x, y: position.y, w: position.w, h: position.h };
  });
}
