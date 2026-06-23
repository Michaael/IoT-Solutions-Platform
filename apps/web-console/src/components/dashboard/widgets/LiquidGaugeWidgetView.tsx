import type { LiquidGaugeWidget } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface LiquidGaugeWidgetViewProps {
  widget: LiquidGaugeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function LiquidGaugeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: LiquidGaugeWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { rawValue } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );
  const minQuery = useBoundVariable(objectPath, widget.minVariable ?? "", widget.valueField, refreshIntervalMs);
  const maxQuery = useBoundVariable(objectPath, widget.maxVariable ?? "", widget.valueField, refreshIntervalMs);

  const value = Number(rawValue ?? 0);
  const min = widget.minValue ?? Number(minQuery.rawValue ?? 0);
  const max = widget.maxValue ?? Number(maxQuery.rawValue ?? 100);
  const pct = Math.min(100, Math.max(0, ((value - min) / (max - min || 1)) * 100));

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-liquid-gauge"
      editable={editable}
    >
      <div className="dash-liquid-gauge" style={styles.body}>
        <div className="dash-liquid-fill" style={{ height: `${pct}%`, ...styles.value }} />
        <span className="dash-liquid-label">{value.toFixed(widget.decimals ?? 0)}%</span>
      </div>
    </DashWidgetShell>
  );
}
