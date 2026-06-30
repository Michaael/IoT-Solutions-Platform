import { useState } from "react";
import { useTranslation } from "react-i18next";
import BffDataTable from "../operator/BffDataTable";
import {
  columnLabels,
  parseAgentArtifacts,
  type AgentArtifactLink,
  type AgentArtifactTablePreview,
} from "../../utils/agentArtifacts";

export interface AgentChatArtifactsProps {
  result?: Record<string, unknown>;
  i18nNs?: "ai" | "operator";
  onSuggestMessage?: (message: string) => void;
  onOpenDashboard?: (path: string) => void;
  onOpenReport?: (path: string) => void;
}

function openLink(link: AgentArtifactLink, handlers: AgentChatArtifactsProps) {
  if (link.kind === "report") {
    if (handlers.onOpenReport) {
      handlers.onOpenReport(link.path);
      return;
    }
  } else if (handlers.onOpenDashboard) {
    handlers.onOpenDashboard(link.path);
    return;
  }
  if (link.url) {
    window.location.assign(link.url);
  } else if (link.path) {
    window.location.assign(`/?path=${encodeURIComponent(link.path)}`);
  }
}

function TablePreviewModal({
  table,
  onClose,
  i18nNs,
}: {
  table: AgentArtifactTablePreview;
  onClose: () => void;
  i18nNs: "ai" | "operator";
}) {
  const { t } = useTranslation(i18nNs);
  const title = table.title ?? table.reportPath ?? t("agent.tablePreview");
  return (
    <div className="operator-agent-modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="operator-agent-modal"
        role="dialog"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="operator-agent-modal-head">
          <div>
            <strong>{title}</strong>
            {table.truncated && <p className="hint">{t("agent.tableTruncated")}</p>}
          </div>
          <button type="button" className="btn small" onClick={onClose} aria-label={t("agent.closeTable")}>
            ×
          </button>
        </header>
        <div className="operator-agent-modal-body">
          <BffDataTable rows={table.rows} labels={columnLabels(table)} />
        </div>
      </div>
    </div>
  );
}

export default function AgentChatArtifacts({
  result,
  i18nNs = "ai",
  onSuggestMessage,
  onOpenDashboard,
  onOpenReport,
}: AgentChatArtifactsProps) {
  const { t } = useTranslation(i18nNs);
  const parsed = parseAgentArtifacts(result);
  const links = parsed.links ?? [];
  const tables = parsed.tables ?? [];
  const suggestions = parsed.suggestions ?? [];
  const [activeTable, setActiveTable] = useState<AgentArtifactTablePreview | null>(null);

  if (links.length === 0 && tables.length === 0 && suggestions.length === 0) {
    return null;
  }

  return (
    <>
      <div className="operator-agent-artifacts">
        {suggestions.length > 0 && (
          <div className="operator-agent-suggestions-inline">
            <p className="hint">{t("agent.pickOption")}</p>
            <ul>
              {suggestions.map((item) => (
                <li key={`${item.path ?? item.label}:${item.message}`}>
                  <button
                    type="button"
                    className={`btn link${item.primary ? " primary" : ""}`}
                    onClick={() => onSuggestMessage?.(item.message)}
                  >
                    {item.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {links.length > 0 && (
          <div className="operator-agent-artifact-links">
            {links.map((link) => (
              <button
                key={`${link.kind}:${link.path}`}
                type="button"
                className="btn small"
                onClick={() => openLink(link, { onOpenDashboard, onOpenReport })}
              >
                {link.kind === "report" ? t("agent.openReport") : t("agent.openDashboard")}: {link.title}
              </button>
            ))}
          </div>
        )}
        {tables.map((table, index) => (
          <button
            key={`${table.reportPath ?? table.title ?? "table"}-${index}`}
            type="button"
            className="btn small primary"
            onClick={() => setActiveTable(table)}
          >
            {t("agent.openTable", {
              title: table.title ?? table.reportPath ?? t("agent.tablePreview"),
              count: table.rowCount ?? table.rows.length,
            })}
          </button>
        ))}
      </div>
      {activeTable && (
        <TablePreviewModal table={activeTable} onClose={() => setActiveTable(null)} i18nNs={i18nNs} />
      )}
    </>
  );
}

export function AgentStarterSuggestions({
  onPick,
  i18nNs = "ai",
  suggestionKeys,
}: {
  onPick: (message: string) => void;
  i18nNs?: "ai" | "operator";
  suggestionKeys: string[];
}) {
  const { t } = useTranslation(i18nNs);

  return (
    <div className="operator-agent-suggestions">
      <p className="op-muted">{t("agent.emptyHint")}</p>
      <ul>
        {suggestionKeys.map((key) => (
          <li key={key}>
            <button type="button" className="btn link" onClick={() => onPick(t(key))}>
              {t(key)}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
