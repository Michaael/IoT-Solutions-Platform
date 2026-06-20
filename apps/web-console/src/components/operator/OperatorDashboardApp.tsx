import { useCallback, useEffect, useMemo, useState } from "react";
import type { AuthSession } from "../../auth/session";
import { useOperatorUi } from "../../hooks/useOperatorUi";
import { useObjectWebSocket } from "../../hooks/useObjectWebSocket";
import { resolveOperatorDashboard, type OperatorUi } from "../../types/operatorUi";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import OperatorSidebar from "./OperatorSidebar";

interface OperatorDashboardAppProps {
  appId: string;
  operatorId?: string;
  onSwitchAdmin?: () => void;
  session?: AuthSession;
  onLogout?: () => void;
}

function resolveDashboardFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("dashboard");
}

export default function OperatorDashboardApp({
  appId,
  operatorId = "operator",
  onSwitchAdmin,
  session,
  onLogout,
}: OperatorDashboardAppProps) {
  useObjectWebSocket();
  const uiQuery = useOperatorUi(appId);
  const [dashboardPath, setDashboardPath] = useState<string | null>(resolveDashboardFromUrl);
  const [selection, setSelection] = useState<Record<string, string>>({});

  const ui = uiQuery.data;
  const activePath = useMemo(
    () => (ui ? resolveOperatorDashboard(ui, dashboardPath) : ""),
    [ui, dashboardPath]
  );

  const navigateDashboard = useCallback(
    (path: string) => {
      setDashboardPath(path);
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", appId);
      url.searchParams.set("dashboard", path);
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
    },
    [appId]
  );

  useEffect(() => {
    if (!ui) {
      return;
    }
    const resolved = resolveOperatorDashboard(ui, dashboardPath);
    if (!resolved || resolved === dashboardPath) {
      return;
    }
    navigateDashboard(resolved);
  }, [ui, dashboardPath, navigateDashboard]);

  if (uiQuery.isLoading) {
    return <div className="operator-shell op-loading">Загрузка operator UI…</div>;
  }

  if (uiQuery.error || !ui || !activePath) {
    return (
      <div className="operator-shell op-loading">
        {uiQuery.error
          ? String(uiQuery.error)
          : `Operator UI для «${appId}» не найден. Настройте в дереве → root.platform.operator-apps или deploy bundle operatorUi.`}
      </div>
    );
  }

  return (
    <div className="operator-shell">
      <OperatorDashboardChrome
        ui={ui}
        activePath={activePath}
        appId={appId}
        session={session}
        onSwitchAdmin={onSwitchAdmin}
        onLogout={onLogout}
        onSelectDashboard={navigateDashboard}
      />
      <div className="operator-layout operator-dashboard-layout">
        <main className="operator-dashboard operator-dashboard-shell-host">
          <DashboardBuilder
            key={activePath}
            path={activePath}
            operatorMode
            selection={selection}
            onSelectionChange={setSelection}
            onNavigateDashboard={navigateDashboard}
          />
        </main>
        <aside className="operator-sidebar">
          <OperatorSidebar operatorId={operatorId} />
        </aside>
      </div>
    </div>
  );
}

function OperatorDashboardChrome({
  ui,
  activePath,
  appId,
  session,
  onSwitchAdmin,
  onLogout,
  onSelectDashboard,
}: {
  ui: OperatorUi;
  activePath: string;
  appId: string;
  session?: AuthSession;
  onSwitchAdmin?: () => void;
  onLogout?: () => void;
  onSelectDashboard: (path: string) => void;
}) {
  const activeTitle =
    ui.dashboards.find((item) => item.path === activePath)?.title ?? activePath;

  return (
    <>
      <header className="operator-topbar">
        <div>
          <strong>{ui.title}</strong>
          <span className="brand-sub">
            {appId} · {activeTitle}
            {session ? ` · ${session.displayName}` : ""}
          </span>
        </div>
        <div className="topbar-actions">
          {onLogout && (
            <button type="button" className="btn" onClick={onLogout}>
              Выйти
            </button>
          )}
          {onSwitchAdmin && (
            <button type="button" className="btn" onClick={onSwitchAdmin}>
              Админ-консоль
            </button>
          )}
        </div>
      </header>
      <nav className="op-nav">
        {ui.dashboards.map((dashboard) => (
          <button
            key={dashboard.path}
            type="button"
            className={`btn small ${dashboard.path === activePath ? "primary" : ""}`}
            onClick={() => onSelectDashboard(dashboard.path)}
          >
            {dashboard.title}
          </button>
        ))}
      </nav>
    </>
  );
}
