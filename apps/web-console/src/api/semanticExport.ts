import { getAuthHeaders } from "../auth/session";

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export async function exportHaystackModel(options?: {
  rootPath?: string;
  includePoints?: boolean;
}): Promise<Record<string, unknown>> {
  const params = new URLSearchParams();
  if (options?.rootPath?.trim()) {
    params.set("rootPath", options.rootPath.trim());
  }
  params.set("includePoints", options?.includePoints === false ? "false" : "true");
  const response = await fetch(`/api/v1/platform/haystack/export?${params.toString()}`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Haystack export failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export async function downloadHaystackExport(options?: {
  rootPath?: string;
  includePoints?: boolean;
}): Promise<void> {
  const data = await exportHaystackModel(options);
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-");
  downloadBlob(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    `haystack-export-${stamp}.json`
  );
}

export async function exportBrickJsonLd(options?: {
  rootPath?: string;
  includePoints?: boolean;
}): Promise<Record<string, unknown>> {
  const params = new URLSearchParams({ format: "jsonld" });
  if (options?.rootPath?.trim()) {
    params.set("rootPath", options.rootPath.trim());
  }
  params.set("includePoints", options?.includePoints === false ? "false" : "true");
  const response = await fetch(`/api/v1/platform/brick/export?${params.toString()}`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Brick export failed: ${response.status}`);
  }
  return response.json() as Promise<Record<string, unknown>>;
}

export async function exportBrickTurtle(options?: {
  rootPath?: string;
  includePoints?: boolean;
}): Promise<string> {
  const params = new URLSearchParams({ format: "turtle" });
  if (options?.rootPath?.trim()) {
    params.set("rootPath", options.rootPath.trim());
  }
  params.set("includePoints", options?.includePoints === false ? "false" : "true");
  const response = await fetch(`/api/v1/platform/brick/export?${params.toString()}`, {
    headers: { ...getAuthHeaders(), Accept: "text/turtle" },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Brick turtle export failed: ${response.status}`);
  }
  return response.text();
}

export async function downloadBrickExport(
  format: "jsonld" | "turtle",
  options?: { rootPath?: string; includePoints?: boolean }
): Promise<void> {
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-");
  if (format === "turtle") {
    const turtle = await exportBrickTurtle(options);
    downloadBlob(new Blob([turtle], { type: "text/turtle" }), `brick-export-${stamp}.ttl`);
    return;
  }
  const data = await exportBrickJsonLd(options);
  downloadBlob(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    `brick-export-${stamp}.jsonld`
  );
}
