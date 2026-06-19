import { useQuery } from "@tanstack/react-query";
import { useObjectWebSocket } from "../../hooks/useObjectWebSocket";
import { fetchDashboard } from "../../api";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import OperatorManifestView from "./OperatorManifestView";
import OperatorSidebar from "./OperatorSidebar";

const DEFAULT_DASHBOARD = "root.platform.dashboards.demo-sensor";

interface OperatorViewProps {
  dashboardPath?: string;
  operatorId?: string;
  appId?: string | null;
  onSwitchAdmin?: () => void;
}

export default function OperatorView({
  dashboardPath = DEFAULT_DASHBOARD,
  operatorId = "operator",
  appId = null,
  onSwitchAdmin,
}: OperatorViewProps) {
  if (appId) {
    return (
      <OperatorManifestView appId={appId} operatorId={operatorId} onSwitchAdmin={onSwitchAdmin} />
    );
  }

  return <OperatorDashboardView
    dashboardPath={dashboardPath}
    operatorId={operatorId}
    onSwitchAdmin={onSwitchAdmin}
  />;
}

function OperatorDashboardView({
  dashboardPath,
  operatorId,
  onSwitchAdmin,
}: {
  dashboardPath: string;
  operatorId: string;
  onSwitchAdmin?: () => void;
}) {
  useObjectWebSocket();

  const dashboard = useQuery({
    queryKey: ["dashboard", dashboardPath],
    queryFn: () => fetchDashboard(dashboardPath),
  });

  return (
    <div className="operator-shell">
      <header className="operator-topbar">
        <div>
          <strong>Оператор · HMI</strong>
          <span className="brand-sub">{dashboard.data?.title ?? dashboardPath}</span>
        </div>
        {onSwitchAdmin && (
          <button type="button" className="btn" onClick={onSwitchAdmin}>
            Админ-консоль
          </button>
        )}
      </header>
      <div className="operator-layout">
        <main className="operator-dashboard">
          <DashboardBuilder path={dashboardPath} operatorMode onClose={() => undefined} />
        </main>
        <aside className="operator-sidebar">
          <OperatorSidebar operatorId={operatorId} />
        </aside>
      </div>
    </div>
  );
}
