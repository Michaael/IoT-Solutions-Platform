export interface PlatformInfo {
  name: string;
  shortName: string;
  version: string;
  timestamp: string;
  capabilities: string[];
}

export type ObjectType =
  | "ROOT"
  | "TENANT"
  | "USER"
  | "DEVICE"
  | "DRIVER"
  | "MODEL"
  | "DASHBOARD"
  | "WORKFLOW"
  | "ALERT"
  | "AGENT"
  | "APPLICATION"
  | "REPORT"
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
  variableNames: string[];
  eventNames: string[];
}

export interface DataSchema {
  name: string;
  fields: Array<{
    name: string;
    type: string;
    description?: string;
    nullable?: boolean;
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
  bindingExpression: string | null;
  updatedAt: string | null;
}

export interface FunctionDescriptor {
  name: string;
  description: string;
  inputSchema: DataSchema;
  outputSchema: DataSchema;
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
  driverId?: string;
  driverPollIntervalMs?: number;
  autoStartDriver?: boolean;
}

export interface UpdateObjectPayload {
  displayName?: string;
  description?: string;
  iconId?: string | null;
}
