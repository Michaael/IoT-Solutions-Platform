import { isModelsPath } from "../types/models";
import type { ObjectType } from "../types";
import { isPlatformSqlObjectPath } from "./platformSqlPath";

/** Objects edited in a dedicated builder (dashboard / report / workflow / model / platform SQL). */
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
    || type === "DATA_SOURCE"
    || type === "MIGRATION"
    || type === "BINDING"
    || type === "SCHEDULE"
    || type === "MIMIC"
  ) {
    return true;
  }
  if (
    templateId === "report-v1"
    || templateId === "dashboard-v1"
    || templateId === "workflow-v1"
    || templateId === "data-source-v1"
    || templateId === "migration-v1"
    || templateId === "sql-binding-v1"
    || templateId === "schedule-v1"
    || templateId === "mimic-v1"
  ) {
    return true;
  }
  if (isModelsPath(path)) {
    return true;
  }
  return (
    path.startsWith("root.platform.reports.")
    || path.startsWith("root.platform.dashboards.")
    || path.startsWith("root.platform.workflows.")
    || path.startsWith("root.platform.mimics.")
    || isPlatformSqlObjectPath(path)
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
    || type === "DATA_SOURCE"
    || type === "MIGRATION"
    || type === "BINDING"
    || type === "SCHEDULE"
    || type === "MIMIC"
  ) {
    return type;
  }
  if (templateId === "report-v1") return "REPORT";
  if (templateId === "dashboard-v1") return "DASHBOARD";
  if (templateId === "workflow-v1") return "WORKFLOW";
  if (templateId === "data-source-v1") return "DATA_SOURCE";
  if (templateId === "migration-v1") return "MIGRATION";
  if (templateId === "sql-binding-v1") return "BINDING";
  if (templateId === "schedule-v1") return "SCHEDULE";
  if (templateId === "mimic-v1") return "MIMIC";
  if (isModelsPath(path)) return "MODEL";
  if (path.startsWith("root.platform.reports.")) return "REPORT";
  if (path.startsWith("root.platform.dashboards.")) return "DASHBOARD";
  if (path.startsWith("root.platform.workflows.")) return "WORKFLOW";
  if (path.startsWith("root.platform.mimics.")) return "MIMIC";
  if (path.startsWith("root.platform.data-sources.")) return "DATA_SOURCE";
  if (path.startsWith("root.platform.migrations.")) return "MIGRATION";
  if (path.startsWith("root.platform.bindings.")) return "BINDING";
  if (path.startsWith("root.platform.schedules.")) return "SCHEDULE";
  return type;
}
