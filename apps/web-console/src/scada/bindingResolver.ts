import type { DashboardSession } from "../components/dashboard/DashboardContext";
import { resolveWidgetPath } from "../components/dashboard/dashboardUtils";
import { readFieldValue } from "../types/dashboard";
import type { VariableDto } from "../types";
import type { MimicBinding, MimicConnection, MimicElement } from "../types/scadaMimic";
import { asBool, asNum } from "./utils";

export interface ResolvedBindingValues {
  byElementId: Record<string, Record<string, unknown>>;
  byConnectionId: Record<string, Record<string, unknown>>;
}

export function collectBindingPaths(
  elements: MimicElement[],
  connections: MimicConnection[]
): string[] {
  const paths = new Set<string>();
  for (const el of elements) {
    for (const binding of Object.values(el.bindings)) {
      addPath(paths, binding);
    }
    for (const action of el.actions ?? []) {
      if (action.objectPath) paths.add(action.objectPath);
    }
  }
  for (const conn of connections) {
    for (const binding of Object.values(conn.bindings ?? {})) {
      if (binding) addPath(paths, binding);
    }
  }
  return [...paths];
}

function addPath(paths: Set<string>, binding: MimicBinding): void {
  if (binding.objectPath?.trim()) {
    paths.add(binding.objectPath.trim());
  }
}

export function resolveBindingValue(
  binding: MimicBinding | undefined,
  session: Pick<DashboardSession, "selection" | "params">,
  variablesByPath: Record<string, VariableDto[] | undefined>
): unknown {
  if (!binding?.variableName) return undefined;
  const objectPath = resolveWidgetPath(
    binding.objectPath,
    binding.selectionKey,
    session.selection,
    undefined,
    session.params
  );
  if (!objectPath) return undefined;
  const variables = variablesByPath[objectPath];
  const variable = variables?.find((v) => v.name === binding.variableName);
  const row = variable?.value?.rows?.[0];
  if (!row) return undefined;
  const raw = readFieldValue(row, binding.valueField ?? "value");
  return transformBindingValue(raw, binding.transform);
}

function transformBindingValue(raw: unknown, transform?: MimicBinding["transform"]): unknown {
  if (!transform) return raw;
  switch (transform) {
    case "bool":
      return asBool(raw);
    case "number":
      return asNum(raw);
    case "string":
      return raw == null ? "" : String(raw);
    default:
      return raw;
  }
}

export function resolveDocumentBindings(
  elements: MimicElement[],
  connections: MimicConnection[],
  session: Pick<DashboardSession, "selection" | "params">,
  variablesByPath: Record<string, VariableDto[] | undefined>
): ResolvedBindingValues {
  const byElementId: Record<string, Record<string, unknown>> = {};
  for (const el of elements) {
    const values: Record<string, unknown> = {};
    for (const [key, binding] of Object.entries(el.bindings)) {
      values[key] = resolveBindingValue(binding, session, variablesByPath);
    }
    byElementId[el.id] = values;
  }
  const byConnectionId: Record<string, Record<string, unknown>> = {};
  for (const conn of connections) {
    const values: Record<string, unknown> = {};
    if (conn.bindings?.flowing) {
      values.flowing = resolveBindingValue(conn.bindings.flowing, session, variablesByPath);
    }
    if (conn.bindings?.alarm) {
      values.alarm = resolveBindingValue(conn.bindings.alarm, session, variablesByPath);
    }
    byConnectionId[conn.id] = values;
  }
  return { byElementId, byConnectionId };
}
