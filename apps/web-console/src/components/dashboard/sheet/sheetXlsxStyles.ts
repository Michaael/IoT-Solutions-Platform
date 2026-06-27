import type { SheetCellFormat, SheetCellStyle, SheetMergeRange } from "../../../types/dashboard";
import { a1ToRowCol, rowColToA1 } from "./sheetAddress";

type ExcelCell = {
  value?: unknown;
  formula?: string;
  text?: string;
  font?: { bold?: boolean; color?: { argb?: string } };
  fill?: { type?: string; fgColor?: { argb?: string } };
  alignment?: { horizontal?: string; vertical?: string };
  numFmt?: string;
};

type ExcelWorksheet = {
  getCell: (row: number, col: number) => ExcelCell;
  mergeCells: (range: string) => void;
  getColumn: (index: number) => { width?: number };
  getRow: (index: number) => { height?: number };
  model?: { merges?: string[] };
};

function argbToCss(argb?: string): string | undefined {
  if (!argb || argb.length < 6) {
    return undefined;
  }
  const hex = argb.length >= 8 ? argb.slice(2) : argb;
  return `#${hex.slice(0, 6)}`;
}

function formatFromNumFmt(numFmt?: string): SheetCellFormat | undefined {
  if (!numFmt) {
    return undefined;
  }
  if (numFmt.includes("%")) {
    return { type: "number", decimals: 0, suffix: "%" };
  }
  const decimalMatch = numFmt.match(/0\.(0+)/);
  if (decimalMatch) {
    return { type: "number", decimals: decimalMatch[1].length };
  }
  if (numFmt.includes("0") || numFmt.includes("#")) {
    return { type: "number", decimals: 0 };
  }
  return undefined;
}

export function styleFromExcelCell(cell: ExcelCell): SheetCellStyle | undefined {
  const style: SheetCellStyle = {};
  if (cell.font?.bold) {
    style.fontWeight = "bold";
  }
  const color = argbToCss(cell.font?.color?.argb);
  if (color) {
    style.color = color;
  }
  const bg =
    cell.fill?.type === "pattern"
      ? argbToCss(cell.fill.fgColor?.argb)
      : argbToCss(cell.fill?.fgColor?.argb);
  if (bg && bg.toLowerCase() !== "#ffffff") {
    style.backgroundColor = bg;
  }
  if (cell.alignment?.horizontal === "right") {
    style.textAlign = "right";
  } else if (cell.alignment?.horizontal === "center") {
    style.textAlign = "center";
  }
  return Object.keys(style).length > 0 ? style : undefined;
}

export function importStylesAndMerges(
  worksheet: ExcelWorksheet,
  top: number,
  left: number,
  bottom: number,
  right: number
): {
  cellStyles: Record<string, { style?: SheetCellStyle; format?: SheetCellFormat }>;
  mergedCells: SheetMergeRange[];
  columnWidths: number[];
  rowHeights: number[];
} {
  const cellStyles: Record<string, { style?: SheetCellStyle; format?: SheetCellFormat }> = {};
  for (let r = top; r <= bottom; r++) {
    for (let c = left; c <= right; c++) {
      const cell = worksheet.getCell(r, c);
      const style = styleFromExcelCell(cell);
      const format = formatFromNumFmt(cell.numFmt);
      if (!style && !format) {
        continue;
      }
      const address = rowColToA1(r - top, c - left);
      cellStyles[address] = { style, format };
    }
  }

  const mergedCells: SheetMergeRange[] = [];
  const merges = worksheet.model?.merges ?? [];
  for (const mergeRef of merges) {
    const parsed = parseMergeRef(mergeRef, top, left);
    if (parsed) {
      mergedCells.push(parsed);
    }
  }

  const columnWidths: number[] = [];
  for (let c = left; c <= right; c++) {
    const width = worksheet.getColumn(c).width;
    columnWidths.push(typeof width === "number" ? width : 10);
  }

  const rowHeights: number[] = [];
  for (let r = top; r <= bottom; r++) {
    const height = worksheet.getRow(r).height;
    rowHeights.push(typeof height === "number" ? height : 18);
  }

  return { cellStyles, mergedCells, columnWidths, rowHeights };
}

function parseMergeRef(
  mergeRef: string,
  top: number,
  left: number
): SheetMergeRange | null {
  const parts = mergeRef.split(":");
  if (parts.length !== 2) {
    return null;
  }
  const start = a1ToRowCol(parts[0]);
  const end = a1ToRowCol(parts[1]);
  if (!start || !end) {
    return null;
  }
  const anchorRow = start.row - (top - 1);
  const anchorCol = start.col - (left - 1);
  if (anchorRow < 0 || anchorCol < 0) {
    return null;
  }
  return {
    anchor: rowColToA1(anchorRow, anchorCol),
    rowSpan: end.row - start.row + 1,
    colSpan: end.col - start.col + 1,
  };
}

export function applyStylesAndMergesToWorksheet(
  worksheet: ExcelWorksheet,
  options: {
    rows: number;
    cols: number;
    cellStyles?: Record<string, { style?: SheetCellStyle; format?: SheetCellFormat }>;
    mergedCells?: SheetMergeRange[];
    columnWidths?: number[];
    rowHeights?: number[];
  }
): void {
  if (options.columnWidths) {
    for (let c = 0; c < options.cols; c++) {
      const width = options.columnWidths[c];
      if (typeof width === "number") {
        worksheet.getColumn(c + 1).width = width;
      }
    }
  }
  if (options.rowHeights) {
    for (let r = 0; r < options.rows; r++) {
      const height = options.rowHeights[r];
      if (typeof height === "number") {
        worksheet.getRow(r + 1).height = height;
      }
    }
  }
  if (options.cellStyles) {
    for (const [address, patch] of Object.entries(options.cellStyles)) {
      const rc = a1ToRowCol(address);
      if (!rc) {
        continue;
      }
      const cell = worksheet.getCell(rc.row + 1, rc.col + 1);
      applyStyleToExcelCell(cell, patch.style, patch.format);
    }
  }
  for (const merge of options.mergedCells ?? []) {
    const start = a1ToRowCol(merge.anchor);
    if (!start) {
      continue;
    }
    const end = rowColToA1(start.row + merge.rowSpan - 1, start.col + merge.colSpan - 1);
    worksheet.mergeCells(`${merge.anchor}:${end}`);
  }
}

function applyStyleToExcelCell(
  cell: ExcelCell & { font?: Record<string, unknown>; fill?: Record<string, unknown>; alignment?: Record<string, unknown>; numFmt?: string },
  style?: SheetCellStyle,
  format?: SheetCellFormat
): void {
  if (style?.fontWeight === "bold") {
    cell.font = { ...(cell.font ?? {}), bold: true };
  }
  if (style?.color) {
    cell.font = { ...(cell.font ?? {}), color: { argb: cssToArgb(style.color) } };
  }
  if (style?.backgroundColor) {
    cell.fill = {
      type: "pattern",
      pattern: "solid",
      fgColor: { argb: cssToArgb(style.backgroundColor) },
    };
  }
  if (style?.textAlign) {
    cell.alignment = { ...(cell.alignment ?? {}), horizontal: style.textAlign };
  }
  if (format?.type === "number") {
    const decimals = format.decimals ?? 0;
    cell.numFmt = decimals > 0 ? `0.${"0".repeat(decimals)}` : "0";
  }
}

function cssToArgb(css: string): string {
  const hex = css.replace("#", "");
  return hex.length === 6 ? `FF${hex.toUpperCase()}` : hex.toUpperCase();
}
