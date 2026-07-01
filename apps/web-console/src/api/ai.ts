import { getAuthHeaders, getStoredSession } from "../auth/session";
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
  capabilities?: {
    vision?: boolean;
    textAttachments?: boolean;
  };
  supportedAttachmentTypes?: Array<{
    kind: string;
    mimeTypes?: string[];
    extensions?: string[];
  }>;
}

export interface AgentMessageAttachment {
  name: string;
  mimeType: string;
  contentBase64: string;
}

export interface AgentMessageAttachmentMeta {
  name?: string;
  mimeType?: string;
  byteSize?: number;
  kind?: "text" | "image";
  truncated?: boolean;
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
  type: "tool" | "finish" | "error" | "guard";
  tool?: string;
  label?: string;
  arguments?: Record<string, unknown>;
  result?: Record<string, unknown>;
  summary?: string;
  error?: string;
  hint?: string;
  rawPreview?: string;
  truncated?: boolean;
}

export interface AiAgentTurn {
  turnId: string;
  userMessage: string;
  assistantSummary: string;
  status: string;
  steps: AiAgentStep[];
  result: Record<string, unknown>;
  attachments?: AgentMessageAttachmentMeta[];
  interactionMode?: AgentInteractionMode;
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
  planState?: AgentPlanState;
}

export interface AiAgentRunProgress {
  running: boolean;
  sessionId?: string;
  userMessage?: string;
  steps?: AiAgentStep[];
  stepsCompleted?: number;
  planState?: AgentPlanState;
}

export type AgentInteractionMode = "auto" | "plan" | "execute" | "ask";

export interface AgentPlanState {
  interactionMode?: AgentInteractionMode;
  planPhase?: "none" | "planning" | "awaiting_approval" | "approved";
  planApproved?: boolean;
  plan?: Record<string, unknown>;
}

export interface AgentPlanQuestion {
  id?: string;
  text?: string;
  options?: Array<{ label?: string; value?: string }>;
}

