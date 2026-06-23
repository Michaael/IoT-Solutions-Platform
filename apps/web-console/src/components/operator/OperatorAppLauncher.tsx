import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchOperatorApps, type OperatorAppEntry } from "../../api/operatorApps";
import LocaleSwitcher from "../LocaleSwitcher";

interface OperatorAppLauncherProps {
  onOpenApp: (appId: string) => void;
  onSwitchAdmin?: () => void;
}

async function loadAppsIndex() {
  try {
    return await fetchOperatorApps();
  } catch {
    return [{ appId: "platform", title: "Platform HMI" } satisfies OperatorAppEntry];
  }
}

export default function OperatorAppLauncher({ onOpenApp, onSwitchAdmin }: OperatorAppLauncherProps) {
  const { t } = useTranslation(["operator", "common"]);
  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: loadAppsIndex,
  });

  return (
    <div className="operator-shell">
      <header className="operator-topbar">
        <div>
          <strong>{t("operator:launcher.title")}</strong>
          <span className="brand-sub">{t("operator:launcher.subtitle")}</span>
        </div>
        <div className="topbar-actions">
          <LocaleSwitcher />
          {onSwitchAdmin && (
            <button type="button" className="btn" onClick={onSwitchAdmin}>
              {t("operator:launcher.switchAdmin")}
            </button>
          )}
        </div>
      </header>
      <main className="op-launcher">
        {appsQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
        {appsQuery.error && <p className="op-alert op-alert-error">{String(appsQuery.error)}</p>}
        <div className="op-launcher-grid">
          {(appsQuery.data ?? []).map((app: OperatorAppEntry) => (
            <button
              key={app.appId}
              type="button"
              className="op-launcher-card"
              onClick={() => onOpenApp(app.appId)}
            >
              <strong>{app.title}</strong>
              <span className="op-muted">{app.appId}</span>
            </button>
          ))}
        </div>
        <p className="op-muted op-launcher-hint">{t("operator:launcher.hint")}</p>
      </main>
    </div>
  );
}
