import { useMemo, useState } from "react";
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

interface EventJournalPanelProps {
  appId?: string;
  ui?: OperatorUi;
  operatorApps?: OperatorUi[];
  objectPath?: string;
  limit?: number;
  showFilters?: boolean;
  objectPathFilter?: string;
}

export default function EventJournalPanel({
  appId,
  ui,
  operatorApps = [],
  objectPath: fixedObjectPath,
  limit = 40,
  showFilters = false,
  objectPathFilter: initialFilter = "",
}: EventJournalPanelProps) {
  const { t } = useTranslation(["operator", "common"]);
  const operatorScoped = Boolean(appId && ui);
  const [filterPath, setFilterPath] = useState(initialFilter);
  const objectPath = fixedObjectPath ?? (filterPath.trim() || undefined);
  useOperatorSidebarRefresh(appId, operatorScoped ? ui : undefined);

  const events = useQuery({
    queryKey: ["events", operatorScoped ? OPERATOR_SIDEBAR_EVENTS_QUERY_KEY : objectPath ?? "all", limit],
    queryFn: () =>
      operatorScoped ? fetchEvents(undefined, Math.max(limit, 80)) : fetchEvents(objectPath, limit),
    refetchInterval: operatorScoped ? 5000 : 8000,
    staleTime: 0,
  });

  const items = useMemo(() => {
    if (!operatorScoped || !ui || !appId) {
      return events.data ?? [];
    }
    return filterOperatorSidebarEvents(events.data ?? [], {
      appId,
      ui,
      operatorApps,
    });
  }, [appId, events.data, operatorApps, operatorScoped, ui]);

  return (
    <section className="event-journal-panel">
      <header className="event-journal-head">
        <div>
          <h3>{t("eventJournal.title")}</h3>
          {operatorScoped && appId && (
            <p className="hint">{t("eventJournal.operatorApp", { appId })}</p>
          )}
          {!operatorScoped && objectPath && (
            <p className="hint">
              {t("eventJournal.filter", { path: objectPath })}
            </p>
          )}
        </div>
        <span className="badge">{items.length}</span>
      </header>

      {showFilters && !fixedObjectPath && (
        <div className="runtime-journal-filters">
          <label>
            objectPath
            <input
              value={filterPath}
              onChange={(e) => setFilterPath(e.target.value)}
              placeholder={t("eventJournal.filterPlaceholder")}
            />
          </label>
        </div>
      )}

      {events.isLoading && <p className="hint">{t("common:action.loading")}</p>}
      {events.error && <p className="hint error">{t("eventJournal.loadError")}</p>}
      {items.length === 0 && !events.isLoading && (
        <p className="hint">
          {operatorScoped ? t("eventJournal.emptyScoped") : t("eventJournal.empty")}
        </p>
      )}
      <ul className="event-journal-list">
        {items.map((event) => (
          <EventRow key={event.id} event={event} />
        ))}
      </ul>
    </section>
  );
}

function EventRow({ event }: { event: ObjectEvent }) {
  const payload = event.payload?.rows?.[0];
  const detail =
    payload && typeof payload.value !== "undefined"
      ? `${payload.value}${payload.unit ? ` ${payload.unit}` : ""}`
      : null;

  return (
    <li className={`event-journal-item level-${event.level.toLowerCase()}`}>
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
