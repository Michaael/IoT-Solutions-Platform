import { getAuthHeaders } from "../auth/session";

export interface PlatformRuntimeSetting {
  id: string;
  envVar: string;
  propertyKey: string;
  type: "boolean" | "integer" | "string" | "duration";
  value: string;
  defaultValue: string;
  source: "environment" | "file" | "default" | "override";
  environmentValue?: string | null;
  overridesEnvironment?: boolean;
  sensitive: boolean;
  editable: boolean;
  hotReloadable: boolean;
  restartRequired: boolean;
}

export interface PlatformRuntimeSettingsSection {
  id: string;
  title: string;
  settings: PlatformRuntimeSetting[];
}

export interface PlatformRuntimeSettingsResponse {
  settingsFile: string;
  sections: PlatformRuntimeSettingsSection[];
}

export interface PlatformRuntimeSettingsPatchResult {
  restartRequired: boolean;
  appliedLive: string[];
  skippedEnvLocked: string[];
  errors: string[];
}

async function parseJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchPlatformRuntimeSettings(): Promise<PlatformRuntimeSettingsResponse> {
  return fetch("/api/v1/platform/runtime-settings", {
    headers: getAuthHeaders(),
  }).then((response) => parseJson<PlatformRuntimeSettingsResponse>(response));
}

export function patchPlatformRuntimeSettings(
  values: Record<string, string>,
): Promise<PlatformRuntimeSettingsPatchResult> {
  return fetch("/api/v1/platform/runtime-settings", {
    method: "PATCH",
    headers: {
      ...getAuthHeaders(),
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ values }),
  }).then((response) => parseJson<PlatformRuntimeSettingsPatchResult>(response));
}
