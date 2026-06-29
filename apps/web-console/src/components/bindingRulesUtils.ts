import type { BindingRule, BindingTarget } from "../types";
import { defaultBindingActivators } from "./bindingActivatorsUtils";

export function emptyBindingRule(): BindingRule {
  return {
    id: "",
    name: "",
    enabled: true,
    order: 0,
    activators: defaultBindingActivators(),
    condition: "",
    expression: "",
    target: { kind: "variable", variableName: "", field: "value" },
  };
}

export function emptyDashboardContextRule(): BindingRule {
  return {
    id: "",
    name: "",
    enabled: true,
    order: 0,
    activators: {
      onStartup: false,
      onVariableChange: [],
      onEvent: null,
      periodicMs: 0,
      onContextChange: true,
    },
    condition: "",
    expression: "",
    target: { kind: "context", path: "params.mode" },
  };
}

export function targetKind(rule: BindingRule): NonNullable<BindingTarget["kind"]> {
  return rule.target.kind ?? "variable";
}

export function targetSummary(target: BindingTarget): string {
  const kind = target.kind ?? "variable";
  if (kind === "context") {
    return `context.${target.path ?? "?"}`;
  }
  if (kind === "event") {
    return `event:${target.eventName ?? "?"}`;
  }
  return target.variableName?.trim() || "—";
}

export function mergeBindingRules(rules: BindingRule[], rule: BindingRule): BindingRule[] {
  const next = rules.filter((item) => item.id !== rule.id);
  next.push(rule);
  next.sort((a, b) => a.order - b.order || a.id.localeCompare(b.id));
  return next;
}

export function isBindingRuleSaveable(rule: BindingRule): boolean {
  if (!rule.id.trim() || !rule.expression.trim()) {
    return false;
  }
  const kind = targetKind(rule);
  if (kind === "context") {
    return Boolean(rule.target.path?.trim());
  }
  if (kind === "event") {
    return Boolean(rule.target.eventName?.trim());
  }
  return Boolean(rule.target.variableName?.trim());
}

export function prepareBindingRuleForSave(rule: BindingRule): BindingRule {
  const trimmedId = rule.id.trim();
  const kind = targetKind(rule);
  return {
    ...rule,
    id: trimmedId,
    name: rule.name?.trim() || trimmedId,
    target: {
      kind,
      variableName: kind === "variable" ? (rule.target.variableName?.trim() ?? "") : rule.target.variableName ?? null,
      field: rule.target.field ?? "value",
      path: kind === "context" ? (rule.target.path?.trim() ?? "") : rule.target.path ?? null,
      eventName: kind === "event" ? (rule.target.eventName?.trim() ?? "") : rule.target.eventName ?? null,
    },
  };
}

export function isExistingBindingRule(rules: BindingRule[], ruleId: string): boolean {
  return rules.some((rule) => rule.id === ruleId);
}
