import { useState } from "react";
import { useTranslation } from "react-i18next";
import BffDataTable from "../operator/BffDataTable";
import {
  columnLabels,
  parseAgentArtifacts,
  type AgentArtifactLink,
  type AgentArtifactTablePreview,
} from "../../utils/agentArtifacts";
import type {
  AgentGapMatrixRow,
  AgentPlanQuestion,
  AgentPlanSection,
  AgentSpecBrief,
  OperatorAgentSuggestion,
} from "../../utils/operatorAgentArtifacts";
import {
  formatPlanQuestionAnswer,
  isPlanApprovalSuggestion,
  localizeCompletenessGaps,
} from "../../utils/operatorAgentArtifacts";

export interface AgentChatArtifactsProps {
  result?: Record<string, unknown>;
  i18nNs?: "ai" | "operator";
  /** Sends immediately (suggestions, approval buttons). */
  onSuggestMessage?: (message: string) => void;
  /** Appends to chat input — plan question options (user sends when ready). */
  onAppendToInput?: (text: string) => void;
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

function PlanSectionBlock({ section }: { section: AgentPlanSection }) {
  const steps = section.steps ?? [];
  const metaTags = [...(section.objectTypes ?? []), ...(section.tools ?? [])].filter(Boolean);

  return (
    <section className="agent-plan-section" aria-labelledby={section.id ? `plan-section-${section.id}` : undefined}>
      {section.title && (
        <h4 className="agent-plan-section-title" id={section.id ? `plan-section-${section.id}` : undefined}>
          {section.title}
        </h4>
      )}
      {section.summary && <p className="agent-plan-section-summary">{section.summary}</p>}
      {section.relatedFrIds && section.relatedFrIds.length > 0 && (
        <div className="agent-plan-section-fr" aria-label="Related requirements">
          {section.relatedFrIds.map((fr) => (
            <span key={fr} className="agent-plan-fr-tag">
              {fr}
            </span>
          ))}
        </div>
      )}
      {metaTags.length > 0 && (
        <div className="agent-plan-section-meta" aria-label="Object types and tools">
          {metaTags.map((tag) => (
            <span key={`${section.id ?? section.title}:${tag}`} className="agent-plan-section-tag">
              {tag}
            </span>
          ))}
        </div>
      )}
      {steps.length > 0 && (
        <ol className="agent-plan-section-steps">
          {steps.map((step, index) => {
            const text = normalizePlanStepText(step);
            return (
              <li key={`${index}:${text}`} className="agent-plan-panel-step">
                <span className="agent-plan-panel-step-text">{text}</span>
              </li>
            );
          })}
        </ol>
      )}
      {section.deliverables && section.deliverables.length > 0 && (
        <ul className="agent-plan-section-deliverables">
          {section.deliverables.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function AnalyticalIntakeBlock({
  executiveSummary,
  specBrief,
  gapMatrix,
  assumptions,
  planCompletenessGaps,
  t,
  language,
}: {
  executiveSummary?: string;
  specBrief?: AgentSpecBrief;
  gapMatrix?: AgentGapMatrixRow[];
  assumptions?: string[];
  planCompletenessGaps?: string[];
  t: (key: string) => string;
  language: string;
}) {
  const displayGaps = localizeCompletenessGaps(planCompletenessGaps, language);
  const hasBrief =
    specBrief?.title ||
    specBrief?.businessGoal ||
    (specBrief?.functionalRequirements?.length ?? 0) > 0 ||
    (specBrief?.entities?.length ?? 0) > 0;
  if (!executiveSummary && !hasBrief && !(gapMatrix?.length ?? 0) && !(assumptions?.length ?? 0)) {
    return displayGaps?.length ? (
      <div className="agent-plan-completeness-gaps" role="status">
        <strong>{t("agent.plan.completenessGaps")}</strong>
        <ul>
          {displayGaps.map((gap) => (
            <li key={gap}>{gap}</li>
          ))}
        </ul>
      </div>
    ) : null;
  }

  return (
    <div className="agent-plan-analytical">
      {executiveSummary && (
        <section className="agent-plan-executive-summary">
          <h4>{t("agent.plan.executiveSummary")}</h4>
          <p>{executiveSummary}</p>
        </section>
      )}
      {hasBrief && specBrief && (
        <section className="agent-plan-spec-brief">
          <h4>{t("agent.plan.specBrief")}</h4>
          {specBrief.title && <p className="agent-plan-spec-title">{specBrief.title}</p>}
          {specBrief.businessGoal && <p>{specBrief.businessGoal}</p>}
          {specBrief.entities && specBrief.entities.length > 0 && (
            <ul className="agent-plan-entity-list">
              {specBrief.entities.map((entity) => (
                <li key={entity.id ?? entity.label}>
                  <strong>{entity.id ?? entity.label}</strong>
                  {entity.label && entity.id ? `: ${entity.label}` : ""}
                  {entity.kind ? ` (${entity.kind})` : ""}
                </li>
              ))}
            </ul>
          )}
          {specBrief.functionalRequirements && specBrief.functionalRequirements.length > 0 && (
            <div className="agent-plan-fr-table-wrap">
              <table className="agent-plan-fr-table">
                <thead>
                  <tr>
                    <th>{t("agent.plan.frId")}</th>
                    <th>{t("agent.plan.frTitle")}</th>
                    <th>{t("agent.plan.frSource")}</th>
                  </tr>
                </thead>
                <tbody>
                  {specBrief.functionalRequirements.map((fr) => (
                    <tr key={fr.id ?? fr.title}>
                      <td>{fr.id}</td>
                      <td>{fr.title}</td>
                      <td className="agent-plan-fr-source">{fr.sourcePhrase}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
      {gapMatrix && gapMatrix.length > 0 && (
        <section className="agent-plan-gap-matrix">
          <h4>{t("agent.plan.gapMatrix")}</h4>
          <ul>
            {gapMatrix.map((row) => (
              <li key={`${row.requirementId}:${row.capabilityId}`}>
                {row.requirementId} → {row.capabilityId} ({row.status ?? "full"})
              </li>
            ))}
          </ul>
        </section>
      )}
      {assumptions && assumptions.length > 0 && (
        <section className="agent-plan-assumptions">
          <h4>{t("agent.plan.assumptions")}</h4>
          <ul>
            {assumptions.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>
      )}
      {displayGaps && displayGaps.length > 0 && (
        <div className="agent-plan-completeness-gaps" role="status">
          <strong>{t("agent.plan.completenessGaps")}</strong>
          <ul>
            {displayGaps.map((gap) => (
              <li key={gap}>{gap}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function PlanPanel({
  planGoal,
  planLayers,
  planSections,
  planSteps,
  executiveSummary,
  specBrief,
  gapMatrix,
  assumptions,
  planCompletenessGaps,
  questions,
  suggestions,
  onSuggestMessage,
  onAppendToInput,
  t,
  language,
}: {
  planGoal?: string;
  planLayers: string[];
  planSections: AgentPlanSection[];
  planSteps: string[];
  executiveSummary?: string;
  specBrief?: AgentSpecBrief;
  gapMatrix?: AgentGapMatrixRow[];
  assumptions?: string[];
  planCompletenessGaps?: string[];
  questions: AgentPlanQuestion[];
  suggestions: OperatorAgentSuggestion[];
  onSuggestMessage?: (message: string) => void;
  onAppendToInput?: (text: string) => void;
  t: (key: string) => string;
  language: string;
}) {
  const hasCompletenessGaps = (planCompletenessGaps?.length ?? 0) > 0;
  const actionableSuggestions = hasCompletenessGaps
    ? suggestions.filter((item) => !isPlanApprovalSuggestion(item))
    : suggestions;
  const primarySuggestions = actionableSuggestions.filter((item) => item.primary);
  const secondarySuggestions = actionableSuggestions.filter((item) => !item.primary);
  const hasActions =
    questions.length > 0 || primarySuggestions.length > 0 || secondarySuggestions.length > 0;

  return (
    <div className="agent-plan-panel">
      <header className="agent-plan-panel-head">
        <span className="agent-plan-panel-label">{t("agent.plan.title")}</span>
        {planGoal && <h3 className="agent-plan-panel-goal">{planGoal}</h3>}
      </header>

      <AnalyticalIntakeBlock
        executiveSummary={executiveSummary}
        specBrief={specBrief}
        gapMatrix={gapMatrix}
        assumptions={assumptions}
        planCompletenessGaps={planCompletenessGaps}
        t={t}
        language={language}
      />

      {planLayers.length > 0 && (
        <div className="agent-plan-panel-layers" aria-label={t("agent.plan.layers")}>
          {planLayers.map((layer) => (
            <span key={layer} className="agent-plan-layer-tag">
              {layer}
            </span>
          ))}
        </div>
      )}

      {planSections.length > 0 && (
        <div className="agent-plan-sections" aria-label={t("agent.plan.sections")}>
          {planSections.map((section) => (
            <PlanSectionBlock key={section.id ?? section.title ?? section.summary} section={section} />
          ))}
        </div>
      )}

      {planSections.length === 0 && planSteps.length > 0 && (
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
                      onClick={() => {
                        const line = formatPlanQuestionAnswer(question, option);
                        if (line) {
                          onAppendToInput?.(line);
                        }
                      }}
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
  onAppendToInput,
  onOpenDashboard,
  onOpenReport,
}: AgentChatArtifactsProps) {
  const { t, i18n } = useTranslation(i18nNs);
  const parsed = parseAgentArtifacts(result);
  const links = parsed.links ?? [];
  const tables = parsed.tables ?? [];
  const suggestions = parsed.suggestions ?? [];
  const plan = parsed.plan;
  const questions = parsed.questions ?? [];
  const isPlan = parsed.phase === "plan" || Boolean(plan);
  const [activeTable, setActiveTable] = useState<AgentArtifactTablePreview | null>(null);

  const planGoal = typeof plan?.goal === "string" ? plan.goal : undefined;
  const planSteps = Array.isArray(plan?.steps) ? (plan.steps as string[]).filter(Boolean) : [];
  const planLayers = Array.isArray(plan?.layers) ? (plan.layers as string[]).filter(Boolean) : [];
  const planSections = Array.isArray(plan?.sections) ? (plan.sections as AgentPlanSection[]) : [];
  const hasPlanContent = Boolean(
    planGoal ||
      planSteps.length > 0 ||
      planLayers.length > 0 ||
      planSections.length > 0 ||
      parsed.executiveSummary ||
      parsed.specBrief ||
      (parsed.gapMatrix?.length ?? 0) > 0
  );

  if (links.length === 0 && tables.length === 0 && suggestions.length === 0 && !hasPlanContent && questions.length === 0) {
    return null;
  }

  const showPlanPanel = isPlan && hasPlanContent;

  return (
    <>
      <div className={`operator-agent-artifacts${showPlanPanel ? " operator-agent-artifacts--plan" : ""}`}>
        {showPlanPanel && (
          <PlanPanel
            planGoal={planGoal}
            planLayers={planLayers}
            planSections={planSections}
            planSteps={planSteps}
            executiveSummary={parsed.executiveSummary}
            specBrief={parsed.specBrief}
            gapMatrix={parsed.gapMatrix}
            assumptions={parsed.assumptions}
            planCompletenessGaps={parsed.planCompletenessGaps}
            questions={questions}
            suggestions={suggestions}
            onSuggestMessage={onSuggestMessage}
            onAppendToInput={onAppendToInput}
            t={t}
            language={i18n.language}
          />
        )}
        {!showPlanPanel && questions.length > 0 && (
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
                        onClick={() => {
                          const line = formatPlanQuestionAnswer(question, option);
                          if (line) {
                            onAppendToInput?.(line);
                          }
                        }}
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
        {!showPlanPanel && suggestions.length > 0 && (
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
