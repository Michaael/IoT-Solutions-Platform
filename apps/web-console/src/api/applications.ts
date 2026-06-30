import { getAuthHeaders } from "../auth/session";

export interface RegisterApplicationPayload {
  appId: string;
  displayName: string;
  tablePrefix?: string;
  schemaName?: string;
}

export interface DeployHistoryEntry {
  version: string;
  deployedAt: string;
  active: boolean;
}

export interface DeployRollbackResult {
  status?: string;
  rolledBackTo?: string;
  applied?: string[];
  skipped?: string[];
  errors?: string[];
}

export async function registerApplication(
  payload: RegisterApplicationPayload
): Promise<{ appId: string; displayName: string }> {
  const response = await fetch("/api/v1/applications", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({
      appId: payload.appId,
      displayName: payload.displayName,
      tablePrefix: payload.tablePrefix ?? "",
      schemaName: payload.schemaName ?? null,
    }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Register application failed: ${response.status}`);
  }
  return response.json() as Promise<{ appId: string; displayName: string }>;
}

export interface ApplicationEventCatalogEntry {
  id: string;
  roles?: string[];
}

export async function fetchApplicationEventCatalog(
  appId: string
): Promise<ApplicationEventCatalogEntry[]> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/events`, {
    headers: getAuthHeaders(),
  });
  if (response.status === 404 || response.status === 403) {
    return [];
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Application events failed: ${response.status}`);
  }
  return response.json() as Promise<ApplicationEventCatalogEntry[]>;
}

export async function fetchDeployHistory(appId: string): Promise<DeployHistoryEntry[]> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/deploy/history`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Deploy history failed: ${response.status}`);
  }
  return response.json() as Promise<DeployHistoryEntry[]>;
}

export interface FunctionVersionEntry {
  version: string;
  deployedAt?: string;
  active?: boolean;
}

export interface FunctionRollbackResult {
  appId: string;
  objectPath: string;
  functionName: string;
  version: string;
  status: string;
}

export async function listFunctionVersions(
  appId: string,
  objectPath: string,
  functionName: string
): Promise<FunctionVersionEntry[]> {
  const params = new URLSearchParams({ objectPath, functionName });
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/functions?${params.toString()}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `List function versions failed: ${response.status}`);
  }
  return response.json() as Promise<FunctionVersionEntry[]>;
}

export async function rollbackFunction(
  appId: string,
  objectPath: string,
  functionName: string,
  version: string
): Promise<FunctionRollbackResult> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/functions/rollback`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ objectPath, functionName, version }),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Function rollback failed: ${response.status}`);
  }
  return response.json() as Promise<FunctionRollbackResult>;
}

export interface BundleObjectLifecycleResult {
  appId?: string;
  version?: string;
  action?: string;
  status?: string;
  applied?: string[];
  skipped?: string[];
  removed?: string[];
  errors?: string[];
}

async function postBundleObjectLifecycle(
  appId: string,
  action: "create" | "update" | "delete"
): Promise<BundleObjectLifecycleResult> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/bundle-objects/${action}`,
    {
      method: "POST",
      headers: getAuthHeaders(),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Bundle object ${action} failed: ${response.status}`);
  }
  return response.json() as Promise<BundleObjectLifecycleResult>;
}

export function createBundleObjects(appId: string): Promise<BundleObjectLifecycleResult> {
  return postBundleObjectLifecycle(appId, "create");
}

export function updateBundleObjects(appId: string): Promise<BundleObjectLifecycleResult> {
  return postBundleObjectLifecycle(appId, "update");
}

export function deleteBundleObjects(appId: string): Promise<BundleObjectLifecycleResult> {
  return postBundleObjectLifecycle(appId, "delete");
}

export async function rollbackDeploy(
  appId: string,
  version: string
): Promise<DeployRollbackResult> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/deploy/rollback`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ version }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Deploy rollback failed: ${response.status}`);
  }
  return response.json() as Promise<DeployRollbackResult>;
}

export interface ApplicationBundleExport {
  appId: string;
  version: string;
  deployedAt: string;
  active: boolean;
  manifest: Record<string, unknown>;
}

export interface BundleValidationResult {
  status: string;
  errors?: string[];
  warnings?: string[];
  wouldApply?: string[];
}

export async function exportApplicationBundle(
  appId: string,
  version?: string
): Promise<ApplicationBundleExport> {
  const params = version ? `?version=${encodeURIComponent(version)}` : "";
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/export${params}`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Bundle export failed: ${response.status}`);
  }
  return response.json() as Promise<ApplicationBundleExport>;
}

export async function validateApplicationBundle(
  appId: string,
  manifest: unknown,
  dryRun = false
): Promise<BundleValidationResult> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/bundle/validate?dryRun=${dryRun ? "true" : "false"}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify(manifest),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Bundle validate failed: ${response.status}`);
  }
  return response.json() as Promise<BundleValidationResult>;
}

