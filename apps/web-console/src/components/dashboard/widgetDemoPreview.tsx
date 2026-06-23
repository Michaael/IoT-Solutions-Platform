import type { DashboardWidget } from "../../types/dashboard";
import type { TrendPoint } from "../../hooks/useTrendSeries";

export function parseDemoPreview<T>(raw: string | undefined): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function useEditorDemoRows<T>(
  widget: Pick<DashboardWidget, "demoPreviewJson">,
  liveRows: T[],
  editable?: boolean
): { rows: T[]; isDemo: boolean } {
  const demo = parseDemoPreview<T[]>(widget.demoPreviewJson);
  if (editable && liveRows.length === 0 && Array.isArray(demo) && demo.length > 0) {
    return { rows: demo, isDemo: true };
  }
  return { rows: liveRows, isDemo: false };
}

export function buildDemoTrendPoints(
  raw: Array<{ t?: number; v: number }> | null
): TrendPoint[] {
  if (!raw?.length) return [];
  const now = Date.now();
  return raw.map((item, index) => {
    const t = item.t ?? now - (raw.length - index) * 60_000;
    return {
      t,
      time: new Date(t).toLocaleTimeString(),
      value: item.v,
    };
  });
}

export function WidgetDemoBadge() {
  return <span className="dash-widget-demo-badge">пример</span>;
}
