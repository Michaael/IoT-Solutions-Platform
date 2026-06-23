import type { DashboardWidget } from "../../types/dashboard";
import { useTranslation } from "react-i18next";
import ValueWidgetView from "./widgets/ValueWidgetView";
import IndicatorWidgetView, { ToggleWidgetView } from "./widgets/IndicatorWidgetView";
import ChartWidgetView from "./widgets/ChartWidgetView";
import SparklineWidgetView from "./widgets/SparklineWidgetView";
import FunctionFormWidgetView from "./widgets/FunctionFormWidgetView";
import ProgressWidgetView from "./widgets/ProgressWidgetView";
import ObjectTableWidgetView from "./widgets/ObjectTableWidgetView";
import EventFeedWidgetView from "./widgets/EventFeedWidgetView";
import WorkQueueWidgetView from "./widgets/WorkQueueWidgetView";
import StatusBadgeWidgetView from "./widgets/StatusBadgeWidgetView";
import GaugeWidgetView from "./widgets/GaugeWidgetView";
import CardGridWidgetView from "./widgets/CardGridWidgetView";
import FunctionWidgetView from "./widgets/FunctionWidgetView";
import DashboardLinkWidgetView from "./widgets/DashboardLinkWidgetView";
import ReportWidgetView from "./widgets/ReportWidgetView";
import PieChartWidgetView from "./widgets/PieChartWidgetView";
import HistoryTableWidgetView from "./widgets/HistoryTableWidgetView";
import VariableEditorWidgetView from "./widgets/VariableEditorWidgetView";
import SvgWidgetView from "./widgets/SvgWidgetView";
import LabelWidgetView from "./widgets/LabelWidgetView";
import ImageWidgetView from "./widgets/ImageWidgetView";
import MiniTecSldWidgetView from "./widgets/MiniTecSldWidgetView";
import HtmlSnippetWidgetView from "./widgets/HtmlSnippetWidgetView";
import ObjectTreeWidgetView from "./widgets/ObjectTreeWidgetView";
import BreadcrumbsWidgetView from "./widgets/BreadcrumbsWidgetView";
import TimerWidgetView from "./widgets/TimerWidgetView";
import ContextListWidgetView from "./widgets/ContextListWidgetView";
import LinearGaugeWidgetView from "./widgets/LinearGaugeWidgetView";
import InputFormWidgetView from "./widgets/InputFormWidgetView";
import GanttChartWidgetView from "./widgets/GanttChartWidgetView";
import NetworkGraphWidgetView from "./widgets/NetworkGraphWidgetView";
import SpreadsheetWidgetView from "./widgets/SpreadsheetWidgetView";
import LiquidGaugeWidgetView from "./widgets/LiquidGaugeWidgetView";
import NavMenuWidgetView from "./widgets/NavMenuWidgetView";
import MapWidgetView from "./widgets/MapWidgetView";

interface DashboardWidgetContentProps {
  widget: DashboardWidget;
  refreshIntervalMs: number;
  editable: boolean;
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
        <ValueWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "indicator":
      return (
        <IndicatorWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "toggle":
      return (
        <ToggleWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "chart":
      return (
        <ChartWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "sparkline":
      return (
        <SparklineWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "function":
      return <FunctionWidgetView widget={widget} editable={editable} />;
    case "function-form":
      return <FunctionFormWidgetView widget={widget} editable={editable} />;
    case "progress":
      return (
        <ProgressWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "object-table":
      return (
        <ObjectTableWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "event-feed":
      return (
        <EventFeedWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "work-queue":
      return <WorkQueueWidgetView widget={widget} editable={editable} />;
    case "status-badge":
      return (
        <StatusBadgeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "gauge":
      return (
        <GaugeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "card-grid":
      return (
        <CardGridWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "dashboard-link":
      return <DashboardLinkWidgetView widget={widget} editable={editable} />;
    case "report":
      return (
        <ReportWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "pie-chart":
      return (
        <PieChartWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "history-table":
      return (
        <HistoryTableWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "variable-editor":
      return (
        <VariableEditorWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "svg-widget":
      return (
        <SvgWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "label":
      return <LabelWidgetView widget={widget} editable={editable} />;
    case "image":
      return <ImageWidgetView widget={widget} editable={editable} />;
    case "mini-tec-sld":
      return (
        <MiniTecSldWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "html-snippet":
      return <HtmlSnippetWidgetView widget={widget} editable={editable} />;
    case "object-tree":
      return (
        <ObjectTreeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "breadcrumbs":
      return <BreadcrumbsWidgetView widget={widget} editable={editable} />;
    case "timer":
      return (
        <TimerWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "context-list":
      return <ContextListWidgetView widget={widget} editable={editable} />;
    case "linear-gauge":
      return (
        <LinearGaugeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "input-form":
      return (
        <InputFormWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "gantt-chart":
      return (
        <GanttChartWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "network-graph":
      return (
        <NetworkGraphWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "spreadsheet":
      return (
        <SpreadsheetWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "liquid-gauge":
      return (
        <LiquidGaugeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "nav-menu":
      return <NavMenuWidgetView widget={widget} editable={editable} />;
    case "map":
      return (
        <MapWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
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
