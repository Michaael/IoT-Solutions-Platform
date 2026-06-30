import { getAuthHeaders } from "../auth/session";

export interface ResolvedTimeZone {
  objectPath: string;
  timeZone: string;
}

export async function resolvePlatformTimeZone(objectPath: string): Promise<ResolvedTimeZone> {
  const params = new URLSearchParams({ objectPath });
  const response = await fetch(`/api/v1/platform/timezone/resolve?${params.toString()}`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Timezone resolve failed: ${response.status}`);
  }
  return response.json() as Promise<ResolvedTimeZone>;
}
