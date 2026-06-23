import { useMemo } from "react";
import type { NavMenuWidget } from "../../../types/dashboard";
import { triggerDashboardOpen, useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";

interface NavMenuItem {
  label: string;
  dashboardPath: string;
  openMode?: "navigate" | "modal";
}

interface NavMenuWidgetViewProps {
  widget: NavMenuWidget;
  editable?: boolean;
}

export default function NavMenuWidgetView({ widget, editable }: NavMenuWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const actions = useDashboardContext();
  const items = useMemo(
    () => parseJsonArray<NavMenuItem>(widget.itemsJson, []),
    [widget.itemsJson]
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-nav-menu"
      editable={editable}
    >
      <nav className="dash-nav-menu" style={styles.body}>
        {items.map((item) => (
          <button
            key={item.dashboardPath}
            type="button"
            className="btn small"
            disabled={editable}
            onClick={() =>
              triggerDashboardOpen(
                item.openMode ?? "navigate",
                item.dashboardPath,
                item.label,
                actions
              )
            }
          >
            {item.label}
          </button>
        ))}
      </nav>
    </DashWidgetShell>
  );
}
