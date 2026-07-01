/** True when suggestion is the plan-approval primary action (must be hidden while gaps remain). */
export function isPlanApprovalSuggestion(item: OperatorAgentSuggestion | undefined): boolean {
  if (!item?.primary) {
    return false;
  }
  const label = (item.label ?? "").toLowerCase();
  const message = (item.message ?? "").toLowerCase();
  return (
    label.includes("утверд") ||
    label.includes("approve") ||
    message.includes("утверждаю план") ||
    message.includes("approve the plan")
  );
}

/** Translate legacy English gap strings when UI locale is Russian. */
export function localizeCompletenessGaps(
  gaps: string[] | undefined,
  language: string
): string[] | undefined {
  if (!gaps?.length || !language.toLowerCase().startsWith("ru")) {
    return gaps;
  }
  return gaps.map((gap) => {
    if (/[\u0400-\u04FF]/.test(gap)) {
      return gap;
    }
    let translated = gap;
    translated = translated.replace(
      /plan\.goal or plan\.executiveSummary missing/,
      "Не указаны plan.goal и plan.executiveSummary"
    );
    translated = translated.replace(
      /specBrief incomplete — need title, entities\[\], ≥(\d+) functionalRequirements with sourcePhrase/,
      "specBrief неполный — нужны title, entities[], ≥$1 functionalRequirements с sourcePhrase"
    );
    translated = translated.replace(
      /plan\.sections count (\d+) — need all (\d+) core sections/,
      "Секций plan.sections: $1 — нужны все $2 базовых слоёв"
    );
    translated = translated.replace(/missing section id: model_strategy/, "Нет секции: стратегия моделей (model_strategy)");
    translated = translated.replace(/missing section id: validation_layer/, "Нет секции: валидация (validation_layer)");
    translated = translated.replace(/missing section id: ground_truth/, "Нет секции: исходные данные (ground_truth)");
    translated = translated.replace(/missing section id: intent_scope/, "Нет секции: цели и границы (intent_scope)");
    translated = translated.replace(/missing section id: source_layer/, "Нет секции: слой источников (source_layer)");
    translated = translated.replace(/missing section id: aggregation_layer/, "Нет секции: слой агрегации (aggregation_layer)");
    translated = translated.replace(/missing section id: alert_layer/, "Нет секции: слой алертов (alert_layer)");
    translated = translated.replace(/missing section id: operator_layer/, "Нет секции: операторский слой (operator_layer)");
    translated = translated.replace(
      /section (\w+) too thin — need summary ≥(\d+) chars, ≥(\d+) concrete steps, deliverables\[\]/,
      "Секция $1 слишком краткая — summary ≥$2 символов, ≥$3 конкретных шагов, deliverables[]"
    );
    translated = translated.replace(
      /objectTypesCoverage\[\] missing/,
      "Нет objectTypesCoverage[] — перечислите типы объектов платформы"
    );
    translated = translated.replace(
      /gapMatrix missing — map each FR to capability/,
      "Нет gapMatrix — сопоставьте каждый FR с capability платформы"
    );
    translated = translated.replace(
      /handoffFrame missing for approval/,
      "Нет handoffFrame — нужен для утверждения"
    );
    return translated;
  });
}

export interface OperatorAgentLink {
  kind: "dashboard" | "report" | string;
  path: string;
  title: string;
  url?: string;
}

export interface OperatorAgentTableColumn {
  field: string;
  label?: string;
}

export interface OperatorAgentTablePreview {
  title?: string;
  reportPath?: string;
  columns: OperatorAgentTableColumn[];
  rows: Array<Record<string, unknown>>;
  rowCount?: number;
  truncated?: boolean;
}

export interface OperatorAgentSuggestion {
  label: string;
  message: string;
  kind?: string;
  path?: string;
  primary?: boolean;
}

export interface AgentSpecBrief {
  title?: string;
  businessGoal?: string;
  entities?: Array<{ id?: string; label?: string; kind?: string }>;
  functionalRequirements?: Array<{
    id?: string;
    title?: string;
    sourcePhrase?: string;
    layer?: string;
    acceptanceCriteria?: string;
  }>;
  assumptions?: string[];
  constraints?: string[];
}

