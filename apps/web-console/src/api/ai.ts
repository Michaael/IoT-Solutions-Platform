import { getAuthHeaders } from "../auth/session";

export interface AiValidationResult {
  status: string;
  errors?: string[];
  warnings?: string[];
  wouldApply?: string[];
  auditId?: number;
}

export interface AiContextPackInfo {
  contextPackVersion: string;
  platformVersion?: string;
  generatedAt?: string;
  contentSha256?: string;
  exampleCount?: number;
}

export interface AiProviderStatus {
  enabled: boolean;
  providerId: string;
  available: boolean;
  model?: string;
}

export interface AiGenerateResult {
  artifact: Record<string, unknown>;
  validation: AiValidationResult;
  dryRun: AiValidationResult;
  publishable: boolean;
  auditId: number;
  provider: AiProviderStatus;
}

async function parseError(response: Response, fallback: string): Promise<string> {
  const text = await response.text();
  return text || fallback;
}

export function fetchAiContextPack(): Promise<AiContextPackInfo> {
  return fetch("/api/v1/ai/tools/context-pack", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Context pack failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchAiProviderStatus(): Promise<AiProviderStatus> {
  return fetch("/api/v1/ai/provider", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Provider status failed: ${response.status}`));
    }
    return response.json();
  });
}

export function validateAiBundle(appId: string, manifest: unknown): Promise<AiValidationResult> {
  return fetch("/api/v1/ai/tools/validate-bundle", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ appId, manifest }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Validate failed: ${response.status}`));
    }
    return response.json();
  });
}

export function dryRunAiDeploy(appId: string, manifest: unknown): Promise<AiValidationResult> {
  return fetch("/api/v1/ai/tools/dry-run-deploy", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ appId, manifest }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Dry-run failed: ${response.status}`));
    }
    return response.json();
  });
}

export function generateAiBundle(
  appId: string,
  prompt: string,
  baseManifest?: unknown
): Promise<AiGenerateResult> {
  return fetch("/api/v1/ai/bundles/generate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ appId, prompt, baseManifest }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(await parseError(response, `Generate failed: ${response.status}`));
    }
    return response.json();
  });
}
