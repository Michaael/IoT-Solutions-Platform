import type { SheetConfig } from "../../../types/dashboard";
import type { SheetMode } from "../../../types/dashboard";
import { rowColToA1 } from "./sheetAddress";
import {
  evaluateSheetFormula,
  expandRange,
  literalCellValue,
  type SheetEvalResult,
} from "./ispfSheetEval";
import { getIspfFormulaContext } from "./sheetFormulaEngineContext";

export type SheetValues = Record<string, string>;

export {
  bindingCacheKey,
  histCacheKey,
  setIspfFormulaContext,
  type IspfFormulaContext,
} from "./sheetFormulaEngineContext";

export function rawToCellContent(raw: string): string {
  return raw;
}

function seedContentForCell(
  addr: string,
  config: SheetConfig,
  contents: SheetValues
): string {
  if (contents[addr] !== undefined) {
    return contents[addr];
  }
  const cell = config.cells[addr];
  if (!cell) {
    return "";
  }
  if (cell.kind === "formula" && cell.expr) {
    return cell.expr.startsWith("=") ? cell.expr : `=${cell.expr}`;
  }
  if (cell.kind === "input" || cell.kind === "readonly") {
    return cell.default ?? "";
  }
  if (cell.kind === "label") {
    return cell.text ?? "";
  }
  return "";
}

function isFormulaContent(raw: string): boolean {
  return raw.trim().startsWith("=");
}

function extractDependencies(formula: string): string[] {
  const deps = new Set<string>();
  const normalized = formula.trim().startsWith("=") ? formula.slice(1) : formula;
  const cellRef = /\b([A-Z]+\d+)\b/gi;
  let match: RegExpExecArray | null;
  while ((match = cellRef.exec(normalized)) !== null) {
    deps.add(match[1].toUpperCase());
  }
  const rangeRef = /\b([A-Z]+\d+)\s*:\s*([A-Z]+\d+)\b/gi;
  while ((match = rangeRef.exec(normalized)) !== null) {
    const cells = expandRange(match[1], match[2]);
    cells?.forEach((addr) => deps.add(addr));
  }
  return [...deps];
}

function sortEvalOrder(addresses: string[], rawByAddr: Map<string, string>): string[] {
  const formulaAddrs = addresses.filter((addr) => isFormulaContent(rawByAddr.get(addr) ?? ""));
  const deps = new Map<string, string[]>();
  for (const addr of formulaAddrs) {
    deps.set(addr, extractDependencies(rawByAddr.get(addr) ?? "").filter((d) => d !== addr));
  }
  const sorted: string[] = [];
  const temp = new Set<string>();
  const perm = new Set<string>();

  function visit(addr: string): boolean {
    if (perm.has(addr)) {
      return true;
    }
    if (temp.has(addr)) {
      return false;
    }
    temp.add(addr);
    for (const dep of deps.get(addr) ?? []) {
      if (formulaAddrs.includes(dep) && !visit(dep)) {
        return false;
      }
    }
    temp.delete(addr);
    perm.add(addr);
    if (formulaAddrs.includes(addr)) {
      sorted.push(addr);
    }
    return true;
  }

  for (const addr of formulaAddrs) {
    if (!visit(addr)) {
      return formulaAddrs;
    }
  }
  return sorted;
}

export class SheetFormulaEngine {
  private readonly config: SheetConfig;
  private readonly mode: SheetMode;
  private cellContents: SheetValues;
  private readonly bindingAddresses: Set<string>;
  private readonly externalByAddr = new Map<string, number | string | boolean>();
  private readonly computed = new Map<string, SheetEvalResult>();
  private readonly rawByAddr = new Map<string, string>();
  private selectionAnchor: string | null = null;
  private clipboard: string | null = null;
  private readonly undoStack: SheetValues[] = [];
  private readonly redoStack: SheetValues[] = [];

