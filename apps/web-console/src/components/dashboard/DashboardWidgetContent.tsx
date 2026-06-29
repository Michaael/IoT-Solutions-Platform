import { lazy, Suspense, type ReactNode } from "react";
import type { DashboardWidget } from "../../types/dashboard";
import { useTranslation } from "react-i18next";

const ValueWidgetView = lazy(() => import("./widgets/ValueWidgetView"));
const IndicatorWidgetView = lazy(() => import("./widgets/IndicatorWidgetView"));
const ToggleWidgetView = lazy(() =>
  import("./widgets/IndicatorWidgetView").then((module) => ({ default: module.ToggleWidgetView }))
);
const ChartWidgetView = lazy(() => import("./widgets/ChartWidgetView"));
const SparklineWidgetView = lazy(() => import("./widgets/SparklineWidgetView"));
const FunctionFormWidgetView = lazy(() => import("./widgets/FunctionFormWidgetView"));
const ProgressWidgetView = lazy(() => import("./widgets/ProgressWidgetView"));
const ObjectTableWidgetView = lazy(() => import("./widgets/ObjectTableWidgetView"));
const EventFeedWidgetView = lazy(() => import("./widgets/EventFeedWidgetView"));
const WorkQueueWidgetView = lazy(() => import("./widgets/WorkQueueWidgetView"));
const StatusBadgeWidgetView = lazy(() => import("./widgets/StatusBadgeWidgetView"));
const GaugeWidgetView = lazy(() => import("./widgets/GaugeWidgetView"));
const CardGridWidgetView = lazy(() => import("./widgets/CardGridWidgetView"));
const FunctionWidgetView = lazy(() => import("./widgets/FunctionWidgetView"));
const DashboardLinkWidgetView = lazy(() => import("./widgets/DashboardLinkWidgetView"));
const ReportWidgetView = lazy(() => import("./widgets/ReportWidgetView"));
const PieChartWidgetView = lazy(() => import("./widgets/PieChartWidgetView"));
const HistoryTableWidgetView = lazy(() => import("./widgets/HistoryTableWidgetView"));
const VariableEditorWidgetView = lazy(() => import("./widgets/VariableEditorWidgetView"));
const SvgWidgetView = lazy(() => import("./widgets/SvgWidgetView"));
const LabelWidgetView = lazy(() => import("./widgets/LabelWidgetView"));
const ImageWidgetView = lazy(() => import("./widgets/ImageWidgetView"));
const MiniTecSldWidgetView = lazy(() => import("./widgets/MiniTecSldWidgetView"));
const ScadaMimicWidgetView = lazy(() => import("./widgets/ScadaMimicWidgetView"));
const HtmlSnippetWidgetView = lazy(() => import("./widgets/HtmlSnippetWidgetView"));
const ObjectTreeWidgetView = lazy(() => import("./widgets/ObjectTreeWidgetView"));
const BreadcrumbsWidgetView = lazy(() => import("./widgets/BreadcrumbsWidgetView"));
const TimerWidgetView = lazy(() => import("./widgets/TimerWidgetView"));
const ContextListWidgetView = lazy(() => import("./widgets/ContextListWidgetView"));
const LinearGaugeWidgetView = lazy(() => import("./widgets/LinearGaugeWidgetView"));
const InputFormWidgetView = lazy(() => import("./widgets/InputFormWidgetView"));
const GanttChartWidgetView = lazy(() => import("./widgets/GanttChartWidgetView"));
const NetworkGraphWidgetView = lazy(() => import("./widgets/NetworkGraphWidgetView"));
const SpreadsheetWidgetView = lazy(() => import("./widgets/SpreadsheetWidgetView"));
const LiquidGaugeWidgetView = lazy(() => import("./widgets/LiquidGaugeWidgetView"));
const NavMenuWidgetView = lazy(() => import("./widgets/NavMenuWidgetView"));
const MapWidgetView = lazy(() => import("./widgets/MapWidgetView"));

