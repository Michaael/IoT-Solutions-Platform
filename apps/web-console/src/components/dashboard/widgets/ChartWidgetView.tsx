import { useMemo } from "react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ChartWidget } from "../../../types/dashboard";
import { widgetHistoryRangeLabel } from "../../../types/dashboard";
import { useTrendSeries } from "../../../hooks/useTrendSeries";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { useWidgetStyles } from "../widgetStyles";
import WidgetDragHandle from "../WidgetDragHandle";
import WidgetHistoryControls from "../WidgetHistoryControls";
import { buildDemoTrendPoints, parseDemoPreview } from "../widgetDemoPreview";

interface ChartWidgetViewProps {
  widget: ChartWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ChartWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ChartWidgetViewProps) {
  const maxPoints = widget.maxPoints ?? 120;
  const historyRange = widget.historyRange ?? "live";
  const color = widget.color ?? "#2f81f7";
  const chartStyle = widget.chartStyle ?? "area";
  const chartType = widget.chartType ?? chartStyle;
  const decimals = widget.decimals ?? 1;
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const styles = useWidgetStyles(widget.stylesJson);

  const { points: livePoints, stats, isLoading, isError, variable, historyEnabled, aggregated, historyBucket } =
    useTrendSeries(
      objectPath,
      widget.variableName ?? "",
      widget.valueField,
      refreshIntervalMs,
      maxPoints,
      historyRange
    );

  const demoPoints = useMemo(
    () =>
      editable && livePoints.length < 2
        ? buildDemoTrendPoints(parseDemoPreview<Array<{ t?: number; v: number }>>(widget.demoPreviewJson))
        : [],
    [editable, livePoints.length, widget.demoPreviewJson]
  );
  const points = demoPoints.length >= 2 ? demoPoints : livePoints;
  const isDemo = demoPoints.length >= 2;
  const displayStats = isDemo
    ? {
        latest: points[points.length - 1]?.value ?? null,
        min: Math.min(...points.map((p) => p.value)),
        max: Math.max(...points.map((p) => p.value)),
      }
    : stats;

  const unitRow = variable?.value?.rows[0];
  const unit =
    widget.unit ??
    (widget.unitField && unitRow ? String(unitRow[widget.unitField] ?? "") : "");

  return (
    <div className="dash-widget dash-widget-chart" style={styles.card}>
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-chart-head" style={styles.body}>
        <div className="dash-widget-title" style={styles.title}>
          {widget.title}
          {isDemo ? <span className="dash-widget-demo-badge">пример</span> : null}
        </div>
        <div className="dash-chart-head-side">
          <div className="dash-chart-stats">
            {displayStats.latest != null ? (
              <span className="dash-chart-latest" style={styles.value}>
                {displayStats.latest.toFixed(decimals)}
                {unit ? ` ${unit}` : ""}
              </span>
            ) : (
              <span className="dash-chart-latest muted">—</span>
            )}
            {displayStats.min != null && displayStats.max != null && (
              <span className="dash-chart-range">
                min {displayStats.min.toFixed(decimals)} · max {displayStats.max.toFixed(decimals)}
                {aggregated && historyBucket ? ` · avg/${historyBucket}` : ""}
              </span>
            )}
          </div>
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
        </div>
      </div>
      <div className="dash-chart-body" style={styles.chart}>
        {!objectPath && widget.selectionKey ? (
          <div className="dash-chart-placeholder">Выберите устройство</div>
        ) : isLoading && points.length === 0 && !isDemo ? (
          <div className="dash-chart-placeholder">Сбор данных…</div>
        ) : isError && !isDemo ? (
          <div className="dash-chart-placeholder error">Ошибка привязки</div>
        ) : points.length < 2 ? (
          <div className="dash-chart-placeholder">
            {variable?.historyEnabled === false
              ? "История отключена для переменной"
              : "Ожидание точек тренда…"}
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            {chartType === "bar" ? (
              <BarChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
                <YAxis tick={{ fontSize: 10 }} width={42} />
                <Tooltip />
                <Bar dataKey="value" fill={color} isAnimationActive={false} />
              </BarChart>
            ) : chartStyle === "line" ? (
              <LineChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
                <YAxis
                  tick={{ fontSize: 10 }}
                  width={42}
                  domain={["auto", "auto"]}
                  tickFormatter={(v) => Number(v).toFixed(decimals)}
                />
                <Tooltip
                  formatter={(value) => {
                    const numeric = typeof value === "number" ? value : Number(value);
                    return [
                      `${numeric.toFixed(decimals)}${unit ? ` ${unit}` : ""}`,
                      widget.title,
                    ];
                  }}
                  labelFormatter={(label) => `Время: ${label}`}
                  contentStyle={{
                    background: "#161b22",
                    border: "1px solid #30363d",
                    borderRadius: 8,
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke={color}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            ) : (
              <AreaChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
                <YAxis
                  tick={{ fontSize: 10 }}
                  width={42}
                  domain={["auto", "auto"]}
                  tickFormatter={(v) => Number(v).toFixed(decimals)}
                />
                <Tooltip
                  formatter={(value) => {
                    const numeric = typeof value === "number" ? value : Number(value);
                    return [
                      `${numeric.toFixed(decimals)}${unit ? ` ${unit}` : ""}`,
                      widget.title,
                    ];
                  }}
                  labelFormatter={(label) => `Время: ${label}`}
                  contentStyle={{
                    background: "#161b22",
                    border: "1px solid #30363d",
                    borderRadius: 8,
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="value"
                  stroke={color}
                  fill={color}
                  fillOpacity={0.18}
                  strokeWidth={2}
                  isAnimationActive={false}
                />
              </AreaChart>
            )}
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
