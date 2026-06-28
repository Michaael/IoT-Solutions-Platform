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
  | "nav-menu"
  | "mini-tec-sld";

export type DashboardOpenMode = "navigate" | "modal";

export type ChartStyle = "line" | "area";

export type ChartType = "line" | "area" | "bar" | "candlestick" | "bubble" | "radar" | "range";

/** How chart/sparkline loads historian data. `live` = sliding window + live tail (default). */
export type WidgetHistoryRange = "live" | "1h" | "6h" | "24h" | "7d" | "all";

export const WIDGET_HISTORY_RANGE_IDS: WidgetHistoryRange[] = [
  "live",
  "1h",
  "6h",
  "24h",
  "7d",
  "all",
];

export const WIDGET_HISTORY_RANGE_OPTIONS: { id: WidgetHistoryRange; label: string }[] =
  WIDGET_HISTORY_RANGE_IDS.map((id) => ({ id, label: id }));

export function widgetHistoryRangeLabel(
  range: WidgetHistoryRange | undefined,
  t?: (key: string, options?: { ns?: string }) => string
): string | undefined {
  const id = range ?? "live";
  if (t) {
    return t(`history.${id}`, { ns: "widgets" });
  }
  return WIDGET_HISTORY_RANGE_OPTIONS.find((item) => item.id === id)?.label;
}

/** History table widget range (default 5m for backward compatibility). */
export type HistoryTableRange = "5m" | Exclude<WidgetHistoryRange, "live">;

export const HISTORY_TABLE_RANGE_IDS: HistoryTableRange[] = ["5m", "1h", "6h", "24h", "7d", "all"];

export function historyTableRangeLabel(
  range: HistoryTableRange | undefined,
  t?: (key: string, options?: { ns?: string }) => string
): string {
  const id = range ?? "5m";
  if (t) {
    return t(`history.${id}`, { ns: "widgets" });
  }
  return id;
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
  /** When true, variable=true means alarm (red). Default false: true=active/ok (green). */
  alarmMode?: boolean;
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
  functionName?: string;
  buttonLabel?: string;
  confirmMessage?: string;
  /** Static JSON input rows for invoke (optional). */
  inputJson?: string;
  /** When set, button runs workflow instead of invoking a function. */
  workflowPath?: string;
}

export interface FunctionFormSelectOption {
  value: string;
  label: string;
}

export interface FunctionFormField {
  name: string;
  label: string;
  type: "text" | "number" | "select" | "multiselect" | "time" | "checkbox" | "textarea";
  /** Parent path — options = child object display names / paths */
  optionsFrom?: string;
  /** Report path — options loaded from report rows (same source as report widget) */
  optionsFromReport?: string;
  /** Report column for option value (default: code) */
  optionsValueField?: string;
  /** Report column appended to value in label, e.g. code + name → "120 — …" */
  optionsLabelField?: string;
  staticOptions?: string[];
  selectOptions?: FunctionFormSelectOption[];
  defaultValue?: string;
  /** Legacy bundle key — normalized to defaultValue in the widget */
  default?: string;
  hint?: string;
  hidden?: boolean;
  /** Wizard step id — field shown only on this step */
  step?: string;
  required?: boolean;
  /** Bind field value from session.params[key] (read-only in view mode) */
  paramKey?: string;
  /** Grid width in wizard layout: 1 = half row, 2 = full row (default 2) */
  colSpan?: 1 | 2;
  /** JSON object: field name → value or array of values; field shown only when all match */
  showWhenJson?: string;
  /** When loading optionsFromReport, keep rows where optionsFilterColumn equals form field optionsFilterField */
  optionsFilterField?: string;
  optionsFilterColumn?: string;
}

export interface FunctionFormWidget extends DashboardWidgetBase {
  type: "function-form";
  functionName: string;
  buttonLabel?: string;
  confirmMessage?: string;
  fieldsJson?: string;
  /** Multi-step wizard: [{ id, label }, …] */
  wizardStepsJson?: string;
  /** Optional BFF function invoked on «Далее» (receives all field values + step) */
  validateFunctionName?: string;
  /** JSON map: form field name → session.params key */
  paramBindingsJson?: string;
  /** JSON array — session param keys that must be set before submit / «Далее» */
  requireSessionParamsJson?: string;
  /** JSON map: form field name → session param key (sync on change) */
  syncFieldsToSessionJson?: string;
  /** JSON array — session param keys cleared after successful submit */
  clearSessionParamsJson?: string;
  /** Close parent modal after successful submit (default: true in modal) */
  closeModalOnSuccess?: boolean;
}

