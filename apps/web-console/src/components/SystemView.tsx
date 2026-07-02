import { useTranslation } from "react-i18next";
import SystemMetricsView from "./SystemMetricsView";
import SystemSettingsView from "./SystemSettingsView";
import EventJournalPanel from "./operator/EventJournalPanel";
import FunctionInvokeJournalPanel from "./runtime/FunctionInvokeJournalPanel";
import BindingInvokeJournalPanel from "./runtime/BindingInvokeJournalPanel";
import PlatformBackupPanel from "./platform/PlatformBackupPanel";
import PlatformChangeSetsPanel from "./platform/PlatformChangeSetsPanel";
import PlatformSchedulesPanel from "./platform/PlatformSchedulesPanel";
import SemanticExportPanel from "./platform/SemanticExportPanel";
import { usePersistentTab } from "../hooks/usePersistentTab";

type SystemTab =
  | "metrics"
  | "settings"
  | "events"
  | "functions"
  | "bindings"
  | "changeSets"
  | "schedules"
  | "semanticExport"
  | "backup";

const SYSTEM_TABS: readonly SystemTab[] = [
  "metrics", "settings", "events", "functions", "bindings", "changeSets", "schedules", "semanticExport", "backup",
];

export default function SystemView() {
  const { t } = useTranslation("system");
  const [tab, setTab] = usePersistentTab<SystemTab>("system", "metrics", SYSTEM_TABS);

  const tabs: { id: SystemTab; labelKey: string }[] = [
    { id: "metrics", labelKey: "tab.metrics" },
    { id: "settings", labelKey: "tab.settings" },
    { id: "events", labelKey: "tab.events" },
    { id: "functions", labelKey: "tab.functions" },
    { id: "bindings", labelKey: "tab.bindings" },
    { id: "changeSets", labelKey: "tab.changeSets" },
    { id: "schedules", labelKey: "tab.schedules" },
    { id: "semanticExport", labelKey: "tab.semanticExport" },
    { id: "backup", labelKey: "tab.backup" },
  ];

  return (
    <main className="main system-view">
      <header className="system-metrics-header">
        <div>
          <h2>{t("title")}</h2>
          <p className="op-muted">{t("subtitle")}</p>
        </div>
      </header>

      <div className="tabs-scroll">
        <nav className="tabs" aria-label={t("tab.tabsAria")}>
          {tabs.map((item) => (
            <button
              key={item.id}
              type="button"
              className={tab === item.id ? "active" : ""}
              onClick={() => setTab(item.id)}
            >
              {t(item.labelKey)}
            </button>
          ))}
        </nav>
      </div>

      {tab === "metrics" && <SystemMetricsView embedded />}
      {tab === "settings" && <SystemSettingsView />}
      {tab === "events" && (
        <EventJournalPanel limit={100} showFilters objectPathFilter="" />
      )}
      {tab === "functions" && <FunctionInvokeJournalPanel limit={100} showFilters />}
      {tab === "bindings" && <BindingInvokeJournalPanel limit={100} showFilters />}
      {tab === "changeSets" && <PlatformChangeSetsPanel />}
      {tab === "schedules" && <PlatformSchedulesPanel />}
      {tab === "semanticExport" && <SemanticExportPanel />}
      {tab === "backup" && <PlatformBackupPanel />}
    </main>
  );
}
