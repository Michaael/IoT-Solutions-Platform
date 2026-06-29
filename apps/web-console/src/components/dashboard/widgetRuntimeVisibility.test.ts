import { describe, expect, it } from "vitest";
import { isWidgetVisibleAtRuntime } from "./widgetRuntimeVisibility";
import type { DashboardWidget } from "../../types/dashboard";

function widget(overrides: Partial<DashboardWidget> = {}): DashboardWidget {
  return {
    id: "panel-a",
    type: "metric",
    x: 0,
    y: 0,
    w: 4,
    h: 2,
    ...overrides,
  } as DashboardWidget;
}

describe("isWidgetVisibleAtRuntime", () => {
  it("uses layout default when context has no override", () => {
    expect(isWidgetVisibleAtRuntime(widget({ visible: true }), {})).toBe(true);
    expect(isWidgetVisibleAtRuntime(widget({ visible: false }), {})).toBe(false);
    expect(isWidgetVisibleAtRuntime(widget(), {})).toBe(true);
  });

  it("prefers context.widgets override", () => {
    expect(
      isWidgetVisibleAtRuntime(widget({ visible: true }), { "panel-a": { visible: false } }),
    ).toBe(false);
    expect(
      isWidgetVisibleAtRuntime(widget({ visible: false }), { "panel-a": { visible: true } }),
    ).toBe(true);
  });
});
