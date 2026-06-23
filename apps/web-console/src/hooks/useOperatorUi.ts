import { useQuery } from "@tanstack/react-query";
import { loadOperatorAppUi } from "./useOperatorAppsRegistry";
import type { OperatorUi } from "../types/operatorUi";

async function loadUiFromPublic(appId: string): Promise<OperatorUi | null> {
  const response = await fetch(`/operator-apps/${appId}.ui.json`);
  if (!response.ok) {
    return null;
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("json")) {
    return null;
  }
  return response.json() as Promise<OperatorUi>;
}

async function loadOperatorUi(appId: string): Promise<OperatorUi | null> {
  const fromRegistry = await loadOperatorAppUi(appId);
  if (fromRegistry) {
    return fromRegistry;
  }
  return loadUiFromPublic(appId);
}

export function useOperatorUi(appId: string | null) {
  return useQuery({
    queryKey: ["operator-ui", appId],
    queryFn: () => loadOperatorUi(appId!),
    enabled: Boolean(appId),
  });
}
