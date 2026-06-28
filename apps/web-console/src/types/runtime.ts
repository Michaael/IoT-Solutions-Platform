export interface FunctionInvokeAuditEntry {
  id: string;
  correlationId: string;
  objectPath: string;
  functionName: string;
  appId: string | null;
  success: boolean;
  errorMessage: string | null;
  inputJson: string | null;
  outputJson: string | null;
  invokedAt: string;
}

export interface BindingInvokeAuditEntry {
  id: string;
  bindingKind: string;
  objectPath: string;
  ruleId: string | null;
  ruleName: string | null;
  triggerKind: string;
  targetVariable: string | null;
  success: boolean;
  changed: boolean;
  errorMessage: string | null;
  durationMs: number | null;
  detailJson: string | null;
  invokedAt: string;
}