export interface AiAgentChatResponse {
  status: string;
  sessionId: string;
  turnId?: string;
  title: string;
  message: string;
  rootPath: string;
  steps: AiAgentStep[];
  summary: string;
  result: Record<string, unknown>;
  provider: AiProviderStatus;
  contextPackVersion: string;
  stepsCompleted?: number;
  maxSteps?: number;
  running?: boolean;
  planState?: AgentPlanState;
  attachments?: AgentMessageAttachmentMeta[];
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

export interface AiModelEntry {
  id: string;
  label?: string;
  providerId?: string;
}

export interface AiModelsResponse {
  models?: AiModelEntry[];
  defaultModel?: string;
}

export function fetchAiModels(): Promise<AiModelsResponse> {
  return fetch("/api/v1/ai/models", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Models list failed: ${response.status}`));
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

export function createAgentSession(
  rootPath?: string,
  interactionMode?: AgentInteractionMode
): Promise<AiAgentSessionSummary> {
  return fetch("/api/v1/ai/agent/sessions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({
      rootPath: rootPath || "root",
      interactionMode: interactionMode ?? "auto",
    }),
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
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Fetch session failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface AiAgentAcceptedResponse {
  status: "ACCEPTED";
  sessionId: string;
  running: true;
}

export function isAgentAcceptedResponse(
  value: AiAgentChatResponse | AiAgentAcceptedResponse
): value is AiAgentAcceptedResponse {
  return value.status === "ACCEPTED" && value.running === true;
}

export function sessionTurnToChatResponse(
  session: AiAgentSession,
  turn: AiAgentTurn,
  provider: AiProviderStatus,
  contextPackVersion = ""
): AiAgentChatResponse {
  return {
    status: turn.status,
    sessionId: session.sessionId,
    turnId: turn.turnId,
    title: session.title,
    message: turn.userMessage,
    rootPath: session.rootPath,
    steps: turn.steps,
    summary: turn.assistantSummary,
    result: turn.result,
    provider,
    contextPackVersion,
    planState: session.planState,
    attachments: turn.attachments,
  };
}

export function waitUntilAgentRunIdle(sessionId: string, timeoutMs = 25_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const tick = async () => {
      try {
        const progress = await fetchAgentRunProgress(sessionId);
        if (!progress.running) {
          resolve();
          return;
        }
        if (Date.now() >= deadline) {
          reject(new Error("Agent run still in progress — try Cancel or wait a moment"));
          return;
        }
        setTimeout(() => void tick(), 500);
      } catch (error) {
        reject(error);
      }
    };
    void tick();
  });
}

export function sendAgentMessage(
  sessionId: string,
  message: string,
  rootPath?: string,
  interactionMode?: AgentInteractionMode,
  attachments?: AgentMessageAttachment[],
  async = true
): Promise<AiAgentChatResponse | AiAgentAcceptedResponse> {
  const url = `/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/messages${
    async ? "?async=true" : ""
  }`;
  return fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ message, rootPath, interactionMode, attachments }),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Send message failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface WaitForAgentTurnOptions {
  /** Turns already in session before this message — ignore stale last turn. */
  baselineTurnCount?: number;
}

/** Ignore early running=false before the server registers the async run. */
const POST_SEND_GRACE_MS = 8_000;
/** Require running=false to stay stable before treating the run as finished. */
const RUN_IDLE_CONFIRM_MS = 2_000;
/** Poll session for a new turn after running=false (LLM + DB commit can take minutes). */
const TURN_FETCH_ATTEMPTS = 120;
const TURN_FETCH_DELAY_MS = 500;
const TURN_FETCH_LATE_PAUSE_MS = 10_000;
const TURN_FETCH_LATE_ATTEMPTS = 80;

export function isAgentTurnResultDeliveryError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return message.includes("without a turn result");
}

export async function fetchNewAgentTurnResponse(
  sessionId: string,
  baselineTurnCount: number
): Promise<AiAgentChatResponse | null> {
  const [session, provider, contextPack] = await Promise.all([
    fetchAgentSession(sessionId),
    fetchAiProviderStatus(),
    fetchAiContextPack().catch(() => ({ contextPackVersion: "" } as AiContextPackInfo)),
  ]);
  if (session.turns.length <= baselineTurnCount) {
    return null;
  }
  const turn = session.turns.at(-1);
  if (!turn) {
    return null;
  }
  return sessionTurnToChatResponse(session, turn, provider, contextPack.contextPackVersion ?? "");
}

export async function fetchNewAgentTurnWithRetry(
  sessionId: string,
  baselineTurnCount: number,
  attempts = 40,
  delayMs = 500
): Promise<AiAgentChatResponse | null> {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const response = await fetchNewAgentTurnResponse(sessionId, baselineTurnCount);
    if (response) {
      return response;
    }
    if (attempt + 1 < attempts) {
      await new Promise((r) => setTimeout(r, delayMs));
    }
  }
  return null;
}

export function waitForAgentTurnCompletion(
  sessionId: string,
  onProgress?: (progress: AiAgentRunProgress) => void,
  options?: WaitForAgentTurnOptions
): Promise<AiAgentChatResponse> {
  const baselineTurnCount = options?.baselineTurnCount ?? 0;
  return new Promise((resolve, reject) => {
    let settled = false;
    let finishInFlight = false;
    let pollTimer: ReturnType<typeof setInterval> | undefined;
    let sessionPollTimer: ReturnType<typeof setInterval> | undefined;
    let unsubscribe: (() => void) | undefined;
    const waitStartedAt = Date.now();
    let sawRunning = false;
    let idleSince: number | null = null;

    const cleanup = () => {
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = undefined;
      }
      if (sessionPollTimer) {
        clearInterval(sessionPollTimer);
        sessionPollTimer = undefined;
      }
      unsubscribe?.();
      unsubscribe = undefined;
    };

    const loadCompletedTurn = async (): Promise<AiAgentChatResponse | null> =>
      fetchNewAgentTurnResponse(sessionId, baselineTurnCount);

    const settle = (response: AiAgentChatResponse) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      resolve(response);
    };

    const rejectTurnMissing = () => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      reject(new Error("Agent run completed without a turn result"));
    };

    const finish = async () => {
      if (settled || finishInFlight) {
        return;
      }
      finishInFlight = true;
      try {
        const response = await fetchNewAgentTurnWithRetry(
          sessionId,
          baselineTurnCount,
          TURN_FETCH_ATTEMPTS,
          TURN_FETCH_DELAY_MS
        );
        if (response) {
          settle(response);
          return;
        }
        await new Promise((r) => setTimeout(r, TURN_FETCH_LATE_PAUSE_MS));
        if (settled) {
          return;
        }
        const late = await fetchNewAgentTurnWithRetry(
          sessionId,
          baselineTurnCount,
          TURN_FETCH_LATE_ATTEMPTS,
          TURN_FETCH_DELAY_MS
        );
        if (late) {
          settle(late);
          return;
        }
        rejectTurnMissing();
      } catch (error) {
        if (!settled) {
          settled = true;
          cleanup();
          reject(error instanceof Error ? error : new Error(String(error)));
        }
      } finally {
        finishInFlight = false;
      }
    };

    const considerFinish = (running: boolean) => {
      if (settled) {
        return;
      }
      if (running) {
        sawRunning = true;
        idleSince = null;
        return;
      }
      const now = Date.now();
      if (!sawRunning && now - waitStartedAt < POST_SEND_GRACE_MS) {
        return;
      }
      if (idleSince == null) {
        idleSince = now;
        return;
      }
      if (now - idleSince < RUN_IDLE_CONFIRM_MS) {
        return;
      }
      void finish();
    };

    sessionPollTimer = setInterval(() => {
      void loadCompletedTurn().then((response) => {
        if (response) {
          settle(response);
        }
      });
    }, 1000);

    unsubscribe = subscribeAgentRunProgress(
      sessionId,
      (progress) => {
        onProgress?.(progress);
        considerFinish(progress.running === true);
      },
      () => {
        // SSE errors — polling fallback continues
      }
    );

    pollTimer = setInterval(() => {
      void fetchAgentRunProgress(sessionId)
        .then((progress) => {
          onProgress?.(progress);
          considerFinish(progress.running === true);
        })
        .catch(() => {
          // ignore transient poll errors while run is active
        });
    }, 2000);
  });
}

export function fetchAgentRunProgress(sessionId: string): Promise<AiAgentRunProgress> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/progress`, {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Fetch progress failed: ${response.status}`);
    }
    return response.json();
  });
}

export function subscribeAgentRunProgress(
  sessionId: string,
  onProgress: (progress: AiAgentRunProgress) => void,
  onError?: (error: Event) => void
): () => void {
  const base = `/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/progress/stream`;
  const token = getStoredSession()?.token;
  const url = token ? `${base}?${new URLSearchParams({ token }).toString()}` : base;
  const source = new EventSource(url);

  source.addEventListener("progress", (event) => {
    try {
      const progress = JSON.parse((event as MessageEvent).data) as AiAgentRunProgress;
      onProgress(progress);
    } catch {
      // ignore malformed SSE payloads
    }
  });

  source.onerror = (error) => {
    onError?.(error);
  };

  return () => {
    source.close();
  };
}

export function cancelAgentRun(
  sessionId: string
): Promise<{ status: string; sessionId: string; cancelRequested: boolean }> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/cancel`, {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Cancel run failed: ${response.status}`);
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
