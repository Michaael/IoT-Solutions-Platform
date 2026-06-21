export interface PlatformUpdateStatus {
  checkEnabled: boolean;
  applyEnabled: boolean;
  currentVersion: string;
  latestVersion: string | null;
  updateAvailable: boolean;
  releaseName: string | null;
  releaseUrl: string | null;
  releaseNotes: string | null;
  publishedAt: string | null;
  checkedAt: string | null;
  checkError: string | null;
  applyState: string;
  applyMessage: string | null;
  applyStartedAt: string | null;
  accepted?: boolean;
  message?: string;
}

import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(parseApiError(text, `Request failed: ${response.status}`));
  }
  return response.json();
}

export function fetchPlatformUpdateStatus(): Promise<PlatformUpdateStatus> {
  return request("/api/v1/platform/update/status");
}

export function checkPlatformUpdateNow(): Promise<PlatformUpdateStatus> {
  return request("/api/v1/platform/update/check", { method: "POST" });
}

export function applyPlatformUpdate(): Promise<PlatformUpdateStatus> {
  return request("/api/v1/platform/update/apply", { method: "POST" });
}
