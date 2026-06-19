import { useQuery } from "@tanstack/react-query";
import { getStoredRole } from "../auth/role";
import type { OperatorManifest } from "../types/operatorManifest";

async function loadManifestFromApi(appId: string): Promise<OperatorManifest | null> {
  const response = await fetch(`/api/v1/applications/${encodeURIComponent(appId)}/operator-manifest`, {
    headers: { "X-ISPF-Role": getStoredRole() },
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Operator manifest API failed: ${response.status}`);
  }
  return response.json();
}

async function loadManifestFromPublic(appId: string): Promise<OperatorManifest> {
  const response = await fetch(`/operator-apps/${appId}.manifest.json`);
  if (!response.ok) {
    throw new Error(`Operator manifest not found for app: ${appId}`);
  }
  return response.json();
}

async function loadManifest(appId: string): Promise<OperatorManifest> {
  const fromApi = await loadManifestFromApi(appId);
  if (fromApi) {
    return fromApi;
  }
  return loadManifestFromPublic(appId);
}

export function useOperatorManifest(appId: string | null) {
  return useQuery({
    queryKey: ["operator-manifest", appId],
    queryFn: () => loadManifest(appId!),
    enabled: Boolean(appId),
  });
}
