import { getAuthHeaders } from "../auth/session";

export interface AutomationIndexStats {
  alertRulesIndexed: number;
  correlatorsIndexed: number;
  workflowTriggersIndexed: number;
  lastRebuildAt: string | null;
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchAutomationIndexStats(): Promise<AutomationIndexStats> {
  return fetch("/api/v1/platform/automation-index/stats", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<AutomationIndexStats>(response));
}
