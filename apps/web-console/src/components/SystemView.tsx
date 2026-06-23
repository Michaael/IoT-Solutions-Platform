import { useState } from "react";
import { useTranslation } from "react-i18next";
import SystemMetricsView from "./SystemMetricsView";
import EventJournalPanel from "./operator/EventJournalPanel";
import FunctionInvokeJournalPanel from "./runtime/FunctionInvokeJournalPanel";

type SystemTab = "metrics" | "events" | "functions";

export default function SystemView() {
  const { t } = useTranslation("system");
  const [tab, setTab] = useState<SystemTab>("metrics");

  const tabs: { id: SystemTab; labelKey: string }[] = [
    { id: "metrics", labelKey: "tab.metrics" },
    { id: "events", labelKey: "tab.events" },
    { id: "functions", labelKey: "tab.functions" },
  ];

  return (
    <main className="main system-view">
      <header className="system-metrics-header">
        <div>
          <h2>{t("title")}</h2>
          <p className="op-muted">{t("subtitle")}</p>
        </div>
      </header>

      <nav className="system-subnav">
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

      {tab === "metrics" && <SystemMetricsView embedded />}
      {tab === "events" && (
        <EventJournalPanel limit={100} showFilters objectPathFilter="" />
      )}
      {tab === "functions" && <FunctionInvokeJournalPanel limit={100} showFilters />}
    </main>
  );
}
