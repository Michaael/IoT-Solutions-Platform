import { getAuthHeaders } from "../auth/session";

export interface ReportColumn {
  field: string;
  label: string;
}

export interface ReportDefinition {
  path: string;
  title: string;
  appId: string;
  query: string;
  parameters: string[];
  columns: ReportColumn[];
  defaultParameters: Record<string, unknown>;
  maxRows: number;
  refreshIntervalMs: number;
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
  appId?: string;
  query: string;
  parameters?: string[];
  columns?: ReportColumn[];
  defaultParameters?: Record<string, unknown>;
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

export function downloadReportCsvByPath(
  path: string,
  parameters?: Record<string, string>
): Promise<void> {
  const params = new URLSearchParams(parameters ?? {});
  params.set("path", path);
  return fetch(`/api/v1/reports/by-path/export?${params.toString()}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Report export failed: ${response.status}`));
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${path.split(".").pop() ?? "report"}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
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
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${reportId}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  }
}

export function reportPathFromId(reportId: string): string {
  return `root.platform.reports.${reportId}`;
}
