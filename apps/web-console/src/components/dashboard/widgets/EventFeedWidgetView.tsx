import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchEvents } from "../../../api";
import type { EventFeedWidget } from "../../../types/dashboard";
import { matchesPayloadFilter } from "../../../utils/payloadFilter";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { parseDemoPreview } from "../widgetDemoPreview";
import JournalViewShell, { JOURNAL_VIEW_MODES, type JournalViewMode } from "../../journal/JournalViewShell";
import JournalVirtualList from "../../journal/JournalVirtualList";
import JournalExpandableItem from "../../journal/JournalExpandableItem";
import { usePersistentTab } from "../../../hooks/usePersistentTab";

const LIVE_LIMIT = 25;
const HISTORY_PAGE = 50;
const HISTORY_MAX = 200;
const EVENT_ITEM_ESTIMATE_PX = 88;

interface DemoFeedEvent {
  id: string;
  eventName: string;
  level: string;
  objectPath: string;
  timestamp: string;
  payload?: { rows?: Array<Record<string, unknown>> };
}

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
  const { t } = useTranslation(["widgets", "journal", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const [mode, setMode] = usePersistentTab<JournalViewMode>(
    `event-feed-widget:${widget.id}`,
    "live",
    JOURNAL_VIEW_MODES
  );
  const [historyLimit, setHistoryLimit] = useState(HISTORY_PAGE);
  const [levelFilter, setLevelFilter] = useState("");
  const [searchFilter, setSearchFilter] = useState("");

  const eventNames = useMemo(() => {
    try {
      return widget.eventNamesJson ? (JSON.parse(widget.eventNamesJson) as string[]) : [];
    } catch {
      return [] as string[];
    }
  }, [widget.eventNamesJson]);

  const liveMax = widget.maxItems ?? LIVE_LIMIT;
  const fetchLimit = mode === "live" ? liveMax : historyLimit;

  const events = useQuery({
    queryKey: ["events", "feed", widget.objectPathPrefix, fetchLimit, mode],
    queryFn: () => fetchEvents(undefined, fetchLimit),
    refetchInterval: mode === "live" ? refreshIntervalMs : false,
    staleTime: mode === "live" ? 0 : 30_000,
  });

  const filtered = useMemo(() => {
    let rows = (events.data ?? []).filter((event) => {
      if (widget.objectPathPrefix && !event.objectPath.startsWith(widget.objectPathPrefix)) {
        return false;
      }
      if (eventNames.length > 0 && !eventNames.includes(event.eventName)) {
        return false;
      }
      const payloadRow = event.payload?.rows?.[0];
      if (!matchesPayloadFilter(payloadRow, widget.payloadFilterExpr)) {
        return false;
      }
      return true;
    });

    if (mode === "history") {
      if (levelFilter) {
        rows = rows.filter((event) => event.level === levelFilter);
      }
      const query = searchFilter.trim().toLowerCase();
      if (query) {
        rows = rows.filter(
          (event) =>
            event.objectPath.toLowerCase().includes(query)
            || event.eventName.toLowerCase().includes(query),
        );
      }
    }

    return rows;
  }, [eventNames, events.data, levelFilter, mode, searchFilter, widget.objectPathPrefix, widget.payloadFilterExpr]);

  const levels = useMemo(() => {
    const set = new Set<string>();
    for (const event of events.data ?? []) {
      set.add(event.level);
    }
    return Array.from(set).sort();
  }, [events.data]);

  const demoEvents =
    editable && filtered.length === 0 && !events.isLoading
      ? parseDemoPreview<DemoFeedEvent[]>(widget.demoPreviewJson) ?? []
      : [];
  const isDemo = demoEvents.length > 0;
  const displayEvents = isDemo ? demoEvents : filtered;

  const canLoadMore =
    !isDemo
    && mode === "history"
    && (events.data?.length ?? 0) >= historyLimit
    && historyLimit < HISTORY_MAX;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-event-feed"
      editable={editable}
      demo={isDemo}
    >
      <JournalViewShell
        title=""
        mode={mode}
        onModeChange={setMode}
        count={displayEvents.length}
        isLoading={events.isLoading && !isDemo}
        error={isDemo ? undefined : events.error}
        empty={displayEvents.length === 0}
        emptyMessage={t("view.noEvents")}
        headless
        compact
        scrollMaxHeight="100%"
        className="dash-widget-event-feed-journal"
        filters={
          mode === "history" && !isDemo ? (
            <div className="journal-filters form-grid">
              <label>
                {t("journal:filter.level")}
                <select value={levelFilter} onChange={(e) => setLevelFilter(e.target.value)}>
                  <option value="">{t("journal:filter.all")}</option>
                  {levels.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                {t("common:action.search")}
                <input
                  value={searchFilter}
                  onChange={(e) => setSearchFilter(e.target.value)}
                  placeholder={t("journal:filter.searchPlaceholder")}
                />
              </label>
            </div>
          ) : undefined
        }
        footer={
          canLoadMore ? (
            <div className="journal-footer">
              <button
                type="button"
                className="btn small"
                disabled={events.isFetching}
                onClick={() => setHistoryLimit((n) => Math.min(n + HISTORY_PAGE, HISTORY_MAX))}
              >
                {t("journal:loadMore")}
              </button>
            </div>
          ) : undefined
        }
      >
        <JournalVirtualList
          items={displayEvents}
          estimateSizePx={EVENT_ITEM_ESTIMATE_PX}
          className="dash-event-feed-list"
          getTime={(event) => event.timestamp}
          getKey={(event) => event.id}
          renderItem={(event) => {
            const payload = event.payload?.rows?.[0];
            const detail = payload
              ? Object.entries(payload)
                  .map(([k, v]) => `${k}=${v}`)
                  .join(", ")
              : "";
            const sections = [
              {
                id: "payload",
                label: t("journal:details.payload"),
                value: event.payload,
              },
            ];
            return (
              <JournalExpandableItem
                className={`dash-event-item level-${event.level.toLowerCase()}`}
                sections={sections}
              >
                <div className="dash-event-row-top" style={styles.body}>
                  <strong>{event.eventName}</strong>
                  <time className="hint">
                    {new Date(event.timestamp).toLocaleTimeString()}
                  </time>
                </div>
                <p className="hint">{event.objectPath}</p>
                {detail && <p className="dash-event-detail">{detail}</p>}
              </JournalExpandableItem>
            );
          }}
        />
      </JournalViewShell>
    </DashWidgetShell>
  );
}
