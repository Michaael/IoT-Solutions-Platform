import type { ReportColumn } from "../../api/reports";

export type ReportKind = "sql" | "tree-variables";

export function isTreeVariablesReport(reportType?: string): boolean {
  return reportType?.trim() === "tree-variables";
}

export function parseParametersText(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function parseColumnsJson(text: string): ReportColumn[] {
  const parsed = JSON.parse(text) as ReportColumn[];
  if (!Array.isArray(parsed)) {
    throw new Error("columns must be a JSON array");
  }
  return parsed.map(normalizeColumn);
}

export function normalizeColumn(col: ReportColumn): ReportColumn {
  return {
    field: String(col.field ?? "").trim(),
    label: String(col.label ?? "").trim(),
  };
}

export function validateParameters(names: string[]): string | null {
  const seen = new Set<string>();
  for (const name of names) {
    if (!name) {
      return "Имя параметра не может быть пустым";
    }
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name)) {
      return `Недопустимое имя параметра: ${name}`;
    }
    if (seen.has(name)) {
      return `Дублирующийся параметр: ${name}`;
    }
    seen.add(name);
  }
  return null;
}

export function validateColumns(columns: ReportColumn[]): string | null {
  if (columns.length === 0) {
    return "Добавьте хотя бы одну колонку";
  }
  const seen = new Set<string>();
  for (const col of columns) {
    if (!col.field) {
      return "У каждой колонки должен быть field";
    }
    if (!col.label) {
      return `Колонка ${col.field}: укажите label`;
    }
    if (seen.has(col.field)) {
      return `Дублирующийся field: ${col.field}`;
    }
    seen.add(col.field);
  }
  return null;
}

export function buildDefaultParameters(
  parameters: string[],
  values: Record<string, string>
): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const name of parameters) {
    const raw = values[name];
    if (raw == null || !String(raw).trim()) {
      continue;
    }
    const trimmed = String(raw).trim();
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) {
      result[name] = Number(trimmed);
    } else if (trimmed === "true" || trimmed === "false") {
      result[name] = trimmed === "true";
    } else {
      result[name] = trimmed;
    }
  }
  return result;
}

export function defaultParameterValues(
  parameters: string[],
  defaults: Record<string, unknown> | undefined
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const name of parameters) {
    const value = defaults?.[name];
    result[name] = value != null ? String(value) : "";
  }
  return result;
}

export function paramValuesFromRun(
  parameters: string[],
  defaults: Record<string, unknown> | undefined
): Record<string, string> {
  return defaultParameterValues(parameters, defaults);
}

export function inferTemplateFormat(filename: string, fallback = "xls"): string {
  const dot = filename.lastIndexOf(".");
  if (dot < 0) {
    return fallback;
  }
  switch (filename.slice(dot + 1).toLowerCase()) {
    case "xlsx":
      return "xlsx";
    case "xls":
      return "xls";
    case "docx":
      return "docx";
    case "doc":
      return "doc";
    case "html":
    case "htm":
      return "html";
    default:
      return fallback;
  }
}
