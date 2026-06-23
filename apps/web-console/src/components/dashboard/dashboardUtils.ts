import type { DataSchema } from "../../types";
import type { DashboardSession } from "./DashboardContext";
import type { FunctionFormField } from "../../types/dashboard";

export function resolveWidgetPath(
  objectPath: string | undefined,
  selectionKey: string | undefined,
  selection: Record<string, string>,
  contextPathKey?: string,
  params?: Record<string, unknown>
): string {
  if (selectionKey && selection[selectionKey]) {
    return selection[selectionKey];
  }
  if (contextPathKey && params) {
    const fromParams = params[contextPathKey];
    if (typeof fromParams === "string" && fromParams.trim()) {
      return fromParams;
    }
  }
  return objectPath ?? "";
}

export function resolveContextPath(
  staticPath: string | undefined,
  pathKey: string | undefined,
  session: Pick<DashboardSession, "selection" | "params">
): string {
  if (staticPath?.trim()) {
    return staticPath.trim();
  }
  if (!pathKey) {
    return "";
  }
  const fromSelection = session.selection[pathKey];
  if (fromSelection?.trim()) {
    return fromSelection;
  }
  const fromParams = session.params[pathKey];
  if (typeof fromParams === "string" && fromParams.trim()) {
    return fromParams;
  }
  return "";
}

export function resolveContextParam(
  paramKey: string | undefined,
  params: Record<string, unknown>
): unknown {
  if (!paramKey) {
    return undefined;
  }
  return params[paramKey];
}

export function parseJsonObject(
  raw: string | undefined
): Record<string, unknown> | undefined {
  if (!raw?.trim()) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

export function parseSelectionJson(raw?: string): Record<string, string> | undefined {
  const obj = parseJsonObject(raw);
  if (!obj) {
    return undefined;
  }
  const result: Record<string, string> = {};
  for (const [key, value] of Object.entries(obj)) {
    if (typeof value === "string") {
      result[key] = value;
    }
  }
  return Object.keys(result).length > 0 ? result : undefined;
}

export function buildFunctionInput(
  fields: FunctionFormField[],
  values: Record<string, string | number>,
  inputSchema?: DataSchema
): { schema: DataSchema; rows: Array<Record<string, unknown>> } {
  const schema: DataSchema = inputSchema ?? {
    name: "functionInput",
    fields: fields.map((field) => ({
      name: field.name,
      type: field.type === "number" ? "DOUBLE" : "STRING",
    })),
  };
  const row: Record<string, unknown> = {};
  for (const field of fields) {
    const raw = values[field.name];
    if (raw === undefined || raw === "") continue;
    row[field.name] = field.type === "number" ? Number(raw) : raw;
  }
  return { schema, rows: [row] };
}

export function parseJsonArray<T>(raw: string | undefined, fallback: T[]): T[] {
  if (!raw?.trim()) return fallback;
  try {
    const parsed = JSON.parse(raw) as T[];
    return Array.isArray(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

export function formatDispatchStatus(status: unknown): string {
  if (status == null) return "—";
  const value = String(status);
  const labels: Record<string, string> = {
    planned: "План",
    ready: "Готов",
    filling: "Налив",
    completed: "Завершён",
    closed: "Закрыт",
  };
  return labels[value] ?? value;
}

export const DISPATCH_STATUS_COLORS: Record<string, string> = {
  planned: "#8b949e",
  ready: "#2f81f7",
  filling: "#d29922",
  completed: "#3fb950",
  closed: "#6e7681",
};
