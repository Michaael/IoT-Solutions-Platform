import type { BindingRule } from "../../types";

export interface DashboardRuleTemplate {
  id: string;
  labelKey: string;
  rule: BindingRule;
}

export function dashboardRuleTemplates(widgetIds: string[]): DashboardRuleTemplate[] {
  const detailWidget = widgetIds.find((id) => id.includes("chart") || id.includes("net")) ?? widgetIds[0] ?? "detail-panel";

  return [
    {
      id: "ctx-on-select-mode",
      labelKey: "rules.templates.onSelectMode",
      rule: {
        id: "ctx-on-select-mode",
        name: "Detail mode on selection",
        enabled: true,
        order: 10,
        activators: {
          onStartup: false,
          onVariableChange: [],
          onEvent: null,
          periodicMs: 0,
          onContextChange: true,
        },
        condition: 'context.selection.device != ""',
        expression: '"detail"',
        target: { kind: "context", path: "params.mode" },
      },
    },
    {
      id: "ctx-show-widget",
      labelKey: "rules.templates.showWidget",
      rule: {
        id: "ctx-show-widget",
        name: "Show widget in detail mode",
        enabled: true,
        order: 20,
        activators: {
          onStartup: false,
          onVariableChange: [],
          onEvent: null,
          periodicMs: 0,
          onContextChange: true,
        },
        condition: 'context.params.mode == "detail"',
        expression: "true",
        target: { kind: "context", path: `widgets.${detailWidget}.visible` },
      },
    },
    {
      id: "ctx-hide-widget",
      labelKey: "rules.templates.hideWidget",
      rule: {
        id: "ctx-hide-widget",
        name: "Hide widget when idle",
        enabled: true,
        order: 30,
        activators: {
          onStartup: false,
          onVariableChange: [],
          onEvent: null,
          periodicMs: 0,
          onContextChange: true,
        },
        condition: 'context.params.mode != "detail"',
        expression: "false",
        target: { kind: "context", path: `widgets.${detailWidget}.visible` },
      },
    },
  ];
}
