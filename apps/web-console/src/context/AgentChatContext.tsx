import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  agentApiUnavailableMessage,
  cancelAgentRun,
  createAgentSession,
  deleteAgentSession,
  fetchAgentRunProgress,
  fetchAgentSession,
  fetchAiAgentTools,
  fetchAiProviderStatus,
  sendAgentMessage,
  subscribeAgentRunProgress,
  type AiAgentChatResponse,
  type AiAgentSession,
  type AiAgentSessionSummary,
  type AiAgentStep,
  type AiAgentTool,
  type AiAgentTurn,
  type AiProviderStatus,
  type AgentInteractionMode,
  type AgentMessageAttachmentMeta,
  type AgentPlanState,
} from "../api/ai";
import {
  clearAgentChatIndex,
  loadAgentChatIndex,
  loadAiStudioPrefs,
  purgeLegacyAgentPending,
  removeChatEntry,
  saveAgentChatIndex,
  saveAiStudioPrefs,
  upsertChatEntry,
  type AgentChatIndex,
} from "../utils/agentChatStorage";
import i18n from "../i18n";
import {
  buildAttachmentApiPayload,
  revokeAttachmentPreviews,
  type AgentChatAttachment,
} from "../utils/agentChatAttachments";

export interface ChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  attachments?: AgentMessageAttachmentMeta[];
  steps?: AiAgentStep[];
  result?: Record<string, unknown>;
  status?: string;
}

interface AgentChatContextValue {
  provider: AiProviderStatus | undefined;
  providerLoading: boolean;
  providerReachable: boolean;
  agentApiReady: boolean;
  agentApiChecking: boolean;
  agentApiBanner: string | null;
  agentTools: AiAgentTool[] | undefined;
  chatIndex: AgentChatIndex;
  activeSessionId: string | null;
  messages: ChatMessage[];
  input: string;
  setInput: (value: string) => void;
  pendingAttachments: AgentChatAttachment[];
  setPendingAttachments: (value: AgentChatAttachment[]) => void;
  attachmentRejectHint: string | null;
  clearAttachmentRejectHint: () => void;
  rejectAttachment: (reason: "unsupported" | "vision-not-supported") => void;
  loadingSession: boolean;
  isPending: boolean;
  liveSteps: AiAgentStep[];
  livePlanPhase?: AgentPlanState["planPhase"];
  pendingUserMessage: string | null;
  defaultRootPath: string;
  startNewChat: () => Promise<void>;
  switchSession: (sessionId: string) => Promise<void>;
  deleteChat: (sessionId: string) => Promise<void>;
  sendMessage: (text: string) => Promise<void>;
  cancelRun: () => Promise<void>;
  clearLocalChatIndex: () => void;
  interactionMode: AgentInteractionMode;
  setInteractionMode: (mode: AgentInteractionMode) => void;
}

const AgentChatContext = createContext<AgentChatContextValue | null>(null);

function newId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function turnsToMessages(turns: AiAgentTurn[]): ChatMessage[] {
  const messages: ChatMessage[] = [];
  for (const turn of turns) {
    messages.push({
      id: turn.turnId + "-u",
      role: "user",
      text: turn.userMessage,
      attachments: turn.attachments,
    });
    messages.push({
      id: turn.turnId + "-a",
      role: "agent",
      text: turn.assistantSummary,
      steps: turn.steps,
      result: turn.result,
      status: turn.status,
    });
  }
  return messages;
}

function responseToAgentMessage(data: AiAgentChatResponse): ChatMessage {
  return {
    id: `${data.turnId ?? newId()}-a`,
    role: "agent",
    text: data.summary,
    steps: data.steps,
    result: data.result,
    status: data.status,
  };
}

function resolveRootPath(): string {
  const rootPath = loadAiStudioPrefs().defaultRootPath.trim();
  return rootPath || "root";
}

