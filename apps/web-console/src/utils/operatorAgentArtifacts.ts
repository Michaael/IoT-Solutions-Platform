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

export interface OperatorAgentArtifacts {
  links?: OperatorAgentLink[];
  tables?: OperatorAgentTablePreview[];
  table?: OperatorAgentTablePreview;
  suggestions?: OperatorAgentSuggestion[];
  interactive?: boolean;
  phase?: string;
  plan?: Record<string, unknown>;
  questions?: AgentPlanQuestion[];
}

export interface AgentPlanQuestion {
  id?: string;
  text?: string;
  options?: Array<{ label?: string; value?: string }>;
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
  const plan =
    result.plan && typeof result.plan === "object" ? (result.plan as Record<string, unknown>) : undefined;
  return {
    links,
    tables,
    suggestions,
    interactive: Boolean(result.interactive),
    phase: typeof result.phase === "string" ? result.phase : undefined,
    plan,
    questions,
  };
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
