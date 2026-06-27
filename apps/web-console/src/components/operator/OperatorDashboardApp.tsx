import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { AuthSession } from "../../auth/session";
import { useOperatorUi } from "../../hooks/useOperatorUi";
import { useObjectWebSocket } from "../../hooks/useObjectWebSocket";
import { useOperatorAlarmBar } from "../../hooks/useOperatorAlarmBar";
import {
  resolveOperatorDashboard,
  resolveOperatorReport,
  type OperatorUi,
} from "../../types/operatorUi";
import type { DashboardSession } from "../dashboard/DashboardContext";
import { emptySession, mergeSession } from "../dashboard/DashboardContext";
import LocaleSwitcher from "../LocaleSwitcher";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import ReportBuilder from "../report/ReportBuilder";
import AlarmBarOverlay from "./AlarmBarOverlay";
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

function operatorDashboardSessionKey(appId: string, dashboardPath: string): string {
  return `ispf-operator-dashboard-session:${appId}:${dashboardPath}`;
}

function loadStoredDashboardSession(appId: string, dashboardPath: string): DashboardSession | null {
  try {
    const raw = sessionStorage.getItem(operatorDashboardSessionKey(appId, dashboardPath));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as DashboardSession;
    return {
      selection: parsed.selection ?? {},
      params: parsed.params ?? {},
    };
  } catch {
    return null;
  }
}

function saveStoredDashboardSession(
  appId: string,
  dashboardPath: string,
  session: DashboardSession
): void {
  try {
    sessionStorage.setItem(
      operatorDashboardSessionKey(appId, dashboardPath),
      JSON.stringify(session)
    );
  } catch {
    // ignore quota / private mode
  }
}

export default function OperatorDashboardApp({
  appId,
  operatorId = "operator",
  onSwitchAdmin,
  session,
  onLogout,
}: OperatorDashboardAppProps) {
  const { t } = useTranslation(["operator", "common"]);
  useObjectWebSocket();
  const uiQuery = useOperatorUi(appId);
  const [viewKind, setViewKind] = useState<OperatorViewKind>(resolveViewKindFromUrl);
  const [dashboardPath, setDashboardPath] = useState<string | null>(resolveDashboardFromUrl);
  const [reportPath, setReportPath] = useState<string | null>(resolveReportFromUrl);
  const [sessionsByDashboard, setSessionsByDashboard] = useState<
    Record<string, DashboardSession>
  >(() => {
    const path = resolveDashboardFromUrl();
    if (!path) {
      return {};
    }
    const stored = loadStoredDashboardSession(appId, path);
    const urlSession = resolveSessionFromUrl();
    return {
      [path]: stored ? mergeSession(stored, urlSession) : urlSession,
    };
  });

  const ui = uiQuery.data;
  const activeDashboardPath = useMemo(
    () => (ui ? resolveOperatorDashboard(ui, dashboardPath) : ""),
    [ui, dashboardPath]
  );
  const activeReportPath = useMemo(
    () => (ui ? resolveOperatorReport(ui, reportPath) : ""),
    [ui, reportPath]
  );

  const dashboardSession = useMemo(
    () => (activeDashboardPath ? (sessionsByDashboard[activeDashboardPath] ?? emptySession()) : emptySession()),
    [activeDashboardPath, sessionsByDashboard]
  );

  const handleDashboardSessionChange = useCallback(
    (next: DashboardSession) => {
      if (!activeDashboardPath) {
        return;
      }
      setSessionsByDashboard((prev) => ({ ...prev, [activeDashboardPath]: next }));
      saveStoredDashboardSession(appId, activeDashboardPath, next);
    },
    [activeDashboardPath, appId]
  );

  useEffect(() => {
    if (!activeDashboardPath || viewKind !== "dashboard") {
      return;
    }
    setSessionsByDashboard((prev) => {
      if (prev[activeDashboardPath]) {
        return prev;
      }
      const stored = loadStoredDashboardSession(appId, activeDashboardPath);
      return { ...prev, [activeDashboardPath]: stored ?? emptySession() };
    });
  }, [activeDashboardPath, appId, viewKind]);

  const navigateDashboard = useCallback(
    (path: string, options?: import("../dashboard/DashboardContext").OpenDashboardOptions) => {
      setSessionsByDashboard((prev) => {
        const current = prev[path] ?? loadStoredDashboardSession(appId, path) ?? emptySession();
        const next = options ? mergeSession(current, options) : current;
        saveStoredDashboardSession(appId, path, next);
        return { ...prev, [path]: next };
      });
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

  const alarmBar = useOperatorAlarmBar(ui?.alarmBar, {
    ui: ui ?? undefined,
    navigateDashboard,
    navigateReport,
  });

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
    return <div className="operator-shell op-loading">{t("operator:loadingUi")}</div>;
  }

  const hasDashboards = Boolean(ui?.dashboards?.length);
  const hasReports = Boolean(ui?.reports?.length);
  const activePath = viewKind === "report" ? activeReportPath : activeDashboardPath;

  if (uiQuery.error || !ui || (!hasDashboards && !hasReports)) {
    return (
      <div className="operator-shell op-loading">
        {uiQuery.error
          ? String(uiQuery.error)
          : t("operator:uiNotFound", { appId })}
      </div>
    );
  }

  if (!activePath) {
    return (
      <div className="operator-shell op-loading">
        {t("operator:uiNoDashboards", { appId })}
      </div>
    );
  }

  return (
    <div className={`operator-shell${alarmBar.hasActiveAlarm ? " operator-alarm-active" : ""}`}>
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
      {alarmBar.position === "top" && <AlarmBarOverlay {...alarmBar} />}
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
              onSessionChange={handleDashboardSessionChange}
              onNavigateDashboard={navigateDashboard}
            />
          )}
        </main>
        <aside className="operator-sidebar">
          <OperatorSidebar appId={appId} operatorId={operatorId} ui={ui} />
        </aside>
      </div>
      {alarmBar.position === "bottom" && <AlarmBarOverlay {...alarmBar} />}
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
  const { t } = useTranslation(["operator", "common"]);
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
          <LocaleSwitcher />
          {onLogout && (
            <button type="button" className="btn" onClick={onLogout}>
              {t("common:action.logout")}
            </button>
          )}
          {onSwitchAdmin && (
            <button type="button" className="btn" onClick={onSwitchAdmin}>
              {t("operator:launcher.switchAdmin")}
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
