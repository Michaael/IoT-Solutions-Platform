import { getAuthHeaders } from "../auth/session";
import type { OperatorUi } from "../types/operatorUi";

export interface OperatorAppEntry {
  appId: string;
  title: string;
}

export async function fetchOperatorApps(): Promise<OperatorAppEntry[]> {
  const response = await fetch("/api/v1/operator-apps", {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Operator apps API failed: ${response.status}`);
  }
  return response.json() as Promise<OperatorAppEntry[]>;
}

export async function fetchOperatorAppUi(appId: string): Promise<OperatorUi | null> {
  const response = await fetch(`/api/v1/operator-apps/${encodeURIComponent(appId)}/ui`, {
    headers: getAuthHeaders(),
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Operator app UI API failed: ${response.status}`);
  }
  return response.json() as Promise<OperatorUi>;
}

export async function createOperatorApp(appId: string, title: string): Promise<OperatorUi> {
  const response = await fetch(`/api/v1/operator-apps/${encodeURIComponent(appId)}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ title }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Create operator app failed: ${response.status}`);
  }
  return response.json() as Promise<OperatorUi>;
}

export async function saveOperatorAppUi(appId: string, ui: OperatorUi): Promise<OperatorUi> {
  const response = await fetch(`/api/v1/operator-apps/${encodeURIComponent(appId)}/ui`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({
      title: ui.title,
      defaultDashboard: ui.defaultDashboard,
      dashboards: ui.dashboards,
    }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Save operator app failed: ${response.status}`);
  }
  return response.json() as Promise<OperatorUi>;
}
