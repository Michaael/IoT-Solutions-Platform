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
  | "report";

export type DashboardOpenMode = "navigate" | "modal";

export type ChartStyle = "line" | "area";

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
}

export interface EventFeedWidget extends DashboardWidgetBase {
  type: "event-feed";
  objectPathPrefix?: string;
  eventNamesJson?: string;
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
}

export interface DashboardLinkWidget extends DashboardWidgetBase {
  type: "dashboard-link";
  targetDashboardPath: string;
  openMode?: DashboardOpenMode;
  buttonLabel?: string;
  modalTitle?: string;
  confirmMessage?: string;
}

export interface ReportWidget extends DashboardWidgetBase {
  type: "report";
  reportPath: string;
  emptyMessage?: string;
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
  | ReportWidget;

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
