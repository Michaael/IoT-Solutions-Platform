import { createContext, useRef, type ReactNode, type RefObject } from "react";
import { useTranslation } from "react-i18next";
import JournalExportButtons from "./JournalExportButtons";
import type { JournalExportRow } from "../../utils/journalExport";

export const JournalScrollContext = createContext<RefObject<HTMLDivElement | null> | null>(null);

export type JournalViewMode = "live" | "history";
export const JOURNAL_VIEW_MODES: readonly JournalViewMode[] = ["live", "history"];

export interface JournalViewShellProps {
  title: string;
  subtitle?: string;
  mode: JournalViewMode;
  onModeChange: (mode: JournalViewMode) => void;
  showModeToggle?: boolean;
  count?: number;
  isLoading?: boolean;
  error?: unknown;
  empty?: boolean;
  emptyMessage?: string;
  filters?: ReactNode;
  footer?: ReactNode;
  scrollMaxHeight?: number | string;
  compact?: boolean;
  headless?: boolean;
  className?: string;
  exportFilenameBase?: string;
  exportRows?: JournalExportRow[];
  children: ReactNode;
}

export default function JournalViewShell({
  title,
  subtitle,
  mode,
  onModeChange,
  showModeToggle = true,
  count,
  isLoading,
  error,
  empty,
  emptyMessage,
  filters,
  footer,
  scrollMaxHeight = 320,
  compact = false,
  headless = false,
  className = "",
  exportFilenameBase,
  exportRows,
  children,
}: JournalViewShellProps) {
  const { t } = useTranslation(["journal", "common"]);
  const scrollRef = useRef<HTMLDivElement>(null);

  return (
    <section
      className={`journal-panel${compact ? " journal-panel-compact" : ""}${headless ? " journal-panel-headless" : ""} ${className}`.trim()}
    >
      <header className={`journal-panel-head${headless ? " journal-panel-head-inline" : ""}`}>
        {!headless && (
          <div className="journal-panel-head-text">
            <h3>{title}</h3>
            {subtitle && <p className="hint">{subtitle}</p>}
          </div>
        )}
        <div className="journal-panel-head-actions">
          {mode === "live" && (
            <span className="journal-live-badge" title={t("journal:liveHint")}>
              <span className="journal-live-dot" aria-hidden />
              {t("journal:mode.live")}
            </span>
          )}
          {showModeToggle && (
            <nav className="journal-mode-tabs" aria-label={t("journal:modeLabel")}>
              <button
                type="button"
                className={mode === "live" ? "active" : ""}
                onClick={() => onModeChange("live")}
              >
                {t("journal:mode.live")}
              </button>
              <button
                type="button"
                className={mode === "history" ? "active" : ""}
                onClick={() => onModeChange("history")}
              >
                {t("journal:mode.history")}
              </button>
            </nav>
          )}
          {typeof count === "number" && <span className="badge">{count}</span>}
          {exportFilenameBase && exportRows && (
            <JournalExportButtons
              filenameBase={exportFilenameBase}
              rows={exportRows}
              disabled={isLoading || Boolean(error)}
            />
          )}
        </div>
      </header>

      {mode === "history" && filters}

      {Boolean(error) && (
        <p className="hint error">{t("journal:loadError")}</p>
      )}
      {isLoading && <p className="hint">{t("common:action.loading")}</p>}
      {empty && !isLoading && !error && (
        <p className="hint">{emptyMessage ?? t("journal:empty")}</p>
      )}

      {!empty && !error && (
        <JournalScrollContext.Provider value={scrollRef}>
          <div
            ref={scrollRef}
            className="journal-scroll"
            style={{ maxHeight: typeof scrollMaxHeight === "number" ? `${scrollMaxHeight}px` : scrollMaxHeight }}
          >
            {children}
          </div>
        </JournalScrollContext.Provider>
      )}

      {footer}
    </section>
  );
}
