import { a1ToRowCol, rowColToA1 } from "./sheetAddress";
import { normalizeFormulaSyntax, normalizeFunctionName } from "./sheetFormulaNormalize";
import {
  buildMatrixFromRange,
  countBlank,
  countNonBlank,
  countNumeric,
  excelAverageif,
  excelCountif,
  excelHlookup,
  excelIndex,
  excelMatch,
  excelSumif,
  excelVlookup,
  type SheetMatrix,
} from "./sheetExcelFunctions";
import {
  bindingCacheKey,
  histCacheKey,
  type IspfFormulaContext,
} from "./sheetFormulaEngineContext";

export type SheetEvalValue = number | string | boolean;
export type SheetEvalResult = SheetEvalValue | null;

const ERROR = {
  name: "#NAME?",
  ref: "#REF!",
  div0: "#DIV/0!",
  value: "#VALUE!",
  cycle: "#CYCLE!",
  na: "#N/A",
} as const;

export function isSheetError(value: unknown): value is string {
  return (
    value === ERROR.name ||
    value === ERROR.ref ||
    value === ERROR.div0 ||
    value === ERROR.value ||
    value === ERROR.cycle ||
    value === ERROR.na
  );
}

export function expandRange(start: string, end: string): string[] | null {
  const a = a1ToRowCol(start.toUpperCase());
  const b = a1ToRowCol(end.toUpperCase());
  if (!a || !b) {
    return null;
  }
  const minRow = Math.min(a.row, b.row);
  const maxRow = Math.max(a.row, b.row);
  const minCol = Math.min(a.col, b.col);
  const maxCol = Math.max(a.col, b.col);
  const cells: string[] = [];
  for (let r = minRow; r <= maxRow; r++) {
    for (let c = minCol; c <= maxCol; c++) {
      cells.push(rowColToA1(r, c));
    }
  }
  return cells;
}

export interface SheetEvalEnvironment {
  getCell: (address: string) => SheetEvalResult;
  ispf: IspfFormulaContext;
}

type Token =
  | { kind: "num"; value: number }
  | { kind: "str"; value: string }
  | { kind: "cell"; value: string }
  | { kind: "ident"; value: string }
  | { kind: "op"; value: string }
  | { kind: "lparen" }
  | { kind: "rparen" }
  | { kind: "comma" }
  | { kind: "colon" }
  | { kind: "eof" };

function tokenize(input: string): Token[] | string {
  const tokens: Token[] = [];
  let i = 0;
  while (i < input.length) {
    const ch = input[i];
    if (/\s/.test(ch)) {
      i++;
      continue;
    }
    if (ch === "(") {
      tokens.push({ kind: "lparen" });
      i++;
      continue;
    }
    if (ch === ")") {
      tokens.push({ kind: "rparen" });
      i++;
      continue;
    }
    if (ch === "," || ch === ";") {
      tokens.push({ kind: "comma" });
      i++;
      continue;
    }
    if (ch === ":") {
      tokens.push({ kind: "colon" });
      i++;
      continue;
    }
    if (ch === '"' || ch === "'") {
      const quote = ch;
      i++;
      let value = "";
      while (i < input.length && input[i] !== quote) {
        value += input[i];
        i++;
      }
      if (i >= input.length) {
        return ERROR.value;
      }
      i++;
      tokens.push({ kind: "str", value });
      continue;
    }
    if (ch === "&") {
      tokens.push({ kind: "op", value: "&" });
      i++;
      continue;
    }
    if (">=<=".includes(ch) || "<>".includes(ch) || "+-*/=".includes(ch)) {
      let op = ch;
      const two = input.slice(i, i + 2);
      if (two === ">=" || two === "<=" || two === "<>") {
        op = two;
        i += 2;
      } else {
        i++;
      }
      tokens.push({ kind: "op", value: op });
      continue;
    }
    if (/[0-9.]/.test(ch) || (ch === "-" && /[0-9]/.test(input[i + 1] ?? ""))) {
      let num = ch;
      i++;
      while (i < input.length && /[0-9.]/.test(input[i])) {
        num += input[i];
        i++;
      }
      const parsed = Number.parseFloat(num);
      if (!Number.isFinite(parsed)) {
        return ERROR.value;
      }
      tokens.push({ kind: "num", value: parsed });
      continue;
    }
    if (/[A-Za-z_А-ЯЁа-яё]/.test(ch)) {
      let ident = ch;
      i++;
      while (i < input.length && /[A-Za-z0-9_.А-ЯЁа-яё]/.test(input[i])) {
        ident += input[i];
        i++;
      }
      const upper = ident.toUpperCase();
      if (/^[A-Z]+\d+$/.test(upper)) {
        tokens.push({ kind: "cell", value: upper });
      } else {
        tokens.push({ kind: "ident", value: upper });
      }
      continue;
    }
    return ERROR.value;
  }
  tokens.push({ kind: "eof" });
  return tokens;
}

