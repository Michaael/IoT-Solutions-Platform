import { getAuthHeaders } from "../auth/session";

export interface PlatformAppSchedule {
  scheduleId: string;
  appId: string;
  enabled: boolean;
  intervalMs: number;
  actionType?: string;
  actionJson?: string;
}

export interface UpsertPlatformAppSchedulePayload {
  scheduleId: string;
  appId: string;
  enabled: boolean;
  intervalMs: number;
  action: {
    type: string;
    json: string;
  };
}

export async function fetchPlatformAppSchedules(): Promise<PlatformAppSchedule[]> {
  const response = await fetch("/api/v1/schedules", { headers: getAuthHeaders() });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Schedules list failed: ${response.status}`);
  }
  const rows = (await response.json()) as Array<Record<string, unknown>>;
  return rows.map((row) => ({
    scheduleId: String(row.scheduleId ?? ""),
    appId: String(row.appId ?? ""),
    enabled: Boolean(row.enabled),
    intervalMs: Number(row.intervalMs ?? 0),
    actionType: row.actionType != null ? String(row.actionType) : undefined,
    actionJson: row.actionJson != null ? String(row.actionJson) : undefined,
  }));
}

export async function upsertPlatformAppSchedule(
  payload: UpsertPlatformAppSchedulePayload
): Promise<{ scheduleId: string; status: string }> {
  const response = await fetch("/api/v1/schedules", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Schedule upsert failed: ${response.status}`);
  }
  return response.json() as Promise<{ scheduleId: string; status: string }>;
}
