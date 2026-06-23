import { useMemo, useState } from "react";
import type { DashboardWidget, DrawerPanelWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";
import { renderWidgetList } from "../renderDashboardWidget";

interface DrawerPanelWidgetViewProps {
  widget: DrawerPanelWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function DrawerPanelWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: DrawerPanelWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const [open, setOpen] = useState(false);
  const children = useMemo(
    () => parseJsonArray<DashboardWidget>(widget.childrenJson, []),
    [widget.childrenJson]
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-drawer"
      editable={editable}
    >
      <button type="button" className="btn" onClick={() => setOpen((v) => !v)}>
        {widget.drawerLabel ?? (open ? "Скрыть" : "Открыть")}
      </button>
      {open && (
        <div className="dash-drawer-body" style={styles.body}>
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
