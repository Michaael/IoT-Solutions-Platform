import type { EventDescriptor, FunctionDescriptor, ObjectType, DataRecord, DataSchema } from "../types";
import type { BindingActivators } from "../types";

export type ModelType = "RELATIVE" | "ABSOLUTE" | "INSTANCE";

export interface ModelVariableDefinition {
  name: string;
  description: string;
  group: string;
  schema: DataSchema;
  readable: boolean;
  writable: boolean;
  defaultValue: DataRecord | null;
  historyEnabled?: boolean;
  historyRetentionDays?: number | null;
}

export interface ModelBindingRule {
  id: string;
  name?: string | null;
  enabled?: boolean | null;
  order?: number | null;
  activators?: BindingActivators | null;
  condition?: string | null;
  expression: string;
  targetVariable: string;
  targetField?: string | null;
}

export interface ModelDto {
  id: string;
  name: string;
  description: string;
  type: ModelType;
  targetObjectType: ObjectType;
  suitabilityExpression: string;
  objectPath: string;
  variables: ModelVariableDefinition[];
  events: EventDescriptor[];
  functions: FunctionDescriptor[];
  bindings: ModelBindingRule[];
  parameters: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface ModelMergeWarningDto {
  kind: string;
  name: string;
  previousModelId: string | null;
  appliedModelId: string;
}

export interface ModelAttachmentDto {
  id: string;
  modelId: string;
  modelName: string;
  modelType: ModelType;
  objectPath: string;
  attachedAt: string;
  warnings?: ModelMergeWarningDto[];
}

export interface CreateModelPayload {
  name: string;
  description?: string;
  type?: ModelType;
  targetObjectType?: ObjectType;
  suitabilityExpression?: string;
  variables?: ModelVariableDefinition[];
  events?: EventDescriptor[];
  functions?: FunctionDescriptor[];
  bindings?: ModelBindingRule[];
  parameters?: Record<string, string>;
}

export interface UpdateModelPayload {
  name?: string;
  description?: string;
  type?: ModelType;
  targetObjectType?: ObjectType;
  suitabilityExpression?: string;
  variables?: ModelVariableDefinition[];
  events?: EventDescriptor[];
  functions?: FunctionDescriptor[];
  bindings?: ModelBindingRule[];
  parameters?: Record<string, string>;
}

export const MODELS_ROOT = "root.platform.models";
export const RELATIVE_MODELS_ROOT = "root.platform.relative-models";
export const INSTANCE_TYPES_ROOT = "root.platform.instance-types";
export const ABSOLUTE_MODELS_ROOT = "root.platform.absolute-models";

export const MODEL_CATALOG_ROOTS = [
  MODELS_ROOT,
  RELATIVE_MODELS_ROOT,
  INSTANCE_TYPES_ROOT,
  ABSOLUTE_MODELS_ROOT,
] as const;

export const BUILTIN_MODEL_NAMES = new Set([
  "mqtt-sensor-v1",
  "dashboard-v1",
  "workflow-v1",
  "virtual-lab-v1",
  "snmp-agent-v1",
  "vendor-sensor-ext-v1",
]);

export function modelNameFromPath(path: string): string | null {
  for (const root of MODEL_CATALOG_ROOTS) {
    const prefix = `${root}.`;
    if (path.startsWith(prefix)) {
      return path.slice(prefix.length);
    }
  }
  return null;
}

export function isModelsPath(path: string): boolean {
  if (MODEL_CATALOG_ROOTS.some((root) => path === root || path.startsWith(`${root}.`))) {
    return true;
  }
  return false;
}

export function modelCatalogRootFromPath(path: string): string | null {
  for (const root of MODEL_CATALOG_ROOTS) {
    if (path === root || path.startsWith(`${root}.`)) {
      return root;
    }
  }
  return null;
}
