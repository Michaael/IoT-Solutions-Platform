import type { MimicFormatRule } from "../types/scadaMimic";
import { asBool, asNum } from "./utils";

export function applyFormatRules(
  values: Record<string, unknown>,
  rules: MimicFormatRule[] | undefined
): Record<string, string> {
  if (!rules?.length) return {};
  const merged: Record<string, string> = {};
  for (const rule of rules) {
    const raw = values[rule.bindingKey];
    if (matchesRule(raw, rule)) {
      Object.assign(merged, rule.style);
    }
  }
  return merged;
}

function matchesRule(raw: unknown, rule: MimicFormatRule): boolean {
  const op = rule.operator;
  const target = rule.value;
  if (typeof target === "boolean") {
    const left = asBool(raw);
    if (op === "==") return left === target;
    if (op === "!=") return left !== target;
    return false;
  }
  const leftNum = asNum(raw);
  const rightNum = typeof target === "number" ? target : asNum(target);
  if (leftNum == null || rightNum == null) {
    if (op === "==") return String(raw ?? "") === String(target);
    if (op === "!=") return String(raw ?? "") !== String(target);
    return false;
  }
  switch (op) {
    case ">":
      return leftNum > rightNum;
    case ">=":
      return leftNum >= rightNum;
    case "<":
      return leftNum < rightNum;
    case "<=":
      return leftNum <= rightNum;
    case "==":
      return leftNum === rightNum;
    case "!=":
      return leftNum !== rightNum;
    default:
      return false;
  }
}
