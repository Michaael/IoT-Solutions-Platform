import { useTranslation } from "react-i18next";
import type { DashboardLayout } from "../../types/dashboard";

interface DashboardSettingsPanelProps {
  layout: DashboardLayout;
  refreshIntervalMs: number;
  dashboardPath: string;
  onLayoutChange: (patch: Partial<DashboardLayout>) => void;
  onRefreshIntervalChange: (ms: number) => void;
}

const THEME_OPTIONS: Array<{ id: string; labelKey?: string; label?: string }> = [
  { id: "", labelKey: "settings.themeDefault" },
  { id: "btop", label: "BTOP" },
];

export default function DashboardSettingsPanel({
  layout,
  refreshIntervalMs,
  onLayoutChange,
  onRefreshIntervalChange,
}: DashboardSettingsPanelProps) {
  const { t } = useTranslation("dashboard");

  return (
    <aside className="dashboard-sidebar">
      <header className="dashboard-sidebar-head">
        <h4>{t("settings.title")}</h4>
      </header>
      <div className="form-grid compact">
        <label>
          {t("settings.refreshInterval")}
          <input
            type="number"
            min={500}
            step={500}
            value={refreshIntervalMs}
            onChange={(e) => onRefreshIntervalChange(Number(e.target.value))}
          />
          <span className="hint">{t("settings.refreshIntervalHint")}</span>
        </label>
        <label>
          {t("settings.theme")}
          <select
            value={layout.theme ?? ""}
            onChange={(e) =>
              onLayoutChange({ theme: e.target.value || undefined })
            }
          >
            {THEME_OPTIONS.map((item) => (
              <option key={item.id || "default"} value={item.id}>
                {item.labelKey ? t(item.labelKey) : item.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("settings.gridColumns")}
          <input
            type="number"
            min={4}
            max={24}
            value={layout.columns}
            onChange={(e) => onLayoutChange({ columns: Number(e.target.value) })}
          />
        </label>
        <label>
          {t("settings.rowHeight")}
          <input
            type="number"
            min={32}
            max={200}
            value={layout.rowHeight}
            onChange={(e) => onLayoutChange({ rowHeight: Number(e.target.value) })}
          />
        </label>
      </div>
    </aside>
  );
}
