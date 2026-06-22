import { useAgentChatOptional } from "../context/AgentChatContext";

interface AgentChatStatusBarProps {
  workspaceTab: string;
  onOpenAiStudio: () => void;
}

export default function AgentChatStatusBar({ workspaceTab, onOpenAiStudio }: AgentChatStatusBarProps) {
  const chat = useAgentChatOptional();
  const busy = chat?.isPending;
  if (!busy) {
    return null;
  }

  const onAgentTab = workspaceTab === "ai-studio";
  if (onAgentTab) {
    return null;
  }

  const label = chat.pendingUserMessage
    ? `Агент выполняет: «${chat.pendingUserMessage.length > 48 ? `${chat.pendingUserMessage.slice(0, 48)}…` : chat.pendingUserMessage}»`
    : "Агент выполняет задачу…";

  return (
    <div className="ai-agent-status-bar" role="status">
      <span className="ai-agent-status-bar-pulse" aria-hidden />
      <span className="ai-agent-status-bar-text">{label}</span>
      <button type="button" className="btn small primary" onClick={onOpenAiStudio}>
        Открыть AI Studio
      </button>
    </div>
  );
}
