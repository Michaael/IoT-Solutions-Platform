import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";

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
  reason?: string;
}

export interface AiGenerateResult {
  artifact: Record<string, unknown>;
  validation: AiValidationResult;
  dryRun: AiValidationResult;
  publishable: boolean;
  auditId: number;
  provider: AiProviderStatus;
}

export interface AiAgentTool {
  name: string;
  description: string;
}

export interface AiAgentStep {
  step: number;
  type: "tool" | "finish";
  tool?: string;
  label?: string;
  arguments?: Record<string, unknown>;
  result?: Record<string, unknown>;
  summary?: string;
}

export interface AiAgentTurn {
  turnId: string;
  userMessage: string;
  assistantSummary: string;
  status: string;
  steps: AiAgentStep[];
  result: Record<string, unknown>;
  createdAt: string;
}

export interface AiAgentSessionSummary {
  sessionId: string;
  title: string;
  rootPath: string;
  createdAt: string;
  updatedAt: string;
}

export interface AiAgentSession extends AiAgentSessionSummary {
  turns: AiAgentTurn[];
}

export interface AiAgentChatResponse {
  status: string;
  sessionId: string;
  turnId: string;
  title: string;
  message: string;
  rootPath: string;
  steps: AiAgentStep[];
  summary: string;
  result: Record<string, unknown>;
  provider: AiProviderStatus;
  contextPackVersion: string;
}

export interface AiAgentRunResult {
  status: string;
  sessionId?: string;
  turnId?: string;
  title?: string;
  goal?: string;
  message?: string;
  rootPath: string;
  steps: AiAgentStep[];
  summary: string;
  result: Record<string, unknown>;
  tools?: AiAgentTool[];
  provider: AiProviderStatus;
  contextPackVersion: string;
}

/** When provider responds but agent routes do not, the backend build is usually stale. */
export function agentApiUnavailableMessage(providerReachable: boolean): string {
  if (providerReachable) {
    return (
      "Сервер устарел: нет API агента (/api/v1/ai/agent/*). " +
      "Пересоберите и перезапустите ispf-server (./gradlew :packages:ispf-server:bootRun --args=\"--spring.profiles.active=local\")."
    );
  }
  return "Нет прав administrator или сессия истекла. Выйдите из консоли и войдите снова как admin.";
}

async function throwAiHttpError(response: Response, fallback: string): Promise<never> {
  const text = await response.text();
  if (response.status === 403 && fallback.includes("agent")) {
    throw new Error(
      "Нет доступа к API агента (403). " +
        "Если статус провайдера на вкладке «Настройки» загружается — пересоберите и перезапустите ispf-server. " +
        "Иначе войдите как admin или обновите сессию."
    );
  }
  throw new Error(parseApiError(text, fallback));
}

export function fetchAiContextPack(): Promise<AiContextPackInfo> {
  return fetch("/api/v1/ai/tools/context-pack", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Context pack failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchAiProviderStatus(): Promise<AiProviderStatus> {
  return fetch("/api/v1/ai/provider", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Provider status failed: ${response.status}`));
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
      throw new Error(parseApiError(await response.text(), `Validate failed: ${response.status}`));
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
      throw new Error(parseApiError(await response.text(), `Dry-run failed: ${response.status}`));
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
      throw new Error(parseApiError(await response.text(), `Generate failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchAiAgentTools(): Promise<{ tools: AiAgentTool[] }> {
  return fetch("/api/v1/ai/agent/tools", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Agent tools failed: ${response.status}`);
    }
    return response.json();
  });
}

export function runAiAgent(goal: string, rootPath?: string): Promise<AiAgentRunResult> {
  return fetch("/api/v1/ai/agent/run", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ goal, rootPath: rootPath || "root" }),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Agent run failed: ${response.status}`);
    }
    return response.json();
  });
}

export function createAgentSession(rootPath?: string): Promise<AiAgentSessionSummary> {
  return fetch("/api/v1/ai/agent/sessions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ rootPath: rootPath || "root" }),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Create session failed: ${response.status}`);
    }
    return response.json();
  });
}

export function fetchAgentSession(sessionId: string): Promise<AiAgentSession> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Fetch session failed: ${response.status}`);
    }
    return response.json();
  });
}

export function sendAgentMessage(
  sessionId: string,
  message: string,
  rootPath?: string
): Promise<AiAgentChatResponse> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ message, rootPath }),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Send message failed: ${response.status}`);
    }
    return response.json();
  });
}

export function deleteAgentSession(sessionId: string): Promise<{ status: string; sessionId: string }> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Delete session failed: ${response.status}`);
    }
    return response.json();
  });
}
