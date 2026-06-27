import type { DataRecord } from "../../../types";
import type { SheetConfig, SheetMode } from "../../../types/dashboard";
import type { SheetValues } from "./sheetFormulaEngine";
import { parseSheetRuntimeMeta, type SheetRuntimeMeta } from "./sheetRuntimeMeta";

type SheetCellRow = { cell?: unknown; value?: unknown };

export const SHEET_META_JSON_FIELD = "metaJson";

/** Lab model stores cells under sheetValues.rows (RECORD_LIST) → [{ rows: [{ cell, value }] }]. */
export function extractSheetCellRows(record: DataRecord | undefined): SheetCellRow[] {
  if (!record?.rows?.length) {
    return [];
  }
  const first = record.rows[0];
  const nested = first?.rows;
  if (Array.isArray(nested)) {
    return nested as SheetCellRow[];
  }
  // Legacy flat rows: [{ cell, value }]
  if (first && ("cell" in first || "value" in first)) {
    return record.rows as SheetCellRow[];
  }
  return [];
}

export function sheetValuesRecordSchema(
  existing: DataRecord | undefined,
  includeMetaJson = false
): DataRecord["schema"] {
  if (existing?.schema) {
    const fields = [...existing.schema.fields];
    if (includeMetaJson && !fields.some((field) => field.name === SHEET_META_JSON_FIELD)) {
      fields.push({ name: SHEET_META_JSON_FIELD, type: "STRING" });
    }
    return { ...existing.schema, fields };
  }
  const fields: DataRecord["schema"]["fields"] = [{ name: "rows", type: "RECORD_LIST" }];
  if (includeMetaJson) {
    fields.push({ name: SHEET_META_JSON_FIELD, type: "STRING" });
  }
  return {
    name: "sheetValues",
    fields,
  };
}

export function serializeSheetMeta(meta: SheetRuntimeMeta | null | undefined): string | undefined {
  if (!meta) {
    return undefined;
  }
  const json = JSON.stringify(meta);
  return parseSheetRuntimeMeta(JSON.parse(json)) ? json : undefined;
}

export function loadMetaFromVariableRecord(record: DataRecord | undefined): SheetRuntimeMeta | null {
  const raw = record?.rows?.[0]?.[SHEET_META_JSON_FIELD];
  if (typeof raw !== "string" || !raw.trim()) {
    return null;
  }
  try {
    return parseSheetRuntimeMeta(JSON.parse(raw));
  } catch {
    return null;
  }
}

export function loadValuesFromVariableRecord(record: DataRecord | undefined): SheetValues {
  const result: SheetValues = {};
  for (const row of extractSheetCellRows(record)) {
    const cell = String(row.cell ?? "").toUpperCase();
    if (cell) {
      result[cell] = String(row.value ?? "");
    }
  }
  return result;
}

export function loadValuesFromSession(
  params: Record<string, unknown>,
  sessionKey: string
): SheetValues {
  const raw = params[sessionKey];
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return {};
  }
  const result: SheetValues = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      result[key.toUpperCase()] = String(value);
    }
  }
  return result;
}

export function seedsFromConfig(config: SheetConfig): SheetValues {
  const result: SheetValues = {};
  for (const [addr, cell] of Object.entries(config.cells)) {
    if (cell.kind === "input" || cell.kind === "readonly") {
      if (cell.default !== undefined) {
        result[addr] = cell.default;
      }
    } else if (cell.kind === "formula" && cell.expr) {
      result[addr] = cell.expr.startsWith("=") ? cell.expr : `=${cell.expr}`;
    }
  }
  return result;
}

export function loadCellContents(
  record: DataRecord | undefined,
  config: SheetConfig,
  mode: SheetMode
): SheetValues {
  const seeds = mode === "free" ? seedsFromConfig(config) : defaultsFromConfig(config);
  const stored = loadValuesFromVariableRecord(record);
  if (Object.keys(stored).length === 0) {
    return seeds;
  }
  return { ...seeds, ...stored };
}

function defaultsFromConfig(config: SheetConfig): SheetValues {
  const result: SheetValues = {};
  for (const [addr, cell] of Object.entries(config.cells)) {
    if (cell.kind === "input" && cell.default !== undefined) {
      result[addr] = cell.default;
    }
  }
  return result;
}

export function saveValuesToSessionPatch(values: SheetValues): Record<string, string> {
  return { ...values };
}

export function saveValuesToVariableRecord(
  values: SheetValues,
  existing: DataRecord | undefined,
  meta?: SheetRuntimeMeta | null
): DataRecord {
  const metaJson = serializeSheetMeta(meta);
  const existingMetaJson = existing?.rows?.[0]?.[SHEET_META_JSON_FIELD];
  const includeMetaJson = metaJson !== undefined || typeof existingMetaJson === "string";
  const schema = sheetValuesRecordSchema(existing, includeMetaJson);
  const cellRows = Object.entries(values)
    .filter(([, value]) => value.trim() !== "")
    .map(([cell, value]) => ({ cell, value }));
  const row: Record<string, unknown> = { rows: cellRows };
  if (metaJson !== undefined) {
    row[SHEET_META_JSON_FIELD] = metaJson;
  } else if (typeof existingMetaJson === "string") {
    row[SHEET_META_JSON_FIELD] = existingMetaJson;
  }
  return { schema, rows: [row] };
}

export function hasSheetValuesSchema(record: DataRecord | undefined): boolean {
  const fields = record?.schema?.fields;
  return Boolean(fields?.some((field) => field.name === "rows"));
}

export function canWriteSheetValues(
  variable: { value?: DataRecord | null; writable?: boolean } | undefined
): boolean {
  if (!variable?.writable) {
    return false;
  }
  if (!variable.value) {
    return true;
  }
  return hasSheetValuesSchema(variable.value);
}

export function loadValuesFromVariable(
  record: DataRecord | undefined,
  config: SheetConfig,
  mode: SheetMode = "configured"
): SheetValues {
  return loadCellContents(record, config, mode);
}
