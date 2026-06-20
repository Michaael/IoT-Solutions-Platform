import { useQuery } from "@tanstack/react-query";
import { fetchOperatorAppUi } from "../api/operatorApps";
import { getAuthHeaders } from "../auth/session";
import type { OperatorUi } from "../types/operatorUi";

const BUNDLE_API_PATHS = ["operator-ui", "hmi-ui"] as const;

async function loadUiFromBundleApi(appId: string): Promise<OperatorUi | null> {
  for (const path of BUNDLE_API_PATHS) {
    const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/${path}`, {
      headers: getAuthHeaders(),
    });
    if (response.status === 404 || response.status === 403) {
      continue;
    }
    if (!response.ok) {
      throw new Error(`Operator UI API failed: ${response.status}`);
    }
    return response.json() as Promise<OperatorUi>;
  }
  return null;
}

async function loadUiFromPublic(appId: string): Promise<OperatorUi | null> {
  const response = await fetch(`/operator-apps/${appId}.ui.json`);
  if (!response.ok) {
    return null;
  }
  return response.json() as Promise<OperatorUi>;
}

async function loadOperatorUi(appId: string): Promise<OperatorUi> {
  const fromPlatformApi = await fetchOperatorAppUi(appId);
  if (fromPlatformApi) {
    return fromPlatformApi;
  }
  const fromBundle = await loadUiFromBundleApi(appId);
  if (fromBundle) {
    return fromBundle;
  }
  const fromPublic = await loadUiFromPublic(appId);
  if (fromPublic) {
    return fromPublic;
  }
  throw new Error(`Operator UI not found for app: ${appId}`);
}

export function useOperatorUi(appId: string | null) {
  return useQuery({
    queryKey: ["operator-ui", appId],
    queryFn: () => loadOperatorUi(appId!),
    enabled: Boolean(appId),
  });
}
