import { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useAgentChat } from "../context/AgentChatContext";
import type { AiAgentStep } from "../api/ai";

const CHAT_INPUT_MAX_HEIGHT_PX = 320;

function resizeChatInput(textarea: HTMLTextAreaElement | null) {
  if (!textarea) {
    return;
  }
  const maxHeight = Math.min(window.innerHeight * 0.4, CHAT_INPUT_MAX_HEIGHT_PX);
  textarea.style.height = "auto";
  const next = Math.min(textarea.scrollHeight, maxHeight);
  textarea.style.height = `${next}px`;
  textarea.style.overflowY = textarea.scrollHeight > maxHeight ? "auto" : "hidden";
}

function formatChatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString(undefined, {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}

function dashboardLink(path: string | undefined): string | null {
  if (!path) {
    return null;
  }
  return `/?path=${encodeURIComponent(path)}`;
}

export default function AiAgentChat() {
  const { t } = useTranslation("ai");
  const {
    provider,
    agentApiReady,
    agentApiBanner,
    chatIndex,
    activeSessionId,
    messages,
    input,
    setInput,
    loadingSession,
    isPending,
    liveSteps,
    startNewChat,
    switchSession,
    deleteChat,
    sendMessage,
    cancelRun,
  } = useAgentChat();
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const syncInputHeight = useCallback(() => {
    resizeChatInput(inputRef.current);
  }, []);

  useEffect(() => {
    syncInputHeight();
  }, [input, syncInputHeight]);

  useEffect(() => {
    if (isPending) {
      scrollRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [isPending, liveSteps.length, messages.length]);

  if (loadingSession) {
    return <p className="op-muted">{t("agent.loadingChat")}</p>;
  }

  const providerReady = provider?.available ?? false;
  const sending = isPending;
  const chatEnabled = providerReady && agentApiReady;
  const toolStepCount = liveSteps.filter((s) => s.type === "tool").length;

  return (
    <div className="ai-agent-chat">
      {agentApiBanner && (
        <div className="op-alert op-alert-error" role="alert">
          {agentApiBanner}
        </div>
      )}

      {!providerReady && !agentApiBanner && (
        <div className="op-alert op-alert-error">
          {t("agent.llmNotConfigured")}
        </div>
      )}

      <div className="ai-agent-toolbar" role="toolbar" aria-label={t("agent.toolbarAria")}>
        <button
          type="button"
          className="btn primary ai-agent-new-chat"
          disabled={sending || !chatEnabled}
          onClick={() => void startNewChat()}
          title={t("agent.newChatTitle")}
        >
          <span className="ai-agent-new-chat-label">{t("agent.newChat")}</span>
        </button>
        <div className="ai-agent-chat-strip" role="tablist" aria-label={t("agent.toolbarAria")}>
          {chatIndex.chats.length === 0 && (
            <span className="ai-agent-chat-strip-empty op-muted">{t("agent.noChats")}</span>
          )}
          {chatIndex.chats.map((chat) => {
            const active = chat.id === activeSessionId;
            return (
              <div
                key={chat.id}
                className={active ? "ai-agent-chat-pill active" : "ai-agent-chat-pill"}
                role="presentation"
              >
                <button
                  type="button"
                  role="tab"
                  aria-selected={active}
                  className="ai-agent-chat-pill-btn"
                  disabled={sending}
                  onClick={() => void switchSession(chat.id)}
                  title={chat.updatedAt ? formatChatDate(chat.updatedAt) : chat.title}
                >
                  <span className="ai-agent-chat-title">{chat.title}</span>
                  {isPending && active && (
                    <span className="ai-agent-chat-pending-dot" title={t("agent.pendingTitle")} />
                  )}
                </button>
                <button
                  type="button"
                  className="ai-agent-chat-delete"
                  aria-label={t("agent.deleteChatAria", { title: chat.title })}
                  disabled={sending}
                  onClick={() => void deleteChat(chat.id)}
                >
                  ×
                </button>
              </div>
            );
          })}
        </div>
        {isPending && (
          <button
            type="button"
            className="btn danger ai-agent-cancel-run"
            disabled={!chatEnabled}
            onClick={() => void cancelRun()}
            title={t("agent.cancelTitle")}
          >
            {t("agent.cancel")}
          </button>
        )}
        {isPending && (
          <span className="ai-agent-toolbar-busy op-muted">
            {toolStepCount > 0
              ? t("agent.stepProgress", { count: toolStepCount })
              : t("agent.thinking")}
          </span>
        )}
      </div>

      <div className="ai-agent-chat-main">
        <div className="ai-agent-chat-log">
          {messages.length === 0 && !isPending && (
            <p className="ai-agent-chat-empty op-muted">
              {t("agent.emptyHint")}
            </p>
          )}
          {messages.map((message) => (
            <div
              key={message.id}
              className={
                message.role === "user" ? "ai-agent-bubble user" : "ai-agent-bubble agent"
              }
            >
              <div className="ai-agent-bubble-text">{message.text}</div>
              {message.steps && message.steps.length > 0 && (
                <AgentRunDetails
                  steps={message.steps}
                  status={message.status}
                  result={message.result}
                />
              )}
            </div>
          ))}
          {isPending && (
            <div className="ai-agent-bubble agent ai-agent-bubble-pending">
              <div className="ai-agent-bubble-text">
                {toolStepCount > 0
                  ? t("agent.executing", {
                      count: toolStepCount,
                      steps: t("agent.step", { count: toolStepCount }),
                    })
                  : t("agent.thinking")}
              </div>
              {liveSteps.length > 0 && (
                <AgentRunDetails steps={liveSteps} status="RUNNING" open />
              )}
            </div>
          )}
          <div ref={scrollRef} />
        </div>

        <form
          className="ai-agent-chat-compose"
          onSubmit={(e) => {
            e.preventDefault();
            void sendMessage(input);
          }}
        >
          <textarea
            ref={inputRef}
            rows={1}
            value={input}
            placeholder={t("agent.placeholder")}
            onChange={(e) => {
              setInput(e.target.value);
              resizeChatInput(e.target);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                e.currentTarget.form?.requestSubmit();
              }
            }}
            disabled={sending || !chatEnabled}
          />
          <button
            type="submit"
            className="btn primary"
            disabled={sending || !chatEnabled || !input.trim()}
          >
            {t("agent.send")}
          </button>
        </form>
      </div>
    </div>
  );
}

function stepStatusBadge(status: string | undefined): React.ReactNode {
  if (!status) {
    return null;
  }
  const ok = status === "OK" || status === "SUCCESS";
  const running = status === "RUNNING";
  const cancelled = status === "CANCELLED" || status === "STOPPED";
  const badgeClass = ok ? "ok" : running || cancelled ? "hist" : "danger";
  const label = ok ? "OK" : running ? "…" : status;
  return <span className={`badge ${badgeClass}`}>{label}</span>;
}

function AgentRunDetails({
  steps,
  status,
  result,
  open = false,
}: {
  steps: AiAgentStep[];
  status?: string;
  result?: Record<string, unknown>;
  open?: boolean;
}) {
  const { t } = useTranslation("ai");
  const devicePath = typeof result?.devicePath === "string" ? result.devicePath : undefined;
  const dashboardPath =
    typeof result?.dashboardPath === "string" ? result.dashboardPath : undefined;
  const toolSteps = steps.filter((s) => s.type === "tool");
  const isRunning = status === "RUNNING";

  return (
    <details className="ai-agent-run-details" open={open || undefined}>
      <summary>
        {isRunning
          ? t("agent.details.running")
          : status === "OK"
            ? t("agent.details.ok")
            : t("agent.details.error")}
        {toolSteps.length > 0 ? ` (${toolSteps.length})` : ""}
      </summary>
      {toolSteps.length > 0 && (
        <ol className="ai-agent-step-list">
          {toolSteps.map((step) => (
            <li key={step.step}>
              <code>{step.tool}</code>
              {" "}
              {step.label || ""}
              {" "}
              {stepStatusBadge(
                step.result?.status === "ERROR"
                  ? "ERROR"
                  : step.result?.status === "OK"
                    ? "OK"
                    : isRunning
                      ? "RUNNING"
                      : undefined
              )}
              {step.result?.status === "ERROR" && (
                <span className="ai-agent-step-error">
                  {" "}
                  — {String(step.result.error ?? t("agent.stepError"))}
                </span>
              )}
            </li>
          ))}
        </ol>
      )}
      {!isRunning && (
        <div className="ai-agent-run-links">
          {devicePath && (
            <a className="btn small" href={dashboardLink(devicePath) ?? "#"}>
              {t("agent.openDevice")}
            </a>
          )}
          {dashboardPath && (
            <a className="btn small" href={dashboardLink(dashboardPath) ?? "#"}>
              {t("agent.openDashboard")}
            </a>
          )}
        </div>
      )}
    </details>
  );
}