export function AgentChatProvider({
  children,
  enabled,
}: {
  children: ReactNode;
  enabled: boolean;
}) {
  const queryClient = useQueryClient();
  const initialPrefs = useMemo(() => loadAiStudioPrefs(), []);
  const [chatIndex, setChatIndex] = useState<AgentChatIndex>(() => loadAgentChatIndex());
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [pendingAttachments, setPendingAttachments] = useState<AgentChatAttachment[]>([]);
  const [attachmentRejectHint, setAttachmentRejectHint] = useState<string | null>(null);
  const [loadingSession, setLoadingSession] = useState(enabled);
  const [isPending, setIsPending] = useState(false);
  const [liveSteps, setLiveSteps] = useState<AiAgentStep[]>([]);
  const [livePlanPhase, setLivePlanPhase] = useState<AgentPlanState["planPhase"]>();
  const [interactionMode, setInteractionModeState] = useState<AgentInteractionMode>(
    () => loadAiStudioPrefs().interactionMode
  );
  const turnCountRef = useRef(0);
  const pendingMessageRef = useRef<string | null>(null);
  const activeSessionIdRef = useRef<string | null>(null);

  useEffect(() => {
    activeSessionIdRef.current = activeSessionId;
  }, [activeSessionId]);

  useEffect(() => {
    purgeLegacyAgentPending();
  }, []);

  const providerQuery = useQuery({
    queryKey: ["ai-provider"],
    queryFn: fetchAiProviderStatus,
    enabled,
    staleTime: 60_000,
  });

  const agentToolsQuery = useQuery({
    queryKey: ["ai-agent-tools"],
    queryFn: fetchAiAgentTools,
    enabled,
    staleTime: 60_000,
    retry: false,
  });

  const providerReachable = providerQuery.isSuccess;
  const agentApiReady = agentToolsQuery.isSuccess;
  const agentApiChecking = agentToolsQuery.isLoading || providerQuery.isLoading;
  const agentApiBanner =
    enabled && !agentApiChecking && !agentApiReady
      ? agentApiUnavailableMessage(providerReachable)
      : null;

  const persistIndex = useCallback((index: AgentChatIndex) => {
    setChatIndex(index);
    saveAgentChatIndex(index);
  }, []);

  const setInteractionMode = useCallback((mode: AgentInteractionMode) => {
    setInteractionModeState(mode);
    saveAiStudioPrefs({ ...loadAiStudioPrefs(), interactionMode: mode });
  }, []);

  const registerSession = useCallback(
    (session: AiAgentSessionSummary, index: AgentChatIndex, activeId: string) => {
      const entry = {
        id: session.sessionId,
        title: session.title,
        updatedAt: session.updatedAt,
      };
      const next = upsertChatEntry(index, entry);
      persistIndex({ ...next, activeSessionId: activeId });
      setActiveSessionId(activeId);
      activeSessionIdRef.current = activeId;
    },
    [persistIndex]
  );

  const applySession = useCallback(
    (session: AiAgentSession, index: AgentChatIndex) => {
      registerSession(session, index, session.sessionId);
      turnCountRef.current = session.turns.length;
      const turnMessages = turnsToMessages(session.turns);
      setLiveSteps([]);
      setMessages(turnMessages);
    },
    [registerSession]
  );

  const handleAgentResponse = useCallback(
    (data: AiAgentChatResponse) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      setLiveSteps([]);
      setLivePlanPhase(undefined);
      setMessages((prev) => [...prev, responseToAgentMessage(data)]);
      turnCountRef.current += 1;

      const entry = {
        id: data.sessionId,
        title: data.title,
        updatedAt: new Date().toISOString(),
      };
      setChatIndex((prev) => {
        const next = upsertChatEntry({ ...prev, activeSessionId: data.sessionId }, entry);
        persistIndex(next);
        return next;
      });
      setActiveSessionId(data.sessionId);
      activeSessionIdRef.current = data.sessionId;
    },
    [persistIndex, queryClient]
  );

  const handleAgentError = useCallback((error: unknown, prefix: string) => {
    setLiveSteps([]);
    setLivePlanPhase(undefined);
    setMessages((prev) => [
      ...prev,
      {
        id: newId(),
        role: "agent",
        text: `${prefix}: ${error instanceof Error ? error.message : String(error)}`,
      },
    ]);
  }, []);

  useEffect(() => {
    if (!enabled) {
      setLoadingSession(false);
      return;
    }
    let cancelled = false;
    (async () => {
      setLoadingSession(true);
      try {
        const index = loadAgentChatIndex();
        setChatIndex(index);

        if (initialPrefs.restoreLastChat && index.activeSessionId) {
          try {
            const session = await fetchAgentSession(index.activeSessionId);
            if (!cancelled) {
              applySession(session, index);
            }
          } catch {
            const cleaned = removeChatEntry(index, index.activeSessionId);
            persistIndex(cleaned);
            if (!cancelled) {
              setActiveSessionId(null);
              activeSessionIdRef.current = null;
              setLiveSteps([]);
              setMessages([]);
            }
          }
        } else {
          setActiveSessionId(null);
          activeSessionIdRef.current = null;
        }
      } finally {
        if (!cancelled) {
          setLoadingSession(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [applySession, enabled, initialPrefs.restoreLastChat, persistIndex]);

  useEffect(() => {
    if (!isPending || !activeSessionId) {
      return;
    }
    let cancelled = false;
    let pollTimer: number | undefined;
    let unsubscribe: (() => void) | undefined;

    const applyProgress = (progress: {
      running: boolean;
      steps?: AiAgentStep[];
      planState?: AgentPlanState;
    }) => {
      if (cancelled) {
        return;
      }
      // Keep listening while isPending — an early { running: false } arrives before POST /messages
      // registers the run and must not tear down the subscription.
      if (progress.steps) {
        setLiveSteps(progress.steps);
      }
      if (progress.planState?.planPhase) {
        setLivePlanPhase(progress.planState.planPhase);
      }
    };

    const poll = async () => {
      try {
        const progress = await fetchAgentRunProgress(activeSessionId);
        applyProgress(progress);
      } catch {
        // ignore transient poll errors while run is in flight
      }
    };

    void poll();
    pollTimer = window.setInterval(() => void poll(), 1000);

    unsubscribe = subscribeAgentRunProgress(activeSessionId, applyProgress);

    return () => {
      cancelled = true;
      unsubscribe?.();
      if (pollTimer !== undefined) {
        window.clearInterval(pollTimer);
      }
    };
  }, [activeSessionId, isPending]);

  const startNewChat = useCallback(async () => {
    pendingMessageRef.current = null;
    setIsPending(false);
    setLiveSteps([]);
    const session = await createAgentSession(resolveRootPath(), interactionMode);
    registerSession(session, loadAgentChatIndex(), session.sessionId);
    turnCountRef.current = 0;
    setMessages([]);
    setInput("");
  }, [registerSession, interactionMode]);

  const switchSession = useCallback(
    async (sessionId: string) => {
      if (sessionId === activeSessionIdRef.current || isPending) {
        return;
      }
      try {
        const session = await fetchAgentSession(sessionId);
        applySession(session, chatIndex);
      } catch {
        const cleaned = removeChatEntry(chatIndex, sessionId);
        persistIndex(cleaned);
      }
    },
    [applySession, chatIndex, isPending, persistIndex]
  );

  const deleteChat = useCallback(
    async (sessionId: string) => {
      try {
        await deleteAgentSession(sessionId);
      } catch {
        // session may already be gone on server
      }
      const cleaned = removeChatEntry(chatIndex, sessionId);
      persistIndex(cleaned);
      if (activeSessionIdRef.current === sessionId) {
        if (cleaned.chats.length > 0 && cleaned.activeSessionId) {
          await switchSession(cleaned.activeSessionId);
        } else {
          setActiveSessionId(null);
          activeSessionIdRef.current = null;
          turnCountRef.current = 0;
          setLiveSteps([]);
          setMessages([]);
        }
      }
    },
    [chatIndex, persistIndex, switchSession]
  );

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      const attachmentsToSend = pendingAttachments;
      if ((!trimmed && attachmentsToSend.length === 0) || isPending) {
        return;
      }

      pendingMessageRef.current = trimmed;
      setInput("");
      setLiveSteps([]);
      setLivePlanPhase(
        interactionMode === "plan" || interactionMode === "ask" ? "planning" : undefined
      );
      const attachmentMeta = attachmentsToSend.map((item) => ({
        name: item.name,
        mimeType: item.mimeType,
        kind: item.kind,
        byteSize: item.file.size,
      }));
      setMessages((prev) => [
        ...prev,
        { id: newId(), role: "user", text: trimmed, attachments: attachmentMeta },
      ]);
      setPendingAttachments([]);
      revokeAttachmentPreviews(attachmentsToSend);

      try {
        let sessionId = activeSessionIdRef.current;
        const rootPath = resolveRootPath();
        const apiAttachments =
          attachmentsToSend.length > 0 ? await buildAttachmentApiPayload(attachmentsToSend) : undefined;

        if (!sessionId) {
          const session = await createAgentSession(rootPath, interactionMode);
          sessionId = session.sessionId;
          registerSession(session, loadAgentChatIndex(), sessionId);
          turnCountRef.current = 0;
        }

        setIsPending(true);
        const data = await sendAgentMessage(
          sessionId,
          trimmed,
          rootPath,
          interactionMode,
          apiAttachments
        );
        pendingMessageRef.current = null;
        handleAgentResponse(data);
      } catch (error) {
        pendingMessageRef.current = null;
        handleAgentError(error, i18n.t("agent.sendFailed", { ns: "ai" }));
      } finally {
        setIsPending(false);
        setLivePlanPhase(undefined);
      }
    },
    [
      handleAgentError,
      handleAgentResponse,
      interactionMode,
      isPending,
      pendingAttachments,
      registerSession,
    ]
  );

  const clearAttachmentRejectHint = useCallback(() => {
    setAttachmentRejectHint(null);
  }, []);

  const rejectAttachment = useCallback((reason: "unsupported" | "vision-not-supported") => {
    setAttachmentRejectHint(
      reason === "vision-not-supported"
        ? i18n.t("agent.attachments.visionNotSupported", { ns: "ai" })
        : i18n.t("agent.attachments.unsupported", { ns: "ai" })
    );
  }, []);

  const cancelRun = useCallback(async () => {
    const sessionId = activeSessionIdRef.current;
    if (!sessionId || !isPending) {
      return;
    }
    try {
      await cancelAgentRun(sessionId);
    } catch (error) {
      handleAgentError(error, i18n.t("agent.cancelFailed", { ns: "ai" }));
    }
  }, [handleAgentError, isPending]);

  const clearLocalChatIndex = useCallback(() => {
    pendingMessageRef.current = null;
    setIsPending(false);
    setLiveSteps([]);
    setLivePlanPhase(undefined);
    const cleared = clearAgentChatIndex();
    setChatIndex(cleared);
    setActiveSessionId(null);
    activeSessionIdRef.current = null;
    turnCountRef.current = 0;
    setMessages([]);
  }, []);

  const pendingUserMessage = isPending ? pendingMessageRef.current : null;

  const value = useMemo<AgentChatContextValue>(
    () => ({
      provider: providerQuery.data,
      providerLoading: providerQuery.isLoading,
      providerReachable,
      agentApiReady,
      agentApiChecking,
      agentApiBanner,
      agentTools: agentToolsQuery.data?.tools,
      chatIndex,
      activeSessionId,
      messages,
      input,
      setInput,
      pendingAttachments,
      setPendingAttachments,
      attachmentRejectHint,
      clearAttachmentRejectHint,
      rejectAttachment,
      loadingSession,
      isPending,
      liveSteps,
      livePlanPhase,
      pendingUserMessage,
      defaultRootPath: resolveRootPath(),
      startNewChat,
      switchSession,
      deleteChat,
      sendMessage,
      cancelRun,
      clearLocalChatIndex,
      interactionMode,
      setInteractionMode,
    }),
    [
      providerQuery.data,
      providerQuery.isLoading,
      providerReachable,
      agentApiReady,
      agentApiChecking,
      agentApiBanner,
      agentToolsQuery.data,
      chatIndex,
      activeSessionId,
      messages,
      input,
      pendingAttachments,
      attachmentRejectHint,
      clearAttachmentRejectHint,
      rejectAttachment,
      loadingSession,
      isPending,
      liveSteps,
      livePlanPhase,
      pendingUserMessage,
      startNewChat,
      switchSession,
      deleteChat,
      sendMessage,
      cancelRun,
      clearLocalChatIndex,
      interactionMode,
      setInteractionMode,
    ]
  );

  return <AgentChatContext.Provider value={value}>{children}</AgentChatContext.Provider>;
}

export function useAgentChat(): AgentChatContextValue {
  const ctx = useContext(AgentChatContext);
  if (!ctx) {
    throw new Error("useAgentChat must be used within AgentChatProvider");
  }
  return ctx;
}

export function useAgentChatOptional(): AgentChatContextValue | null {
  return useContext(AgentChatContext);
}
