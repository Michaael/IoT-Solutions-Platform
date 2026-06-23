import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { CompositeWidget, DashboardWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";
import { renderWidgetList } from "../renderDashboardWidget";

interface CompositeWidgetViewProps {
  widget: CompositeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function CompositeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: CompositeWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const children = useMemo(
    () => parseJsonArray<DashboardWidget>(widget.childrenJson, []),
    [widget.childrenJson]
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-composite"
      editable={editable}
    >
      {children.length === 0 ? (
        <p className="hint">{t("view.specifyChildrenJson")}</p>
      ) : (
        <div className="dash-composite-body" style={styles.body}>
          {renderWidgetList(children, {
            refreshIntervalMs,
            editable: editable ?? false,
            depth: depth + 1,
          })}
        </div>
      )}
    </DashWidgetShell>
  );
}
