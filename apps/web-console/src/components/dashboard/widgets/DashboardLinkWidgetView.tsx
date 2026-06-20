import type { DashboardLinkWidget } from "../../../types/dashboard";
import { useDashboardContext, triggerDashboardOpen } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface DashboardLinkWidgetViewProps {
  widget: DashboardLinkWidget;
  editable?: boolean;
}

export default function DashboardLinkWidgetView({ widget, editable }: DashboardLinkWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const actions = useDashboardContext();
  const targetPath = widget.targetDashboardPath?.trim();
  const canAct = Boolean(targetPath) && !editable;

  const handleClick = () => {
    if (!canAct) {
      return;
    }
    if (widget.confirmMessage && !window.confirm(widget.confirmMessage)) {
      return;
    }
    triggerDashboardOpen(
      widget.openMode ?? "navigate",
      targetPath,
      widget.modalTitle ?? widget.title,
      actions
    );
  };

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dashboard-link-widget"
      editable={editable}
    >
      <button
        type="button"
        className="btn primary dashboard-link-btn"
        style={styles.value}
        disabled={!canAct}
        onClick={handleClick}
      >
        {widget.buttonLabel ?? (widget.openMode === "modal" ? "Открыть" : "Перейти")}
      </button>
      {!targetPath && <p className="hint">Укажите targetDashboardPath</p>}
    </DashWidgetShell>
  );
}