class Parser {
  private pos = 0;

  constructor(
    private readonly tokens: Token[],
    private readonly env: SheetEvalEnvironment
  ) {}

  parse(): SheetEvalResult {
    const value = this.parseCompare();
    if (!this.match("eof")) {
      return ERROR.value;
    }
    return value;
  }

  private parseCompare(): SheetEvalResult {
    const left = this.parseConcat();
    if (isSheetError(left)) {
      return left;
    }
    const tok = this.peek();
    if (tok.kind === "op" && ["=", "<>", ">", "<", ">=", "<="].includes(tok.value)) {
      this.pos++;
      const right = this.parseConcat();
      if (isSheetError(right)) {
        return right;
      }
      const ln = coerceNumber(left);
      const rn = coerceNumber(right);
      if (typeof left === "string" || typeof right === "string") {
        const ls = String(left);
        const rs = String(right);
        switch (tok.value) {
          case "=":
            return ls === rs;
          case "<>":
            return ls !== rs;
          default:
            return ERROR.value;
        }
      }
      switch (tok.value) {
        case ">":
          return ln > rn;
        case "<":
          return ln < rn;
        case ">=":
          return ln >= rn;
        case "<=":
          return ln <= rn;
        case "=":
          return ln === rn;
        case "<>":
          return ln !== rn;
        default:
          return ERROR.value;
      }
    }
    return left;
  }

  private parseConcat(): SheetEvalResult {
    let value = this.parseAdd();
    while (this.match("op", "&")) {
      const right = this.parseAdd();
      if (isSheetError(value) || isSheetError(right)) {
        return isSheetError(value) ? value : right;
      }
      value = String(value ?? "") + String(right ?? "");
    }
    return value;
  }

  private parseAdd(): SheetEvalResult {
    let value = this.parseMul();
    while (true) {
      const tok = this.peek();
      if (tok.kind !== "op" || (tok.value !== "+" && tok.value !== "-")) {
        break;
      }
      this.pos++;
      const right = this.parseMul();
      if (isSheetError(value) || isSheetError(right)) {
        return isSheetError(value) ? value : right;
      }
      const ln = coerceNumber(value);
      const rn = coerceNumber(right);
      value = tok.value === "+" ? ln + rn : ln - rn;
    }
    return value;
  }

  private parseMul(): SheetEvalResult {
    let value = this.parseUnary();
    while (true) {
      const tok = this.peek();
      if (tok.kind !== "op" || (tok.value !== "*" && tok.value !== "/")) {
        break;
      }
      this.pos++;
      const right = this.parseUnary();
      if (isSheetError(value) || isSheetError(right)) {
        return isSheetError(value) ? value : right;
      }
      const ln = coerceNumber(value);
      const rn = coerceNumber(right);
      if (tok.value === "/") {
        if (rn === 0) {
          return ERROR.div0;
        }
        value = ln / rn;
      } else {
        value = ln * rn;
      }
    }
    return value;
  }

  private parseUnary(): SheetEvalResult {
    const tok = this.peek();
    if (tok.kind === "op" && (tok.value === "+" || tok.value === "-")) {
      this.pos++;
      const inner = this.parseUnary();
      if (isSheetError(inner)) {
        return inner;
      }
      const n = coerceNumber(inner);
      return tok.value === "-" ? -n : n;
    }
    return this.parsePrimary();
  }

