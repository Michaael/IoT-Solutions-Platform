import { useMemo } from "react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { PieChartWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { useEditorDemoRows } from "../widgetDemoPreview";

const SLICE_COLORS = ["#2f81f7", "#3fb950", "#d29922", "#f85149", "#a371f7", "#39c5cf"];

interface PieChartWidgetViewProps {
  widget: PieChartWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function PieChartWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: PieChartWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { variable, isLoading, isError } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const labelField = widget.labelField ?? "name";
  const valueField = widget.valueField ?? "value";
  const decimals = widget.decimals ?? 1;

  const slices = useMemo(() => {
    const rows = variable?.value?.rows ?? [];
    return rows
      .map((row, index) => {
        const valueRaw = readFieldValue(row, valueField);
        const value = Number(valueRaw);
        if (!Number.isFinite(value)) {
          return null;
        }
        const labelRaw = readFieldValue(row, labelField);
        return {
          name: labelRaw != null ? String(labelRaw) : `Row ${index + 1}`,
          value,
        };
      })
      .filter((item): item is { name: string; value: number } => item != null);
  }, [labelField, valueField, variable?.value?.rows]);

  const { rows: displaySlices, isDemo } = useEditorDemoRows(
    widget,
    slices,
    editable
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-pie-chart"
      editable={editable}
      demo={isDemo}
    >
      {!objectPath && widget.selectionKey ? (
        <p className="hint">Выберите объект</p>
      ) : isLoading && !isDemo ? (
        <p className="hint">Загрузка…</p>
      ) : isError && !isDemo ? (
        <p className="hint">Ошибка привязки</p>
      ) : displaySlices.length === 0 ? (
        <p className="hint">Нет данных для диаграммы</p>
      ) : (
        <div className="dash-pie-chart-body" style={{ ...styles.body, ...styles.chart, height: "100%" }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={displaySlices}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                outerRadius="78%"
                label={({ name, percent }) =>
                  `${name}: ${((percent ?? 0) * 100).toFixed(decimals)}%`
                }
                isAnimationActive={false}
              >
                {displaySlices.map((_, index) => (
                  <Cell key={index} fill={SLICE_COLORS[index % SLICE_COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                formatter={(value) => Number(value).toFixed(decimals)}
                contentStyle={{
                  background: "#161b22",
                  border: "1px solid #30363d",
                  borderRadius: 8,
                }}
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      )}
    </DashWidgetShell>
  );
}
