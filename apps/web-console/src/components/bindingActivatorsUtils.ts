import type { BindingActivators, BindingVariableRef } from "../types";

export const CUSTOM_BINDING_EVENT = "__custom__";

export function defaultBindingActivators(): BindingActivators {
  return {
    onStartup: false,
    onVariableChange: [{ objectPath: "self", variableName: "*" }],
    onEvent: null,
    periodicMs: 0,
  };
}

export function patchBindingActivators(
  activators: BindingActivators,
  next: Partial<BindingActivators>,
): BindingActivators {
  return { ...activators, ...next };
}

export function remoteActivatorRef(activators: BindingActivators) {
  return activators.onVariableChange.find((ref) => ref.objectPath !== "self");
}

export function resolveOnEventSelectValue(
  activators: BindingActivators,
  eventNames: string[],
): string {
  if (!activators.onEvent) {
    return "";
  }
  if (eventNames.includes(activators.onEvent)) {
    return activators.onEvent;
  }
  return CUSTOM_BINDING_EVENT;
}

export function buildRemoteVariableChange(
  path: string,
  variableName: string,
): BindingVariableRef[] {
  const trimmedPath = path.trim();
  if (!trimmedPath) {
    return [{ objectPath: "self", variableName: "*" }];
  }
  return [{
    objectPath: trimmedPath,
    variableName: variableName.trim() || "*",
  }];
}

export function resolveOnEventAfterSelect(
  currentEvent: string | null,
  selectedValue: string,
  eventNames: string[],
): string | null {
  if (!selectedValue) {
    return null;
  }
  if (selectedValue === CUSTOM_BINDING_EVENT) {
    return currentEvent && !eventNames.includes(currentEvent) ? currentEvent : "";
  }
  return selectedValue;
}

export function activatorsSummary(rule: { activators: BindingActivators }): string {
  const parts: string[] = [];
  if (rule.activators.onStartup) {
    parts.push("startup");
  }
  for (const ref of rule.activators.onVariableChange ?? []) {
    parts.push(`${ref.objectPath}:${ref.variableName}`);
  }
  if (rule.activators.onContextChange) {
    parts.push("context");
  }
  if (rule.activators.onEvent) {
    parts.push(`event:${rule.activators.onEvent}`);
  }
  if (rule.activators.periodicMs > 0) {
    parts.push(`${rule.activators.periodicMs}ms`);
  }
  return parts.length > 0 ? parts.join(", ") : "—";
}