export async function deployApplicationBundle(
  appId: string,
  manifest: unknown
): Promise<Record<string, unknown>> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/deploy`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify(manifest),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Bundle deploy failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export interface PullFromTreeResult {
  appId: string;
  baseVersion?: string;
  manifest: Record<string, unknown>;
  discoveredPaths?: string[];
  pulled?: Record<string, number>;
  warnings?: string[];
}

export interface ApplicationDataStatus {
  appId?: string;
  schemaName?: string;
  version?: string;
  appliedMigrations?: string[];
  status?: string;
}

export async function fetchApplicationDataStatus(appId: string): Promise<ApplicationDataStatus> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/data/status`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Data status failed: ${response.status}`);
  }
  return response.json() as Promise<ApplicationDataStatus>;
}

export interface MigrationScriptPayload {
  id: string;
  sql: string;
}

export async function migrateApplicationData(
  appId: string,
  payload: { version: string; scripts: MigrationScriptPayload[] }
): Promise<Record<string, unknown>> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/data/migrate`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Data migrate failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export async function seedApplicationData(
  appId: string,
  profile: string
): Promise<Record<string, unknown>> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/data/seed`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ profile }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Data seed failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export interface ApplicationBindingEntry {
  objectPath?: string;
  variable?: string;
  query?: string;
  refresh?: string;
  refreshIntervalMs?: number;
  enabled?: boolean;
}

export async function fetchApplicationBindings(appId: string): Promise<ApplicationBindingEntry[]> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/bindings`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Bindings list failed: ${response.status}`);
  }
  return response.json() as Promise<ApplicationBindingEntry[]>;
}

export async function deployApplicationBinding(
  appId: string,
  payload: ApplicationBindingEntry & {
    valueField?: string;
    triggerObjectPath?: string;
    triggerFunctionName?: string;
  }
): Promise<Record<string, unknown>> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/bindings/deploy`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify(payload),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Binding deploy failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export async function refreshApplicationBinding(
  appId: string,
  objectPath: string,
  variable: string
): Promise<Record<string, unknown>> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/bindings/refresh`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ objectPath, variable }),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Binding refresh failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export interface ApplicationReportEntry {
  reportId?: string;
  title?: string;
  reportType?: string;
}

export async function fetchApplicationReports(appId: string): Promise<ApplicationReportEntry[]> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/reports`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Reports list failed: ${response.status}`);
  }
  return response.json() as Promise<ApplicationReportEntry[]>;
}

export async function deployApplicationReport(
  appId: string,
  payload: Record<string, unknown>
): Promise<Record<string, unknown>> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/reports/deploy`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify(payload),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Report deploy failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export async function deployApplicationFunction(
  appId: string,
  payload: Record<string, unknown>
): Promise<Record<string, unknown>> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/functions/deploy`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify(payload),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Function deploy failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export async function pullApplicationBundleFromTree(
  appId: string,
  options?: {
    sections?: string[];
    paths?: string[];
    mergeActive?: boolean;
  }
): Promise<PullFromTreeResult> {
  const response = await fetch(
    `/api/v1/applications/${encodeURIComponent(appId)}/bundle/pull-from-tree`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({
        sections: options?.sections,
        paths: options?.paths,
        mergeActive: options?.mergeActive ?? true,
      }),
    }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Bundle pull-from-tree failed: ${response.status}`);
  }
  return response.json() as Promise<PullFromTreeResult>;
}
