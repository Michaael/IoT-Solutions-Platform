import { getAuthHeaders } from "../auth/session";

export interface RegisterApplicationPayload {
  appId: string;
  displayName: string;
  tablePrefix?: string;
  schemaName?: string;
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
