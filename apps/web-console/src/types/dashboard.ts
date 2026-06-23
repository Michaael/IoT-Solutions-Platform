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
  /** Static preview payload for editor when live data is not yet available */
  demoPreviewJson?: string;
  /** Widget inserted from palette with sample configuration */
  sampleTemplate?: boolean;
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

import { buildSampleWidget } from "../components/dashboard/widgetSamples";

export function newWidget(type: WidgetType, index: number): DashboardWidget {
  return buildSampleWidget(type, index);
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
