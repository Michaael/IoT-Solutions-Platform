import type { StatusBadgeWidget } from "../../../types/dashboard";
import {
  DISPATCH_STATUS_COLORS,
  formatDispatchStatus,
  resolveWidgetPath,
} from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface StatusBadgeWidgetViewProps {
  widget: StatusBadgeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function StatusBadgeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: StatusBadgeWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);
  const bound = useBoundVariable(
    objectPath,
    widget.variableName ?? "status",
    widget.valueField ?? "value",
    refreshIntervalMs
  );

  const status = bound.rawValue != null ? String(bound.rawValue) : "";
  const color = DISPATCH_STATUS_COLORS[status] ?? "#8b949e";

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-status-badge"
      editable={editable}
    >
      {!objectPath ? (
        <p className="hint">Выберите наряд</p>
      ) : (
        <span
          className="dash-status-badge"
          style={{ borderColor: color, color, ...styles.badge }}
        >
          {formatDispatchStatus(status)}
        </span>
      )}
    </DashWidgetShell>
  );
}
