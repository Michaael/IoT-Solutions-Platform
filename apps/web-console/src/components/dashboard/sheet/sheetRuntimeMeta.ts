import type {
  SheetCellConfig,
  SheetCellFormat,
  SheetCellStyle,
  SheetMergeRange,
} from "../../../types/dashboard";

export interface SheetRuntimeMeta {
  rows?: number;
  cols?: number;
  cellStyles?: Record<string, { style?: SheetCellStyle; format?: SheetCellFormat }>;
  mergedCells?: SheetMergeRange[];
  columnWidths?: number[];
  rowHeights?: number[];
}

export function sheetMetaSessionKey(sessionKey: string): string {
  return `${sessionKey}__meta`;
}

export function parseSheetRuntimeMeta(raw: unknown): SheetRuntimeMeta | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return null;
  }
  const obj = raw as Record<string, unknown>;
  const meta: SheetRuntimeMeta = {};
  if (typeof obj.rows === "number") {
    meta.rows = obj.rows;
  }
  if (typeof obj.cols === "number") {
    meta.cols = obj.cols;
  }
  if (obj.cellStyles && typeof obj.cellStyles === "object" && !Array.isArray(obj.cellStyles)) {
    meta.cellStyles = obj.cellStyles as SheetRuntimeMeta["cellStyles"];
  }
  if (Array.isArray(obj.mergedCells)) {
    meta.mergedCells = obj.mergedCells as SheetMergeRange[];
  }
  if (Array.isArray(obj.columnWidths)) {
    meta.columnWidths = obj.columnWidths.filter((n): n is number => typeof n === "number");
  }
  if (Array.isArray(obj.rowHeights)) {
    meta.rowHeights = obj.rowHeights.filter((n): n is number => typeof n === "number");
  }
  if (
    meta.rows === undefined &&
    meta.cols === undefined &&
    !meta.cellStyles &&
    !meta.mergedCells &&
    !meta.columnWidths &&
    !meta.rowHeights
  ) {
    return null;
  }
  return meta;
}

export function mergeRuntimeIntoCells(
  cells: Record<string, SheetCellConfig>,
  meta?: SheetRuntimeMeta["cellStyles"]
): Record<string, SheetCellConfig> {
  if (!meta) {
    return cells;
  }
  const merged: Record<string, SheetCellConfig> = { ...cells };
  for (const [addr, patch] of Object.entries(meta)) {
    const upper = addr.toUpperCase();
    const base = merged[upper] ?? { kind: "input" as const };
    merged[upper] = {
      ...base,
      style: { ...base.style, ...patch.style },
      format: patch.format ?? base.format,
    };
  }
  return merged;
}

export function buildMergeHiddenSet(merges: SheetMergeRange[] | undefined): Set<string> {
  const hidden = new Set<string>();
  if (!merges) {
    return hidden;
  }
  for (const merge of merges) {
    const anchor = merge.anchor.toUpperCase();
    const anchorRc = parseAddress(anchor);
    if (!anchorRc) {
      continue;
    }
    for (let r = 0; r < merge.rowSpan; r++) {
      for (let c = 0; c < merge.colSpan; c++) {
        if (r === 0 && c === 0) {
          continue;
        }
        hidden.add(toAddress(anchorRc.row + r, anchorRc.col + c));
      }
    }
  }
  return hidden;
}

export function findMergeAt(
  merges: SheetMergeRange[] | undefined,
  address: string
): SheetMergeRange | undefined {
  if (!merges) {
    return undefined;
  }
  const upper = address.toUpperCase();
  return merges.find((m) => m.anchor.toUpperCase() === upper);
}

function parseAddress(address: string): { row: number; col: number } | null {
  const match = /^([A-Z]+)(\d+)$/i.exec(address.trim());
  if (!match) {
    return null;
  }
  let col = 0;
  for (const ch of match[1].toUpperCase()) {
    col = col * 26 + (ch.charCodeAt(0) - 64);
  }
  return { row: Number.parseInt(match[2], 10) - 1, col: col - 1 };
}

function toAddress(row: number, col: number): string {
  let n = col + 1;
  let letters = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    letters = String.fromCharCode(65 + rem) + letters;
    n = Math.floor((n - 1) / 26);
  }
  return `${letters}${row + 1}`;
}
