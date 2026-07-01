import AgentChatArtifacts from "../agent/AgentChatArtifacts";

interface OperatorAgentArtifactsViewProps {
  result?: Record<string, unknown>;
  onOpenDashboard?: (path: string) => void;
  onOpenReport?: (path: string) => void;
  onSuggestMessage?: (message: string) => void;
  onAppendToInput?: (text: string) => void;
}

export default function OperatorAgentArtifactsView(props: OperatorAgentArtifactsViewProps) {
  return (
    <AgentChatArtifacts
      result={props.result}
      i18nNs="operator"
      onSuggestMessage={props.onSuggestMessage}
      onAppendToInput={props.onAppendToInput}
      onOpenDashboard={props.onOpenDashboard}
      onOpenReport={props.onOpenReport}
    />
  );
}
