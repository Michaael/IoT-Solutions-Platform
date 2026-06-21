export type CorrelatorActionType = "RUN_WORKFLOW" | "FIRE_EVENT";

export type CorrelatorPatternType = "COUNT" | "SEQUENCE" | "EVENT_CHAIN";

export interface CreateAlertRulePayload {
  name: string;
  objectPath: string;
  watchVariable: string;
  conditionExpr: string;
  eventName: string;
  payloadVariable?: string;
  enabled: boolean;
  edgeTrigger: boolean;
}

export interface CreateCorrelatorPayload {
  name: string;
  objectPath?: string;
  patternType?: CorrelatorPatternType;
  eventName: string;
  secondEventName?: string;
  windowSeconds: number;
  minOccurrences: number;
  cooldownSeconds: number;
  actionType: CorrelatorActionType;
  actionTarget: string;
  enabled: boolean;
}

export type WorkflowIspfAction =
  | "setVariable"
  | "invoke_function"
  | "fire_event"
  | "read_variable"
  | "start_workflow"
  | "log"
  | "publishNats";

export const WORKFLOW_ISPF_ACTIONS: {
  action: WorkflowIspfAction;
  label: string;
  attrs: string[];
}[] = [
  { action: "fire_event", label: "Fire event", attrs: ["ispf:objectPath", "ispf:eventName", "ispf:payloadJson"] },
  { action: "read_variable", label: "Read variable", attrs: ["ispf:objectPath", "ispf:variable", "ispf:contextKey"] },
  { action: "start_workflow", label: "Start child workflow", attrs: ["ispf:workflowPath", "ispf:targetObject"] },
  { action: "invoke_function", label: "Invoke function", attrs: ["ispf:objectPath", "ispf:function", "ispf:inputJson"] },
  { action: "setVariable", label: "Set variable", attrs: ["ispf:targetObject", "ispf:variable", "ispf:value"] },
];
