import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchBindingAuditStatus, fetchBindingInvocations } from "../../api";
import type { BindingInvokeAuditEntry } from "../../types/runtime";
import { mapBindingInvokeExportRow } from "../../utils/journalExport";
import { parseAuditBeforeAfter } from "../../utils/journalDetails";
import JournalViewShell, { JOURNAL_VIEW_MODES, type JournalViewMode } from "../journal/JournalViewShell";
import JournalVirtualList from "../journal/JournalVirtualList";
import JournalExpandableItem from "../journal/JournalExpandableItem";
import { usePersistentTab } from "../../hooks/usePersistentTab";

const LIVE_LIMIT = 25;
const HISTORY_PAGE = 50;
const HISTORY_MAX = 200;
const ITEM_ESTIMATE_PX = 108;

interface BindingInvokeJournalPanelProps {
  objectPath?: string;
  ruleId?: string;
  limit?: number;
  showFilters?: boolean;
  compact?: boolean;
  scrollMaxHeight?: number | string;
  defaultMode?: JournalViewMode;
}

export default function BindingInvokeJournalPanel({
  objectPath: fixedObjectPath,
  ruleId: fixedRuleId,
  limit,
  showFilters = false,
  compact = false,
  scrollMaxHeight,
  defaultMode = "live",
}: BindingInvokeJournalPanelProps) {
  const { t } = useTranslation(["runtime", "journal", "common"]);
  const [mode, setMode] = usePersistentTab<JournalViewMode>(
    `binding-journal:${fixedObjectPath ?? "all"}:${fixedRuleId ?? "all"}`,
    defaultMode,
    JOURNAL_VIEW_MODES
  );
  const [historyLimit, setHistoryLimit] = useState(HISTORY_PAGE);
  const [objectPath, setObjectPath] = useState(fixedObjectPath ?? "");
  const [bindingKind, setBindingKind] = useState("");
  const [ruleFilter, setRuleFilter] = useState(fixedRuleId ?? "");
  const [successFilter, setSuccessFilter] = useState<"" | "true" | "false">("");
  const [changedFilter, setChangedFilter] = useState<"" | "true" | "false">("");
  const [searchFilter, setSearchFilter] = useState("");

  const liveLimit = limit ?? LIVE_LIMIT;
  const fetchLimit = mode === "live" ? liveLimit : historyLimit;

  const statusQuery = useQuery({
    queryKey: ["binding-audit-status", fixedObjectPath ?? objectPath],
    queryFn: () => fetchBindingAuditStatus(fixedObjectPath ?? (objectPath.trim() || undefined)),
    staleTime: 30_000,
  });

  const query = useQuery({
    queryKey: [
      "binding-invocations",
      fixedObjectPath ?? objectPath,
      fixedRuleId ?? ruleFilter,
      bindingKind,
      successFilter,
      changedFilter,
      fetchLimit,
      mode,
    ],
    queryFn: () =>
      fetchBindingInvocations({
        objectPath: fixedObjectPath ?? (objectPath.trim() || undefined),
        bindingKind: bindingKind || undefined,
        ruleId: fixedRuleId ?? (ruleFilter.trim() || undefined),
        success: successFilter === "" ? undefined : successFilter === "true",
        changed: changedFilter === "" ? undefined : changedFilter === "true",
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
            || (entry.ruleName?.toLowerCase().includes(queryText) ?? false)
            || (entry.ruleId?.toLowerCase().includes(queryText) ?? false)
            || (entry.targetVariable?.toLowerCase().includes(queryText) ?? false)
            || (entry.errorMessage?.toLowerCase().includes(queryText) ?? false),
        );
      }
    }
    return rows;
  }, [mode, query.data, searchFilter]);

  const exportRows = useMemo(
    () => filtered.map(mapBindingInvokeExportRow),
    [filtered],
  );

  const exportFilenameBase = fixedObjectPath
    ? `binding-journal-${fixedObjectPath.replace(/\./g, "-")}`
    : "binding-journal";

  const canLoadMore =
    mode === "history"
    && (query.data?.length ?? 0) >= historyLimit
    && historyLimit < HISTORY_MAX;

  const auditDisabled = statusQuery.data?.enabled === false;
  const auditHint = !statusQuery.data?.masterEnabled
    ? t("bindingJournal.masterDisabledHint")
    : fixedObjectPath && !statusQuery.data?.objectEnabled
      ? t("bindingJournal.objectDisabledHint")
      : auditDisabled
        ? t("bindingJournal.disabledHint")
        : t("bindingJournal.subtitle");

  return (
    <JournalViewShell
      title={t("bindingJournal.title")}
      subtitle={auditHint}
      mode={mode}
      onModeChange={setMode}
      count={filtered.length}
      isLoading={query.isLoading}
      error={query.error}
      empty={filtered.length === 0}
      emptyMessage={
        auditDisabled
          ? (fixedObjectPath ? t("bindingJournal.objectDisabledEmpty") : t("bindingJournal.disabledEmpty"))
          : t("bindingJournal.empty")
      }
      compact={compact}
      scrollMaxHeight={scrollMaxHeight ?? (compact ? 280 : 400)}
      className="event-journal-panel binding-invoke-journal"
      exportFilenameBase={exportFilenameBase}
      exportRows={exportRows}
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
            {(showFilters || mode === "history") && (
              <>
                <label>
                  {t("bindingJournal.filter.kind")}
                  <select value={bindingKind} onChange={(e) => setBindingKind(e.target.value)}>
                    <option value="">{t("journal:filter.all")}</option>
                    <option value="cel">CEL</option>
                    <option value="sql">SQL</option>
                  </select>
                </label>
                <label>
                  success
                  <select
                    value={successFilter}
                    onChange={(e) => setSuccessFilter(e.target.value as "" | "true" | "false")}
                  >
                    <option value="">{t("bindingJournal.filter.all")}</option>
                    <option value="true">{t("bindingJournal.filter.success")}</option>
                    <option value="false">{t("bindingJournal.filter.error")}</option>
                  </select>
                </label>
                <label>
                  {t("bindingJournal.filter.changed")}
                  <select
                    value={changedFilter}
                    onChange={(e) => setChangedFilter(e.target.value as "" | "true" | "false")}
                  >
                    <option value="">{t("journal:filter.all")}</option>
                    <option value="true">{t("bindingJournal.filter.changedYes")}</option>
                    <option value="false">{t("bindingJournal.filter.changedNo")}</option>
                  </select>
                </label>
              </>
            )}
            {showFilters && !fixedRuleId && (
              <label>
                ruleId
                <input
                  value={ruleFilter}
                  onChange={(e) => setRuleFilter(e.target.value)}
                  placeholder="rule-id"
                />
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
        estimateSizePx={ITEM_ESTIMATE_PX}
        getTime={(entry) => entry.invokedAt}
        getKey={(entry) => entry.id}
        renderItem={(entry) => <BindingRow entry={entry} />}
      />
    </JournalViewShell>
  );
}

function BindingRow({
  entry,
}: {
  entry: BindingInvokeAuditEntry;
}) {
  const { t } = useTranslation(["runtime", "journal"]);
  const label = entry.ruleName || entry.ruleId || entry.targetVariable || "binding";
  const status = !entry.success ? "FAIL" : entry.changed ? "CHANGED" : "OK";
  const diff = useMemo(() => parseAuditBeforeAfter(entry.detailJson), [entry.detailJson]);
  const sections = useMemo(
    () => [
      {
        id: "before",
        label: t("journal:details.before"),
        value: diff.before,
      },
      {
        id: "after",
        label: t("journal:details.after"),
        value: diff.after,
      },
    ],
    [diff.after, diff.before, t],
  );

  return (
    <JournalExpandableItem
      className={`event-journal-item ${entry.success ? (entry.changed ? "level-info" : "level-info") : "level-error"}`}
      sections={sections}
    >
      <div className="event-journal-row-top">
        <strong>{label}</strong>
        <span className="event-level-pill">{status}</span>
      </div>
      <p className="hint">
        {entry.bindingKind.toUpperCase()} · {entry.triggerKind}
        {entry.targetVariable ? ` → ${entry.targetVariable}` : ""}
      </p>
      <p className="hint">{entry.objectPath}</p>
      {entry.durationMs != null && (
        <p className="hint">{entry.durationMs} ms</p>
      )}
      {entry.errorMessage && <p className="event-journal-detail">{entry.errorMessage}</p>}
      <time className="hint event-journal-time">
        {new Date(entry.invokedAt).toLocaleString()}
      </time>
    </JournalExpandableItem>
  );
}
