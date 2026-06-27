import type { SheetEvalResult } from "./ispfSheetEval";
import { a1ToRowCol, rowColToA1 } from "./sheetAddress";

export type SheetMatrix = SheetEvalResult[][];

export function buildMatrixFromRange(
  start: string,
  end: string,
  getCell: (address: string) => SheetEvalResult
): SheetMatrix | "#REF!" {
  const a = a1ToRowCol(start.toUpperCase());
  const b = a1ToRowCol(end.toUpperCase());
  if (!a || !b) {
    return "#REF!";
  }
  const minRow = Math.min(a.row, b.row);
  const maxRow = Math.max(a.row, b.row);
  const minCol = Math.min(a.col, b.col);
  const maxCol = Math.max(a.col, b.col);
  const matrix: SheetMatrix = [];
  for (let r = minRow; r <= maxRow; r++) {
    const row: SheetEvalResult[] = [];
    for (let c = minCol; c <= maxCol; c++) {
      row.push(getCell(rowColToA1(r, c)));
    }
    matrix.push(row);
  }
  return matrix;
}

export function flattenMatrix(matrix: SheetMatrix): SheetEvalResult[] {
  return matrix.flat();
}

function isBlank(value: SheetEvalResult): boolean {
  return value === null || value === "" || value === undefined;
}

function asString(value: SheetEvalResult): string {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value);
}

function asNumber(value: SheetEvalResult): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }
  const num = Number.parseFloat(String(value ?? ""));
  return Number.isFinite(num) ? num : null;
}

export function matchCriteria(criteria: SheetEvalResult, value: SheetEvalResult): boolean {
  const crit = asString(criteria);
  const val = value;
  if (crit.startsWith("=") && crit.length > 1) {
    return asString(val) === crit.slice(1);
  }
  if (crit.startsWith("<>")) {
    return asString(val) !== crit.slice(2);
  }
  if (crit.startsWith(">=")) {
    const n = Number.parseFloat(crit.slice(2));
    const vn = asNumber(val);
    return vn !== null && Number.isFinite(n) && vn >= n;
  }
  if (crit.startsWith("<=")) {
    const n = Number.parseFloat(crit.slice(2));
    const vn = asNumber(val);
    return vn !== null && Number.isFinite(n) && vn <= n;
  }
  if (crit.startsWith(">")) {
    const n = Number.parseFloat(crit.slice(1));
    const vn = asNumber(val);
    return vn !== null && Number.isFinite(n) && vn > n;
  }
  if (crit.startsWith("<")) {
    const n = Number.parseFloat(crit.slice(1));
    const vn = asNumber(val);
    return vn !== null && Number.isFinite(n) && vn < n;
  }
  if (crit.includes("*") || crit.includes("?")) {
    const escaped = crit.replace(/[.+^${}()|[\]\\]/g, "\\$&").replace(/\*/g, ".*").replace(/\?/g, ".");
    return new RegExp(`^${escaped}$`, "i").test(asString(val));
  }
  const numCrit = Number.parseFloat(crit);
  const numVal = asNumber(val);
  if (Number.isFinite(numCrit) && numVal !== null) {
    return numVal === numCrit;
  }
  return asString(val).toLowerCase() === crit.toLowerCase();
}

export function excelVlookup(
  lookup: SheetEvalResult,
  table: SheetMatrix,
  colIndex: number,
  approximate: boolean
): SheetEvalResult {
  if (table.length === 0 || colIndex < 1) {
    return "#REF!";
  }
  const col = colIndex - 1;
  if (approximate) {
    let bestRow = -1;
    let bestNum = -Infinity;
    for (let r = 0; r < table.length; r++) {
      const keyNum = asNumber(table[r][0]);
      if (keyNum === null) {
        continue;
      }
      const lookupNum = asNumber(lookup);
      if (lookupNum === null || keyNum > lookupNum) {
        continue;
      }
      if (keyNum >= bestNum) {
        bestNum = keyNum;
        bestRow = r;
      }
    }
    if (bestRow < 0 || col >= table[bestRow].length) {
      return "#N/A";
    }
    return table[bestRow][col] ?? "";
  }
  for (const row of table) {
    if (valuesEqual(row[0], lookup)) {
      return col < row.length ? row[col] ?? "" : "#REF!";
    }
  }
  return "#N/A";
}

