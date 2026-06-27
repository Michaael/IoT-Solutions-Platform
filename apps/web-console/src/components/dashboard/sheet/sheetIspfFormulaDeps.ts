import type { SheetConfig } from "../../../types/dashboard";
import type { SheetValues } from "./sheetFormulaEngine";

export interface IspfFormulaVarRef {
  objectPath: string;
  variableName: string;
  field: string;
  histMinutes?: number;
  needsTableSum?: boolean;
  sumColumn?: string;
}

const ISPREF_RE =
  /ISPREF\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"(?:\s*,\s*"([^"]+)")?\s*\)/gi;
const ISPSUM_RE = /ISPSUM\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)/gi;
const ISPHIST_RE =
  /ISPHIST\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"(?:\s*,\s*(\d+(?:\.\d+)?))?\s*\)/gi;

function collectFormulaStrings(
  contents: SheetValues,
  config: SheetConfig
): string[] {
  const formulas: string[] = [];
  for (const raw of Object.values(contents)) {
    if (raw.trim().startsWith("=")) {
      formulas.push(raw);
    }
  }
  for (const cell of Object.values(config.cells)) {
    if (cell.kind === "formula" && cell.expr) {
      formulas.push(cell.expr.startsWith("=") ? cell.expr : `=${cell.expr}`);
    }
  }
  return formulas;
}

export function extractIspfFormulaVarRefs(
  contents: SheetValues,
  config: SheetConfig,
  defaultObjectPath: string
): IspfFormulaVarRef[] {
  const refs: IspfFormulaVarRef[] = [];
  const formulas = collectFormulaStrings(contents, config);

  for (const formula of formulas) {
    ISPREF_RE.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = ISPREF_RE.exec(formula)) !== null) {
      refs.push({
        objectPath: match[1],
        variableName: match[2],
        field: match[3] ?? "value",
      });
    }

    ISPSUM_RE.lastIndex = 0;
    while ((match = ISPSUM_RE.exec(formula)) !== null) {
      refs.push({
        objectPath: defaultObjectPath,
        variableName: match[1],
        field: match[2],
        needsTableSum: true,
        sumColumn: match[2],
      });
    }

    ISPHIST_RE.lastIndex = 0;
    while ((match = ISPHIST_RE.exec(formula)) !== null) {
      const minutes = match[3] !== undefined ? Math.trunc(Number.parseFloat(match[3])) : 5;
      refs.push({
        objectPath: match[1],
        variableName: match[2],
        field: "value",
        histMinutes: minutes,
      });
    }
  }

  return refs;
}

export function mergeVarRefKey(path: string, varName: string): string {
  return `${path}|${varName}`;
}
