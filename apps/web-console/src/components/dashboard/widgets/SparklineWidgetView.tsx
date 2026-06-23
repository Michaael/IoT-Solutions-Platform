import { Line, LineChart, ResponsiveContainer } from "recharts";
import { useMemo } from "react";
import type { SparklineWidget } from "../../../types/dashboard";
import { widgetHistoryRangeLabel } from "../../../types/dashboard";
import { useTrendSeries } from "../../../hooks/useTrendSeries";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import WidgetHistoryControls from "../WidgetHistoryControls";
import { buildDemoTrendPoints, parseDemoPreview } from "../widgetDemoPreview";

interface SparklineWidgetViewProps {
  widget: SparklineWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SparklineWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: SparklineWidgetViewProps) {
  const maxPoints = widget.maxPoints ?? 40;
  const historyRange = widget.historyRange ?? "live";
  const color = widget.color ?? "#3fb950";
  const decimals = widget.decimals ?? 1;
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const styles = useWidgetStyles(widget.stylesJson);

  const { points: livePoints, stats, isLoading, historyEnabled } = useTrendSeries(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs,
    maxPoints,
    historyRange
  );

  const demoPoints = useMemo(() => {
    if (!editable || livePoints.length >= 2) return [];
    const structured = parseDemoPreview<Array<{ t?: number; v: number }>>(widget.demoPreviewJson);
    if (structured?.length) return buildDemoTrendPoints(structured);
    const flat = parseDemoPreview<number[]>(widget.demoPreviewJson);
    if (flat?.length) return buildDemoTrendPoints(flat.map((v) => ({ v })));
    return [];
  }, [editable, livePoints.length, widget.demoPreviewJson]);
  const points = demoPoints.length >= 2 ? demoPoints : livePoints;
  const isDemo = demoPoints.length >= 2;
  const latest = isDemo ? points[points.length - 1]?.value : stats.latest;

  const historyControls = (
    <WidgetHistoryControls
      objectPath={objectPath}
      variableName={widget.variableName ?? ""}
      valueField={widget.valueField}
      title={widget.title}
      historyEnabled={historyEnabled}
      historyRangeLabel={
        historyRange !== "live" ? widgetHistoryRangeLabel(historyRange) : undefined
      }
    />
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-sparkline"
      editable={editable}
      demo={isDemo}
    >
      <div className="dash-sparkline-body" style={styles.body}>
        {!objectPath && widget.selectionKey ? (
          <p className="hint">Выберите устройство</p>
        ) : (
          <>
            <div className="dash-sparkline-head">
              <div className="dash-sparkline-value" style={styles.value}>
                {latest != null ? latest.toFixed(decimals) : isLoading ? "…" : "—"}
              </div>
              {historyControls}
            </div>
            <div className="dash-sparkline-chart" style={styles.chart}>
              {points.length >= 2 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={points}>
                    <Line
                      type="monotone"
                      dataKey="value"
                      stroke={color}
                      strokeWidth={2}
                      dot={false}
                      isAnimationActive={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <span className="dash-chart-placeholder">…</span>
              )}
            </div>
          </>
        )}
      </div>
    </DashWidgetShell>
  );
}
