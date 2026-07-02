import { useTranslation } from "react-i18next";
import { useOperatorAppsRegistry } from "../../hooks/useOperatorAppsRegistry";
import type { OperatorUi } from "../../types/operatorUi";
import WorkQueuePanel from "./WorkQueuePanel";
import EventJournalPanel from "./EventJournalPanel";
import { usePersistentTab } from "../../hooks/usePersistentTab";

interface OperatorSidebarProps {
  appId?: string;
  operatorId?: string;
  ui?: OperatorUi;
}

type SidebarTab = "tasks" | "events";
const SIDEBAR_TABS: readonly SidebarTab[] = ["tasks", "events"];

export default function OperatorSidebar({ appId, operatorId = "operator", ui }: OperatorSidebarProps) {
  const { t } = useTranslation("operator");
  const resolvedAppId = appId ?? ui?.appId;
  const [tab, setTab] = usePersistentTab<SidebarTab>(
    `operator-sidebar:${resolvedAppId ?? "default"}`,
    "tasks",
    SIDEBAR_TABS
  );
  const { operatorApps } = useOperatorAppsRegistry(ui);

  return (
    <div className="operator-sidebar-inner">
      <div className="operator-sidebar-tabs">
        <button
          type="button"
          className={`btn small ${tab === "tasks" ? "primary" : ""}`}
          onClick={() => setTab("tasks")}
        >
          {t("sidebar.tasks")}
        </button>
        <button
          type="button"
          className={`btn small ${tab === "events" ? "primary" : ""}`}
          onClick={() => setTab("events")}
        >
          {t("sidebar.events")}
        </button>
      </div>
      <div className="operator-sidebar-content">
        {tab === "tasks" ? (
          <WorkQueuePanel
            operatorId={operatorId}
            appId={resolvedAppId}
            ui={ui}
            operatorApps={operatorApps}
          />
        ) : (
          <EventJournalPanel
            appId={resolvedAppId}
            ui={ui}
            operatorApps={operatorApps}
          />
        )}
      </div>
    </div>
  );
}