export function excelHlookup(
  lookup: SheetEvalResult,
  table: SheetMatrix,
  rowIndex: number,
  approximate: boolean
): SheetEvalResult {
  if (table.length === 0 || rowIndex < 1) {
    return "#REF!";
  }
  const row = rowIndex - 1;
  const header = table[0] ?? [];
  if (approximate) {
    let bestCol = -1;
    let bestNum = -Infinity;
    for (let c = 0; c < header.length; c++) {
      const keyNum = asNumber(header[c]);
      if (keyNum === null) {
        continue;
      }
      const lookupNum = asNumber(lookup);
      if (lookupNum === null || keyNum > lookupNum) {
        continue;
      }
      if (keyNum >= bestNum) {
        bestNum = keyNum;
        bestCol = c;
      }
    }
    if (bestCol < 0 || row >= table.length) {
      return "#N/A";
    }
    return table[row][bestCol] ?? "";
  }
  for (let c = 0; c < header.length; c++) {
    if (valuesEqual(header[c], lookup)) {
      return row < table.length ? table[row][c] ?? "" : "#REF!";
    }
  }
  return "#N/A";
}

export function excelIndex(matrix: SheetMatrix, rowNum: number, colNum?: number): SheetEvalResult {
  const row = rowNum - 1;
  const col = (colNum ?? 1) - 1;
  if (row < 0 || row >= matrix.length) {
    return "#REF!";
  }
  const targetRow = matrix[row];
  if (col < 0 || col >= targetRow.length) {
    return "#REF!";
  }
  return targetRow[col] ?? "";
}

export function excelMatch(
  lookup: SheetEvalResult,
  array: SheetEvalResult[],
  matchType = 0
): SheetEvalResult {
  if (array.length === 0) {
    return "#N/A";
  }
  if (matchType === 0) {
    for (let i = 0; i < array.length; i++) {
      if (valuesEqual(array[i], lookup)) {
        return i + 1;
      }
    }
    return "#N/A";
  }
  const lookupNum = asNumber(lookup);
  if (lookupNum === null) {
    return "#N/A";
  }
  if (matchType > 0) {
    let best = -1;
    let bestVal = -Infinity;
    for (let i = 0; i < array.length; i++) {
      const n = asNumber(array[i]);
      if (n === null || n > lookupNum) {
        continue;
      }
      if (n >= bestVal) {
        bestVal = n;
        best = i;
      }
    }
    return best >= 0 ? best + 1 : "#N/A";
  }
  let best = -1;
  let bestVal = Infinity;
  for (let i = 0; i < array.length; i++) {
    const n = asNumber(array[i]);
    if (n === null || n < lookupNum) {
      continue;
    }
    if (n <= bestVal) {
      bestVal = n;
      best = i;
    }
  }
  return best >= 0 ? best + 1 : "#N/A";
}

export function excelSumif(
  range: SheetEvalResult[],
  criteria: SheetEvalResult,
  sumRange?: SheetEvalResult[]
): number {
  let total = 0;
  for (let i = 0; i < range.length; i++) {
    if (!matchCriteria(criteria, range[i])) {
      continue;
    }
    const src = sumRange ? sumRange[i] : range[i];
    const n = asNumber(src);
    if (n !== null) {
      total += n;
    }
  }
  return total;
}

export function excelCountif(range: SheetEvalResult[], criteria: SheetEvalResult): number {
  return range.filter((v) => matchCriteria(criteria, v)).length;
}

export function excelAverageif(
  range: SheetEvalResult[],
  criteria: SheetEvalResult,
  avgRange?: SheetEvalResult[]
): SheetEvalResult {
  let total = 0;
  let count = 0;
  for (let i = 0; i < range.length; i++) {
    if (!matchCriteria(criteria, range[i])) {
      continue;
    }
    const src = avgRange ? avgRange[i] : range[i];
    const n = asNumber(src);
    if (n !== null) {
      total += n;
      count++;
    }
  }
  return count > 0 ? total / count : "#DIV/0!";
}

function valuesEqual(a: SheetEvalResult, b: SheetEvalResult): boolean {
  if (typeof a === "number" && typeof b === "number") {
    return a === b;
  }
  if (typeof a === "boolean" || typeof b === "boolean") {
    return Boolean(a) === Boolean(b);
  }
  return asString(a).toLowerCase() === asString(b).toLowerCase();
}

export function countNumeric(values: SheetEvalResult[]): number {
  return values.filter((v) => asNumber(v) !== null).length;
}

export function countNonBlank(values: SheetEvalResult[]): number {
  return values.filter((v) => !isBlank(v)).length;
}

export function countBlank(values: SheetEvalResult[]): number {
  return values.filter((v) => isBlank(v)).length;
}
