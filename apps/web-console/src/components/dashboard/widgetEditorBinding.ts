import type { WidgetType } from "../../types/dashboard";

/** How the widget binds to platform data (shown in properties panel). */
export type WidgetDataBinding =
  | "object-variable"
  | "object-only"
  | "parent-catalog"
  | "session"
  | "static"
  | "external"
  | "composition";

const BINDING_BY_TYPE: Record<WidgetType, WidgetDataBinding> = {
  value: "object-variable",
  toggle: "object-variable",
  indicator: "object-variable",
  chart: "object-variable",
  sparkline: "object-variable",
  gauge: "object-variable",
  progress: "object-variable",
  "status-badge": "object-variable",
  "history-table": "object-variable",
  "pie-chart": "object-variable",
  "linear-gauge": "object-variable",
  "liquid-gauge": "object-variable",
  timer: "object-variable",
  spreadsheet: "object-variable",
  "gantt-chart": "object-variable",
  "network-graph": "object-variable",
  function: "object-only",
  "function-form": "object-only",
  "variable-editor": "object-only",
  "input-form": "object-only",
  "svg-widget": "object-variable",
  "object-table": "parent-catalog",
  "card-grid": "parent-catalog",
  map: "parent-catalog",
  "object-tree": "parent-catalog",
  label: "session",
  breadcrumbs: "session",
  "context-list": "session",
  "dashboard-link": "external",
  "sub-dashboard": "external",
  report: "external",
  "event-feed": "external",
  "work-queue": "external",
  image: "static",
  "html-snippet": "static",
  panel: "composition",
  "tab-panel": "composition",
  "drawer-panel": "composition",
  carousel: "composition",
  "steps-panel": "composition",
  "composite-widget": "composition",
  "nav-menu": "composition",
  "mini-tec-sld": "composition",
  "scada-mimic": "static",
};

export function widgetDataBinding(type: WidgetType): WidgetDataBinding {
  return BINDING_BY_TYPE[type];
}

export const DATA_BINDING_HINT_KEYS: Record<WidgetDataBinding, string> = {
  "object-variable": "editor.bindingHint.objectVariable",
  "object-only": "editor.bindingHint.objectOnly",
  "parent-catalog": "editor.bindingHint.parentCatalog",
  session: "editor.bindingHint.session",
  static: "editor.bindingHint.static",
  external: "editor.bindingHint.external",
  composition: "editor.bindingHint.composition",
};

export const WIDGET_TYPE_HINT_KEYS: Partial<Record<WidgetType, string>> = {
  value: "editor.typeHint.value",
  toggle: "editor.typeHint.toggle",
  indicator: "editor.typeHint.indicator",
  chart: "editor.typeHint.chart",
  sparkline: "editor.typeHint.sparkline",
  gauge: "editor.typeHint.gauge",
  progress: "editor.typeHint.progress",
  "status-badge": "editor.typeHint.statusBadge",
  map: "editor.typeHint.map",
  "object-table": "editor.typeHint.objectTable",
  "event-feed": "editor.typeHint.eventFeed",
  spreadsheet: "editor.typeHint.spreadsheet",
  timer: "editor.typeHint.timer",
};
