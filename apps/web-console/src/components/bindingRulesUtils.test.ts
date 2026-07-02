import { describe, expect, it } from "vitest";
import type { BindingRule } from "../types";
import {
  emptyBindingRule,
  isBindingRuleSaveable,
  isExistingBindingRule,
  mergeBindingRules,
  prepareBindingRuleForSave,
} from "./bindingRulesUtils";

function sampleRule(overrides: Partial<BindingRule> = {}): BindingRule {
  return {
    id: "rule-a",
    name: "Rule A",
    enabled: true,
    order: 10,
    activators: {
      onStartup: false,
      onVariableChange: [{ objectPath: "self", variableName: "*" }],
      onEvent: null,
      periodicMs: 0,
    },
    condition: "",
    expression: "42",
    target: { variableName: "output", field: "value" },
    ...overrides,
  };
}

describe("emptyBindingRule", () => {
  it("creates editable draft with default activators", () => {
    const draft = emptyBindingRule();
    expect(draft.id).toBe("");
    expect(draft.enabled).toBe(true);
    expect(draft.target.field).toBe("value");
    expect(draft.activators.onVariableChange).toEqual([{ objectPath: "self", variableName: "*" }]);
  });
});

describe("mergeBindingRules", () => {
  it("replaces rule with same id and keeps sort order", () => {
    const merged = mergeBindingRules(
      [sampleRule({ id: "rule-b", order: 20 }), sampleRule({ id: "rule-a", order: 10 })],
      sampleRule({ id: "rule-a", order: 10, expression: "updated" }),
    );
    expect(merged.map((rule) => rule.id)).toEqual(["rule-a", "rule-b"]);
    expect(merged[0]?.expression).toBe("updated");
  });

  it("appends new rule and sorts by order then id", () => {
    const merged = mergeBindingRules(
      [sampleRule({ id: "rule-b", order: 20 })],
      sampleRule({ id: "rule-a", order: 10 }),
    );
    expect(merged.map((rule) => rule.id)).toEqual(["rule-a", "rule-b"]);
  });
});

describe("isBindingRuleSaveable", () => {
  it("requires id, target variable, and expression", () => {
    expect(isBindingRuleSaveable(sampleRule())).toBe(true);
    expect(isBindingRuleSaveable(sampleRule({ id: "  " }))).toBe(false);
    expect(isBindingRuleSaveable(sampleRule({ id: "привязка" }))).toBe(false);
    expect(isBindingRuleSaveable(sampleRule({ id: "rule 1" }))).toBe(false);
    expect(isBindingRuleSaveable(sampleRule({ target: { variableName: " ", field: "value" } }))).toBe(false);
    expect(isBindingRuleSaveable(sampleRule({ expression: "  " }))).toBe(false);
  });

  it("accepts context and event targets", () => {
    expect(
      isBindingRuleSaveable(
        sampleRule({
          target: { kind: "context", path: "params.mode" },
          expression: '"detail"',
        }),
      ),
    ).toBe(true);
    expect(
      isBindingRuleSaveable(
        sampleRule({
          target: { kind: "event", eventName: "alarm.raised" },
          expression: "payload",
        }),
      ),
    ).toBe(true);
    expect(
      isBindingRuleSaveable(
        sampleRule({ target: { kind: "context", path: "  " }, expression: "true" }),
      ),
    ).toBe(false);
  });
});

describe("prepareBindingRuleForSave", () => {
  it("trims ids and falls back name to id", () => {
    const prepared = prepareBindingRuleForSave(
      sampleRule({ id: "  rule-x  ", name: "  ", target: { variableName: "out", field: null } }),
    );
    expect(prepared.id).toBe("rule-x");
    expect(prepared.name).toBe("rule-x");
    expect(prepared.target.field).toBe("value");
  });
});

describe("isExistingBindingRule", () => {
  it("detects persisted rule ids", () => {
    const rules = [sampleRule({ id: "rule-a" }), sampleRule({ id: "rule-b" })];
    expect(isExistingBindingRule(rules, "rule-a")).toBe(true);
    expect(isExistingBindingRule(rules, "rule-new")).toBe(false);
  });
});
