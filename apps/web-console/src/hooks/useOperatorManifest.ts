import { useQuery } from "@tanstack/react-query";
import type { OperatorManifest } from "../types/operatorManifest";

async function loadManifest(appId: string): Promise<OperatorManifest> {
  const response = await fetch(`/operator-apps/${appId}.manifest.json`);
  if (!response.ok) {
    throw new Error(`Operator manifest not found for app: ${appId}`);
  }
  return response.json();
}

export function useOperatorManifest(appId: string | null) {
  return useQuery({
    queryKey: ["operator-manifest", appId],
    queryFn: () => loadManifest(appId!),
    enabled: Boolean(appId),
  });
}
