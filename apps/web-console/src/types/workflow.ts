export type WorkflowLifecycleStatus = "DRAFT" | "ACTIVE" | "STOPPED";

export interface WorkflowView {
  path: string;
  title: string;
  status: WorkflowLifecycleStatus;
  bpmnXml: string;
  triggerJson: string;
  operatorAppId: string | null;
  instanceState: string;
  lastRunAt: string | null;
}

export interface WorkflowInstanceState {
  instanceId?: string;
  status?: string;
  currentNodeId?: string;
  startedAt?: string;
  completedAt?: string | null;
  history?: string[];
  errorMessage?: string | null;
  pendingSignalName?: string | null;
  pendingUserTaskId?: string | null;
  assignee?: string | null;
}

export function parseInstanceState(raw: string | undefined | null): WorkflowInstanceState {
  if (!raw) return {};
  try {
    return JSON.parse(raw) as WorkflowInstanceState;
  } catch {
    return {};
  }
}
