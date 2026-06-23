import { useCallback, useEffect, useMemo, useState } from "react";
import type { AuthSession } from "../../auth/session";
import { useOperatorUi } from "../../hooks/useOperatorUi";
import { useObjectWebSocket } from "../../hooks/useObjectWebSocket";
import {
  resolveOperatorDashboard,
  resolveOperatorReport,
  type OperatorUi,
} from "../../types/operatorUi";
import type { DashboardSession } from "../dashboard/DashboardContext";
import { emptySession, mergeSession } from "../dashboard/DashboardContext";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import ReportBuilder from "../report/ReportBuilder";
import OperatorSidebar from "./OperatorSidebar";

interface OperatorDashboardAppProps {
  appId: string;
  operatorId?: string;
  onSwitchAdmin?: () => void;
  session?: AuthSession;
  onLogout?: () => void;
}

type OperatorViewKind = "dashboard" | "report";

function resolveDashboardFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("dashboard");
}

function resolveReportFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("report");
}

function resolveSessionFromUrl(): DashboardSession {
  const params = new URLSearchParams(window.location.search);
  const session = emptySession();
  for (const [key, value] of params.entries()) {
    if (key.startsWith("ctx.")) {
      session.selection[key.slice(4)] = value;
    }
  }
  const rawCtx = params.get("ctx");
  if (rawCtx) {
    try {
      const parsed = JSON.parse(rawCtx) as {
        selection?: Record<string, string>;
        params?: Record<string, unknown>;
      };
      if (parsed.selection) {
        session.selection = { ...session.selection, ...parsed.selection };
      }
      if (parsed.params) {
        session.params = { ...session.params, ...parsed.params };
      }
    } catch {
      // ignore malformed ctx
    }
  }
  return session;
}

function resolveViewKindFromUrl(): OperatorViewKind {
  return new URLSearchParams(window.location.search).get("report") ? "report" : "dashboard";
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
  const [viewKind, setViewKind] = useState<OperatorViewKind>(resolveViewKindFromUrl);
  const [dashboardPath, setDashboardPath] = useState<string | null>(resolveDashboardFromUrl);
  const [reportPath, setReportPath] = useState<string | null>(resolveReportFromUrl);
  const [dashboardSession, setDashboardSession] = useState<DashboardSession>(resolveSessionFromUrl);

  const ui = uiQuery.data;
  const activeDashboardPath = useMemo(
    () => (ui ? resolveOperatorDashboard(ui, dashboardPath) : ""),
    [ui, dashboardPath]
  );
  const activeReportPath = useMemo(
    () => (ui ? resolveOperatorReport(ui, reportPath) : ""),
    [ui, reportPath]
  );

  const navigateDashboard = useCallback(
    (path: string, options?: import("../dashboard/DashboardContext").OpenDashboardOptions) => {
      if (options) {
        setDashboardSession((current) => mergeSession(current, options));
      }
      setViewKind("dashboard");
      setDashboardPath(path);
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", appId);
      url.searchParams.set("dashboard", path);
      url.searchParams.delete("report");
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
    },
    [appId]
  );

  const navigateReport = useCallback(
    (path: string) => {
      setViewKind("report");
      setReportPath(path);
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", appId);
      url.searchParams.set("report", path);
      url.searchParams.delete("dashboard");
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
    },
    [appId]
  );

  useEffect(() => {
    if (!ui) {
      return;
    }
    if (viewKind === "report") {
      const resolved = resolveOperatorReport(ui, reportPath);
      if (resolved && resolved !== reportPath) {
        navigateReport(resolved);
      }
      return;
    }
    const resolved = resolveOperatorDashboard(ui, dashboardPath);
    if (resolved && resolved !== dashboardPath) {
      navigateDashboard(resolved);
    }
  }, [ui, viewKind, dashboardPath, reportPath, navigateDashboard, navigateReport]);

  if (uiQuery.isLoading) {
    return <div className="operator-shell op-loading">Загрузка operator UI…</div>;
  }

  const hasDashboards = Boolean(ui?.dashboards?.length);
  const hasReports = Boolean(ui?.reports?.length);
  const activePath = viewKind === "report" ? activeReportPath : activeDashboardPath;

  if (uiQuery.error || !ui || (!hasDashboards && !hasReports)) {
    return (
      <div className="operator-shell op-loading">
        {uiQuery.error
          ? String(uiQuery.error)
          : `Operator UI для «${appId}» не найден. Настройте в дереве → root.platform.operator-apps или deploy bundle operatorUi.`}
      </div>
    );
  }

  if (!activePath) {
    return (
      <div className="operator-shell op-loading">
        {`Operator UI для «${appId}» не содержит дашбордов или отчётов.`}
      </div>
    );
  }

  return (
    <div className="operator-shell">
      <OperatorDashboardChrome
        ui={ui}
        viewKind={viewKind}
        activePath={activePath}
        appId={appId}
        session={session}
        onSwitchAdmin={onSwitchAdmin}
        onLogout={onLogout}
        onSelectDashboard={navigateDashboard}
        onSelectReport={navigateReport}
      />
      <div className="operator-layout operator-dashboard-layout">
        <main className="operator-dashboard operator-dashboard-shell-host">
          {viewKind === "report" ? (
            <ReportBuilder key={activeReportPath} path={activeReportPath} operatorMode />
          ) : (
            <DashboardBuilder
              key={activeDashboardPath}
              path={activeDashboardPath}
              operatorMode
              session={dashboardSession}
              onSessionChange={setDashboardSession}
              onNavigateDashboard={navigateDashboard}
            />
          )}
        </main>
        <aside className="operator-sidebar">
          <OperatorSidebar operatorId={operatorId} ui={ui} />
        </aside>
      </div>
    </div>
  );
}

function OperatorDashboardChrome({
  ui,
  viewKind,
  activePath,
  appId,
  session,
  onSwitchAdmin,
  onLogout,
  onSelectDashboard,
  onSelectReport,
}: {
  ui: OperatorUi;
  viewKind: OperatorViewKind;
  activePath: string;
  appId: string;
  session?: AuthSession;
  onSwitchAdmin?: () => void;
  onLogout?: () => void;
  onSelectDashboard: (path: string) => void;
  onSelectReport: (path: string) => void;
}) {
  const activeTitle =
    viewKind === "report"
      ? (ui.reports?.find((item) => item.path === activePath)?.title ?? activePath)
      : (ui.dashboards.find((item) => item.path === activePath)?.title ?? activePath);

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
            className={`btn small ${viewKind === "dashboard" && dashboard.path === activePath ? "primary" : ""}`}
            onClick={() => onSelectDashboard(dashboard.path)}
          >
            {dashboard.title}
          </button>
        ))}
        {(ui.reports ?? []).map((report) => (
          <button
            key={report.path}
            type="button"
            className={`btn small ${viewKind === "report" && report.path === activePath ? "primary" : ""}`}
            onClick={() => onSelectReport(report.path)}
          >
            {report.title}
          </button>
        ))}
      </nav>
    </>
  );
}
