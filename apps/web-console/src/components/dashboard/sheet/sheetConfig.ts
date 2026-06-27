import type { SheetConfig, SheetCellConfig, SpreadsheetWidget, SheetMode } from "../../../types/dashboard";
import { parseJsonObject } from "../dashboardUtils";
import { defaultColLabels } from "./sheetAddress";

export function resolveSheetMode(widget: SpreadsheetWidget): SheetMode {
  return widget.sheetMode === "configured" ? "configured" : "free";
}

export const FREE_SHEET_CONFIG: SheetConfig = {
  rows: 20,
  cols: 8,
  frozenRows: 0,
  cells: {
    A1: { kind: "input", default: "10" },
    B1: { kind: "formula", expr: "=A1*2" },
  },
};

export const DEFAULT_SHEET_CONFIG: SheetConfig = {
  rows: 10,
  cols: 4,
  frozenRows: 1,
  frozenCols: 0,
  cells: {
    A1: { kind: "label", text: "A" },
    B1: { kind: "label", text: "B" },
    A2: { kind: "input", default: "10" },
    B2: { kind: "formula", expr: "=A2*2" },
    C2: { kind: "formula", expr: "=SUM(A2:A10)" },
  },
};

export function parseSheetConfig(raw?: string): SheetConfig | undefined {
  const obj = parseJsonObject(raw);
  if (!obj || typeof obj.rows !== "number" || typeof obj.cols !== "number") {
    return undefined;
  }
  const cells: Record<string, SheetCellConfig> = {};
  const rawCells = obj.cells;
  if (rawCells && typeof rawCells === "object" && !Array.isArray(rawCells)) {
    for (const [addr, cell] of Object.entries(rawCells)) {
      if (cell && typeof cell === "object" && !Array.isArray(cell)) {
        const c = cell as Record<string, unknown>;
        if (typeof c.kind === "string") {
          cells[addr.toUpperCase()] = c as unknown as SheetCellConfig;
        }
      }
    }
  }
  const columnFilters = Array.isArray(obj.columnFilters)
    ? (obj.columnFilters as SheetConfig["columnFilters"])
    : undefined;
  const dataRegion =
    obj.dataRegion && typeof obj.dataRegion === "object" && !Array.isArray(obj.dataRegion)
      ? (obj.dataRegion as SheetConfig["dataRegion"])
      : undefined;
  const conditionalStyles = Array.isArray(obj.conditionalStyles)
    ? (obj.conditionalStyles as SheetConfig["conditionalStyles"])
    : undefined;
  const mergedCells = Array.isArray(obj.mergedCells)
    ? (obj.mergedCells as SheetConfig["mergedCells"])
    : undefined;
  return {
    rows: Math.max(1, Math.min(500, obj.rows)),
    cols: Math.max(1, Math.min(52, obj.cols)),
    frozenRows: typeof obj.frozenRows === "number" ? obj.frozenRows : undefined,
    frozenCols: typeof obj.frozenCols === "number" ? obj.frozenCols : undefined,
    colLabels: Array.isArray(obj.colLabels)
      ? (obj.colLabels as string[])
      : defaultColLabels(obj.cols),
    cells,
    columnFilters,
    dataRegion,
    conditionalStyles,
    mergedCells,
  };
}

export function sheetConfigToJson(config: SheetConfig): string {
  return JSON.stringify(config, null, 2);
}

export function resolveSheetConfig(widget: SpreadsheetWidget): SheetConfig {
  const parsed = parseSheetConfig(widget.sheetConfigJson);
  if (parsed) {
    return parsed;
  }
  return resolveSheetMode(widget) === "free" ? FREE_SHEET_CONFIG : DEFAULT_SHEET_CONFIG;
}

export const CALCULATOR_SHEET_CONFIG: SheetConfig = {
  rows: 6,
  cols: 4,
  frozenRows: 1,
  cells: {
    A1: { kind: "label", text: "A (int)" },
    B1: { kind: "label", text: "B (float)" },
    C1: { kind: "label", text: "Sum" },
    D1: { kind: "label", text: "A × 110%" },
    A2: {
      kind: "input",
      default: "10",
      validation: { type: "range", min: 0, max: 1000, message: "0–1000" },
    },
    B2: { kind: "input", default: "5.5" },
    C2: { kind: "formula", expr: "=A2+B2", format: { type: "number", decimals: 2 } },
    D2: { kind: "formula", expr: "=A2*1.1", format: { type: "number", decimals: 2 } },
    A4: { kind: "label", text: "Σ A column" },
    C4: { kind: "formula", expr: "=SUM(A2:A10)", format: { type: "number", decimals: 0 } },
  },
  columnFilters: [{ column: "A" }],
};

export function defaultSessionKey(widget: SpreadsheetWidget): string {
  return widget.sessionKey?.trim() || `sheet:${widget.id}`;
}