  private parsePrimary(): SheetEvalResult {
    const tok = this.peek();
    if (tok.kind === "num") {
      this.pos++;
      return tok.value;
    }
    if (tok.kind === "str") {
      this.pos++;
      return tok.value;
    }
    if (tok.kind === "cell") {
      this.pos++;
      const next = this.peek();
      if (next.kind === "colon") {
        return ERROR.ref;
      }
      const value = this.env.getCell(tok.value);
      if (isSheetError(value)) {
        return value;
      }
      return value ?? 0;
    }
    if (tok.kind === "ident") {
      if (tok.value === "TRUE") {
        this.pos++;
        return true;
      }
      if (tok.value === "FALSE") {
        this.pos++;
        return false;
      }
      return this.parseFunction(tok.value);
    }
    if (tok.kind === "lparen") {
      this.pos++;
      const inner = this.parseCompare();
      if (!this.match("rparen")) {
        return ERROR.value;
      }
      return inner;
    }
    return ERROR.value;
  }

  private parseFunction(name: string): SheetEvalResult {
    this.pos++;
    if (!this.match("lparen")) {
      return ERROR.name;
    }
    const fn = normalizeFunctionName(name);
    let result: SheetEvalResult;
    switch (fn) {
      case "VLOOKUP":
        result = this.parseVlookupArgs();
        break;
      case "HLOOKUP":
        result = this.parseHlookupArgs();
        break;
      case "INDEX":
        result = this.parseIndexArgs();
        break;
      case "MATCH":
        result = this.parseMatchArgs();
        break;
      case "SUMIF":
        result = this.parseSumifArgs();
        break;
      case "COUNTIF":
        result = this.parseCountifArgs();
        break;
      case "AVERAGEIF":
        result = this.parseAverageifArgs();
        break;
      default:
        result = this.parseGenericFunctionArgs(name);
        break;
    }
    if (!this.match("rparen")) {
      return ERROR.value;
    }
    return result;
  }

  private parseGenericFunctionArgs(name: string): SheetEvalResult {
    const args: SheetEvalResult[] = [];
    if (!this.check("rparen")) {
      do {
        const rangeArg = this.tryParseRangeArg();
        if (rangeArg !== undefined) {
          if (typeof rangeArg === "string" && isSheetError(rangeArg)) {
            args.push(rangeArg);
          } else if (Array.isArray(rangeArg)) {
            args.push(...rangeArg);
          }
        } else {
          args.push(this.parseCompare());
        }
      } while (this.match("comma"));
    }
    return invokeFunction(name, args, this.env);
  }

