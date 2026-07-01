import { useState } from "react";
import { useTranslation } from "react-i18next";
import BffDataTable from "../operator/BffDataTable";
import {
  columnLabels,
  parseAgentArtifacts,
  type AgentArtifactLink,
  type AgentArtifactTablePreview,
} from "../../utils/agentArtifacts";
import type { AgentPlanQuestion, OperatorAgentSuggestion } from "../../utils/operatorAgentArtifacts";

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

/** Strip leading "1." / "1)" so `<ol>` counters do not double-number agent steps. */
function normalizePlanStepText(step: string): string {
  return step.replace(/^\s*\d+[\.)]\s*/, "").trim() || step;
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

function PlanPanel({
  planGoal,
  planLayers,
  planSteps,
  questions,
  suggestions,
  onSuggestMessage,
  t,
}: {
  planGoal?: string;
  planLayers: string[];
  planSteps: string[];
  questions: AgentPlanQuestion[];
  suggestions: OperatorAgentSuggestion[];
  onSuggestMessage?: (message: string) => void;
  t: (key: string) => string;
}) {
  const primarySuggestions = suggestions.filter((item) => item.primary);
  const secondarySuggestions = suggestions.filter((item) => !item.primary);
  const hasActions =
    questions.length > 0 || primarySuggestions.length > 0 || secondarySuggestions.length > 0;

  return (
    <div className="agent-plan-panel">
      <header className="agent-plan-panel-head">
        <span className="agent-plan-panel-label">{t("agent.plan.title")}</span>
        {planGoal && <h3 className="agent-plan-panel-goal">{planGoal}</h3>}
      </header>

      {planLayers.length > 0 && (
        <div className="agent-plan-panel-layers" aria-label={t("agent.plan.layers")}>
          {planLayers.map((layer) => (
            <span key={layer} className="agent-plan-layer-tag">
              {layer}
            </span>
          ))}
        </div>
      )}

      {planSteps.length > 0 && (
        <ol className="agent-plan-panel-steps">
        {planSteps.map((step, index) => {
            const text = normalizePlanStepText(step);
            return (
              <li key={`${index}:${text}`} className="agent-plan-panel-step">
                <span className="agent-plan-panel-step-text">{text}</span>
              </li>
            );
          })}
        </ol>
      )}

      {hasActions && (
        <footer className="agent-plan-panel-actions">
          {questions.map((question) => (
            <div key={question.id ?? question.text} className="agent-plan-panel-question">
              {question.text && <p className="agent-plan-panel-question-text">{question.text}</p>}
              {question.options && question.options.length > 0 && (
                <div className="agent-plan-option-row" role="group" aria-label={question.text}>
                  {question.options.map((option) => (
                    <button
                      key={`${option.label}:${option.value}`}
                      type="button"
                      className="agent-plan-option-btn"
                      onClick={() =>
                        onSuggestMessage?.(
                          option.value?.trim() || option.label?.trim() || question.text || ""
                        )
                      }
                    >
                      {option.label ?? option.value}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}

          {(primarySuggestions.length > 0 || secondarySuggestions.length > 0) && (
            <div className="agent-plan-suggest-block">
              {questions.length > 0 && primarySuggestions.length > 0 && (
                <span className="agent-plan-suggest-divider" aria-hidden="true" />
              )}
              {primarySuggestions.map((item) => (
                <button
                  key={`${item.path ?? item.label}:${item.message}`}
                  type="button"
                  className="agent-plan-primary-btn"
                  onClick={() => onSuggestMessage?.(item.message)}
                >
                  {item.label}
                </button>
              ))}
              {secondarySuggestions.length > 0 && (
                <div className="agent-plan-secondary-row">
                  {secondarySuggestions.map((item) => (
                    <button
                      key={`${item.path ?? item.label}:${item.message}`}
                      type="button"
                      className="agent-plan-secondary-btn"
                      onClick={() => onSuggestMessage?.(item.message)}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </footer>
      )}
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
  const plan = parsed.plan;
  const questions = parsed.questions ?? [];
  const isPlan = parsed.phase === "plan" || Boolean(plan);
  const [activeTable, setActiveTable] = useState<AgentArtifactTablePreview | null>(null);

  const planGoal = typeof plan?.goal === "string" ? plan.goal : undefined;
  const planSteps = Array.isArray(plan?.steps) ? (plan.steps as string[]) : [];
  const planLayers = Array.isArray(plan?.layers) ? (plan.layers as string[]) : [];

  if (links.length === 0 && tables.length === 0 && suggestions.length === 0 && !isPlan && questions.length === 0) {
    return null;
  }

  return (
    <>
      <div className={`operator-agent-artifacts${isPlan ? " operator-agent-artifacts--plan" : ""}`}>
        {isPlan && (
          <PlanPanel
            planGoal={planGoal}
            planLayers={planLayers}
            planSteps={planSteps}
            questions={questions}
            suggestions={suggestions}
            onSuggestMessage={onSuggestMessage}
            t={t}
          />
        )}
        {!isPlan && questions.length > 0 && (
          <div className="agent-plan-questions">
            {questions.map((question) => (
              <div key={question.id ?? question.text} className="agent-plan-question">
                <p>{question.text}</p>
                {question.options && question.options.length > 0 && (
                  <div className="agent-plan-option-row">
                    {question.options.map((option) => (
                      <button
                        key={`${option.label}:${option.value}`}
                        type="button"
                        className="agent-plan-option-btn"
                        onClick={() =>
                          onSuggestMessage?.(
                            option.value?.trim() || option.label?.trim() || question.text || ""
                          )
                        }
                      >
                        {option.label ?? option.value}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
        {!isPlan && suggestions.length > 0 && (
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
