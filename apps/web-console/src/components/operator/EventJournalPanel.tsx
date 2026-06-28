import { useMemo, useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchEvents } from "../../api";
import {
  OPERATOR_SIDEBAR_EVENTS_QUERY_KEY,
  useOperatorSidebarRefresh,
} from "../../hooks/useOperatorSidebarRefresh";
import type { ObjectEvent } from "../../types/event";
import type { OperatorUi } from "../../types/operatorUi";
import { filterOperatorSidebarEvents } from "../../utils/operatorSidebarScope";
import JournalViewShell, { type JournalViewMode } from "../journal/JournalViewShell";
import JournalVirtualList from "../journal/JournalVirtualList";

const LIVE_LIMIT = 25;
const HISTORY_PAGE = 50;
const HISTORY_MAX = 200;
const EVENT_ITEM_ESTIMATE_PX = 120;

interface EventJournalPanelProps {
  appId?: string;
  ui?: OperatorUi;
  operatorApps?: OperatorUi[];
  objectPath?: string;
  limit?: number;
  showFilters?: boolean;
  objectPathFilter?: string;
  knownEventNames?: string[];
  compact?: boolean;
  scrollMaxHeight?: number | string;
  defaultMode?: JournalViewMode;
  showModeToggle?: boolean;
}

export default function EventJournalPanel({
  appId,
  ui,
  operatorApps = [],
  objectPath: fixedObjectPath,
  limit,
  showFilters = false,
  objectPathFilter: initialFilter = "",
  knownEventNames,
  compact = false,
  scrollMaxHeight,
  defaultMode = "live",
  showModeToggle = true,
}: EventJournalPanelProps) {
  const { t } = useTranslation(["operator", "journal", "common"]);
  const operatorScoped = Boolean(appId && ui);
  const [mode, setMode] = useState<JournalViewMode>(defaultMode);
  const [historyLimit, setHistoryLimit] = useState(HISTORY_PAGE);
  const [filterPath, setFilterPath] = useState(initialFilter);
  const [eventNameFilter, setEventNameFilter] = useState("");
  const [levelFilter, setLevelFilter] = useState("");
  const [searchFilter, setSearchFilter] = useState("");

  const objectPath = fixedObjectPath ?? (filterPath.trim() || undefined);
  const liveLimit = limit ?? LIVE_LIMIT;
  const fetchLimit = mode === "live" ? liveLimit : historyLimit;

  useOperatorSidebarRefresh(appId, operatorScoped ? ui : undefined);

  const events = useQuery({
    queryKey: [
      "events",
      operatorScoped ? OPERATOR_SIDEBAR_EVENTS_QUERY_KEY : objectPath ?? "all",
      fetchLimit,
      mode,
    ],
    queryFn: () => {
      if (operatorScoped) {
        return fetchEvents(undefined, Math.max(fetchLimit, mode === "live" ? 80 : fetchLimit));
      }
      return fetchEvents(objectPath, fetchLimit);
    },
    refetchInterval: mode === "live" ? (operatorScoped ? 5000 : 8000) : false,
    staleTime: mode === "live" ? 0 : 30_000,
  });

  const rawItems = useMemo(() => {
    if (!operatorScoped || !ui || !appId) {
      return events.data ?? [];
    }
    return filterOperatorSidebarEvents(events.data ?? [], {
      appId,
      ui,
      operatorApps,
    });
  }, [appId, events.data, operatorApps, operatorScoped, ui]);

  const eventNames = useMemo(() => {
    const set = new Set<string>(knownEventNames ?? []);
    for (const event of rawItems) {
      set.add(event.eventName);
    }
    return Array.from(set).sort();
  }, [knownEventNames, rawItems]);

  const levels = useMemo(() => {
    const set = new Set<string>();
    for (const event of rawItems) {
      set.add(event.level);
    }
    return Array.from(set).sort();
  }, [rawItems]);

  const filtered = useMemo(() => {
    let rows = rawItems;
    if (mode === "history") {
      if (eventNameFilter) {
        rows = rows.filter((event) => event.eventName === eventNameFilter);
      }
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
  }, [eventNameFilter, levelFilter, mode, rawItems, searchFilter]);

  const canLoadMore =
    mode === "history"
    && !operatorScoped
    && (events.data?.length ?? 0) >= historyLimit
    && historyLimit < HISTORY_MAX;

  const subtitle = operatorScoped && appId
    ? t("eventJournal.operatorApp", { appId })
    : !operatorScoped && objectPath
      ? t("eventJournal.filter", { path: objectPath })
      : undefined;

  const emptyMessage = operatorScoped ? t("eventJournal.emptyScoped") : t("eventJournal.empty");

  return (
    <JournalViewShell
      title={t("eventJournal.title")}
      subtitle={subtitle}
      mode={mode}
      onModeChange={setMode}
      showModeToggle={showModeToggle}
      count={filtered.length}
      isLoading={events.isLoading}
      error={events.error}
      empty={filtered.length === 0}
      emptyMessage={emptyMessage}
      compact={compact}
      scrollMaxHeight={scrollMaxHeight ?? (compact ? 280 : 400)}
      className="event-journal-panel"
      filters={
        (mode === "history" || (showFilters && !fixedObjectPath)) ? (
          <div className="journal-filters form-grid">
            {showFilters && !fixedObjectPath && (
              <label>
                objectPath
                <input
                  value={filterPath}
                  onChange={(e) => setFilterPath(e.target.value)}
                  placeholder={t("eventJournal.filterPlaceholder")}
                />
              </label>
            )}
            {mode === "history" && (
              <>
                <label>
                  {t("journal:filter.eventName")}
                  <select value={eventNameFilter} onChange={(e) => setEventNameFilter(e.target.value)}>
                    <option value="">{t("journal:filter.all")}</option>
                    {eventNames.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                </label>
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
              </>
            )}
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
        items={filtered}
        estimateSizePx={EVENT_ITEM_ESTIMATE_PX}
        getKey={(event) => event.id}
        renderItem={(event, style) => <EventRow event={event} style={style} />}
      />
    </JournalViewShell>
  );
}

function EventRow({
  event,
  style,
}: {
  event: ObjectEvent;
  style?: CSSProperties;
}) {
  const payload = event.payload?.rows?.[0];
  const detail =
    payload && typeof payload.value !== "undefined"
      ? `${payload.value}${payload.unit ? ` ${payload.unit}` : ""}`
      : null;

  return (
    <li
      className={`event-journal-item level-${event.level.toLowerCase()} dash-virtual-list-item`}
      style={style}
    >
      <div className="event-journal-row-top">
        <strong>{event.eventName}</strong>
        <span className="event-level-pill">{event.level}</span>
      </div>
      <p className="hint">{event.objectPath}</p>
      {detail && <p className="event-journal-detail">{detail}</p>}
      <time className="hint event-journal-time">
        {new Date(event.timestamp).toLocaleString()}
      </time>
    </li>
  );
}
