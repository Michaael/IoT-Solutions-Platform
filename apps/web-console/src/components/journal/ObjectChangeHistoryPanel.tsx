import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchObjectAudit, type ObjectConfigAuditEntry } from "../../api";
import JournalViewShell, { type JournalViewMode } from "./JournalViewShell";

const LIVE_LIMIT = 25;
const HISTORY_PAGE = 50;
const HISTORY_MAX = 200;

interface ObjectChangeHistoryPanelProps {
  objectPath: string;
  compact?: boolean;
  scrollMaxHeight?: number | string;
}

export default function ObjectChangeHistoryPanel({
  objectPath,
  compact = false,
  scrollMaxHeight,
}: ObjectChangeHistoryPanelProps) {
  const { t } = useTranslation(["journal", "common"]);
  const [mode, setMode] = useState<JournalViewMode>("live");
  const [historyLimit, setHistoryLimit] = useState(HISTORY_PAGE);
  const [changeType, setChangeType] = useState("");
  const [fieldFilter, setFieldFilter] = useState("");
  const [actorFilter, setActorFilter] = useState("");

  const fetchLimit = mode === "live" ? LIVE_LIMIT : historyLimit;

  const auditQuery = useQuery({
    queryKey: ["object-audit", objectPath, fetchLimit],
    queryFn: () => fetchObjectAudit(objectPath, fetchLimit),
    refetchInterval: mode === "live" ? 10_000 : false,
    staleTime: mode === "live" ? 0 : 30_000,
  });

  const changeTypes = useMemo(() => {
    const set = new Set<string>();
    for (const entry of auditQuery.data ?? []) {
      if (entry.changeType) {
        set.add(entry.changeType);
      }
    }
    return Array.from(set).sort();
  }, [auditQuery.data]);

  const filtered = useMemo(() => {
    let rows = auditQuery.data ?? [];
    if (mode === "history") {
      if (changeType) {
        rows = rows.filter((e) => e.changeType === changeType);
      }
      const field = fieldFilter.trim().toLowerCase();
      if (field) {
        rows = rows.filter((e) => e.field?.toLowerCase().includes(field));
      }
      const actor = actorFilter.trim().toLowerCase();
      if (actor) {
        rows = rows.filter((e) => e.actor?.toLowerCase().includes(actor));
      }
    }
    return rows;
  }, [actorFilter, auditQuery.data, changeType, fieldFilter, mode]);

  const canLoadMore =
    mode === "history"
    && (auditQuery.data?.length ?? 0) >= historyLimit
    && historyLimit < HISTORY_MAX;

  return (
    <JournalViewShell
      title={t("journal:changeHistory.title")}
      subtitle={t("journal:changeHistory.subtitle")}
      mode={mode}
      onModeChange={setMode}
      count={filtered.length}
      isLoading={auditQuery.isLoading}
      error={auditQuery.error}
      empty={filtered.length === 0}
      emptyMessage={t("common:empty.noRecords")}
      compact={compact}
      scrollMaxHeight={scrollMaxHeight ?? (compact ? 280 : 400)}
      filters={
        <div className="journal-filters form-grid">
          <label>
            {t("common:table.type")}
            <select value={changeType} onChange={(e) => setChangeType(e.target.value)}>
              <option value="">{t("journal:filter.all")}</option>
              {changeTypes.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t("common:field.field")}
            <input
              value={fieldFilter}
              onChange={(e) => setFieldFilter(e.target.value)}
              placeholder={t("journal:filter.fieldPlaceholder")}
            />
          </label>
          <label>
            {t("common:field.actor")}
            <input
              value={actorFilter}
              onChange={(e) => setActorFilter(e.target.value)}
              placeholder={t("journal:filter.actorPlaceholder")}
            />
          </label>
        </div>
      }
      footer={
        canLoadMore ? (
          <div className="journal-footer">
            <button
              type="button"
              className="btn small"
              disabled={auditQuery.isFetching}
              onClick={() => setHistoryLimit((n) => Math.min(n + HISTORY_PAGE, HISTORY_MAX))}
            >
              {t("journal:loadMore")}
            </button>
          </div>
        ) : undefined
      }
    >
      <div className="table-scroll journal-table-scroll">
        <table className="data-table journal-audit-table">
          <thead>
            <tr>
              <th>{t("common:field.time")}</th>
              <th>{t("common:table.type")}</th>
              <th>{t("common:field.field")}</th>
              <th>{t("common:field.actor")}</th>
              <th>Rev</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((entry) => (
              <AuditRow key={entry.id} entry={entry} />
            ))}
          </tbody>
        </table>
      </div>
    </JournalViewShell>
  );
}

function AuditRow({ entry }: { entry: ObjectConfigAuditEntry }) {
  const { t } = useTranslation("common");
  return (
    <tr>
      <td className="mono small">{new Date(entry.occurredAt).toLocaleString()}</td>
      <td>{entry.changeType}</td>
      <td>{entry.field || t("empty.dash")}</td>
      <td>{entry.actor || t("empty.dash")}</td>
      <td className="mono small">
        {entry.revisionBefore}→{entry.revisionAfter}
      </td>
    </tr>
  );
}
