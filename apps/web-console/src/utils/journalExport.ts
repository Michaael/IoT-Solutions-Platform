import type { ObjectConfigAuditEntry } from "../api";
import type { ObjectEvent } from "../types/event";
import type { BindingInvokeAuditEntry, FunctionInvokeAuditEntry } from "../types/runtime";

export type JournalExportFormat = "csv" | "json";

export type JournalExportRow = Record<string, unknown>;

function stringifyCell(value: unknown): string {
  if (value == null) {
    return "";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

function collectColumns(rows: JournalExportRow[]): string[] {
  const columns: string[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    for (const key of Object.keys(row)) {
      if (!seen.has(key)) {
        seen.add(key);
        columns.push(key);
      }
    }
  }
  return columns;
}

export function rowsToCsv(rows: JournalExportRow[]): string {
  if (rows.length === 0) {
    return "";
  }
  const columns = collectColumns(rows);
  const escape = (value: unknown) => {
    const text = stringifyCell(value);
    if (/[",\n\r]/.test(text)) {
      return `"${text.replace(/"/g, '""')}"`;
    }
    return text;
  };
  const lines = [
    columns.join(","),
    ...rows.map((row) => columns.map((column) => escape(row[column])).join(",")),
  ];
  return lines.join("\n");
}

export function downloadJournalExport(
  filenameBase: string,
  format: JournalExportFormat,
  rows: JournalExportRow[],
): void {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const filename = `${filenameBase}-${timestamp}.${format}`;
  const content = format === "json" ? JSON.stringify(rows, null, 2) : rowsToCsv(rows);
  const mime = format === "json" ? "application/json" : "text/csv;charset=utf-8";
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function mapObjectAuditExportRow(entry: ObjectConfigAuditEntry): JournalExportRow {
  return {
    id: entry.id,
    objectPath: entry.objectPath,
    occurredAt: entry.occurredAt,
    changeType: entry.changeType,
    field: entry.field,
    actor: entry.actor,
    revisionBefore: entry.revisionBefore,
    revisionAfter: entry.revisionAfter,
    summaryJson: entry.summaryJson,
  };
}

export function mapEventJournalExportRow(event: ObjectEvent): JournalExportRow {
  return {
    id: event.id,
    timestamp: event.timestamp,
    objectPath: event.objectPath,
    eventName: event.eventName,
    level: event.level,
    payload: event.payload,
  };
}

export function mapFunctionInvokeExportRow(entry: FunctionInvokeAuditEntry): JournalExportRow {
  return { ...entry };
}

export function mapBindingInvokeExportRow(entry: BindingInvokeAuditEntry): JournalExportRow {
  return { ...entry };
}
