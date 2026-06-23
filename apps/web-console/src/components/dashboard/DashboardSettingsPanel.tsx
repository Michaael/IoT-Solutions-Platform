import type { DashboardLayout } from "../../types/dashboard";

interface DashboardSettingsPanelProps {
  layout: DashboardLayout;
  refreshIntervalMs: number;
  dashboardPath: string;
  onLayoutChange: (patch: Partial<DashboardLayout>) => void;
  onRefreshIntervalChange: (ms: number) => void;
}

const THEME_OPTIONS = [
  { id: "", label: "По умолчанию" },
  { id: "btop", label: "BTOP" },
];

export default function DashboardSettingsPanel({
  layout,
  refreshIntervalMs,
  onLayoutChange,
  onRefreshIntervalChange,
}: DashboardSettingsPanelProps) {
  return (
    <aside className="dashboard-sidebar">
      <header className="dashboard-sidebar-head">
        <h4>Настройки дашборда</h4>
      </header>
      <div className="form-grid compact">
        <label>
          Интервал опроса (мс)
          <input
            type="number"
            min={500}
            step={500}
            value={refreshIntervalMs}
            onChange={(e) => onRefreshIntervalChange(Number(e.target.value))}
          />
          <span className="hint">Сохраняется в переменной refreshIntervalMs объекта</span>
        </label>
        <label>
          Тема
          <select
            value={layout.theme ?? ""}
            onChange={(e) =>
              onLayoutChange({ theme: e.target.value || undefined })
            }
          >
            {THEME_OPTIONS.map((item) => (
              <option key={item.id || "default"} value={item.id}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Колонок сетки
          <input
            type="number"
            min={4}
            max={24}
            value={layout.columns}
            onChange={(e) => onLayoutChange({ columns: Number(e.target.value) })}
          />
        </label>
        <label>
          Высота строки (px)
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
