export const FUNCTION_SCRIPT_STEP_TYPES = [
  "return",
  "setVar",
  "buildRecord",
  "when",
  "if",
  "failIfNull",
  "failIfNotEquals",
  "jsonParse",
  "readVariable",
  "instantiateModelIfMissing",
  "setDriverTelemetry",
  "invoke_function",
  "cancel_workflows",
  "selectOne",
  "selectMany",
  "exec",
  "map",
] as const;

export type FunctionScriptStepType = (typeof FUNCTION_SCRIPT_STEP_TYPES)[number];

export type ScriptStep = Record<string, unknown> & { type: FunctionScriptStepType };

export type ScriptStepCategory = "flow" | "variables" | "data" | "platform";

export const STEP_CATEGORIES: Record<FunctionScriptStepType, ScriptStepCategory> = {
  return: "flow",
  when: "flow",
  if: "flow",
  failIfNull: "flow",
  failIfNotEquals: "flow",
  setVar: "variables",
  buildRecord: "variables",
  readVariable: "variables",
  jsonParse: "data",
  map: "data",
  selectOne: "data",
  selectMany: "data",
  exec: "data",
  instantiateModelIfMissing: "platform",
  setDriverTelemetry: "platform",
  invoke_function: "platform",
  cancel_workflows: "platform",
};

export type WhenConditionKind =
  | "truthy"
  | "notNull"
  | "equals"
  | "notEquals"
  | "gt"
  | "lt"
  | "gte"
  | "lte";

export interface EditableScriptStep {
  id: string;
  step: ScriptStep;
}

export interface ScriptTemplate {
  id: string;
  steps: ScriptStep[];
}

let stepIdCounter = 0;

export function newStepId(): string {
  stepIdCounter += 1;
  return `step-${Date.now()}-${stepIdCounter}`;
}

export function wrapSteps(steps: ScriptStep[]): EditableScriptStep[] {
  return steps.map((step) => ({ id: newStepId(), step: { ...step } }));
}

export function unwrapSteps(items: EditableScriptStep[]): ScriptStep[] {
  return items.map((item) => serializeStep(item.step));
}

function serializeStep(step: ScriptStep): ScriptStep {
  const out: ScriptStep = { type: step.type };
  for (const [key, value] of Object.entries(step)) {
    if (key === "type") continue;
    if (value === undefined || value === null) continue;
    if (typeof value === "string" && value.trim() === "" && key !== "sql") continue;
    if (Array.isArray(value) && value.length === 0 && key !== "then") continue;
    if (
      typeof value === "object" &&
      !Array.isArray(value) &&
      Object.keys(value as object).length === 0
    ) {
      continue;
    }
    out[key] = value;
  }
  return out;
}

export function parseScriptBody(sourceBody: string): {
  steps: EditableScriptStep[];
  error?: string;
} {
  if (!sourceBody.trim()) {
    return { steps: wrapSteps([defaultStep("return")]) };
  }
  try {
    const parsed = JSON.parse(sourceBody) as { steps?: unknown };
    if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.steps)) {
      return { steps: [], error: "Script body must be a JSON object with a steps array" };
    }
    const steps = parsed.steps
      .filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
      .map((item) => {
        const type = String(item.type ?? "return") as FunctionScriptStepType;
        const normalized = FUNCTION_SCRIPT_STEP_TYPES.includes(type) ? type : "return";
        return { id: newStepId(), step: { ...item, type: normalized } as ScriptStep };
      });
    if (steps.length === 0) {
      return { steps: wrapSteps([defaultStep("return")]) };
    }
    return { steps };
  } catch (ex) {
    return { steps: [], error: (ex as Error).message };
  }
}

export function serializeScriptBody(items: EditableScriptStep[]): string {
  return JSON.stringify({ steps: unwrapSteps(items) }, null, 2);
}

export function serializeStepsArray(steps: ScriptStep[]): string {
  return JSON.stringify({ steps: steps.map(serializeStep) }, null, 2);
}

export function defaultStep(type: FunctionScriptStepType): ScriptStep {
  switch (type) {
    case "return":
      return { type, fields: { ok: true } };
    case "setVar":
      return { type, var: "result", value: "" };
    case "buildRecord":
      return { type, var: "row", fields: {} };
    case "jsonParse":
      return { type, source: "${input.raw}", var: "parsed", fields: ["id"] };
    case "readVariable":
      return { type, objectPath: "self", variable: "myVar", field: "value", var: "value" };
    case "instantiateModelIfMissing":
      return {
        type,
        modelName: "${modelName}",
        parentPath: "${parentPath}",
        instanceName: "${input.id}",
        var: "instancePath",
      };
    case "setDriverTelemetry":
      return {
        type,
        objectPath: "${instancePath}",
        variable: "temperature",
        fields: { value: "${parsed.value}", unit: "C" },
      };
    case "failIfNull":
      return { type, var: "parsed", error_code: "NOT_FOUND", error_message: "Required value missing" };
    case "failIfNotEquals":
      return { type, var: "status", equals: "OPEN", error_code: "BAD_STATE", error_message: "Invalid state" };
    case "selectOne":
      return { type, var: "row", sql: "SELECT 1 AS id", params: [] };
    case "selectMany":
      return { type, var: "rows", sql: "SELECT 1 AS id", params: [] };
    case "exec":
      return { type, sql: "UPDATE table SET col = ?", params: ["${input.value}"] };
    case "map":
      return { type, var: "items", source: "${rows}", fields: { label: "${item.name}" } };
    case "invoke_function":
      return {
        type,
        objectPath: "self",
        functionName: "myFunction",
        var: "result",
        input: {},
      };
    case "cancel_workflows":
      return {
        type,
        workflowPath: "root.platform.workflows.my-workflow",
        statusIn: ["RUNNING"],
        reason: "cancelled",
        var: "cancelled",
      };
    case "when":
    case "if":
      return {
        type,
        var: "input.value",
        gt: 0,
        then: [{ type: "return", fields: { ok: true, branch: "then" } }],
        else: [{ type: "return", fields: { ok: true, branch: "else" } }],
      };
    default:
      return { type: "return", fields: { ok: true } };
  }
}

