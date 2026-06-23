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