  constructor(
    config: SheetConfig,
    mode: SheetMode,
    initialContents: SheetValues,
    externalByAddr: Map<string, number | string | boolean> = new Map()
  ) {
    this.config = config;
    this.mode = mode;
    this.cellContents = { ...initialContents };
    this.bindingAddresses = new Set(
      Object.entries(config.cells)
        .filter(([, cell]) => cell.kind === "binding")
        .map(([addr]) => addr.toUpperCase())
    );
    for (const [addr, value] of externalByAddr) {
      this.externalByAddr.set(addr.toUpperCase(), value);
    }
    this.bootstrapRawCells();
    this.recalc();
  }

  private bootstrapRawCells(): void {
    this.rawByAddr.clear();
    for (let r = 0; r < this.config.rows; r++) {
      for (let c = 0; c < this.config.cols; c++) {
        const addr = rowColToA1(r, c);
        if (this.bindingAddresses.has(addr)) {
          continue;
        }
        const seed = seedContentForCell(addr, this.config, this.cellContents);
        if (seed) {
          this.rawByAddr.set(addr, seed);
        }
      }
    }
  }

  private pushUndo(): void {
    this.undoStack.push(structuredClone(this.cellContents));
    this.redoStack.length = 0;
  }

  private recalc(): void {
    this.computed.clear();
    const addresses = [...this.rawByAddr.keys()];
    const evalOrder = [
      ...addresses.filter((addr) => !isFormulaContent(this.rawByAddr.get(addr) ?? "")),
      ...sortEvalOrder(addresses, this.rawByAddr),
    ];
    const visiting = new Set<string>();

    const getCell = (address: string): SheetEvalResult => {
      const addr = address.toUpperCase();
      if (this.computed.has(addr)) {
        return this.computed.get(addr)!;
      }
      if (this.bindingAddresses.has(addr)) {
        const ext = this.externalByAddr.get(addr);
        if (ext === undefined) {
          return null;
        }
        return typeof ext === "number" || typeof ext === "boolean" ? ext : ext;
      }
      const raw = this.rawByAddr.get(addr) ?? "";
      if (!raw.trim()) {
        this.computed.set(addr, null);
        return null;
      }
      if (!isFormulaContent(raw)) {
        const literal = literalCellValue(raw);
        const value = typeof literal === "string" && literal.startsWith("=") ? literal : literal;
        this.computed.set(addr, value as SheetEvalResult);
        return value as SheetEvalResult;
      }
      if (visiting.has(addr)) {
        this.computed.set(addr, "#CYCLE!");
        return "#CYCLE!";
      }
      visiting.add(addr);
      const result = evaluateSheetFormula(raw, {
        getCell: (ref) => getCell(ref),
        ispf: getIspfFormulaContext(),
      });
      visiting.delete(addr);
      this.computed.set(addr, result);
      return result;
    };

    for (const addr of evalOrder) {
      getCell(addr);
    }
    for (let r = 0; r < this.config.rows; r++) {
      for (let c = 0; c < this.config.cols; c++) {
        const addr = rowColToA1(r, c);
        if (this.bindingAddresses.has(addr)) {
          getCell(addr);
        }
      }
    }
  }

  isBindingCell(address: string): boolean {
    return this.bindingAddresses.has(address.toUpperCase());
  }

  setCellContent(address: string, raw: string): void {
    if (this.isBindingCell(address)) {
      return;
    }
    this.pushUndo();
    const addr = address.toUpperCase();
    const trimmed = raw.trim();
    if (trimmed === "") {
      delete this.cellContents[addr];
      this.rawByAddr.delete(addr);
    } else {
      this.cellContents[addr] = raw;
      this.rawByAddr.set(addr, trimmed);
    }
    this.recalc();
  }

  setInputValue(address: string, raw: string): void {
    this.setCellContent(address, raw);
  }

  setExternalValue(address: string, value: number | string | boolean): void {
    const addr = address.toUpperCase();
    if (!this.isBindingCell(addr) && this.mode !== "free") {
      const cell = this.config.cells[addr];
      if (!cell || cell.kind !== "binding") {
        return;
      }
    }
    this.externalByAddr.set(addr, value);
    this.recalc();
  }

