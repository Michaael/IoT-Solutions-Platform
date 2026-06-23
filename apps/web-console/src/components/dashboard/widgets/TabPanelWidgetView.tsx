import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardWidget, TabPanelWidget, TabPanelTab } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { renderWidgetList } from "../renderDashboardWidget";

interface TabPanelWidgetViewProps {
  widget: TabPanelWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function TabPanelWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: TabPanelWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const tabs = useMemo(() => {
    try {
      const parsed = widget.tabsJson ? (JSON.parse(widget.tabsJson) as TabPanelTab[]) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [] as TabPanelTab[];
    }
  }, [widget.tabsJson]);

  const [activeId, setActiveId] = useState(tabs[0]?.id ?? "");
  const activeTab = tabs.find((tab) => tab.id === activeId) ?? tabs[0];

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-tab-panel"
      editable={editable}
    >
      {tabs.length === 0 ? (
        <p className="hint">{t("view.specifyTabsJson")}</p>
      ) : (
        <div className="dash-tab-panel-body" style={styles.body}>
          <nav className="dash-tab-bar">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                className={`btn small ${tab.id === activeTab?.id ? "primary" : ""}`}
                onClick={() => setActiveId(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
          <div className="dash-tab-content">
            {activeTab?.children?.length
              ? renderWidgetList(activeTab.children as DashboardWidget[], {
                  refreshIntervalMs,
                  editable: editable ?? false,
                  depth: depth + 1,
                })
              : <p className="hint">{t("view.emptyTab")}</p>}
          </div>
        </div>
      )}
    </DashWidgetShell>
  );
}
