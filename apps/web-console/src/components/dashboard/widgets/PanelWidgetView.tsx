import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { PanelWidget, DashboardWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";
import { renderWidgetList } from "../renderDashboardWidget";

interface PanelWidgetViewProps {
  widget: PanelWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function PanelWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: PanelWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const [collapsed, setCollapsed] = useState(false);
  const children = useMemo(
    () => parseJsonArray<DashboardWidget>(widget.childrenJson, []),
    [widget.childrenJson]
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-panel"
      editable={editable}
      headerExtra={
        widget.collapsible ? (
          <button
            type="button"
            className="btn small"
            onClick={() => setCollapsed((v) => !v)}
          >
            {collapsed ? "▸" : "▾"}
          </button>
        ) : undefined
      }
    >
      {!collapsed && (
        <div className="dash-panel-body" style={styles.body}>
          {children.length === 0 ? (
            <p className="hint">{t("view.addChildrenJson")}</p>
          ) : (
            renderWidgetList(children, {
              refreshIntervalMs,
              editable: editable ?? false,
              depth: depth + 1,
            })
          )}
        </div>
      )}
    </DashWidgetShell>
  );
}
