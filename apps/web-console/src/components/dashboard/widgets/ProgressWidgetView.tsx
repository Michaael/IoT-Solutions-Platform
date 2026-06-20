import type { ProgressWidget } from "../../../types/dashboard";
import { resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface ProgressWidgetViewProps {
  widget: ProgressWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ProgressWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: ProgressWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);

  const current = useBoundVariable(
    objectPath,
    widget.currentVariable,
    "value",
    refreshIntervalMs
  );
  const max = useBoundVariable(objectPath, widget.maxVariable, "value", refreshIntervalMs);

  const currentNum = Number(current.rawValue ?? 0);
  const maxNum = Number(max.rawValue ?? 0);
  const ratio = maxNum > 0 ? Math.min(100, (currentNum / maxNum) * 100) : 0;
  const decimals = widget.decimals ?? 0;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-progress"
      editable={editable}
      footer={objectPath ? `${ratio.toFixed(0)}%` : undefined}
    >
      {!objectPath ? (
        <p className="hint">Выберите наряд</p>
      ) : (
        <div style={styles.body}>
          <div className="dash-progress-head">
            <span className="dash-progress-value" style={styles.value}>
              {currentNum.toFixed(decimals)}
              {widget.unit ? ` ${widget.unit}` : ""}
            </span>
            <span className="hint" style={styles.unit}>
              / {maxNum.toFixed(decimals)}
              {widget.unit ? ` ${widget.unit}` : ""}
            </span>
          </div>
          <div className="dash-progress-track">
            <div className="dash-progress-fill" style={{ width: `${ratio}%` }} />
          </div>
        </div>
      )}
    </DashWidgetShell>
  );
}