export interface FunctionFormWizardStep {
  id: string;
  label: string;
}

export interface ProgressWidget extends DashboardWidgetBase {
  type: "progress";
  currentVariable: string;
  maxVariable: string;
  unit?: string;
  decimals?: number;
}

export interface ObjectTableColumn {
  variable?: string;
  label: string;
  /** DataRecord field; default "value". Use "online" for status.online, etc. */
  field?: string;
  /** Object field when variable is omitted (displayName, path). */
  objectField?: "displayName" | "path";
  trueLabel?: string;
  falseLabel?: string;
}

export interface ObjectTableWidget extends DashboardWidgetBase {
  type: "object-table";
  parentPath: string;
  columnsJson?: string;
  /** Glob on object leaf name, e.g. gpu-* */
  namePattern?: string;
  /** Filter children by ObjectType */
  objectType?: import("../types").ObjectType;
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
  /** Filter tasks to this operator app (e.g. mes-defect-demo). */
  operatorAppId?: string;
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
  /** JSON array of session param keys that must be non-empty before the link is enabled */
  requireSessionParamsJson?: string;
}

export interface ReportWidget extends DashboardWidgetBase {
  type: "report";
  reportPath: string;
  emptyMessage?: string;
  /** Static run parameters (JSON object) */
  parametersJson?: string;
  /** Session param key → report parameter name mapping (JSON object) */
  contextParamsJson?: string;
  showCsv?: boolean;
  showPdf?: boolean;
  showXlsx?: boolean;
  showHtml?: boolean;
  showTruncatedWarning?: boolean;
  /** Clickable rows — writes row columns into session.params */
  selectable?: boolean;
  /** Report column used as row id for highlight (default id) */
  rowSelectionKey?: string;
  /** JSON map: session.params key → report row column name */
  rowParamsFromRowJson?: string;
  /** Select first row when data loads and nothing is selected yet */
  autoSelectFirstRow?: boolean;
  /** JSON array — table columns rendered as ok/warn status dots */
  statusDotColumnsJson?: string;
}

export interface PieChartWidget extends DashboardWidgetBase {
  type: "pie-chart";
  labelField?: string;
  decimals?: number;
}

