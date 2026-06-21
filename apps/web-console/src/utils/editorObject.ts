import { isModelsPath } from "../types/models";
import type { ObjectType } from "../types";

/** Objects edited in a dedicated builder (dashboard / report / workflow / model). */
export function isSpecializedEditorObject(
  path: string,
  type?: ObjectType,
  templateId?: string | null,
): boolean {
  if (
    type === "DASHBOARD"
    || type === "REPORT"
    || type === "WORKFLOW"
    || type === "MODEL"
  ) {
    return true;
  }
  if (templateId === "report-v1" || templateId === "dashboard-v1" || templateId === "workflow-v1") {
    return true;
  }
  if (isModelsPath(path)) {
    return true;
  }
  return (
    path.startsWith("root.platform.reports.")
    || path.startsWith("root.platform.dashboards.")
    || path.startsWith("root.platform.workflows.")
  );
}

export function resolveEditorObjectType(
  path: string,
  type?: ObjectType,
  templateId?: string | null,
): ObjectType | undefined {
  if (
    type === "DASHBOARD"
    || type === "REPORT"
    || type === "WORKFLOW"
    || type === "MODEL"
  ) {
    return type;
  }
  if (templateId === "report-v1") return "REPORT";
  if (templateId === "dashboard-v1") return "DASHBOARD";
  if (templateId === "workflow-v1") return "WORKFLOW";
  if (isModelsPath(path)) return "MODEL";
  if (path.startsWith("root.platform.reports.")) return "REPORT";
  if (path.startsWith("root.platform.dashboards.")) return "DASHBOARD";
  if (path.startsWith("root.platform.workflows.")) return "WORKFLOW";
  return type;
}