  private parseVlookupArgs(): SheetEvalResult {
    const lookup = this.parseCompare();
    if (isSheetError(lookup) || !this.match("comma")) {
      return ERROR.value;
    }
    const matrix = this.requireRangeMatrix();
    if (typeof matrix === "string") {
      return matrix;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const colIndex = Math.trunc(coerceNumber(this.parseCompare()));
    let approximate = true;
    if (this.match("comma")) {
      approximate = coerceBool(this.parseCompare());
    }
    return excelVlookup(lookup, matrix, colIndex, approximate);
  }

  private parseHlookupArgs(): SheetEvalResult {
    const lookup = this.parseCompare();
    if (isSheetError(lookup) || !this.match("comma")) {
      return ERROR.value;
    }
    const matrix = this.requireRangeMatrix();
    if (typeof matrix === "string") {
      return matrix;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const rowIndex = Math.trunc(coerceNumber(this.parseCompare()));
    let approximate = true;
    if (this.match("comma")) {
      approximate = coerceBool(this.parseCompare());
    }
    return excelHlookup(lookup, matrix, rowIndex, approximate);
  }

  private parseIndexArgs(): SheetEvalResult {
    const matrix = this.requireRangeMatrix();
    if (typeof matrix === "string") {
      return matrix;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const rowNum = Math.trunc(coerceNumber(this.parseCompare()));
    let colNum: number | undefined;
    if (this.match("comma")) {
      colNum = Math.trunc(coerceNumber(this.parseCompare()));
    }
    return excelIndex(matrix, rowNum, colNum);
  }

  private parseMatchArgs(): SheetEvalResult {
    const lookup = this.parseCompare();
    if (isSheetError(lookup) || !this.match("comma")) {
      return ERROR.value;
    }
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    let matchType = 0;
    if (this.match("comma")) {
      matchType = Math.trunc(coerceNumber(this.parseCompare()));
    }
    return excelMatch(lookup, rangeValues, matchType);
  }

  private parseSumifArgs(): SheetEvalResult {
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const criteria = this.parseCompare();
    let sumRange: SheetEvalResult[] | undefined;
    if (this.match("comma")) {
      const sr = this.tryParseRangeArg();
      if (sr && typeof sr !== "string") {
        sumRange = sr;
      }
    }
    return excelSumif(rangeValues, criteria, sumRange);
  }

  private parseCountifArgs(): SheetEvalResult {
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const criteria = this.parseCompare();
    return excelCountif(rangeValues, criteria);
  }

  private parseAverageifArgs(): SheetEvalResult {
    const rangeValues = this.requireRangeValues();
    if (typeof rangeValues === "string") {
      return rangeValues;
    }
    if (!this.match("comma")) {
      return ERROR.value;
    }
    const criteria = this.parseCompare();
    let avgRange: SheetEvalResult[] | undefined;
    if (this.match("comma")) {
      const sr = this.tryParseRangeArg();
      if (sr && typeof sr !== "string") {
        avgRange = sr;
      }
    }
    return excelAverageif(rangeValues, criteria, avgRange);
  }

  private requireRangeMatrix(): SheetMatrix | string {
    const matrix = this.tryParseRangeMatrix();
    if (!matrix) {
      return ERROR.ref;
    }
    return matrix;
  }

  private requireRangeValues(): SheetEvalResult[] | string {
    const range = this.tryParseRangeArg();
    if (!range || typeof range === "string") {
      return range ?? ERROR.ref;
    }
    return range;
  }

  private tryParseRangeMatrix(): SheetMatrix | string | undefined {
    const startTok = this.peek();
    if (startTok.kind !== "cell") {
      return undefined;
    }
    if (this.tokens[this.pos + 1]?.kind !== "colon" || this.tokens[this.pos + 2]?.kind !== "cell") {
      return undefined;
    }
    const start = startTok.value;
    const end = (this.tokens[this.pos + 2] as { kind: "cell"; value: string }).value;
    this.pos += 3;
    return buildMatrixFromRange(start, end, (addr) => {
      const v = this.env.getCell(addr);
      return isSheetError(v) ? v : v;
    });
  }

  private tryParseRangeArg(): SheetEvalResult[] | string | undefined {
    const startTok = this.peek();
    if (startTok.kind !== "cell") {
      return undefined;
    }
    if (this.tokens[this.pos + 1]?.kind !== "colon" || this.tokens[this.pos + 2]?.kind !== "cell") {
      return undefined;
    }
    const start = startTok.value;
    const end = (this.tokens[this.pos + 2] as { kind: "cell"; value: string }).value;
    this.pos += 3;
    return this.readRangeValues(start, end);
  }

  private readRangeValues(start: string, end: string): SheetEvalResult[] | string {
    const cells = expandRange(start, end);
    if (!cells) {
      return ERROR.ref;
    }
    const values: SheetEvalResult[] = [];
    for (const addr of cells) {
      const v = this.env.getCell(addr);
      if (isSheetError(v)) {
        return v;
      }
      values.push(v);
    }
    return values;
  }

  private peek(): Token {
    return this.tokens[this.pos] ?? { kind: "eof" };
  }

  private match(kind: Token["kind"], value?: string): boolean {
    const tok = this.peek();
    if (tok.kind !== kind) {
      return false;
    }
    if (value !== undefined && (tok.kind === "op" || tok.kind === "ident") && tok.value !== value) {
      return false;
    }
    this.pos++;
    return true;
  }

  private check(kind: Token["kind"]): boolean {
    return this.peek().kind === kind;
  }
}

function coerceNumber(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }
  const num = Number.parseFloat(String(value ?? ""));
  return Number.isFinite(num) ? num : 0;
}

function coerceBool(value: SheetEvalResult): boolean {
  if (typeof value === "boolean") {
    return value;
  }
  if (typeof value === "number") {
    return value !== 0;
  }
  return String(value ?? "").length > 0 && String(value).toLowerCase() !== "false";
}

function numbersFromArgs(args: SheetEvalResult[]): number[] | string {
  const nums: number[] = [];
  for (const arg of args) {
    if (isSheetError(arg)) {
      return arg;
    }
    if (Array.isArray(arg)) {
      return ERROR.value;
    }
    nums.push(coerceNumber(arg));
  }
  return nums;
}

function invokeFunction(
  name: string,
  args: SheetEvalResult[],
  env: SheetEvalEnvironment
): SheetEvalResult {
  name = normalizeFunctionName(name);

  if (name === "IFERROR") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const primary = args[0];
    if (isSheetError(primary)) {
      return args[1];
    }
    return primary;
  }

  for (const arg of args) {
    if (isSheetError(arg)) {
      return arg;
    }
  }

  if (name === "ABS") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.abs(coerceNumber(args[0]));
  }

