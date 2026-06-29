import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { WidgetType } from "../../types/dashboard";
import { translateWidgetType } from "./widgetI18n";

interface WidgetPaletteProps {
  onAdd: (type: WidgetType) => void;
  layout?: "sidebar";
}

interface PaletteGroup {
  id: string;
  titleKey: string;
  types: WidgetType[];
}

const PALETTE_GROUPS: PaletteGroup[] = [
  {
    id: "metrics",
    titleKey: "palette.metrics",
    types: [
      "value",
      "indicator",
      "toggle",
      "progress",
      "status-badge",
      "gauge",
      "linear-gauge",
      "liquid-gauge",
    ],
  },
  {
    id: "charts",
    titleKey: "palette.charts",
    types: ["chart", "sparkline", "pie-chart", "gantt-chart", "network-graph"],
  },
  {
    id: "tables",
    titleKey: "palette.tables",
    types: [
      "object-table",
      "event-feed",
      "work-queue",
      "report",
      "history-table",
      "spreadsheet",
      "card-grid",
    ],
  },
  {
    id: "navigation",
    titleKey: "palette.navigation",
    types: ["dashboard-link", "sub-dashboard", "nav-menu", "breadcrumbs", "object-tree", "map"],
  },
  {
    id: "containers",
    titleKey: "palette.containers",
    types: ["panel", "tab-panel", "drawer-panel", "carousel", "steps-panel", "composite-widget"],
  },
  {
    id: "actions",
    titleKey: "palette.actions",
    types: ["function", "function-form", "input-form", "variable-editor", "timer", "context-list"],
  },
  {
    id: "scada",
    titleKey: "palette.scada",
    types: ["scada-mimic", "mini-tec-sld"],
  },
  {
    id: "content",
    titleKey: "palette.content",
    types: ["label", "image", "html-snippet", "svg-widget"],
  },
];

