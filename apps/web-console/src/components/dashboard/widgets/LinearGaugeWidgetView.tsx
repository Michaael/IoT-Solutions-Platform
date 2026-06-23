import type { LinearGaugeWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface LinearGaugeWidgetViewProps {
  widget: LinearGaugeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function LinearGaugeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: LinearGaugeWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { rawValue } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );
  const minQuery = useBoundVariable(
    objectPath,
    widget.minVariable ?? "",
    widget.valueField,
    refreshIntervalMs
  );
  const maxQuery = useBoundVariable(
    objectPath,
    widget.maxVariable ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const value = Number(rawValue ?? 0);
  const min =
    widget.minValue ??
    Number(readFieldValue(minQuery.rawValue as Record<string, unknown>, widget.valueField) ?? 0);
  const max =
    widget.maxValue ??
    Number(readFieldValue(maxQuery.rawValue as Record<string, unknown>, widget.valueField) ?? 100);
  const span = max - min || 1;
  const pct = Math.min(100, Math.max(0, ((value - min) / span) * 100));

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-linear-gauge"
      editable={editable}
    >
      <div className="dash-linear-gauge-track" style={styles.body}>
        <div className="dash-linear-gauge-fill" style={{ width: `${pct}%`, ...styles.value }} />
      </div>
      <p className="dash-linear-gauge-label">
        {value.toFixed(widget.decimals ?? 0)}
        {widget.unit ? ` ${widget.unit}` : ""}
      </p>
    </DashWidgetShell>
  );
}
