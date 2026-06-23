import { useTranslation } from "react-i18next";
import { useAgentChatOptional } from "../context/AgentChatContext";

interface AgentChatStatusBarProps {
  workspaceTab: string;
  onOpenAiStudio: () => void;
}

export default function AgentChatStatusBar({ workspaceTab, onOpenAiStudio }: AgentChatStatusBarProps) {
  const { t } = useTranslation("shell");
  const chat = useAgentChatOptional();
  const busy = chat?.isPending;
  if (!busy) {
    return null;
  }

  const onAgentTab = workspaceTab === "ai-studio";
  if (onAgentTab) {
    return null;
  }

  const pendingMessage = chat.pendingUserMessage ?? "";
  const truncated =
    pendingMessage.length > 48 ? `${pendingMessage.slice(0, 48)}…` : pendingMessage;
  const label = pendingMessage
    ? t("agentStatusBar.runningWithMessage", { message: truncated })
    : t("agentStatusBar.running");

  return (
    <div className="ai-agent-status-bar" role="status">
      <span className="ai-agent-status-bar-pulse" aria-hidden />
      <span className="ai-agent-status-bar-text">{label}</span>
      <button type="button" className="btn small primary" onClick={onOpenAiStudio}>
        {t("agentStatusBar.openStudio")}
      </button>
    </div>
  );
}