const WIDGET_ICONS: Record<WidgetType, ReactNode> = {
  value: (
    <>
      <path d="M4 5.5h8M4 8.5h5M4 11.5h6" />
      <path d="M11.5 10.5l1.5 1.5" />
    </>
  ),
  indicator: (
    <>
      <circle cx="8" cy="8" r="3.5" />
      <circle cx="8" cy="8" r="1.2" fill="currentColor" stroke="none" />
    </>
  ),
  toggle: (
    <>
      <rect x="3" y="5.5" width="10" height="5" rx="2.5" />
      <circle cx="10.5" cy="8" r="1.6" fill="currentColor" stroke="none" />
    </>
  ),
  chart: (
    <>
      <path d="M3 12.5V5.5M3 12.5h10" />
      <path d="M5.5 10l2-2.5 2 1.5 2.5-3.5" />
    </>
  ),
  sparkline: <path d="M3 10.5l2.5-3 2 2 2.5-4 2.5 2.5 2-3" />,
  function: <path d="M5 3.5h6v2.5H8.5L11 8.5 8.5 11H11v2.5H5V11h2.5L5 8.5 7.5 6H5V3.5z" />,
  "function-form": (
    <>
      <rect x="3.5" y="3" width="9" height="9" rx="1" />
      <path d="M5.5 6h5M5.5 8.5h3.5" />
      <path d="M10 10.5l1.5 1.5" />
    </>
  ),
  progress: (
    <>
      <rect x="3" y="6.5" width="10" height="3" rx="1.5" />
      <rect x="3" y="6.5" width="6" height="3" rx="1.5" fill="currentColor" stroke="none" />
    </>
  ),
  "object-table": (
    <>
      <rect x="3" y="3.5" width="10" height="9" rx="1" />
      <path d="M3 6.5h10M6 6.5v6M9.5 6.5v6" />
    </>
  ),
  "event-feed": (
    <>
      <path d="M4 4.5h8M4 8h8M4 11.5h5" />
      <circle cx="12.5" cy="4.5" r="0.8" fill="currentColor" stroke="none" />
      <circle cx="12.5" cy="8" r="0.8" fill="currentColor" stroke="none" />
    </>
  ),
  "work-queue": (
    <>
      <rect x="3.5" y="4" width="2" height="2" rx="0.4" />
      <rect x="3.5" y="8" width="2" height="2" rx="0.4" />
      <path d="M7 5h5.5M7 9h5.5" />
    </>
  ),
  "status-badge": (
    <>
      <rect x="4" y="5" width="8" height="6" rx="3" />
      <path d="M6 8h4" />
    </>
  ),
  gauge: (
    <>
      <path d="M3.5 11.5a4.5 4.5 0 0 1 9 0" />
      <path d="M8 11.5V7.5" />
      <circle cx="8" cy="11.5" r="0.8" fill="currentColor" stroke="none" />
    </>
  ),
  "card-grid": (
    <>
      <rect x="2.5" y="3" width="4.5" height="4" rx="0.6" />
      <rect x="8" y="3" width="4.5" height="4" rx="0.6" />
      <rect x="2.5" y="8.5" width="4.5" height="4" rx="0.6" />
      <rect x="8" y="8.5" width="4.5" height="4" rx="0.6" />
    </>
  ),
  "dashboard-link": (
    <>
      <path d="M5.5 10.5l5-5M7.5 5.5h3v3" />
      <rect x="3" y="8" width="5" height="4.5" rx="0.8" />
    </>
  ),
  report: (
    <>
      <path d="M4 2.5h8v11H4z" />
      <path d="M6 6h4M6 8.5h4M6 11h2.5" />
    </>
  ),
  "pie-chart": (
    <>
      <circle cx="8" cy="8.5" r="4" />
      <path d="M8 4.5v4l3 2" fill="currentColor" stroke="none" opacity="0.35" />
    </>
  ),
  "history-table": (
    <>
      <circle cx="5" cy="5" r="2.2" />
      <path d="M5 3.5v1.5l1 1" />
      <rect x="8.5" y="7" width="4.5" height="6" rx="0.6" />
      <path d="M9.5 9h2.5M9.5 11h2.5" />
    </>
  ),
  "variable-editor": (
    <>
      <path d="M10.5 3.5l1 1-6 6H4.5v-1l6-6z" />
      <path d="M9 5l1 1" />
    </>
  ),
  "svg-widget": (
    <>
      <circle cx="6" cy="7" r="2" />
      <path d="M10 11.5l2-2-2-2" />
      <path d="M3 12.5h6" />
    </>
  ),
  "composite-widget": (
    <>
      <path d="M8 2.5L13.5 5.5 8 8.5 2.5 5.5 8 2.5z" />
      <path d="M2.5 8.5L8 11.5l5.5-3" />
    </>
  ),
  "sub-dashboard": (
    <>
      <rect x="2.5" y="2.5" width="5" height="4" rx="0.6" />
      <rect x="8.5" y="2.5" width="5" height="4" rx="0.6" />
      <rect x="2.5" y="8" width="11" height="5" rx="0.6" />
    </>
  ),
  panel: <rect x="3" y="4" width="10" height="8" rx="1" />,
  "tab-panel": (
    <>
      <path d="M3 6.5h3.5l1-2H13v9.5H3V6.5z" />
      <path d="M7.5 6.5h3.5" />
    </>
  ),
  map: (
    <>
      <path d="M8 2.5l4 2v7l-4 2-4-2v-7l4-2z" />
      <circle cx="8" cy="7.5" r="1.2" fill="currentColor" stroke="none" />
    </>
  ),
  label: (
    <>
      <path d="M3 3.5h4.5l5.5 5.5-4.5 4.5L3 8V3.5z" />
      <circle cx="6" cy="6" r="0.8" fill="currentColor" stroke="none" />
    </>
  ),
  image: (
    <>
      <rect x="3" y="3.5" width="10" height="9" rx="1" />
      <circle cx="6" cy="7" r="1.2" />
      <path d="M3 11.5l3-2.5 2.5 2 2-1.5 3.5 3" />
    </>
  ),
  "html-snippet": (
    <>
      <path d="M4.5 5.5l-2 2.5 2 2.5M11.5 5.5l2 2.5-2 2.5" />
      <path d="M9 4.5l-2 7" />
    </>
  ),
  "object-tree": (
    <>
      <path d="M8 3v3M8 6l-3 3M8 6l3 3M5 9v3M11 9v3" />
      <circle cx="8" cy="3" r="1" fill="currentColor" stroke="none" />
    </>
  ),
  breadcrumbs: (
    <>
      <path d="M4 8h2l1.5-2L9 8h2l1.5-2L14 8" />
      <circle cx="4" cy="8" r="0.7" fill="currentColor" stroke="none" />
    </>
  ),
  timer: (
    <>
      <circle cx="8" cy="8.5" r="4.5" />
      <path d="M8 5.5v3.5l2.5 1.5" />
    </>
  ),
  "context-list": (
    <>
      <path d="M3.5 4.5h7M3.5 8h9M3.5 11.5h6" />
      <circle cx="13" cy="4.5" r="0.8" fill="currentColor" stroke="none" />
    </>
  ),
  "linear-gauge": (
    <>
      <rect x="2.5" y="7" width="11" height="2" rx="1" />
      <rect x="2.5" y="7" width="7" height="2" rx="1" fill="currentColor" stroke="none" />
      <path d="M9.5 5.5v6" />
    </>
  ),
  "input-form": (
    <>
      <rect x="3" y="4" width="10" height="2.2" rx="0.5" />
      <rect x="3" y="7.5" width="10" height="2.2" rx="0.5" />
      <rect x="3" y="11" width="5" height="2.2" rx="0.5" />
    </>
  ),
  "drawer-panel": (
    <>
      <rect x="2.5" y="3.5" width="11" height="9" rx="0.8" />
      <rect x="8.5" y="3.5" width="5" height="9" rx="0.8" fill="currentColor" stroke="none" opacity="0.25" />
      <path d="M10 7h2M10 9.5h2" />
    </>
  ),
  carousel: (
    <>
      <rect x="2.5" y="5" width="7" height="6" rx="0.8" />
      <rect x="9" y="5.5" width="4.5" height="5" rx="0.8" opacity="0.45" />
      <path d="M6.5 8h-1M6.5 8l-1.5-1.5M6.5 8l-1.5 1.5" />
    </>
  ),
  "steps-panel": (
    <>
      <circle cx="4.5" cy="8" r="1.5" fill="currentColor" stroke="none" />
      <circle cx="8" cy="8" r="1.5" />
      <circle cx="11.5" cy="8" r="1.5" />
      <path d="M6 8h1.5M9.5 8h1" />
    </>
  ),
  "gantt-chart": (
    <>
      <path d="M3 4.5h10M3 8h10M3 11.5h10" />
      <rect x="4" y="3.5" width="4" height="2" rx="0.4" fill="currentColor" stroke="none" />
      <rect x="6" y="7" width="5" height="2" rx="0.4" fill="currentColor" stroke="none" />
    </>
  ),
  "network-graph": (
    <>
      <circle cx="5" cy="5.5" r="1.5" />
      <circle cx="11" cy="4" r="1.5" />
      <circle cx="10" cy="11" r="1.5" />
      <path d="M6.2 6.5l3.5-1.8M6.5 6.8l2.8 3.5" />
    </>
  ),
  spreadsheet: (
    <>
      <rect x="3" y="3" width="10" height="10" rx="0.8" />
      <path d="M3 6.5h10M3 9.5h10M7 3v10" />
    </>
  ),
  "liquid-gauge": (
    <>
      <circle cx="8" cy="9" r="4" />
      <path d="M5 9.5c.8-1.2 1.8-1.8 3-1.8s2.2.6 3 1.8" fill="currentColor" stroke="none" opacity="0.5" />
    </>
  ),
  "nav-menu": (
    <>
      <path d="M3.5 5h9M3.5 8h9M3.5 11h9" />
    </>
  ),
  "mini-tec-sld": (
    <>
      <rect x="2" y="3" width="12" height="10" rx="1" />
      <path d="M4 7h8M4 9h5" />
    </>
  ),
  "scada-mimic": (
    <>
      <rect x="2" y="2" width="12" height="12" rx="1" />
      <circle cx="6" cy="7" r="2" />
      <path d="M10 5v6M8 8h4" />
    </>
  ),
};

