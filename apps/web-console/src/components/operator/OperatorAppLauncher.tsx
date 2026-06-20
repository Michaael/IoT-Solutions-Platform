import { useQuery } from "@tanstack/react-query";
import { fetchOperatorApps, type OperatorAppEntry } from "../../api/operatorApps";

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
  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: loadAppsIndex,
  });

  return (
    <div className="operator-shell">
      <header className="operator-topbar">
        <div>
          <strong>Оператор · приложения</strong>
          <span className="brand-sub">дашборды из дерева объектов (сервер: Operator Apps)</span>
        </div>
        {onSwitchAdmin && (
          <button type="button" className="btn" onClick={onSwitchAdmin}>
            Админ-консоль
          </button>
        )}
      </header>
      <main className="op-launcher">
        {appsQuery.isLoading && <p className="op-muted">Загрузка…</p>}
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
        <p className="op-muted op-launcher-hint">
          Приложение — набор дашбордов <code>DASHBOARD</code>. Настройка: дерево →{" "}
          <code>root.platform.operator-apps</code>.
        </p>
      </main>
    </div>
  );
}