export function readWhenConditionKind(step: ScriptStep): WhenConditionKind {
  if (step.notNull !== undefined) return "notNull";
  if (step.equals !== undefined) return "equals";
  if (step.notEquals !== undefined) return "notEquals";
  if (step.gt !== undefined) return "gt";
  if (step.lt !== undefined) return "lt";
  if (step.gte !== undefined) return "gte";
  if (step.lte !== undefined) return "lte";
  return "truthy";
}

export function applyWhenConditionKind(step: ScriptStep, kind: WhenConditionKind): ScriptStep {
  const next: ScriptStep = { ...step, type: step.type };
  delete next.notNull;
  delete next.equals;
  delete next.notEquals;
  delete next.gt;
  delete next.lt;
  delete next.gte;
  delete next.lte;
  switch (kind) {
    case "notNull":
      next.notNull = true;
      break;
    case "equals":
      next.equals = "";
      break;
    case "notEquals":
      next.notEquals = "";
      break;
    case "gt":
      next.gt = 0;
      break;
    case "lt":
      next.lt = 0;
      break;
    case "gte":
      next.gte = 0;
      break;
    case "lte":
      next.lte = 0;
      break;
    default:
      break;
  }
  return next;
}

export function readStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item ?? ""));
}

export function readKeyValue(value: unknown): { key: string; val: string }[] {
  if (!value || typeof value !== "object" || Array.isArray(value)) return [];
  return Object.entries(value as Record<string, unknown>).map(([key, val]) => ({
    key,
    val: val === undefined || val === null ? "" : String(val),
  }));
}

export function writeKeyValue(rows: { key: string; val: string }[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const row of rows) {
    if (row.key.trim()) out[row.key.trim()] = row.val;
  }
  return out;
}

export function readNestedSteps(value: unknown): ScriptStep[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
    .map((item) => {
      const type = String(item.type ?? "return") as FunctionScriptStepType;
      const normalized = FUNCTION_SCRIPT_STEP_TYPES.includes(type) ? type : "return";
      return { ...item, type: normalized } as ScriptStep;
    });
}

export const SCRIPT_TEMPLATES: ScriptTemplate[] = [
  {
    id: "echo",
    steps: [
      { type: "return", fields: { message: "${input.text}", ok: true } },
    ],
  },
  {
    id: "mqttIngest",
    steps: [
      {
        type: "jsonParse",
        source: "${input.raw}",
        var: "meter",
        fields: ["id", "temperature"],
      },
      { type: "failIfNull", var: "meter.id", message: "missing id" },
      {
        type: "readVariable",
        objectPath: "self",
        variable: "instanceParentPath",
        field: "value",
        var: "parentPath",
      },
      {
        type: "readVariable",
        objectPath: "self",
        variable: "instanceModelName",
        field: "value",
        var: "modelName",
      },
      {
        type: "instantiateModelIfMissing",
        modelName: "${modelName}",
        parentPath: "${parentPath}",
        instanceName: "${meter.id}",
        var: "instancePath",
      },
      {
        type: "setDriverTelemetry",
        objectPath: "${instancePath}",
        variable: "temperature",
        fields: { value: "${meter.temperature}", unit: "C" },
      },
      {
        type: "return",
        fields: { ok: true, message: "ingested", routedPath: "${instancePath}" },
      },
    ],
  },
  {
    id: "readVariableReturn",
    steps: [
      {
        type: "readVariable",
        objectPath: "self",
        variable: "temperature",
        field: "value",
        var: "temp",
      },
      { type: "return", fields: { temperature: "${temp}", ok: true } },
    ],
  },
  {
    id: "branchByValue",
    steps: [
      {
        type: "when",
        var: "input.value",
        gt: 100,
        then: [{ type: "return", fields: { level: "HIGH", ok: true } }],
        else: [{ type: "return", fields: { level: "NORMAL", ok: true } }],
      },
    ],
  },
  {
    id: "sqlLoadOne",
    steps: [
      {
        type: "selectOne",
        var: "order",
        sql: "SELECT id, status FROM orders WHERE id = ?",
        params: ["${input.orderId}"],
      },
      {
        type: "failIfNull",
        var: "order",
        error_code: "NOT_FOUND",
        error_message: "Order missing",
      },
      {
        type: "return",
        fields: { status: "${order.status}", error_code: "OK", error_message: "" },
      },
    ],
  },
];

export function defaultScriptBody(): string {
  return serializeStepsArray(SCRIPT_TEMPLATES[0].steps);
}