  if (name === "AND") {
    return args.every((arg) => coerceBool(arg));
  }

  if (name === "OR") {
    return args.some((arg) => coerceBool(arg));
  }

  if (name === "NOT") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return !coerceBool(args[0]);
  }

  if (name === "PRODUCT") {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    return nums.reduce((a, b) => a * b, nums.length > 0 ? 1 : 0);
  }

  if (name === "IF") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return coerceBool(args[0]) ? args[1] : args[2] ?? false;
  }

  if (name === "ROUND") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const digits = args.length > 1 ? Math.trunc(coerceNumber(args[1])) : 0;
    const factor = 10 ** digits;
    return Math.round(coerceNumber(args[0]) * factor) / factor;
  }

  if (name === "MOD") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const divisor = coerceNumber(args[1]);
    if (divisor === 0) {
      return ERROR.div0;
    }
    return coerceNumber(args[0]) % divisor;
  }

  if (name === "POWER") {
    if (args.length < 2) {
      return ERROR.value;
    }
    return coerceNumber(args[0]) ** coerceNumber(args[1]);
  }

  if (name === "SQRT") {
    if (args.length < 1) {
      return ERROR.value;
    }
    const n = coerceNumber(args[0]);
    if (n < 0) {
      return ERROR.value;
    }
    return Math.sqrt(n);
  }

  if (name === "INT") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.trunc(coerceNumber(args[0]));
  }

  if (name === "CEILING") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.ceil(coerceNumber(args[0]));
  }

  if (name === "FLOOR") {
    if (args.length < 1) {
      return ERROR.value;
    }
    return Math.floor(coerceNumber(args[0]));
  }

  if (name === "LEN") {
    return String(args[0] ?? "").length;
  }

  if (name === "LEFT") {
    const text = String(args[0] ?? "");
    const n = args.length > 1 ? Math.max(0, Math.trunc(coerceNumber(args[1]))) : 1;
    return text.slice(0, n);
  }

  if (name === "RIGHT") {
    const text = String(args[0] ?? "");
    const n = args.length > 1 ? Math.max(0, Math.trunc(coerceNumber(args[1]))) : 1;
    return text.slice(Math.max(0, text.length - n));
  }

  if (name === "MID") {
    if (args.length < 3) {
      return ERROR.value;
    }
    const text = String(args[0] ?? "");
    const start = Math.max(1, Math.trunc(coerceNumber(args[1]))) - 1;
    const len = Math.max(0, Math.trunc(coerceNumber(args[2])));
    return text.slice(start, start + len);
  }

  if (name === "TRIM") {
    return String(args[0] ?? "").trim();
  }

  if (name === "UPPER") {
    return String(args[0] ?? "").toUpperCase();
  }

  if (name === "LOWER") {
    return String(args[0] ?? "").toLowerCase();
  }

  if (name === "CONCAT" || name === "CONCATENATE") {
    return args.map((arg) => String(arg ?? "")).join("");
  }

  if (name === "TEXT") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const num = coerceNumber(args[0]);
    const fmt = String(args[1]);
    const decimals = (fmt.match(/0\.(0+)/)?.[1]?.length ?? 0);
    return num.toFixed(decimals);
  }

  if (name === "TODAY") {
    const now = new Date();
    return Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 86_400_000;
  }

  if (name === "NOW") {
    const now = new Date();
    const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    return (now.getTime() - midnight) / 86_400_000 + TODAY_SERIAL(now);
  }

  if (name === "ISBLANK") {
    const v = args[0];
    return v === null || v === "" || v === undefined;
  }

  if (name === "ISNUMBER") {
    const v = args[0];
    return typeof v === "number" && Number.isFinite(v);
  }

  if (name === "ISTEXT") {
    const v = args[0];
    return typeof v === "string" && v !== "";
  }

  if (name === "ISERROR") {
    return isSheetError(args[0]);
  }

  if (name === "COUNT") {
    return countNumeric(args);
  }

  if (name === "COUNTA") {
    return countNonBlank(args);
  }

  if (name === "COUNTBLANK") {
    return countBlank(args);
  }

  if (name === "ISPREF") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const path = String(args[0]);
    const varName = String(args[1]);
    const field = args[2] !== undefined ? String(args[2]) : "value";
    const v = env.ispf.bindingValues.get(bindingCacheKey(path, varName, field));
    return coerceNumber(v);
  }

  if (name === "ISPSUM") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const tableVar = String(args[0]);
    const column = String(args[1]);
    return env.ispf.tableColumnSums.get(`${tableVar}|${column}`) ?? 0;
  }

  if (name === "ISPHIST") {
    if (args.length < 2) {
      return ERROR.value;
    }
    const path = String(args[0]);
    const varName = String(args[1]);
    const minutes = args[2] !== undefined ? Math.trunc(coerceNumber(args[2])) : 5;
    const key = histCacheKey(path, varName, minutes);
    const hist = env.ispf.histValues.get(key);
    if (hist !== undefined) {
      return hist;
    }
    const fallback = env.ispf.bindingValues.get(bindingCacheKey(path, varName, "value"));
    return coerceNumber(fallback);
  }

  const aggregateFns = new Set(["SUM", "AVERAGE", "AVG", "MIN", "MAX"]);
  if (aggregateFns.has(name)) {
    const nums = numbersFromArgs(args);
    if (typeof nums === "string") {
      return nums;
    }
    if (nums.length === 0) {
      return 0;
    }
    switch (name) {
      case "SUM":
        return nums.reduce((a, b) => a + b, 0);
      case "AVERAGE":
      case "AVG":
        return nums.reduce((a, b) => a + b, 0) / nums.length;
      case "MIN":
        return Math.min(...nums);
      case "MAX":
        return Math.max(...nums);
      default:
        return ERROR.name;
    }
  }

  return ERROR.name;
}

function TODAY_SERIAL(date: Date): number {
  const epoch = Date.UTC(1899, 11, 30);
  return (Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) - epoch) / 86_400_000;
}

/** Parse and evaluate a formula string (with or without leading `=`). */
export function evaluateSheetFormula(formula: string, env: SheetEvalEnvironment): SheetEvalResult {
  const stripped = formula.trim().startsWith("=") ? formula.trim().slice(1) : formula.trim();
  const normalized = normalizeFormulaSyntax(stripped);
  if (!normalized) {
    return "";
  }
  const tokens = tokenize(normalized);
  if (typeof tokens === "string") {
    return tokens;
  }
  return new Parser(tokens, env).parse();
}

export function literalCellValue(raw: string): SheetEvalResult {
  const trimmed = raw.trim();
  if (trimmed === "") {
    return null;
  }
  if (trimmed.startsWith("=")) {
    return trimmed;
  }
  const num = Number.parseFloat(trimmed);
  if (Number.isFinite(num) && /^-?\d+(\.\d+)?$/.test(trimmed)) {
    return num;
  }
  return raw;
}
