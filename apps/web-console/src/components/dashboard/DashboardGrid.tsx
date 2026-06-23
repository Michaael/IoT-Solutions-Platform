import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardLayout, DashboardWidget } from "../../types/dashboard";
import { mergeWidgetLayout } from "../../types/dashboard";
import renderDashboardWidget from "./renderDashboardWidget";

const GRID_MARGIN: [number, number] = [12, 12];
const DRAG_CANCEL_SELECTOR =
  "button, input, select, textarea, a, .dash-chart-body, .dash-sparkline-body, .dashboard-grid-resize-handle, .dash-object-table, .dash-event-feed-list, .dash-work-queue-list, .function-form-fields, .dashboard-link-btn, .dash-object-card, .dash-pie-chart-body, .dash-variable-editor-list, .dash-svg-widget-btn, .dash-composite-body";

interface DashboardGridProps {
  layout: DashboardLayout;
  refreshIntervalMs: number;
  editable?: boolean;
  selectedWidgetId?: string | null;
  onSelectWidget?: (id: string) => void;
  onLayoutChange?: (widgets: DashboardWidget[]) => void;
  subDashboardDepth?: number;
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
  subDashboardDepth = 0,
}: DashboardGridProps) {
  const { t } = useTranslation("dashboard");
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
              {renderDashboardWidget({
                widget,
                refreshIntervalMs,
                editable,
                depth: subDashboardDepth,
              })}
              {editable && (
                <div
                  className="dashboard-grid-resize-handle"
                  title={t("grid.resize")}
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
