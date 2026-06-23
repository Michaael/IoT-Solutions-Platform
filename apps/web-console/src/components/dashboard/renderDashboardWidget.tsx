import type { DashboardWidget } from "../../types/dashboard";
import DashboardWidgetContent from "./DashboardWidgetContent";
import CarouselWidgetView from "./widgets/CarouselWidgetView";
import CompositeWidgetView from "./widgets/CompositeWidgetView";
import DrawerPanelWidgetView from "./widgets/DrawerPanelWidgetView";
import PanelWidgetView from "./widgets/PanelWidgetView";
import StepsPanelWidgetView from "./widgets/StepsPanelWidgetView";
import SubDashboardWidgetView from "./widgets/SubDashboardWidgetView";
import TabPanelWidgetView from "./widgets/TabPanelWidgetView";

const MAX_WIDGET_DEPTH = 3;

export interface RenderDashboardWidgetProps {
  widget: DashboardWidget;
  refreshIntervalMs: number;
  editable: boolean;
  depth?: number;
}

export default function renderDashboardWidget({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: RenderDashboardWidgetProps) {
  if (depth >= MAX_WIDGET_DEPTH) {
    return <div className="hint">Превышена глубина вложенности виджетов</div>;
  }

  switch (widget.type) {
    case "composite-widget":
      return (
        <CompositeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          depth={depth}
        />
      );
    case "panel":
      return (
        <PanelWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          depth={depth}
        />
      );
    case "tab-panel":
      return (
        <TabPanelWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          depth={depth}
        />
      );
    case "sub-dashboard":
      return (
        <SubDashboardWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          depth={depth}
        />
      );
    case "drawer-panel":
      return (
        <DrawerPanelWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          depth={depth}
        />
      );
    case "carousel":
      return (
        <CarouselWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          depth={depth}
        />
      );
    case "steps-panel":
      return (
        <StepsPanelWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          depth={depth}
        />
      );
    default:
      return (
        <DashboardWidgetContent
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
  }
}

export function renderWidgetList(
  widgets: DashboardWidget[],
  props: Omit<RenderDashboardWidgetProps, "widget">
) {
  return widgets.map((child, index) => (
    <div key={child.id ?? `child-${index}`} className="dash-composite-child">
      {renderDashboardWidget({ widget: child, ...props })}
    </div>
  ));
}
