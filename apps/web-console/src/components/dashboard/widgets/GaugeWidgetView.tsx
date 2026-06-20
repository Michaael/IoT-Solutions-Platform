import type { GaugeWidget } from "../../../types/dashboard";
import { resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface GaugeWidgetViewProps {
  widget: GaugeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function GaugeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: GaugeWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);

  const value = useBoundVariable(objectPath, widget.variableName ?? "", "value", refreshIntervalMs);
  const minVar = useBoundVariable(
    objectPath,
    widget.minVariable ?? "",
    "value",
    refreshIntervalMs
  );
  const maxVar = useBoundVariable(
    objectPath,
    widget.maxVariable ?? "",
    "value",
    refreshIntervalMs
  );

  const current = Number(value.rawValue ?? 0);
  const min = widget.minVariable
    ? Number(minVar.rawValue ?? widget.minValue ?? 0)
    : (widget.minValue ?? 0);
  const max = widget.maxVariable
    ? Number(maxVar.rawValue ?? widget.maxValue ?? 100)
    : (widget.maxValue ?? 100);
  const span = max - min;
  const ratio = span > 0 ? Math.min(100, Math.max(0, ((current - min) / span) * 100)) : 0;
  const decimals = widget.decimals ?? 1;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-gauge"
      editable={editable}
    >
      {!objectPath ? (
        <p className="hint">Укажите объект</p>
      ) : (
        <div style={styles.body}>
          <div className="dash-gauge-value" style={styles.value}>
            {current.toFixed(decimals)}
            {widget.unit ? ` ${widget.unit}` : ""}
          </div>
          <div className="dash-gauge-track">
            <div className="dash-gauge-fill" style={{ width: `${ratio}%` }} />
            <div className="dash-gauge-marker" style={{ left: `${ratio}%` }} />
          </div>
          <div className="dash-gauge-range hint" style={styles.meta}>
            <span>{min.toFixed(decimals)}</span>
            <span>{max.toFixed(decimals)}</span>
          </div>
        </div>
      )}
    </DashWidgetShell>
  );
}
