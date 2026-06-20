import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ChartWidget } from "../../../types/dashboard";
import { useTrendSeries } from "../../../hooks/useTrendSeries";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { useWidgetStyles } from "../widgetStyles";
import WidgetDragHandle from "../WidgetDragHandle";

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
  const color = widget.color ?? "#2f81f7";
  const chartStyle = widget.chartStyle ?? "area";
  const decimals = widget.decimals ?? 1;
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const styles = useWidgetStyles(widget.stylesJson);

  const { points, stats, isLoading, isError, variable } = useTrendSeries(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs,
    maxPoints
  );

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
        </div>
        <div className="dash-chart-stats">
          {stats.latest != null ? (
            <span className="dash-chart-latest" style={styles.value}>
              {stats.latest.toFixed(decimals)}
              {unit ? ` ${unit}` : ""}
            </span>
          ) : (
            <span className="dash-chart-latest muted">—</span>
          )}
          {stats.min != null && stats.max != null && (
            <span className="dash-chart-range">
              min {stats.min.toFixed(decimals)} · max {stats.max.toFixed(decimals)}
            </span>
          )}
        </div>
      </div>
      <div className="dash-chart-body" style={styles.chart}>
        {!objectPath && widget.selectionKey ? (
          <div className="dash-chart-placeholder">Выберите устройство</div>
        ) : isLoading && points.length === 0 ? (
          <div className="dash-chart-placeholder">Сбор данных…</div>
        ) : isError ? (
          <div className="dash-chart-placeholder error">Ошибка привязки</div>
        ) : points.length < 2 ? (
          <div className="dash-chart-placeholder">Ожидание точек тренда…</div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            {chartStyle === "line" ? (
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