export interface HistoryTableWidget extends DashboardWidgetBase {
  type: "history-table";
  decimals?: number;
  historyRange?: HistoryTableRange;
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

/** Live SCADA single-line diagram for the mini-TEC reference plant. */
export interface MiniTecSldWidget extends DashboardWidgetBase {
  type: "mini-tec-sld";
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
  /** MapLibre style JSON URL (optional; default: OSM raster tiles) */
  mapStyleUrl?: string;
  /** Raster tile template (default: OpenStreetMap) */
  tileUrl?: string;
  tileAttribution?: string;
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
  idField?: string;
  edgeFromField?: string;
  edgeToField?: string;
  layout?: "cose" | "circle" | "grid" | "breadthfirst";
}

export type SheetCellKind = "label" | "input" | "formula" | "readonly" | "binding";

export interface SheetCellFormat {
  type?: "number" | "text";
  decimals?: number;
  suffix?: string;
  prefix?: string;
}

export interface SheetCellStyle {
  color?: string;
  backgroundColor?: string;
  fontWeight?: string;
  textAlign?: "left" | "center" | "right";
}

export interface SheetMergeRange {
  anchor: string;
  rowSpan: number;
  colSpan: number;
}

export interface SheetCellValidation {
  type?: "range" | "pattern";
  min?: number;
  max?: number;
  pattern?: string;
  message?: string;
}

export interface SheetCellConfig {
  kind: SheetCellKind;
  text?: string;
  expr?: string;
  default?: string;
  objectPath?: string;
  variableName?: string;
  valueField?: string;
  refreshIntervalMs?: number;
  format?: SheetCellFormat;
  style?: SheetCellStyle;
  validation?: SheetCellValidation;
}

export interface SheetColumnFilter {
  column: string;
  value?: string;
}

export interface SheetDataRegion {
  startRow: number;
  startCol: number;
  variableName: string;
  columnFields: string[];
}

export interface SheetConditionalStyle {
  when: string;
  style?: SheetCellStyle;
}

export interface SheetConfig {
  rows: number;
  cols: number;
  frozenRows?: number;
  frozenCols?: number;
  colLabels?: string[];
  cells: Record<string, SheetCellConfig>;
  columnFilters?: SheetColumnFilter[];
  dataRegion?: SheetDataRegion;
  conditionalStyles?: SheetConditionalStyle[];
  mergedCells?: SheetMergeRange[];
}

export type SheetMode = "free" | "configured";

export interface SpreadsheetWidget extends DashboardWidgetBase {
  type: "spreadsheet";
  sheetMode?: SheetMode;
  sheetConfigJson?: string;
  persistMode?: "session" | "variable";
  valuesVariable?: string;
  sessionKey?: string;
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
  | MiniTecSldWidget
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

export const WIDGET_TYPES: Array<{ type: WidgetType; label: string }> = (
  [
    "value",
    "indicator",
    "toggle",
    "chart",
    "sparkline",
    "function",
    "function-form",
    "progress",
    "object-table",
    "event-feed",
    "work-queue",
    "status-badge",
    "gauge",
    "card-grid",
    "dashboard-link",
    "report",
    "pie-chart",
    "history-table",
    "variable-editor",
    "svg-widget",
    "composite-widget",
    "sub-dashboard",
    "panel",
    "tab-panel",
    "map",
    "label",
    "image",
    "html-snippet",
    "object-tree",
    "breadcrumbs",
    "timer",
    "context-list",
    "linear-gauge",
    "input-form",
    "drawer-panel",
    "carousel",
    "steps-panel",
    "gantt-chart",
    "network-graph",
    "spreadsheet",
    "liquid-gauge",
    "nav-menu",
    "mini-tec-sld",
  ] as WidgetType[]
).map((type) => ({ type, label: type }));

export function emptyLayout(): DashboardLayout {
  return { columns: 12, rowHeight: 72, widgets: [] };
}

function normalizeLayoutWidget(widget: DashboardWidget): DashboardWidget {
  if (widget.type === "dashboard-link") {
    const legacy = widget as DashboardLinkWidget & { dashboardPath?: string; label?: string };
    return {
      ...widget,
      targetDashboardPath: legacy.targetDashboardPath ?? legacy.dashboardPath,
      buttonLabel: legacy.buttonLabel ?? legacy.label,
    };
  }
  return widget;
}

export function normalizeDashboardLayout(
  layout: Partial<DashboardLayout> | DashboardLayout
): DashboardLayout {
  return {
    columns: layout.columns ?? 12,
    rowHeight: layout.rowHeight ?? 72,
    theme: typeof layout.theme === "string" ? layout.theme : undefined,
    widgets: Array.isArray(layout.widgets) ? layout.widgets.map(normalizeLayoutWidget) : [],
  };
}

export function parseLayoutJson(
  raw: string | DashboardLayout | undefined | null
): DashboardLayout {
  if (!raw) {
    return emptyLayout();
  }
  if (typeof raw === "object") {
    return normalizeDashboardLayout(raw);
  }
  try {
    const parsed = JSON.parse(raw) as DashboardLayout;
    return normalizeDashboardLayout(parsed);
  } catch {
    return emptyLayout();
  }
}

export function resolveDashboardLayout(view: DashboardView | undefined | null): DashboardLayout {
  if (!view) {
    return emptyLayout();
  }
  if (typeof view.layoutJson === "string" && view.layoutJson.trim()) {
    const parsed = parseLayoutJson(view.layoutJson);
    if (parsed.widgets.length > 0) {
      return parsed;
    }
  }
  if (view.layout && typeof view.layout === "object") {
    return normalizeDashboardLayout(view.layout as DashboardLayout);
  }
  if (typeof view.layoutJson === "string") {
    return parseLayoutJson(view.layoutJson);
  }
  return emptyLayout();
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
