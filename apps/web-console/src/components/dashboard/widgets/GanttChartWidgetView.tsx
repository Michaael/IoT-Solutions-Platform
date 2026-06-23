import { useMemo } from "react";
import type { GanttChartWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface GanttChartWidgetViewProps {
  widget: GanttChartWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function GanttChartWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: GanttChartWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { variable } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const rows = useMemo(() => {
    const list = variable?.value?.rows ?? [];
    const labelField = widget.labelField ?? "name";
    const startField = widget.startField ?? "start";
    const endField = widget.endField ?? "end";
    return list.map((row, index) => ({
      id: index,
      label: String(readFieldValue(row, labelField) ?? index),
      start: Number(readFieldValue(row, startField) ?? 0),
      end: Number(readFieldValue(row, endField) ?? 1),
    }));
  }, [variable, widget.labelField, widget.startField, widget.endField]);

  const min = rows.reduce((m, r) => Math.min(m, r.start), Infinity);
  const max = rows.reduce((m, r) => Math.max(m, r.end), -Infinity);
  const span = max - min || 1;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-gantt"
      editable={editable}
    >
      <div className="dash-gantt-body" style={styles.body}>
        {rows.map((row) => (
          <div key={row.id} className="dash-gantt-row">
            <span className="dash-gantt-label">{row.label}</span>
            <div className="dash-gantt-track">
              <div
                className="dash-gantt-bar"
                style={{
                  marginLeft: `${((row.start - min) / span) * 100}%`,
                  width: `${((row.end - row.start) / span) * 100}%`,
                }}
              />
            </div>
          </div>
        ))}
      </div>
    </DashWidgetShell>
  );
}