function WidgetTypeIcon({ type }: { type: WidgetType }) {
  return (
    <svg
      className="widget-palette-icon"
      viewBox="0 0 16 16"
      width="16"
      height="16"
      aria-hidden
      fill="none"
      stroke="currentColor"
      strokeWidth="1.2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {WIDGET_ICONS[type]}
    </svg>
  );
}

export default function WidgetPalette({ onAdd, layout }: WidgetPaletteProps) {
  const { t } = useTranslation(["dashboard", "widgets"]);

  return (
    <div
      className={`dashboard-widget-palette${layout === "sidebar" ? " dashboard-widget-palette--sidebar" : ""}`}
    >
      {layout === "sidebar" && (
        <div className="dashboard-palette-sidebar-head">
          <h4>{t("palette.title")}</h4>
        </div>
      )}
      <div className="dashboard-widget-palette-hint" role="note">
        <svg
          className="dashboard-widget-palette-hint-icon"
          viewBox="0 0 16 16"
          width="14"
          height="14"
          aria-hidden
          fill="none"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="8" cy="8" r="5.5" />
          <path d="M8 7v4" />
          <circle cx="8" cy="5" r="0.6" fill="currentColor" stroke="none" />
        </svg>
        <span>{t("palette.dragHint")}</span>
      </div>

      <div className="dashboard-widget-palette-groups">
        {PALETTE_GROUPS.map((group) => (
          <section key={group.id} className="dashboard-widget-palette-group">
            <h4 className="dashboard-widget-palette-group-title">{t(group.titleKey)}</h4>
            <div className="dashboard-widget-palette-items">
              {group.types.map((type) => (
                <button
                  key={type}
                  type="button"
                  className="dashboard-widget-palette-item"
                  title={translateWidgetType(t, type)}
                  onClick={() => onAdd(type)}
                >
                  <WidgetTypeIcon type={type} />
                  <span className="dashboard-widget-palette-item-label">{translateWidgetType(t, type)}</span>
                </button>
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