  getCellValue(address: string): unknown {
    const addr = address.toUpperCase();
    return this.computed.get(addr) ?? null;
  }

  getCellEditContent(address: string): string {
    const addr = address.toUpperCase();
    if (this.cellContents[addr] !== undefined) {
      return this.cellContents[addr];
    }
    const raw = this.rawByAddr.get(addr);
    if (raw) {
      return raw;
    }
    const val = this.getCellValue(addr);
    if (val === null || val === undefined) {
      return "";
    }
    return String(val);
  }

  collectCellContents(): SheetValues {
    if (this.mode === "free") {
      return { ...this.cellContents };
    }
    return this.collectInputValues();
  }

  collectInputValues(): SheetValues {
    const result: SheetValues = {};
    for (const [addr, cell] of Object.entries(this.config.cells)) {
      if (cell.kind === "input") {
        result[addr] = this.getCellEditContent(addr);
      }
    }
    return result;
  }

  setSelectionAnchor(address: string | null): void {
    this.selectionAnchor = address?.toUpperCase() ?? null;
  }

  getSelectionAnchor(): string | null {
    return this.selectionAnchor;
  }

  copySelection(): void {
    if (!this.selectionAnchor) {
      return;
    }
    this.clipboard = this.getCellEditContent(this.selectionAnchor);
  }

  pasteAt(address: string): void {
    if (this.clipboard == null) {
      return;
    }
    this.setCellContent(address, this.clipboard);
  }

  undo(): boolean {
    const prev = this.undoStack.pop();
    if (!prev) {
      return false;
    }
    this.redoStack.push(structuredClone(this.cellContents));
    this.cellContents = prev;
    this.bootstrapRawCells();
    this.recalc();
    return true;
  }

  redo(): boolean {
    const next = this.redoStack.pop();
    if (!next) {
      return false;
    }
    this.undoStack.push(structuredClone(this.cellContents));
    this.cellContents = next;
    this.bootstrapRawCells();
    this.recalc();
    return true;
  }

  exportCsv(): string {
    const lines: string[] = [];
    for (let r = 0; r < this.config.rows; r++) {
      const row: string[] = [];
      for (let c = 0; c < this.config.cols; c++) {
        const addr = rowColToA1(r, c);
        row.push(formatCsvCell(this.getCellValue(addr)));
      }
      lines.push(row.join(","));
    }
    return lines.join("\n");
  }

  /** Re-evaluate all formulas (e.g. after ISPF binding context refresh). */
  refreshComputed(): void {
    this.recalc();
  }

  mergeRegionContents(regionContents: SheetValues): void {
    for (const [addr, raw] of Object.entries(regionContents)) {
      if (!this.isBindingCell(addr) && raw) {
        this.setCellContent(addr, raw);
      }
    }
  }

  destroy(): void {
    this.computed.clear();
    this.rawByAddr.clear();
  }
}

function formatCsvCell(val: unknown): string {
  if (val === null || val === undefined) {
    return "";
  }
  const text = String(val);
  if (text.includes(",") || text.includes('"') || text.includes("\n")) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

export function createSheetFormulaEngine(
  config: SheetConfig,
  mode: SheetMode,
  initialContents: SheetValues,
  externalByAddr?: Map<string, number | string | boolean>
): SheetFormulaEngine {
  return new SheetFormulaEngine(config, mode, initialContents, externalByAddr);
}

export function regionContentsFromRows(
  config: SheetConfig,
  rows: Array<Record<string, unknown>>
): SheetValues {
  const region = config.dataRegion;
  if (!region) {
    return {};
  }
  const result: SheetValues = {};
  rows.forEach((row, ri) => {
    region.columnFields.forEach((field, ci) => {
      const addr = rowColToA1(region.startRow + ri, region.startCol + ci);
      const val = row[field];
      if (val !== undefined && val !== null) {
        result[addr] = String(val);
      }
    });
  });
  return result;
}
