import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { DashboardLayout, DashboardWidget } from "../../types/dashboard";
import { mergeWidgetLayout } from "../../types/dashboard";
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

const GRID_MARGIN: [number, number] = [12, 12];
const DRAG_CANCEL_SELECTOR =
  "button, input, select, textarea, a, .dash-chart-body, .dash-sparkline-body, .dashboard-grid-resize-handle, .dash-object-table, .dash-event-feed-list, .dash-work-queue-list, .function-form-fields, .dashboard-link-btn, .dash-object-card";

interface DashboardGridProps {
  layout: DashboardLayout;
  refreshIntervalMs: number;
  editable?: boolean;
  selectedWidgetId?: string | null;
  onSelectWidget?: (id: string) => void;
  onLayoutChange?: (widgets: DashboardWidget[]) => void;
}

interface GridMetrics {
  colWidth: number;
  rowHeight: number;
  marginX: number;
  marginY: number;
}

function computeMetrics(containerWidth: number, columns: number, rowHeight: number): GridMetrics {
  const [marginX, marginY] = GRID_MARGIN;
  const colWidth = (containerWidth - marginX * (columns + 1)) / columns;
  return { colWidth, rowHeight, marginX, marginY };
}

function itemRect(
  widget: Pick<DashboardWidget, "x" | "y" | "w" | "h">,
  metrics: GridMetrics
) {
  const { colWidth, rowHeight, marginX, marginY } = metrics;
  return {
    left: widget.x * colWidth + marginX * (widget.x + 1),
    top: widget.y * rowHeight + marginY * (widget.y + 1),
    width: widget.w * colWidth + marginX * (widget.w - 1),
    height: widget.h * rowHeight + marginY * (widget.h - 1),
  };
}

function canvasHeight(widgets: DashboardWidget[], metrics: GridMetrics): number {
  if (widgets.length === 0) return 320;
  let maxBottom = 0;
  for (const widget of widgets) {
    const rect = itemRect(widget, metrics);
    maxBottom = Math.max(maxBottom, rect.top + rect.height);
  }
  return maxBottom + metrics.marginY;
}

