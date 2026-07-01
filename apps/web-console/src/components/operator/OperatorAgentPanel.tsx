import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import {
  cancelOperatorAgentRun,
  createOperatorAgentSession,
  fetchOperatorAgentProgress,
  sendOperatorAgentMessage,
  subscribeOperatorAgentProgress,
  fetchOperatorAgentStatus,
  type AiAgentStep,
} from "../../api/operatorAgent";
import { AgentRunDetails } from "../AiAgentChat";
import OperatorAgentArtifactsView from "./OperatorAgentArtifacts";
import { AgentStarterSuggestions } from "../agent/AgentChatArtifacts";

interface ChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  steps?: AiAgentStep[];
  status?: string;
  result?: Record<string, unknown>;
}

function newId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

interface OperatorAgentPanelProps {
  appId: string;
  open: boolean;
  onClose: () => void;
  onOpenDashboard?: (path: string) => void;
  onOpenReport?: (path: string) => void;
}

export default function OperatorAgentPanel({
  appId,
  open,
  onClose,
  onOpenDashboard,
  onOpenReport,
}: OperatorAgentPanelProps) {
  const { t } = useTranslation("operator");
  const statusQuery = useQuery({
    queryKey: ["operator-agent-status", appId],
    queryFn: () => fetchOperatorAgentStatus(appId),
    enabled: open,
    staleTime: 60_000,
  });

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);
  const [liveSteps, setLiveSteps] = useState<AiAgentStep[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const sessionIdRef = useRef<string | null>(null);

  useEffect(() => {
    sessionIdRef.current = sessionId;
  }, [sessionId]);

  useEffect(() => {
    if (open) {
      scrollRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [open, messages.length, liveSteps.length, isPending]);

  useEffect(() => {
    if (!isPending || !sessionId) {
      return;
    }
    let cancelled = false;
    let pollTimer: number | undefined;
    let unsubscribe: (() => void) | undefined;

    const applyProgress = (progress: { steps?: AiAgentStep[] }) => {
      if (cancelled || !progress.steps) {
        return;
      }
      setLiveSteps(progress.steps);
    };

    const poll = async () => {
      try {
        const progress = await fetchOperatorAgentProgress(appId, sessionId);
        applyProgress(progress);
      } catch {
        // ignore transient errors
      }
    };

    void poll();
    pollTimer = window.setInterval(() => void poll(), 1000);
    unsubscribe = subscribeOperatorAgentProgress(appId, sessionId, applyProgress);

    return () => {
      cancelled = true;
      unsubscribe?.();
      if (pollTimer !== undefined) {
        window.clearInterval(pollTimer);
      }
    };
  }, [appId, isPending, sessionId]);

  const providerReady = statusQuery.data?.provider?.available ?? false;
  const toolStepCount = liveSteps.filter((s) => s.type === "tool").length;

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || isPending || !providerReady) {
        return;
      }
      setInput("");
      setLiveSteps([]);
      setMessages((prev) => [...prev, { id: newId(), role: "user", text: trimmed }]);
      setIsPending(true);
      try {
        let activeSessionId = sessionIdRef.current;
        if (!activeSessionId) {
          const session = await createOperatorAgentSession(appId);
          activeSessionId = session.sessionId;
          setSessionId(activeSessionId);
          sessionIdRef.current = activeSessionId;
        }
        const data = await sendOperatorAgentMessage(appId, activeSessionId, trimmed);
        setLiveSteps([]);
        setMessages((prev) => [
          ...prev,
          {
            id: `${data.turnId ?? newId()}-a`,
            role: "agent",
            text: data.summary,
            steps: data.steps,
            status: data.status,
            result: data.result,
          },
        ]);
      } catch (error) {
        setMessages((prev) => [
          ...prev,
          {
            id: newId(),
            role: "agent",
            text: `${t("agent.sendFailed")}: ${error instanceof Error ? error.message : String(error)}`,
          },
        ]);
      } finally {
        setIsPending(false);
      }
    },
    [appId, isPending, providerReady, t]
  );

  const appendToInput = useCallback((text: string) => {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    setInput((prev) => {
      const base = prev.trimEnd();
      return base ? `${base}\n${trimmed}` : trimmed;
    });
    requestAnimationFrame(() => inputRef.current?.focus());
  }, []);

  const handleCancel = useCallback(async () => {
    const activeSessionId = sessionIdRef.current;
    if (!activeSessionId || !isPending) {
      return;
    }
    try {
      await cancelOperatorAgentRun(appId, activeSessionId);
    } catch {
      // ignore
    }
  }, [appId, isPending]);

  if (!open) {
    return null;
  }

  return (
    <div className="operator-agent-drawer" role="dialog" aria-label={t("agent.title")}>
      <div className="operator-agent-drawer-head">
        <div>
          <strong>{t("agent.title")}</strong>
          <p className="op-muted operator-agent-drawer-sub">
            {statusQuery.data?.title ?? appId} · {t("agent.readOnlyHint")}
            {typeof statusQuery.data?.memoryCount === "number" && statusQuery.data.memoryCount > 0
              ? ` · ${t("agent.memoryCount", { count: statusQuery.data.memoryCount })}`
              : ""}
            {typeof statusQuery.data?.documentCount === "number" && statusQuery.data.documentCount > 0
              ? ` · ${t("agent.documentCount", { count: statusQuery.data.documentCount })}`
              : ""}
          </p>
        </div>
        <button type="button" className="btn small" onClick={onClose} aria-label={t("agent.close")}>
          ×
        </button>
      </div>

      {!providerReady && !statusQuery.isLoading && (
        <div className="op-alert op-alert-error">{t("agent.llmUnavailable")}</div>
      )}

      <div className="operator-agent-drawer-log">
        {!messages.some((m) => m.role === "user") && !isPending && (
          <AgentStarterSuggestions
            i18nNs="operator"
            suggestionKeys={[
              "agent.suggest.alarms",
              "agent.suggest.trend",
              "agent.suggest.tasks",
              "agent.suggest.report",
              "agent.suggest.remember",
            ]}
            onPick={(text) => void sendMessage(text)}
          />
        )}
        {messages.map((message) => (
          <div
            key={message.id}
            className={message.role === "user" ? "ai-agent-bubble user" : "ai-agent-bubble agent"}
          >
            <div className="ai-agent-bubble-text">{message.text}</div>
            {message.role === "agent" && (
              <OperatorAgentArtifactsView
                result={message.result}
                onSuggestMessage={(text) => void sendMessage(text)}
                onAppendToInput={appendToInput}
                onOpenDashboard={(path) => {
                  onOpenDashboard?.(path);
                  onClose();
                }}
                onOpenReport={(path) => {
                  onOpenReport?.(path);
                  onClose();
                }}
              />
            )}
            {message.steps && message.steps.length > 0 && (
              <AgentRunDetails steps={message.steps} status={message.status} />
            )}
          </div>
        ))}
        {isPending && (
          <div className="ai-agent-bubble agent ai-agent-bubble-pending">
            <div className="ai-agent-bubble-text">
              {toolStepCount > 0 ? t("agent.executing", { count: toolStepCount }) : t("agent.thinking")}
            </div>
            <AgentRunDetails steps={liveSteps} status="RUNNING" open placeholder={toolStepCount === 0} />
          </div>
        )}
        <div ref={scrollRef} />
      </div>

      <form
        className="operator-agent-drawer-compose"
        onSubmit={(e) => {
          e.preventDefault();
          void sendMessage(input);
        }}
      >
        <textarea
          ref={inputRef}
          rows={2}
          value={input}
          placeholder={t("agent.placeholder")}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              e.currentTarget.form?.requestSubmit();
            }
          }}
          disabled={isPending || !providerReady}
        />
        <div className="operator-agent-drawer-actions">
          {isPending && (
            <button type="button" className="btn danger" onClick={() => void handleCancel()}>
              {t("agent.cancel")}
            </button>
          )}
          <button type="submit" className="btn primary" disabled={isPending || !providerReady || !input.trim()}>
            {t("agent.send")}
          </button>
        </div>
      </form>
    </div>
  );
}
