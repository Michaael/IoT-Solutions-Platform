/** Excel / localized function names mapped to ISPF formula engine names. */
const FUNCTION_ALIASES: Record<string, string> = {
  СУММ: "SUM",
  СУММЕСЛИ: "SUMIF",
  СРЗНАЧ: "AVERAGE",
  СРЗНАЧЕСЛИ: "AVERAGEIF",
  СРЕДНЕЕ: "AVERAGE",
  МИН: "MIN",
  МАКС: "MAX",
  ЕСЛИ: "IF",
  ЕСЛИОШИБКА: "IFERROR",
  ОКРУГЛ: "ROUND",
  СЧЁТ: "COUNT",
  СЧЕТ: "COUNT",
  СЧЁТЕСЛИ: "COUNTIF",
  СЧЕТЕСЛИ: "COUNTIF",
  СЧЁТЗ: "COUNTA",
  СЧЕТЗ: "COUNTA",
  И: "AND",
  ИЛИ: "OR",
  НЕ: "NOT",
  ПРОСМОТР: "VLOOKUP",
  ГПР: "HLOOKUP",
  ИНДЕКС: "INDEX",
  ПОИСКПОЗ: "MATCH",
  ДЛСТР: "LEN",
  ЛЕВСИМВ: "LEFT",
  ПРАВСИМВ: "RIGHT",
  ПСТР: "MID",
  СЖПРОБЕЛЫ: "TRIM",
  ПРОПИСН: "UPPER",
  СТРОЧН: "LOWER",
  СЦЕПИТЬ: "CONCATENATE",
  СЕГОДНЯ: "TODAY",
  ТДАТА: "NOW",
  ОСТАТ: "MOD",
  СТЕПЕНЬ: "POWER",
  КОРЕНЬ: "SQRT",
  ЦЕЛОЕ: "INT",
  ОКРВВЕРХ: "CEILING",
  ОКРВНИЗ: "FLOOR",
  AVG: "AVERAGE",
};

export const SUPPORTED_SHEET_FUNCTIONS = new Set([
  "SUM",
  "AVERAGE",
  "AVG",
  "MIN",
  "MAX",
  "COUNT",
  "COUNTA",
  "COUNTBLANK",
  "IF",
  "IFERROR",
  "ROUND",
  "ABS",
  "AND",
  "OR",
  "NOT",
  "PRODUCT",
  "MOD",
  "POWER",
  "SQRT",
  "INT",
  "CEILING",
  "FLOOR",
  "VLOOKUP",
  "HLOOKUP",
  "INDEX",
  "MATCH",
  "SUMIF",
  "COUNTIF",
  "AVERAGEIF",
  "LEN",
  "LEFT",
  "RIGHT",
  "MID",
  "TRIM",
  "UPPER",
  "LOWER",
  "CONCAT",
  "CONCATENATE",
  "TEXT",
  "TODAY",
  "NOW",
  "ISBLANK",
  "ISNUMBER",
  "ISTEXT",
  "ISERROR",
  "ISPREF",
  "ISPSUM",
  "ISPHIST",
]);

export function normalizeFunctionName(name: string): string {
  const upper = name.toUpperCase();
  return FUNCTION_ALIASES[upper] ?? upper;
}

/** Strip Excel absolute refs ($A$1 → A1) and normalize list separators. */
export function normalizeFormulaSyntax(formula: string): string {
  return formula.replace(/\$/g, "").replace(/;/g, ",");
}

/** Replace localized function names with English equivalents (case-insensitive). */
export function normalizeExcelFunctionNames(formula: string): string {
  return formula.replace(
    /([A-Za-zА-ЯЁа-яё][A-Za-z0-9_.А-ЯЁа-яё]*)\s*(?=\()/g,
    (match) => normalizeFunctionName(match)
  );
}

export function normalizeImportedFormula(rawFormula: string): string {
  const body = rawFormula.trim().startsWith("=") ? rawFormula.trim().slice(1) : rawFormula.trim();
  return normalizeExcelFunctionNames(normalizeFormulaSyntax(body));
}

export function extractFormulaFunctionNames(formula: string): string[] {
  const names = new Set<string>();
  const re = /([A-Za-zА-ЯЁа-яё][A-Za-z0-9_.А-ЯЁа-яё]*)\s*(?=\()/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(formula)) !== null) {
    names.add(normalizeFunctionName(match[1]));
  }
  return [...names];
}

export function findUnsupportedFunctions(formula: string): string[] {
  return extractFormulaFunctionNames(formula).filter((name) => !SUPPORTED_SHEET_FUNCTIONS.has(name));
}