function useContainerWidth(initialWidth = 1280) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(initialWidth);

  useEffect(() => {
    const node = containerRef.current;
    if (!node) return;

    const measure = () => {
      const next = node.offsetWidth;
      if (next > 0) setWidth(next);
    };

    measure();
    const observer = new ResizeObserver(() => measure());
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return { containerRef, width };
}

export default function DashboardGrid({
  layout,
  refreshIntervalMs,
  editable = false,
  selectedWidgetId,
  onSelectWidget,
  onLayoutChange,
}: DashboardGridProps) {
  const { containerRef, width } = useContainerWidth();
  const canvasRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{
    widgetId: string;
    mode: "move" | "resize";
    startClientX: number;
    startClientY: number;
    originX: number;
    originY: number;
    originW: number;
    originH: number;
  } | null>(null);

  const metrics = useMemo(
    () => computeMetrics(width, layout.columns, layout.rowHeight),
    [width, layout.columns, layout.rowHeight]
  );

  const applyLayout = useCallback(
    (nextWidgets: DashboardWidget[]) => {
      onLayoutChange?.(nextWidgets);
    },
    [onLayoutChange]
  );

  const updateWidget = useCallback(
    (widgetId: string, patch: Partial<Pick<DashboardWidget, "x" | "y" | "w" | "h">>) => {
      if (!onLayoutChange) return;
      const positions = layout.widgets.map((w) =>
        w.id === widgetId ? { id: w.id, x: w.x, y: w.y, w: w.w, h: w.h, ...patch } : {
          id: w.id,
          x: w.x,
          y: w.y,
          w: w.w,
          h: w.h,
        }
      );
      applyLayout(mergeWidgetLayout(layout.widgets, positions));
    },
    [applyLayout, layout.widgets, onLayoutChange]
  );

  useEffect(() => {
    if (!editable) return;

    const onMove = (event: MouseEvent) => {
      const drag = dragRef.current;
      const canvas = canvasRef.current;
      if (!drag || !canvas) return;

      const metricsNow = computeMetrics(canvas.clientWidth, layout.columns, layout.rowHeight);

      if (drag.mode === "move") {
        const deltaX = event.clientX - drag.startClientX;
        const deltaY = event.clientY - drag.startClientY;
        const deltaCols = Math.round(deltaX / (metricsNow.colWidth + metricsNow.marginX));
        const deltaRows = Math.round(deltaY / (metricsNow.rowHeight + metricsNow.marginY));
        const maxX = Math.max(0, layout.columns - drag.originW);
        updateWidget(drag.widgetId, {
          x: Math.min(maxX, Math.max(0, drag.originX + deltaCols)),
          y: Math.max(0, drag.originY + deltaRows),
        });
        return;
      }

      const deltaX = event.clientX - drag.startClientX;
      const deltaY = event.clientY - drag.startClientY;
      const deltaCols = Math.round(deltaX / (metricsNow.colWidth + metricsNow.marginX));
      const deltaRows = Math.round(deltaY / (metricsNow.rowHeight + metricsNow.marginY));
      const nextW = Math.max(1, Math.min(layout.columns - drag.originX, drag.originW + deltaCols));
      const nextH = Math.max(1, drag.originH + deltaRows);
      updateWidget(drag.widgetId, { w: nextW, h: nextH });
    };

    const onUp = () => {
      dragRef.current = null;
      document.body.classList.remove("dashboard-grid-dragging");
    };

    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
    return () => {
      document.removeEventListener("mousemove", onMove);
      document.removeEventListener("mouseup", onUp);
      document.body.classList.remove("dashboard-grid-dragging");
    };
  }, [editable, layout.columns, layout.rowHeight, updateWidget]);

  const startDrag = (event: React.MouseEvent, widget: DashboardWidget, mode: "move" | "resize") => {
    if (!editable || event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();

    dragRef.current = {
      widgetId: widget.id,
      mode,
      startClientX: event.clientX,
      startClientY: event.clientY,
      originX: widget.x,
      originY: widget.y,
      originW: widget.w,
      originH: widget.h,
    };
    document.body.classList.add("dashboard-grid-dragging");
    onSelectWidget?.(widget.id);
  };

  const height = canvasHeight(layout.widgets, metrics);

  return (
    <div
      ref={containerRef}
      className={`dashboard-grid-host${layout.theme ? ` dashboard-theme-${layout.theme}` : ""}${editable ? " editable" : ""}`}
    >
      <div
        ref={canvasRef}
        className="dashboard-grid-canvas"
        style={{ height }}
      >
        {layout.widgets.map((widget) => {
          const rect = itemRect(widget, metrics);
          return (
            <div
              key={widget.id}
              className={`dashboard-grid-item ${selectedWidgetId === widget.id ? "selected" : ""}`}
              style={{
                left: rect.left,
                top: rect.top,
                width: rect.width,
                height: rect.height,
              }}
              onMouseDown={(event) => {
                if (!editable) return;
                if ((event.target as HTMLElement).closest(DRAG_CANCEL_SELECTOR)) return;
                startDrag(event, widget, "move");
              }}
              onClick={(event) => {
                if (!onSelectWidget || !editable) return;
                if ((event.target as HTMLElement).closest(DRAG_CANCEL_SELECTOR)) return;
                onSelectWidget(widget.id);
              }}
            >
              <WidgetRenderer
                widget={widget}
                refreshIntervalMs={refreshIntervalMs}
                editable={editable}
              />
              {editable && (
                <div
                  className="dashboard-grid-resize-handle"
                  title="Изменить размер"
                  onMouseDown={(event) => startDrag(event, widget, "resize")}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function WidgetRenderer({
  widget,
  refreshIntervalMs,
  editable,
}: {
  widget: DashboardWidget;
  refreshIntervalMs: number;
  editable: boolean;
}) {
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
    default:
      return <div className="dash-widget">Неизвестный виджет</div>;
  }
}