export interface AgentGapMatrixRow {
  requirementId?: string;
  capabilityId?: string;
  status?: string;
  gapId?: string;
  blocksDev?: boolean;
}

export interface AgentPlanSection {
  id?: string;
  title?: string;
  summary?: string;
  relatedFrIds?: string[];
  objectTypes?: string[];
  tools?: string[];
  steps?: string[];
  deliverables?: string[];
}

export interface OperatorAgentArtifacts {
  links?: OperatorAgentLink[];
  tables?: OperatorAgentTablePreview[];
  table?: OperatorAgentTablePreview;
  suggestions?: OperatorAgentSuggestion[];
  interactive?: boolean;
  phase?: string;
  plan?: Record<string, unknown>;
  questions?: AgentPlanQuestion[];
  specBrief?: AgentSpecBrief;
  gapMatrix?: AgentGapMatrixRow[];
  executiveSummary?: string;
  assumptions?: string[];
  planCompletenessGaps?: string[];
}

export interface AgentPlanQuestion {
  id?: string;
  text?: string;
  options?: Array<{ label?: string; value?: string }>;
}

/** Line to append in chat input when user picks a plan question option (does not auto-send). */
export function formatPlanQuestionAnswer(
  question: AgentPlanQuestion,
  option: { label?: string; value?: string }
): string {
  const answer = option.value?.trim() || option.label?.trim() || "";
  const prompt = question.text?.trim();
  if (prompt && answer) {
    return `${prompt}: ${answer}`;
  }
  return answer || prompt || "";
}

export function parseOperatorAgentArtifacts(result: Record<string, unknown> | undefined): OperatorAgentArtifacts {
  if (!result) {
    return {};
  }
  const links = Array.isArray(result.links)
    ? (result.links as OperatorAgentLink[]).filter((item) => item?.path)
    : [];
  let tables: OperatorAgentTablePreview[] = [];
  if (Array.isArray(result.tables)) {
    tables = (result.tables as OperatorAgentTablePreview[]).filter((item) => item?.rows?.length);
  } else if (result.table && typeof result.table === "object") {
    const single = result.table as OperatorAgentTablePreview;
    if (single.rows?.length) {
      tables = [single];
    }
  }
  const suggestions = Array.isArray(result.suggestions)
    ? (result.suggestions as OperatorAgentSuggestion[]).filter((item) => item?.message?.trim())
    : [];
  const questions = Array.isArray(result.questions)
    ? (result.questions as AgentPlanQuestion[]).filter((item) => item?.text?.trim())
    : [];
  const plan = normalizePlanRecord(
    result.plan && typeof result.plan === "object" ? (result.plan as Record<string, unknown>) : undefined
  );
  const specBrief = normalizeSpecBrief(result.specBrief ?? plan?.specBrief);
  const gapMatrix = normalizeGapMatrix(result.gapMatrix ?? plan?.gapMatrix);
  const executiveSummary =
    typeof (plan?.executiveSummary ?? result.executiveSummary) === "string"
      ? String(plan?.executiveSummary ?? result.executiveSummary).trim()
      : undefined;
  const assumptions = normalizeStringList(plan?.assumptions ?? result.assumptions);
  const planCompletenessGaps = normalizeStringList(result.planCompletenessGaps);
  return {
    links,
    tables,
    suggestions,
    interactive: Boolean(result.interactive),
    phase: typeof result.phase === "string" ? result.phase : undefined,
    plan,
    questions,
    specBrief,
    gapMatrix,
    executiveSummary,
    assumptions,
    planCompletenessGaps,
  };
}

function normalizePlanRecord(plan: Record<string, unknown> | undefined): Record<string, unknown> | undefined {
  if (!plan) {
    return undefined;
  }
  const normalized: Record<string, unknown> = { ...plan };
  if (Array.isArray(plan.steps)) {
    normalized.steps = plan.steps
      .map((step) => normalizePlanStep(step))
      .filter((step) => step.length > 0);
  }
  if (Array.isArray(plan.sections)) {
    normalized.sections = plan.sections
      .map((section) => normalizePlanSection(section))
      .filter((section) => section.title || section.summary || (section.steps?.length ?? 0) > 0);
  }
  return normalized;
}

