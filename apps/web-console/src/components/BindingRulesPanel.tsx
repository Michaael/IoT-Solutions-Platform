import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteBindingRule, fetchBindingRules, saveBindingRules } from "../api";
import type { BindingRule, BindingTargetKind } from "../types";
import BindingActivatorsEditor, { activatorsSummary } from "./BindingActivatorsEditor";
import BindingExpressionField from "./BindingExpressionField";
import { isTechnicalIdentifier } from "../utils/technicalIdentifier";
import {
  emptyBindingRule,
  emptyDashboardContextRule,
  isBindingRuleSaveable,
  isExistingBindingRule,
  mergeBindingRules,
  prepareBindingRuleForSave,
  targetKind,
  targetSummary,
} from "./bindingRulesUtils";

interface RuleTemplate {
  id: string;
  label: string;
  rule: BindingRule;
}

interface BindingRulesPanelProps {
  path: string;
  canManage: boolean;
  eventNames?: string[];
  variableNames?: string[];
  functionNames?: string[];
  dashboardMode?: boolean;
  ruleTemplates?: RuleTemplate[];
}

const TARGET_KINDS: BindingTargetKind[] = ["variable", "context", "event"];

export default function BindingRulesPanel({
  path,
  canManage,
  eventNames = [],
  variableNames = [],
  functionNames = [],
  dashboardMode = false,
  ruleTemplates = [],
}: BindingRulesPanelProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<BindingRule | null>(null);
  const targetDrafts = useRef<Partial<Record<BindingTargetKind, BindingRule["target"]>>>({});

  const rulesQuery = useQuery({
    queryKey: ["binding-rules", path],
    queryFn: () => fetchBindingRules(path),
  });

  const saveMutation = useMutation({
    mutationFn: (rule: BindingRule) => {
      const current = rulesQuery.data ?? [];
      return saveBindingRules(path, mergeBindingRules(current, rule));
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["binding-rules", path] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-context", path] });
      setEditing(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (ruleId: string) => deleteBindingRule(path, ruleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["binding-rules", path] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-context", path] });
    },
  });

  const startNewRule = () => {
    targetDrafts.current = {};
    setEditing(dashboardMode ? emptyDashboardContextRule() : emptyBindingRule());
  };

  const applyTemplate = (template: RuleTemplate) => {
    targetDrafts.current = {};
    const suffix = Date.now().toString(36).slice(-4);
    setEditing({
      ...template.rule,
      id: `${template.rule.id}-${suffix}`,
      name: template.label,
    });
  };

  const setTargetKind = (kind: BindingTargetKind) => {
    if (!editing) return;
    const currentKind = targetKind(editing);
    targetDrafts.current[currentKind] = editing.target;
    const nextTarget =
      targetDrafts.current[kind] ??
      (kind === "context"
        ? { kind, path: "params.mode" as const }
        : kind === "event"
          ? { kind, eventName: "" as const }
          : { kind, variableName: "", field: "value" as const });
    setEditing({ ...editing, target: nextTarget });
  };

  if (rulesQuery.isLoading) {
    return <p>{t("inspector:bindings.loading")}</p>;
  }

  const rules = rulesQuery.data ?? [];
  const editingKind = editing ? targetKind(editing) : "variable";

  return (
    <section className="panel">
      {canManage && (
        <div className="panel-toolbar">
          <button type="button" className="btn primary small" onClick={startNewRule}>
            {t("inspector:bindings.addRule")}
          </button>
          {ruleTemplates.map((template) => (
            <button
              key={template.id}
              type="button"
              className="btn small"
              onClick={() => applyTemplate(template)}
            >
              {template.label}
            </button>
          ))}
        </div>
      )}

      {rules.length === 0 && <p className="hint">{t("inspector:bindings.empty")}</p>}

      {rules.length > 0 && (
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>{t("common:table.id")}</th>
                <th>{t("inspector:bindings.column.target")}</th>
                <th>{t("inspector:bindings.column.expression")}</th>
                <th>{t("inspector:bindings.column.activators")}</th>
                <th>{t("common:table.enabled")}</th>
                {canManage && <th aria-label={t("common:table.actions")} />}
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id}>
                  <td><code>{rule.id}</code></td>
                  <td><code>{targetSummary(rule.target)}</code></td>
                  <td className="mono small" title={rule.expression}>{rule.expression || "—"}</td>
                  <td className="small">{activatorsSummary(rule)}</td>
                  <td>{rule.enabled ? t("common:action.yes") : t("common:action.no")}</td>
                  {canManage && (
                    <td>
                      <button type="button" className="btn small" onClick={() => { targetDrafts.current = {}; setEditing(rule); }}>{t("inspector:bindings.edit")}</button>
                      <button
                        type="button"
                        className="btn small danger"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(rule.id)}
                      >
                        {t("common:action.delete")}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <div className="modal-backdrop" onClick={() => setEditing(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <header>
              <h3>{editing.id ? t("inspector:bindings.ruleTitle", { id: editing.id }) : t("inspector:bindings.newRule")}</h3>
              <button type="button" className="icon-btn" onClick={() => setEditing(null)}>✕</button>
            </header>
            <section className="modal-section form-grid">
              <label className="full">
                ID
                <input
                  value={editing.id}
                  disabled={Boolean(isExistingBindingRule(rules, editing.id))}
                  onChange={(e) => setEditing({ ...editing, id: e.target.value })}
                  pattern="[A-Za-z0-9_-]+"
                  required
                  aria-invalid={Boolean(editing.id) && !isTechnicalIdentifier(editing.id, "pathSegment")}
                />
                {editing.id && !isTechnicalIdentifier(editing.id, "pathSegment") && (
                  <span className="hint error">{t("common:error.invalidPathSegment")}</span>
                )}
              </label>
              {(dashboardMode || editingKind !== "variable") && (
                <label className="full">
                  {t("inspector:bindings.targetKind")}
                  <select
                    value={editingKind}
                    onChange={(e) => setTargetKind(e.target.value as BindingTargetKind)}
                  >
                    {TARGET_KINDS.map((kind) => (
                      <option key={kind} value={kind}>
                        {t(`inspector:bindings.targetKind.${kind}`)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {editingKind === "variable" && (
                <label className="full">
                  {t("inspector:bindings.targetVariable")}
                  <input
                    value={editing.target.variableName ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        target: { ...editing.target, kind: "variable", variableName: e.target.value },
                      })
                    }
                    required
                  />
                </label>
              )}
              {editingKind === "context" && (
                <label className="full">
                  {t("inspector:bindings.targetContextPath")}
                  <input
                    value={editing.target.path ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        target: { ...editing.target, kind: "context", path: e.target.value },
                      })
                    }
                    placeholder="params.mode"
                    required
                  />
                  <span className="hint">{t("inspector:bindings.targetContextPathHint")}</span>
                </label>
              )}
              {editingKind === "event" && (
                <label className="full">
                  {t("inspector:bindings.targetEventName")}
                  <input
                    value={editing.target.eventName ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        target: { ...editing.target, kind: "event", eventName: e.target.value },
                      })
                    }
                    required
                  />
                </label>
              )}
              <label className="full">
                {t("inspector:bindings.column.expression")}
                <BindingExpressionField
                  value={editing.expression}
                  onChange={(expression) => setEditing({ ...editing, expression })}
                  objectPath={path}
                  variableNames={variableNames}
                  functionNames={functionNames}
                />
              </label>
              <label className="full">
                {t("inspector:bindings.condition")}
                <BindingExpressionField
                  value={editing.condition}
                  onChange={(condition) => setEditing({ ...editing, condition })}
                  placeholder={t("inspector:bindings.conditionPlaceholder")}
                  objectPath={path}
                  variableNames={variableNames}
                  functionNames={functionNames}
                />
                {dashboardMode && (
                  <span className="hint">{t("inspector:bindings.dashboardConditionHint")}</span>
                )}
              </label>
              <BindingActivatorsEditor
                activators={editing.activators}
                eventNames={eventNames}
                dashboardMode={dashboardMode}
                onChange={(activators) => setEditing({ ...editing, activators })}
              />
              <label className="checkbox-label inline full">
                <input
                  type="checkbox"
                  checked={editing.enabled}
                  onChange={(e) => setEditing({ ...editing, enabled: e.target.checked })}
                />
                {t("common:action.enabled")}
              </label>
            </section>
            {saveMutation.error && (
              <p className="hint error">{(saveMutation.error as Error).message}</p>
            )}
            <footer>
              <button type="button" className="btn" onClick={() => setEditing(null)}>{t("common:action.cancel")}</button>
              <button
                type="button"
                className="btn primary"
                disabled={!isBindingRuleSaveable(editing) || saveMutation.isPending}
                onClick={() => saveMutation.mutate(prepareBindingRuleForSave(editing))}
              >
                {t("common:action.save")}
              </button>
            </footer>
          </div>
        </div>
      )}
    </section>
  );
}
