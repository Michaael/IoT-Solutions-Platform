import type { DashboardWidget } from "../../types/dashboard";

export type ContextWidgets = Record<string, { visible?: boolean }>;

export function resolveContextWidgetVisibility(
  widgetId: string,
  contextWidgets: ContextWidgets | undefined
): boolean | undefined {
  const entry = contextWidgets?.[widgetId];
  if (entry && typeof entry.visible === "boolean") {
    return entry.visible;
  }
  return undefined;
}

/** Layout default + optional server rule override from @dashboardContext.widgets. */
export function isWidgetVisibleAtRuntime(
  widget: DashboardWidget,
  contextWidgets?: ContextWidgets
): boolean {
  const fromContext = resolveContextWidgetVisibility(widget.id, contextWidgets);
  if (fromContext !== undefined) {
    return fromContext;
  }
  return widget.visible !== false;
}
