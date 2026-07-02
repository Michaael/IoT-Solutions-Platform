import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";

export interface PlatformBackupImportPreview {
  nodeCount: number;
  createCount: number;
  updateCount: number;
  warnings: string[];
}

export interface PlatformBackupImportResult {
  preview: PlatformBackupImportPreview;
  created: number;
  updated: number;
  skipped: number;
  dryRun: boolean;
}

const exportRequests = new Map<string, Promise<Record<string, unknown>>>();

export async function exportPlatformBackup(rootPath?: string): Promise<Record<string, unknown>> {
  const key = rootPath?.trim() ?? "";
  const pending = exportRequests.get(key);
  if (pending) return pending;

  const request = (async () => {
    const params = key ? `?rootPath=${encodeURIComponent(key)}` : "";
    const response = await fetch(`/api/v1/platform/backup/export${params}`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Export failed: ${response.status}`));
    }
    return response.json() as Promise<Record<string, unknown>>;
  })();
  exportRequests.set(key, request);
  try {
    return await request;
  } finally {
    if (exportRequests.get(key) === request) exportRequests.delete(key);
  }
}

export async function importPlatformBackup(
  json: string,
  dryRun: boolean
): Promise<PlatformBackupImportResult> {
  const response = await fetch(`/api/v1/platform/backup/import?dryRun=${dryRun ? "true" : "false"}`, {
    method: "POST",
    headers: {
      ...getAuthHeaders(),
      "Content-Type": "application/json",
    },
    body: json,
  });
  if (!response.ok) {
    throw new Error(parseApiError(await response.text(), `Import failed: ${response.status}`));
  }
  return response.json();
}
