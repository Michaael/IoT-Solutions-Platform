import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { CarouselWidget, DashboardWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { renderWidgetList } from "../renderDashboardWidget";

interface CarouselSlide {
  id: string;
  label?: string;
  children: DashboardWidget[];
}

interface CarouselWidgetViewProps {
  widget: CarouselWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function CarouselWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: CarouselWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const slides = useMemo(() => {
    try {
      const parsed = widget.slidesJson ? (JSON.parse(widget.slidesJson) as CarouselSlide[]) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [] as CarouselSlide[];
    }
  }, [widget.slidesJson]);
  const [index, setIndex] = useState(0);
  const slide = slides[index];

  useEffect(() => {
    if (!widget.autoplayMs || slides.length < 2) return;
    const id = window.setInterval(() => {
      setIndex((i) => (i + 1) % slides.length);
    }, widget.autoplayMs);
    return () => window.clearInterval(id);
  }, [widget.autoplayMs, slides.length]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-carousel"
      editable={editable}
    >
      {slides.length === 0 ? (
        <p className="hint">{t("view.specifySlidesJson")}</p>
      ) : (
        <div className="dash-carousel-body" style={styles.body}>
          <div className="dash-carousel-nav">
            <button
              type="button"
              className="btn small"
              onClick={() => setIndex((i) => (i - 1 + slides.length) % slides.length)}
            >
              ‹
            </button>
            <span>{slide?.label ?? slide?.id ?? index + 1}</span>
            <button
              type="button"
              className="btn small"
              onClick={() => setIndex((i) => (i + 1) % slides.length)}
            >
              ›
            </button>
          </div>
          {slide?.children?.length
            ? renderWidgetList(slide.children, {
                refreshIntervalMs,
                editable: editable ?? false,
                depth: depth + 1,
              })
            : null}
        </div>
      )}
    </DashWidgetShell>
  );
}
