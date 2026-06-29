export interface PlatformInfo {
  name: string;
  shortName: string;
  version: string;
  environment?: string;
  timestamp: string;
  javaVersion?: string;
  springBootVersion?: string;
  capabilities: string[];
  federationSecretsKeyConfigured?: boolean;
  federationSecretsKeySource?: "NONE" | "YAML" | "DATABASE";
}

export type ObjectType =
  | "ROOT"
  | "TENANT"
  | "USER"
  | "PLATFORM"
  | "DEVICES"
  | "DEVICE"
  | "DRIVER"
  | "MODEL"
  | "DASHBOARDS"
  | "DASHBOARD"
  | "WORKFLOWS"
  | "WORKFLOW"
  | "ALERT_RULES"
  | "ALERT"
  | "CORRELATORS"
  | "CORRELATOR"
  | "APPLICATIONS"
  | "APPLICATION"
  | "DATA_SOURCES"
  | "DATA_SOURCE"
  | "OPERATOR_APPS"
  | "SECURITY"
  | "USERS"
  | "ROLES"
  | "ROLE"
  | "REPORTS"
  | "REPORT"
  | "FUNCTIONS"
  | "FUNCTION"
  | "SCHEDULES"
  | "SCHEDULE"
  | "BINDINGS"
  | "BINDING"
  | "MIGRATIONS"
  | "MIGRATION"
  | "SCREENS"
  | "SCREEN"
  | "AGENT"
  | "VISUAL_GROUP"
  | "CUSTOM";

export interface ObjectSummary {
  id: string;
  path: string;
  type: ObjectType;
  displayName: string;
  description: string;
  templateId: string | null;
  iconId?: string | null;
  createdAt: string;
  sortOrder: number;
  revision?: number;
  lastChangedBy?: string | null;
  lastChangedAt?: string | null;
  variableNames: string[];
  eventNames: string[];
  federated?: boolean;
  federationPeerId?: string | null;
  federationRemotePath?: string | null;
  groupRef?: boolean;
  groupContextPath?: string | null;
  groupMemberMissing?: boolean;
  /** DEVICE lite list: driverStatus variable when driverId is configured. */
  driverStatus?: string | null;
  /** DEVICE lite list: live connected flag when status is RUNNING. */
  driverConnected?: boolean | null;
  /** CEL/SQL binding invoke audit for this object only. */
  bindingAuditEnabled?: boolean;
  functionAuditEnabled?: boolean;
  appliedModels?: AppliedModelSummary[];
}

export interface AppliedModelSummary {
  id: string;
  name: string;
  type: "RELATIVE" | "INSTANCE" | "ABSOLUTE";
  primary: boolean;
}

export interface DataSchema {
  name: string;
  fields: Array<{
    name: string;
    type: string;
    description?: string;
    nullable?: boolean;
    nestedSchema?: DataSchema | null;
  }>;
}

export interface DataRecord {
  schema: DataSchema;
  rows: Array<Record<string, unknown>>;
}

export interface VariableDto {
  name: string;
  value: DataRecord | null;
  readable: boolean;
  writable: boolean;
  updatedAt: string | null;
  historyEnabled: boolean;
  historyRetentionDays: number | null;
}

export interface BindingVariableRef {
  objectPath: string;
  variableName: string;
}

export interface BindingActivators {
  onStartup: boolean;
  onVariableChange: BindingVariableRef[];
  onEvent: string | null;
  periodicMs: number;
  /** When true, rule runs on a dedicated single-thread executor (coalesced per rule id). */
  async?: boolean;
  /** When true, rule runs when @dashboardContext changes. */
  onContextChange?: boolean;
}

export type BindingTargetKind = "variable" | "context" | "event";

export interface BindingTarget {
  kind?: BindingTargetKind | null;
  variableName?: string | null;
  field?: string | null;
  path?: string | null;
  eventName?: string | null;
}

export interface BindingRule {
  id: string;
  name?: string | null;
  enabled: boolean;
  order: number;
  activators: BindingActivators;
  condition: string;
  expression: string;
  target: BindingTarget;
}

export interface FunctionDescriptor {
  name: string;
  description: string;
  inputSchema: DataSchema;
  outputSchema: DataSchema;
  sourceType?: string | null;
  sourceBody?: string | null;
  dataSourcePath?: string | null;
  version?: string | null;
}

export interface EventDescriptor {
  name: string;
  description: string;
  payloadSchema: DataSchema;
  level: string;
}

export interface ObjectEditorDto {
  object: ObjectSummary;
  variables: VariableDto[];
  events: EventDescriptor[];
  functions: FunctionDescriptor[];
}

export interface EditorTab {
  id: string;
  path: string;
  title: string;
  objectType?: ObjectType;
}

export interface TreeNode {
  object: ObjectSummary;
  children: TreeNode[];
}

export interface CreateObjectPayload {
  parentPath: string;
  name: string;
  type: ObjectType;
  displayName?: string;
  description?: string;
  templateId?: string;
  autoApplyRelativeModels?: boolean;
  driverId?: string;
  driverPollIntervalMs?: number;
  autoStartDriver?: boolean;
}

export interface UpdateObjectPayload {
  displayName?: string;
  description?: string;
  iconId?: string | null;
  bindingAuditEnabled?: boolean;
  functionAuditEnabled?: boolean;
}
