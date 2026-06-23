import type { DashboardLinkWidget } from "../../../types/dashboard";
import { useTranslation } from "react-i18next";
import { parseJsonObject, parseSelectionJson } from "../dashboardUtils";
import { useDashboardContext, triggerDashboardOpen } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface DashboardLinkWidgetViewProps {
  widget: DashboardLinkWidget;
  editable?: boolean;
}

export default function DashboardLinkWidgetView({ widget, editable }: DashboardLinkWidgetViewProps) {
  const { t } = useTranslation("widgets");
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
      actions,
      {
        selection: parseSelectionJson(widget.contextSelectionJson),
        params: parseJsonObject(widget.contextParamsJson),
      }
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
        {widget.buttonLabel ?? (widget.openMode === "modal" ? t("view.open") : t("view.navigate"))}
      </button>
      {!targetPath && <p className="hint">{t("view.specifyTargetDashboard")}</p>}
    </DashWidgetShell>
  );
}
