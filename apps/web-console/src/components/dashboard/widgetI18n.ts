import type { WidgetType } from "../../types/dashboard";

export function widgetTypeI18nKey(type: WidgetType): string {
  return `type.${type.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())}`;
}