function normalizePlanSection(section: unknown): AgentPlanSection {
  if (!section || typeof section !== "object") {
    return {};
  }
  const record = section as Record<string, unknown>;
  const normalized: AgentPlanSection = {};
  if (typeof record.id === "string") normalized.id = record.id.trim();
  if (typeof record.title === "string") normalized.title = record.title.trim();
  if (typeof record.summary === "string") normalized.summary = record.summary.trim();
  if (Array.isArray(record.relatedFrIds)) {
    normalized.relatedFrIds = record.relatedFrIds
      .map((item) => (typeof item === "string" ? item.trim() : ""))
      .filter(Boolean);
  }
  if (Array.isArray(record.objectTypes)) {
    normalized.objectTypes = record.objectTypes
      .map((item) => (typeof item === "string" ? item.trim() : ""))
      .filter(Boolean);
  }
  if (Array.isArray(record.tools)) {
    normalized.tools = record.tools
      .map((item) => (typeof item === "string" ? item.trim() : ""))
      .filter(Boolean);
  }
  if (Array.isArray(record.steps)) {
    normalized.steps = record.steps
      .map((step) => normalizePlanStep(step))
      .filter((step) => step.length > 0);
  }
  if (Array.isArray(record.deliverables)) {
    normalized.deliverables = record.deliverables
      .map((item) => (typeof item === "string" ? item.trim() : ""))
      .filter(Boolean);
  }
  return normalized;
}

function normalizeStringList(raw: unknown): string[] | undefined {
  if (!Array.isArray(raw)) {
    return undefined;
  }
  const items = raw.map((item) => (typeof item === "string" ? item.trim() : "")).filter(Boolean);
  return items.length > 0 ? items : undefined;
}

function normalizeSpecBrief(raw: unknown): AgentSpecBrief | undefined {
  if (!raw || typeof raw !== "object") {
    return undefined;
  }
  const record = raw as Record<string, unknown>;
  const brief: AgentSpecBrief = {};
  if (typeof record.title === "string") brief.title = record.title.trim();
  if (typeof record.businessGoal === "string") brief.businessGoal = record.businessGoal.trim();
  if (Array.isArray(record.entities)) {
    brief.entities = record.entities
      .filter((item) => item && typeof item === "object")
      .map((item) => item as AgentSpecBrief["entities"] extends Array<infer T> ? T : never);
  }
  if (Array.isArray(record.functionalRequirements)) {
    brief.functionalRequirements = record.functionalRequirements
      .filter((item) => item && typeof item === "object")
      .map((item) => item as NonNullable<AgentSpecBrief["functionalRequirements"]>[number]);
  }
  brief.assumptions = normalizeStringList(record.assumptions);
  brief.constraints = normalizeStringList(record.constraints);
  return brief.title || brief.functionalRequirements?.length ? brief : undefined;
}

function normalizeGapMatrix(raw: unknown): AgentGapMatrixRow[] | undefined {
  if (!Array.isArray(raw)) {
    return undefined;
  }
  const rows = raw
    .filter((item) => item && typeof item === "object")
    .map((item) => item as AgentGapMatrixRow);
  return rows.length > 0 ? rows : undefined;
}

function normalizePlanStep(step: unknown): string {
  if (typeof step === "string") {
    return step.trim();
  }
  if (step && typeof step === "object") {
    const record = step as Record<string, unknown>;
    for (const key of ["text", "step", "description", "label", "action", "title"]) {
      const value = record[key];
      if (typeof value === "string" && value.trim()) {
        return value.trim();
      }
    }
  }
  return "";
}

export function columnLabels(table: OperatorAgentTablePreview): Record<string, string> {
  const labels: Record<string, string> = {};
  for (const col of table.columns ?? []) {
    if (col.field) {
      labels[col.field] = col.label?.trim() || col.field;
    }
  }
  return labels;
}
