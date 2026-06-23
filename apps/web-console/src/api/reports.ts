import { getAuthHeaders } from "../auth/session";

export interface ReportColumn {
  field: string;
  label: string;
}

export interface ReportDefinition {
  path: string;
  title: string;
  dataSourcePath: string;
  legacyAppId?: string;
  query: string;
  reportType?: string;
  devicePathPattern?: string;
  variableName?: string;
  parameters: string[];
  columns: ReportColumn[];
  defaultParameters: Record<string, unknown>;
  maxRows: number;
  refreshIntervalMs: number;
  templateFormat: string;
  hasTemplate: boolean;
}

export interface ReportRunResult {
  path?: string;
  reportId: string;
  title: string;
  columns: ReportColumn[];
  rows: Array<Record<string, unknown>>;
  rowCount: number;
  truncated: boolean;
}

export interface SaveReportDefinitionPayload {
  title?: string;
  dataSourcePath?: string;
  /** @deprecated use dataSourcePath */
  appId?: string;
  query: string;
  parameters?: string[];
  columns?: ReportColumn[];
  defaultParameters?: Record<string, unknown>;
  maxRows?: number;
  refreshIntervalMs?: number;
}

export interface SaveTreeVariablesReportPayload {
  title?: string;
  devicePathPattern: string;
  variableName: string;
  columns?: ReportColumn[];
  maxRows?: number;
  refreshIntervalMs?: number;
}

export interface ObjectWriteOptions {
  revision?: number;
  force?: boolean;
}

async function parseError(response: Response, fallback: string): Promise<string> {
  const text = await response.text();
  return text || fallback;
}

export function fetchReport(path: string): Promise<ReportDefinition> {
  const params = new URLSearchParams({ path });
  return fetch(`/api/v1/reports/by-path?${params}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Failed to load report: ${response.status}`));
    }
    return response.json();
  });
}

export function saveTreeVariablesReportDefinition(
  path: string,
  payload: SaveTreeVariablesReportPayload
): Promise<ReportDefinition> {
  const params = new URLSearchParams({ path });
  return fetch(`/api/v1/reports/by-path/tree-variables-definition?${params}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Failed to save tree-variables report: ${response.status}`));
    }
    return response.json();
  });
}

export function saveReportDefinition(
  path: string,
  payload: SaveReportDefinitionPayload
): Promise<ReportDefinition> {
  const params = new URLSearchParams({ path });
  return fetch(`/api/v1/reports/by-path/definition?${params}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify(payload),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Failed to save report: ${response.status}`));
    }
    return response.json();
  });
}

export function runReportByPath(
  path: string,
  parameters?: Record<string, unknown>
): Promise<ReportRunResult> {
  const params = new URLSearchParams({ path });
  return fetch(`/api/v1/reports/by-path/run?${params}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ parameters: parameters ?? {} }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Report run failed: ${response.status}`));
    }
    return response.json();
  });
}

export type ReportExportFormat = "csv" | "pdf" | "xlsx" | "html";

function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = "none";
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}

export function downloadReportExportByPath(
  path: string,
  format: ReportExportFormat,
  parameters?: Record<string, string>
): Promise<void> {
  const params = new URLSearchParams(parameters ?? {});
  params.set("path", path);
  params.set("format", format);
  return fetch(`/api/v1/reports/by-path/export?${params.toString()}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Report export failed: ${response.status}`));
    }
    const blob = await response.blob();
    triggerBlobDownload(
      blob,
      `${path.split(".").pop() ?? "report"}.${format === "pdf" ? "pdf" : format}`
    );
  });
}

export function uploadReportTemplate(path: string, format: string, file: File): Promise<ReportDefinition> {
  const params = new URLSearchParams({ path, format });
  const body = new FormData();
  body.append("file", file);
  return fetch(`/api/v1/reports/by-path/template?${params}`, {
    method: "POST",
    headers: getAuthHeaders(),
    body,
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Failed to upload template: ${response.status}`));
    }
    return response.json();
  });
}

export function downloadReportTemplate(path: string): Promise<void> {
  const params = new URLSearchParams({ path });
  return fetch(`/api/v1/reports/by-path/template?${params}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Failed to download template: ${response.status}`));
    }
    const blob = await response.blob();
    triggerBlobDownload(blob, `${path.split(".").pop() ?? "report"}-template`);
  });
}

export function deleteReportTemplate(path: string): Promise<ReportDefinition> {
  const params = new URLSearchParams({ path });
  return fetch(`/api/v1/reports/by-path/template?${params}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Failed to delete template: ${response.status}`));
    }
    return response.json();
  });
}

export function downloadReportCsvByPath(
  path: string,
  parameters?: Record<string, string>
): Promise<void> {
  const params = new URLSearchParams(parameters ?? {});
  params.set("path", path);
  params.set("format", "csv");
  return fetch(`/api/v1/reports/by-path/export?${params.toString()}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Report export failed: ${response.status}`));
    }
    const blob = await response.blob();
    triggerBlobDownload(blob, `${path.split(".").pop() ?? "report"}.csv`);
  });
}

/** Legacy app-scoped API (bundle / operator manifest). */
export async function runReport(
  appId: string,
  reportId: string,
  parameters?: Record<string, unknown>
): Promise<ReportRunResult> {
  const path = `root.platform.reports.${reportId}`;
  try {
    return await runReportByPath(path, parameters);
  } catch {
    const response = await fetch(`/api/v1/applications/${appId}/reports/${reportId}/run`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ parameters: parameters ?? {} }),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Report run failed: ${response.status}`);
    }
    return response.json();
  }
}

export async function downloadReportExport(
  appId: string,
  reportId: string,
  format: ReportExportFormat,
  parameters?: Record<string, string>
): Promise<void> {
  const path = reportPathFromId(reportId);
  try {
    await downloadReportExportByPath(path, format, parameters);
  } catch {
    const params = new URLSearchParams(parameters ?? {});
    params.set("format", format);
    const response = await fetch(
      `/api/v1/applications/${appId}/reports/${reportId}/export?${params.toString()}`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Report export failed: ${response.status}`);
    }
    const blob = await response.blob();
    triggerBlobDownload(blob, `${reportId}.${format === "pdf" ? "pdf" : format}`);
  }
}

export async function downloadReportCsv(
  appId: string,
  reportId: string,
  parameters?: Record<string, string>
): Promise<void> {
  const path = `root.platform.reports.${reportId}`;
  try {
    await downloadReportCsvByPath(path, parameters);
  } catch {
    const params = new URLSearchParams(parameters ?? {});
    const response = await fetch(
      `/api/v1/applications/${appId}/reports/${reportId}/export?${params.toString()}`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Report export failed: ${response.status}`);
    }
    const blob = await response.blob();
    triggerBlobDownload(blob, `${reportId}.csv`);
  }
}

export function reportPathFromId(reportId: string): string {
  return `root.platform.reports.${reportId}`;
}
