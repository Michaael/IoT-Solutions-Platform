import { useCallback, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useAgentChat } from "../context/AgentChatContext";
import type { AgentInteractionMode } from "../api/ai";
import type { AiAgentStep } from "../api/ai";
import AgentChatArtifacts, { AgentStarterSuggestions } from "./agent/AgentChatArtifacts";
import AgentChatComposeAttachments, {
  AgentMessageAttachmentPreview,
} from "./agent/AgentChatComposeAttachments";

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
    livePlanPhase,
    startNewChat,
    switchSession,
    deleteChat,
    sendMessage,
    cancelRun,
    interactionMode,
    setInteractionMode,
    pendingAttachments,
    setPendingAttachments,
    attachmentRejectHint,
    clearAttachmentRejectHint,
    rejectAttachment,
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
  const hasUserTurns = messages.some((message) => message.role === "user");

  const modeOptions: AgentInteractionMode[] = ["auto", "plan", "execute", "ask"];

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
          {!hasUserTurns && !isPending && (
            <AgentStarterSuggestions
              i18nNs="ai"
              suggestionKeys={[
                "agent.suggest.explore",
                "agent.suggest.snmp",
                "agent.suggest.report",
                "agent.suggest.scada",
                "agent.suggest.bundle",
              ]}
              onPick={(text) => void sendMessage(text)}
            />
          )}
          {messages.map((message) => (
            <div
              key={message.id}
              className={
                message.role === "user" ? "ai-agent-bubble user" : "ai-agent-bubble agent"
              }
            >
              <div className="ai-agent-bubble-text">{message.text}</div>
              {message.role === "user" && (
                <AgentMessageAttachmentPreview attachments={message.attachments} />
              )}
              {message.role === "agent" && (
                <AgentChatArtifacts
                  result={message.result}
                  i18nNs="ai"
                  onSuggestMessage={(text) => void sendMessage(text)}
                />
              )}
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
                  ? livePlanPhase === "planning" || livePlanPhase === "awaiting_approval"
                    ? t("agent.planning", {
                        count: toolStepCount,
                        steps: t("agent.step", { count: toolStepCount }),
                      })
                    : t("agent.executing", {
                        count: toolStepCount,
                        steps: t("agent.step", { count: toolStepCount }),
                      })
                  : t("agent.thinking")}
              </div>
              {isPending && (
                <AgentRunDetails
                  steps={liveSteps}
                  status="RUNNING"
                  open
                  placeholder={toolStepCount === 0}
                />
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
          <label className="ai-agent-mode-select ai-agent-mode-select--compose">
            <span className="ai-agent-mode-label">{t("agent.mode.label")}</span>
            <select
              value={interactionMode}
              disabled={sending || !chatEnabled}
              aria-label={t("agent.mode.label")}
              onChange={(event) => setInteractionMode(event.target.value as AgentInteractionMode)}
            >
              {modeOptions.map((mode) => (
                <option key={mode} value={mode}>
                  {t(`agent.mode.${mode}`)}
                </option>
              ))}
            </select>
          </label>
          {attachmentRejectHint && (
            <div className="op-alert op-alert-warn ai-agent-attach-reject" role="status">
              {attachmentRejectHint}
              <button type="button" className="btn link small" onClick={clearAttachmentRejectHint}>
                {t("agent.attachments.dismiss")}
              </button>
            </div>
          )}
          <AgentChatComposeAttachments
            provider={provider}
            attachments={pendingAttachments}
            disabled={sending || !chatEnabled}
            onChange={setPendingAttachments}
            onReject={rejectAttachment}
          />
          <div className="ai-agent-chat-compose-row">
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
              disabled={sending || !chatEnabled || (!input.trim() && pendingAttachments.length === 0)}
            >
              {t("agent.send")}
            </button>
          </div>
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

export function AgentRunDetails({
  steps,
  status,
  result,
  open = false,
  placeholder = false,
}: {
  steps: AiAgentStep[];
  status?: string;
  result?: Record<string, unknown>;
  open?: boolean;
  placeholder?: boolean;
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
      {placeholder && toolSteps.length === 0 && (
        <p className="op-muted ai-agent-step-placeholder">{t("agent.details.waiting")}</p>
      )}
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
