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
  image: "static",
  "html-snippet": "static",
  report: "external",
  "dashboard-link": "external",
  "event-feed": "external",
  "work-queue": "external",
  "sub-dashboard": "external",
  "composite-widget": "composition",
  panel: "composition",
  "tab-panel": "composition",
  "drawer-panel": "composition",
  carousel: "composition",
  "steps-panel": "composition",
  "nav-menu": "composition",
  "mini-tec-sld": "composition",
};

export function widgetDataBinding(type: WidgetType): WidgetDataBinding {
  return BINDING_BY_TYPE[type];
}

export const DATA_BINDING_HINTS: Record<WidgetDataBinding, string> = {
  "object-variable":
    "Данные с объекта: objectPath (или selectionKey из сессии) + variableName + valueField.",
  "object-only":
    "Объект для вызова функций / формы: objectPath или selectionKey; переменные задаются в полях виджета.",
  "parent-catalog":
    "Список дочерних объектов каталога parentPath (API GET /objects?parent=…).",
  session:
    "Текст/путь из session.params (paramKey, pathKey) или статическое поле виджета.",
  static: "Статический контент (URL, HTML, текст) без привязки к переменным объекта.",
  external: "Внешний ресурс платформы (отчёт, дашборд, очередь, события) — пути в полях ниже.",
  composition: "Вложенные виджеты или пункты меню в JSON-полях.",
};

export const WIDGET_TYPE_HINTS: Partial<Record<WidgetType, string>> = {
  value: "Читает поле valueField переменной variableName на объекте.",
  toggle: "Переключает writable-переменную на объекте.",
  indicator: "Булево/строковое состояние переменной; цвета и подписи — trueLabel/falseLabel.",
  chart: "История variableName; historyRange и maxPoints управляют выборкой.",
  sparkline: "Компактный тренд variableName.",
  gauge: "Значение variableName; min/max — константы или minVariable/maxVariable.",
  progress: "Две переменные на одном объекте: currentVariable и maxVariable.",
  "status-badge": "Статус из variableName (по умолчанию status).",
  map: "Маркеры для каждого ребёнка parentPath; координаты из latVariable.",
  "object-table": "Таблица детей parentPath; колонки — columnsJson.",
  "event-feed": "События платформы; фильтр по objectPathPrefix и eventNamesJson.",
  spreadsheet: "RECORD_LIST в variableName; editable разрешает запись.",
  timer: "mode=countdown — таймер; elapsed — variableName как старт/длительность.",
};
