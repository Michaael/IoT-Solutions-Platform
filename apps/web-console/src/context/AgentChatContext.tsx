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
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  agentApiUnavailableMessage,
  createAgentSession,
  deleteAgentSession,
  fetchAgentSession,
  fetchAiAgentTools,
  fetchAiProviderStatus,
  sendAgentMessage,
  type AiAgentChatResponse,
  type AiAgentSessionSummary,
  type AiAgentStep,
  type AiAgentTool,
  type AiAgentTurn,
  type AiProviderStatus,
} from "../api/ai";
import {
  clearAgentChatIndex,
  loadAgentChatIndex,
  loadAiStudioPrefs,
  purgeLegacyAgentPending,
  removeChatEntry,
  saveAgentChatIndex,
  upsertChatEntry,
  type AgentChatIndex,
} from "../utils/agentChatStorage";

export const WELCOME_TEXT =
  "Опишите задачу обычным языком — например, создать SNMP-устройство, настроить метрики и дашборд. Контекст чата сохраняется между вкладками и перезагрузкой страницы.";

export interface ChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
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
  loadingSession: boolean;
  isPending: boolean;
  pendingUserMessage: string | null;
  defaultRootPath: string;
  startNewChat: () => Promise<void>;
  switchSession: (sessionId: string) => Promise<void>;
  deleteChat: (sessionId: string) => Promise<void>;
  sendMessage: (text: string) => Promise<void>;
  clearLocalChatIndex: () => void;
}

const AgentChatContext = createContext<AgentChatContextValue | null>(null);

function newId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function turnsToMessages(turns: AiAgentTurn[]): ChatMessage[] {
  const messages: ChatMessage[] = [];
  for (const turn of turns) {
    messages.push({ id: turn.turnId + "-u", role: "user", text: turn.userMessage });
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
    id: data.turnId + "-a",
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
  const [messages, setMessages] = useState<ChatMessage[]>([
    { id: "welcome", role: "agent", text: WELCOME_TEXT },
  ]);
  const [input, setInput] = useState("");
  const [loadingSession, setLoadingSession] = useState(enabled);
  const [isSending, setIsSending] = useState(false);
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
    (session: AiAgentSessionSummary, index: AgentChatIndex, turns: AiAgentTurn[] = []) => {
      registerSession(session, index, session.sessionId);
      turnCountRef.current = turns.length;
      const turnMessages = turnsToMessages(turns);
      setMessages(
        turnMessages.length > 0
          ? turnMessages
          : [{ id: "welcome", role: "agent", text: WELCOME_TEXT }]
      );
    },
    [registerSession]
  );

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
              applySession(session, index, session.turns);
            }
          } catch {
            const cleaned = removeChatEntry(index, index.activeSessionId);
            persistIndex(cleaned);
            if (!cancelled) {
              setActiveSessionId(null);
              activeSessionIdRef.current = null;
              setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
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

  const sendMutation = useMutation({
    mutationFn: ({ sessionId, text, rootPath }: { sessionId: string; text: string; rootPath: string }) =>
      sendAgentMessage(sessionId, text, rootPath),
    onSuccess: (data) => {
      pendingMessageRef.current = null;
      setIsSending(false);
      queryClient.invalidateQueries({ queryKey: ["objects"] });
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
    onError: (error) => {
      pendingMessageRef.current = null;
      setIsSending(false);
      setMessages((prev) => [
        ...prev,
        {
          id: newId(),
          role: "agent",
          text: `Не удалось выполнить задачу: ${error instanceof Error ? error.message : String(error)}`,
        },
      ]);
    },
  });

  const startNewChat = useCallback(async () => {
    pendingMessageRef.current = null;
    setIsSending(false);
    const session = await createAgentSession(resolveRootPath());
    registerSession(session, loadAgentChatIndex(), session.sessionId);
    turnCountRef.current = 0;
    setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
    setInput("");
  }, [registerSession]);

  const switchSession = useCallback(
    async (sessionId: string) => {
      if (sessionId === activeSessionIdRef.current || isSending || sendMutation.isPending) {
        return;
      }
      try {
        const session = await fetchAgentSession(sessionId);
        applySession(session, chatIndex, session.turns);
      } catch {
        const cleaned = removeChatEntry(chatIndex, sessionId);
        persistIndex(cleaned);
      }
    },
    [applySession, chatIndex, isSending, persistIndex, sendMutation.isPending]
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
          setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
        }
      }
    },
    [chatIndex, persistIndex, switchSession]
  );

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || isSending || sendMutation.isPending) {
        return;
      }

      setIsSending(true);
      pendingMessageRef.current = trimmed;
      setInput("");
      setMessages((prev) => [...prev, { id: newId(), role: "user", text: trimmed }]);

      try {
        let sessionId = activeSessionIdRef.current;
        const rootPath = resolveRootPath();

        if (!sessionId) {
          const session = await createAgentSession(rootPath);
          sessionId = session.sessionId;
          registerSession(session, loadAgentChatIndex(), sessionId);
          turnCountRef.current = 0;
        }

        await sendMutation.mutateAsync({ sessionId, text: trimmed, rootPath });
      } catch (error) {
        pendingMessageRef.current = null;
        setMessages((prev) => [
          ...prev,
          {
            id: newId(),
            role: "agent",
            text: `Не удалось отправить сообщение: ${error instanceof Error ? error.message : String(error)}`,
          },
        ]);
      } finally {
        setIsSending(false);
      }
    },
    [isSending, registerSession, sendMutation]
  );

  const clearLocalChatIndex = useCallback(() => {
    pendingMessageRef.current = null;
    setIsSending(false);
    const cleared = clearAgentChatIndex();
    setChatIndex(cleared);
    setActiveSessionId(null);
    activeSessionIdRef.current = null;
    turnCountRef.current = 0;
    setMessages([{ id: "welcome", role: "agent", text: WELCOME_TEXT }]);
  }, []);

  const isPending = sendMutation.isPending || isSending;
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
      loadingSession,
      isPending,
      pendingUserMessage,
      defaultRootPath: resolveRootPath(),
      startNewChat,
      switchSession,
      deleteChat,
      sendMessage,
      clearLocalChatIndex,
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
      loadingSession,
      isPending,
      pendingUserMessage,
      startNewChat,
      switchSession,
      deleteChat,
      sendMessage,
      clearLocalChatIndex,
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
