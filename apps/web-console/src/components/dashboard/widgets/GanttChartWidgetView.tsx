import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { GanttChartWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { useEditorDemoRows } from "../widgetDemoPreview";

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
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { variable } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const liveRows = useMemo(() => {
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

  const { rows: demoGantt, isDemo: isDemoRaw } = useEditorDemoRows(
    widget,
    [] as Array<{ label: string; start: number; end: number }>,
    editable
  );
  const demoRows =
    isDemoRaw && demoGantt.length > 0
      ? demoGantt.map((row, index) => ({
          id: index,
          label: row.label,
          start: row.start,
          end: row.end,
        }))
      : [];
  const rows = demoRows.length > 0 ? demoRows : liveRows;
  const isDemo = demoRows.length > 0;

  const min = rows.reduce((m, r) => Math.min(m, r.start), Infinity);
  const max = rows.reduce((m, r) => Math.max(m, r.end), -Infinity);
  const span = max - min || 1;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-gantt"
      editable={editable}
      demo={isDemo}
    >
      <div className="dash-gantt-body" style={styles.body}>
        {rows.length === 0 ? (
          <p className="hint">{t("view.noGanttRows")}</p>
        ) : (
          rows.map((row) => (
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
        ))
        )}
      </div>
    </DashWidgetShell>
  );
}