interface DashboardWidgetContentProps {
  widget: DashboardWidget;
  refreshIntervalMs: number;
  editable: boolean;
}

function WidgetFallback() {
  return <div className="loading" />;
}

function LazyWidget({ children }: { children: ReactNode }) {
  return <Suspense fallback={<WidgetFallback />}>{children}</Suspense>;
}

/** Renders a dashboard widget body (excluding composite containers). */
export default function DashboardWidgetContent({
  widget,
  refreshIntervalMs,
  editable,
}: DashboardWidgetContentProps) {
  const { t } = useTranslation("widgets");
  switch (widget.type) {
    case "value":
      return (
        <LazyWidget>
          <ValueWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "indicator":
      return (
        <LazyWidget>
          <IndicatorWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "toggle":
      return (
        <LazyWidget>
          <ToggleWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "chart":
      return (
        <LazyWidget>
          <ChartWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "sparkline":
      return (
        <LazyWidget>
          <SparklineWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "function":
      return (
        <LazyWidget>
          <FunctionWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "function-form":
      return (
        <LazyWidget>
          <FunctionFormWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "progress":
      return (
        <LazyWidget>
          <ProgressWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "object-table":
      return (
        <LazyWidget>
          <ObjectTableWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "event-feed":
      return (
        <LazyWidget>
          <EventFeedWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "work-queue":
      return (
        <LazyWidget>
          <WorkQueueWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "status-badge":
      return (
        <LazyWidget>
          <StatusBadgeWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "gauge":
      return (
        <LazyWidget>
          <GaugeWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "card-grid":
      return (
        <LazyWidget>
          <CardGridWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "dashboard-link":
      return (
        <LazyWidget>
          <DashboardLinkWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "report":
      return (
        <LazyWidget>
          <ReportWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "pie-chart":
      return (
        <LazyWidget>
          <PieChartWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "history-table":
      return (
        <LazyWidget>
          <HistoryTableWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "variable-editor":
      return (
        <LazyWidget>
          <VariableEditorWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "svg-widget":
      return (
        <LazyWidget>
          <SvgWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "label":
      return (
        <LazyWidget>
          <LabelWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "image":
      return (
        <LazyWidget>
          <ImageWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "mini-tec-sld":
      return (
        <LazyWidget>
          <MiniTecSldWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "scada-mimic":
      return (
        <LazyWidget>
          <ScadaMimicWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "html-snippet":
      return (
        <LazyWidget>
          <HtmlSnippetWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "object-tree":
      return (
        <LazyWidget>
          <ObjectTreeWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "breadcrumbs":
      return (
        <LazyWidget>
          <BreadcrumbsWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "timer":
      return (
        <LazyWidget>
          <TimerWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "context-list":
      return (
        <LazyWidget>
          <ContextListWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "linear-gauge":
      return (
        <LazyWidget>
          <LinearGaugeWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "input-form":
      return (
        <LazyWidget>
          <InputFormWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "gantt-chart":
      return (
        <LazyWidget>
          <GanttChartWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "network-graph":
      return (
        <LazyWidget>
          <NetworkGraphWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "spreadsheet":
      return (
        <LazyWidget>
          <SpreadsheetWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "liquid-gauge":
      return (
        <LazyWidget>
          <LiquidGaugeWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "nav-menu":
      return (
        <LazyWidget>
          <NavMenuWidgetView widget={widget} editable={editable} />
        </LazyWidget>
      );
    case "map":
      return (
        <LazyWidget>
          <MapWidgetView
            widget={widget}
            refreshIntervalMs={refreshIntervalMs}
            editable={editable}
          />
        </LazyWidget>
      );
    case "composite-widget":
    case "panel":
    case "tab-panel":
    case "sub-dashboard":
    case "drawer-panel":
    case "carousel":
    case "steps-panel":
      return null;
    default:
      return <div className="dash-widget">{t("error.unknownWidget")}</div>;
  }
}
