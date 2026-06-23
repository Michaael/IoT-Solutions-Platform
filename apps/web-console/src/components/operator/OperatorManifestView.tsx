import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useObjectWebSocket } from "../../hooks/useObjectWebSocket";
import { useOperatorManifest } from "../../hooks/useOperatorManifest";
import { ANIMA_OPERATOR_WIRE_PROFILE } from "../../types/bff";
import { resolveOperatorScreen } from "../../types/operatorManifest";
import LocaleSwitcher from "../LocaleSwitcher";
import ManifestScreen from "./ManifestScreen";
import OperatorSidebar from "./OperatorSidebar";

import type { AuthSession } from "../../auth/session";

interface OperatorManifestViewProps {
  appId: string;
  operatorId?: string;
  onSwitchAdmin?: () => void;
  session?: AuthSession;
  onLogout?: () => void;
}

function resolveScreenFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("screen");
}

export default function OperatorManifestView({
  appId,
  operatorId = "operator",
  onSwitchAdmin,
  session,
  onLogout,
}: OperatorManifestViewProps) {
  const { t } = useTranslation(["operator", "common"]);
  useObjectWebSocket();
  const manifestQuery = useOperatorManifest(appId);
  const [screenId, setScreenId] = useState<string | null>(resolveScreenFromUrl);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const manifest = manifestQuery.data;
  const wireProfile = manifest?.wireProfile ?? ANIMA_OPERATOR_WIRE_PROFILE;

  const activeScreen = useMemo(
    () => (manifest ? resolveOperatorScreen(manifest, screenId) : null),
    [manifest, screenId]
  );

  const navigateScreen = useCallback(
    (nextId: string) => {
      setScreenId(nextId);
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", appId);
      url.searchParams.set("screen", nextId);
      window.history.replaceState({}, "", url.toString());
    },
    [appId]
  );

  if (manifestQuery.isLoading) {
    return <div className="operator-shell op-loading">{t("operator:loadingManifest")}</div>;
  }

  if (manifestQuery.error || !manifest || !activeScreen) {
    return (
      <div className="operator-shell op-loading">
        {manifestQuery.error
          ? String(manifestQuery.error)
          : t("operator:manifestNotFound", { appId })}
      </div>
    );
  }

  return (
    <div className="operator-shell">
      <header className="operator-topbar">
        <div>
          <strong>{manifest.title}</strong>
          <span className="brand-sub">
            {appId} · {wireProfile}
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
        {manifest.screens.map((screen) => (
          <button
            key={screen.id}
            type="button"
            className={`btn small ${activeScreen.id === screen.id ? "primary" : ""}`}
            onClick={() => navigateScreen(screen.id)}
          >
            {screen.title}
          </button>
        ))}
      </nav>
      {statusMessage && <div className="op-alert op-alert-info">{statusMessage}</div>}
      <div className="operator-layout">
        <main className="operator-dashboard op-manifest-main">
          <ManifestScreen screen={activeScreen} wireProfile={wireProfile} appId={appId} onStatus={setStatusMessage} />
        </main>
        <aside className="operator-sidebar">
          <OperatorSidebar operatorId={operatorId} />
        </aside>
      </div>
    </div>
  );
}
