import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchEvents } from "../../../api";
import type { EventFeedWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface EventFeedWidgetViewProps {
  widget: EventFeedWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function EventFeedWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: EventFeedWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const eventNames = useMemo(() => {
    try {
      return widget.eventNamesJson ? (JSON.parse(widget.eventNamesJson) as string[]) : [];
    } catch {
      return [] as string[];
    }
  }, [widget.eventNamesJson]);

  const events = useQuery({
    queryKey: ["events", "feed", widget.objectPathPrefix, widget.maxItems],
    queryFn: () => fetchEvents(undefined, widget.maxItems ?? 30),
    refetchInterval: refreshIntervalMs,
  });

  const filtered = (events.data ?? []).filter((event) => {
    if (widget.objectPathPrefix && !event.objectPath.startsWith(widget.objectPathPrefix)) {
      return false;
    }
    if (eventNames.length > 0 && !eventNames.includes(event.eventName)) {
      return false;
    }
    return true;
  });

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-event-feed"
      editable={editable}
    >
      {events.isLoading && <p className="hint">Загрузка…</p>}
      {filtered.length === 0 && !events.isLoading && (
        <p className="hint">Нет событий</p>
      )}
      <ul className="dash-event-feed-list" style={styles.body}>
        {filtered.map((event) => {
          const payload = event.payload?.rows?.[0];
          const detail = payload
            ? Object.entries(payload)
                .map(([k, v]) => `${k}=${v}`)
                .join(", ")
            : "";
          return (
            <li key={event.id} className={`dash-event-item level-${event.level.toLowerCase()}`}>
              <div className="dash-event-row-top">
                <strong>{event.eventName}</strong>
                <time className="hint">
                  {new Date(event.timestamp).toLocaleTimeString()}
                </time>
              </div>
              <p className="hint">{event.objectPath}</p>
              {detail && <p className="dash-event-detail">{detail}</p>}
            </li>
          );
        })}
      </ul>
    </DashWidgetShell>
  );
}
