import i18n from "../i18n";
import { OPERATOR_APPS_ROOT, isOperatorAppChildPath } from "./operatorAppsPath";
import {
  ALERT_RULES_ROOT,
  CORRELATORS_ROOT,
  isAlertRulePath,
  isCorrelatorPath,
} from "./automationPath";
import type { ObjectType } from "../types";
import { BINDINGS_ROOT, DATA_SOURCES_ROOT, MIGRATIONS_ROOT } from "./platformSqlPath";

export const APPLICATIONS_ROOT = "root.platform.applications";
export const REPORTS_ROOT = "root.platform.reports";

export type CreateDialogMode =
  | "object"
  | "application"
  | "operator-app"
  | "alert-rule"
  | "correlator"
  | "report"
  | "data-source"
  | "migration"
  | "sql-binding";

function isPlatformReportsFolder(path: string): boolean {
  return path === REPORTS_ROOT || (path.endsWith(".reports") && !path.startsWith(`${APPLICATIONS_ROOT}.`));
}

function isPlatformCatalogContainer(path: string): boolean {
  if (path === "root" || path === "root.platform") {
    return true;
  }
  return (
    path.endsWith(".devices")
    || path.endsWith(".relative-models")
    || path.endsWith(".instance-types")
    || path.endsWith(".absolute-models")
    || path.endsWith(".instances")
    || path.endsWith(".dashboards")
    || isPlatformReportsFolder(path)
    || path.endsWith(".workflows")
    || path.endsWith(".alert-rules")
    || path.endsWith(".correlators")
    || path.endsWith(".data-sources")
    || path.endsWith(".schedules")
    || path.endsWith(".bindings")
    || path.endsWith(".migrations")
  );
}

export function resolveCreateDialogMode(parentPath: string): CreateDialogMode {
  if (parentPath === OPERATOR_APPS_ROOT) {
    return "operator-app";
  }
  if (parentPath === ALERT_RULES_ROOT) {
    return "alert-rule";
  }
  if (parentPath === CORRELATORS_ROOT) {
    return "correlator";
  }
  if (parentPath === DATA_SOURCES_ROOT || parentPath.endsWith(".data-sources")) {
    return "data-source";
  }
  if (parentPath === MIGRATIONS_ROOT || parentPath.endsWith(".migrations")) {
    return "migration";
  }
  if (parentPath === BINDINGS_ROOT || parentPath.endsWith(".bindings")) {
    return "sql-binding";
  }
  if (isPlatformReportsFolder(parentPath)) {
    return "report";
  }
  return "object";
}

export function createActionLabel(parentPath: string): string {
  switch (resolveCreateDialogMode(parentPath)) {
    case "application":
      return i18n.t("explorer:create.deployApp");
    case "operator-app":
      return i18n.t("explorer:create.operatorApp");
    case "alert-rule":
      return i18n.t("explorer:create.alertRule");
    case "correlator":
      return i18n.t("explorer:create.correlator");
    case "report":
      return i18n.t("explorer:create.report");
    case "data-source":
      return i18n.t("explorer:create.dataSource");
    case "migration":
      return i18n.t("explorer:create.migration");
    case "sql-binding":
      return i18n.t("explorer:create.sqlBinding");
    default:
      return i18n.t("explorer:create.object");
  }
}

/** Whether the selected tree node can have a child created under it. */
export function canCreateChildAt(path: string, objectType: ObjectType | undefined): boolean {
  if (!path) {
    return false;
  }
  if (objectType === "VISUAL_GROUP") {
    return false;
  }
  if (
    path === OPERATOR_APPS_ROOT
    || path === ALERT_RULES_ROOT
    || path === CORRELATORS_ROOT
  ) {
    return true;
  }
  if (path === APPLICATIONS_ROOT) {
    return false;
  }
  if (isOperatorAppChildPath(path)) {
    return false;
  }
  if (isAlertRulePath(path) || isCorrelatorPath(path)) {
    return false;
  }
  if (path.startsWith(`${APPLICATIONS_ROOT}.`)) {
    return false;
  }
  if (path.startsWith(`${OPERATOR_APPS_ROOT}.`)) {
    return false;
  }
  if (path.startsWith("root.platform.security")) {
    return false;
  }
  if (isPlatformCatalogContainer(path)) {
    return true;
  }

  const containerTypes: ObjectType[] = [
    "ROOT",
    "TENANT",
    "PLATFORM",
    "DEVICES",
    "DASHBOARDS",
    "REPORTS",
    "WORKFLOWS",
    "ALERT_RULES",
    "CORRELATORS",
    "APPLICATIONS",
    "OPERATOR_APPS",
    "MODEL",
    "CUSTOM",
  ];
  if (!objectType || !containerTypes.includes(objectType)) {
    return false;
  }

  if (path === "root" || path === "root.platform") {
    return true;
  }

  if (objectType === "CUSTOM") {
    return true;
  }

  const suffixAllowed =
    path.endsWith(".devices")
    || path.endsWith(".relative-models")
    || path.endsWith(".instance-types")
    || path.endsWith(".absolute-models")
    || path.endsWith(".instances")
    || path.endsWith(".dashboards")
    || path.endsWith(".reports")
    || path.endsWith(".workflows")
    || path.endsWith(".alert-rules")
    || path.endsWith(".correlators");
  return suffixAllowed;
}

export function defaultObjectTypeForParent(parentPath: string): ObjectType {
  if (parentPath.endsWith(".dashboards")) {
    return "DASHBOARD";
  }
  if (parentPath.endsWith(".reports")) {
    return "REPORT";
  }
  if (parentPath.endsWith(".workflows")) {
    return "WORKFLOW";
  }
  if (parentPath.endsWith(".devices")) {
    return "DEVICE";
  }
  if (
    parentPath.endsWith(".relative-models")
    || parentPath.endsWith(".instance-types")
    || parentPath.endsWith(".absolute-models")
  ) {
    return "MODEL";
  }
  return "CUSTOM";
}

/** Platform type filter for INSTANCE blueprints in create dialog (undefined = all types). */
export function instanceTypeFilterForParent(parentPath: string): ObjectType | undefined {
  if (parentPath.endsWith(".devices")) {
    return "DEVICE";
  }
  if (parentPath.endsWith(".dashboards")) {
    return "DASHBOARD";
  }
  if (parentPath.endsWith(".reports")) {
    return "REPORT";
  }
  if (parentPath.endsWith(".workflows")) {
    return "WORKFLOW";
  }
  return undefined;
}

/** Tree node name for app id (same rules as server sanitizeNodeName). */
export function sanitizeAppNodeName(appId: string): string {
  if (!appId.trim()) {
    return "node";
  }
  let sanitized = appId.replace(/[^a-zA-Z0-9_-]/g, "_");
  if (!sanitized) {
    return "node";
  }
  if (/^\d/.test(sanitized)) {
    sanitized = `n_${sanitized}`;
  }
  return sanitized;
}

export function applicationObjectPath(appId: string): string {
  return `${APPLICATIONS_ROOT}.${sanitizeAppNodeName(appId)}`;
}

export function operatorAppObjectPath(appId: string): string {
  return `${OPERATOR_APPS_ROOT}.${sanitizeAppNodeName(appId)}`;
}
