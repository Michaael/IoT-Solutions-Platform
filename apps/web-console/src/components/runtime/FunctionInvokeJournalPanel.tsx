import { useMemo, useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchFunctionAuditStatus, fetchFunctionInvocations } from "../../api";
import type { FunctionInvokeAuditEntry } from "../../types/runtime";
import JournalViewShell, { type JournalViewMode } from "../journal/JournalViewShell";
import JournalVirtualList from "../journal/JournalVirtualList";

const LIVE_LIMIT = 25;
const HISTORY_PAGE = 50;
const HISTORY_MAX = 200;
const INVOKE_ITEM_ESTIMATE_PX = 110;

interface FunctionInvokeJournalPanelProps {
  objectPath?: string;
  functionName?: string;
  limit?: number;
  showFilters?: boolean;
  compact?: boolean;
  scrollMaxHeight?: number | string;
  defaultMode?: JournalViewMode;
}

export default function FunctionInvokeJournalPanel({
  objectPath: initialObjectPath,
  functionName: initialFunctionName,
  limit,
  showFilters = false,
  compact = false,
  scrollMaxHeight,
  defaultMode = "live",
}: FunctionInvokeJournalPanelProps) {
  const { t } = useTranslation(["runtime", "journal", "common"]);
  const [mode, setMode] = useState<JournalViewMode>(defaultMode);
  const [historyLimit, setHistoryLimit] = useState(HISTORY_PAGE);
  const [objectPath, setObjectPath] = useState(initialObjectPath ?? "");
  const [functionName, setFunctionName] = useState(initialFunctionName ?? "");
  const [successFilter, setSuccessFilter] = useState<"" | "true" | "false">("");
  const [searchFilter, setSearchFilter] = useState("");

  const liveLimit = limit ?? LIVE_LIMIT;
  const fetchLimit = mode === "live" ? liveLimit : historyLimit;
  const fixedObjectPath = initialObjectPath?.trim() || undefined;
  const fixedFunctionName = initialFunctionName?.trim() || undefined;

  const statusQuery = useQuery({
    queryKey: ["function-audit-status", fixedObjectPath ?? objectPath],
    queryFn: () => fetchFunctionAuditStatus(fixedObjectPath ?? (objectPath.trim() || undefined)),
    staleTime: 30_000,
  });

  const query = useQuery({
    queryKey: [
      "function-invocations",
      fixedObjectPath ?? objectPath,
      fixedFunctionName ?? functionName,
      successFilter,
      fetchLimit,
      mode,
    ],
    queryFn: () =>
      fetchFunctionInvocations({
        objectPath: fixedObjectPath ?? (objectPath.trim() || undefined),
        functionName: fixedFunctionName ?? (functionName.trim() || undefined),
        success: successFilter === "" ? undefined : successFilter === "true",
        limit: fetchLimit,
      }),
    refetchInterval: mode === "live" ? 10_000 : false,
    staleTime: mode === "live" ? 0 : 30_000,
  });

  const filtered = useMemo(() => {
    let rows = query.data ?? [];
    if (mode === "history") {
      const queryText = searchFilter.trim().toLowerCase();
      if (queryText) {
        rows = rows.filter(
          (entry) =>
            entry.objectPath.toLowerCase().includes(queryText)
            || entry.functionName.toLowerCase().includes(queryText)
            || (entry.errorMessage?.toLowerCase().includes(queryText) ?? false),
        );
      }
    }
    return rows;
  }, [mode, query.data, searchFilter]);

  const canLoadMore =
    mode === "history"
    && (query.data?.length ?? 0) >= historyLimit
    && historyLimit < HISTORY_MAX;

  const auditDisabled = statusQuery.data?.enabled === false;
  const auditHint = !statusQuery.data?.masterEnabled
    ? t("functionJournal.masterDisabledHint")
    : fixedObjectPath && !statusQuery.data?.objectEnabled
      ? t("functionJournal.objectDisabledHint")
      : auditDisabled
        ? t("functionJournal.disabledHint")
        : t("functionJournal.subtitle");

  return (
    <JournalViewShell
      title={t("functionJournal.title")}
      subtitle={auditHint}
      mode={mode}
      onModeChange={setMode}
      count={filtered.length}
      isLoading={query.isLoading}
      error={query.error}
      empty={filtered.length === 0}
      emptyMessage={
        auditDisabled
          ? (fixedObjectPath ? t("functionJournal.objectDisabledEmpty") : t("functionJournal.disabledEmpty"))
          : t("functionJournal.empty")
      }
      compact={compact}
      scrollMaxHeight={scrollMaxHeight ?? (compact ? 280 : 400)}
      className="event-journal-panel function-invoke-journal"
      filters={
        (mode === "history" || showFilters) ? (
          <div className="journal-filters form-grid">
            {showFilters && !fixedObjectPath && (
              <label>
                objectPath
                <input
                  value={objectPath}
                  onChange={(e) => setObjectPath(e.target.value)}
                  placeholder="root.platform.devices…"
                />
              </label>
            )}
            {showFilters && !fixedFunctionName && (
              <label>
                functionName
                <input
                  value={functionName}
                  onChange={(e) => setFunctionName(e.target.value)}
                  placeholder="acknowledgeAlarm"
                />
              </label>
            )}
            {(showFilters || mode === "history") && (
              <label>
                success
                <select
                  value={successFilter}
                  onChange={(e) => setSuccessFilter(e.target.value as "" | "true" | "false")}
                >
                  <option value="">{t("functionJournal.filter.all")}</option>
                  <option value="true">{t("functionJournal.filter.success")}</option>
                  <option value="false">{t("functionJournal.filter.error")}</option>
                </select>
              </label>
            )}
            {mode === "history" && (
              <label>
                {t("common:action.search")}
                <input
                  value={searchFilter}
                  onChange={(e) => setSearchFilter(e.target.value)}
                  placeholder={t("journal:filter.searchPlaceholder")}
                />
              </label>
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
              disabled={query.isFetching}
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
        estimateSizePx={INVOKE_ITEM_ESTIMATE_PX}
        getKey={(entry) => entry.id}
        renderItem={(entry, style) => <InvokeRow entry={entry} style={style} />}
      />
    </JournalViewShell>
  );
}

function InvokeRow({
  entry,
  style,
}: {
  entry: FunctionInvokeAuditEntry;
  style?: CSSProperties;
}) {
  return (
    <li
      className={`event-journal-item ${entry.success ? "level-info" : "level-error"} dash-virtual-list-item`}
      style={style}
    >
      <div className="event-journal-row-top">
        <strong>{entry.functionName}</strong>
        <span className="event-level-pill">{entry.success ? "OK" : "FAIL"}</span>
      </div>
      <p className="hint">{entry.objectPath}</p>
      {entry.appId && <p className="hint">app: {entry.appId}</p>}
      {entry.errorMessage && <p className="event-journal-detail">{entry.errorMessage}</p>}
      <time className="hint event-journal-time">
        {new Date(entry.invokedAt).toLocaleString()}
      </time>
    </li>
  );
}
