import { useQueries, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { fetchOperatorApps, fetchOperatorAppUi } from "../api/operatorApps";
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

export async function loadOperatorAppUi(appId: string): Promise<OperatorUi | null> {
  const fromPlatformApi = await fetchOperatorAppUi(appId);
  if (fromPlatformApi) {
    return fromPlatformApi;
  }
  return loadUiFromBundleApi(appId);
}

export function useOperatorAppsRegistry(currentUi?: OperatorUi) {
  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: fetchOperatorApps,
    staleTime: 60_000,
  });

  const appIds = useMemo(() => appsQuery.data?.map((app) => app.appId) ?? [], [appsQuery.data]);

  const uiQueries = useQueries({
    queries: appIds.map((appId) => ({
      queryKey: ["operator-ui", appId],
      queryFn: () => loadOperatorAppUi(appId),
      enabled: Boolean(appId),
      staleTime: 60_000,
    })),
  });

  const uiSignature = uiQueries
    .map((query, index) => `${appIds[index] ?? ""}:${query.dataUpdatedAt}:${query.data?.appId ?? ""}`)
    .join("|");

  const operatorApps = useMemo(() => {
    const loaded = uiQueries
      .map((query) => query.data)
      .filter((ui): ui is OperatorUi => Boolean(ui));
    if (!currentUi) {
      return loaded;
    }
    const merged = new Map(loaded.map((ui) => [ui.appId, ui]));
    merged.set(currentUi.appId, currentUi);
    return [...merged.values()];
  }, [currentUi, uiSignature, uiQueries]);

  const isLoading =
    appsQuery.isLoading || uiQueries.some((query) => query.isLoading && query.fetchStatus !== "idle");

  return { operatorApps, isLoading, appsQuery };
}
