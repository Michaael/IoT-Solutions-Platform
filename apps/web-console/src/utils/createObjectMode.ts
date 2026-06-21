import { OPERATOR_APPS_ROOT, isOperatorAppChildPath } from "./operatorAppsPath";
import {
  ALERT_RULES_ROOT,
  CORRELATORS_ROOT,
  isAlertRulePath,
  isCorrelatorPath,
} from "./automationPath";
import type { ObjectType } from "../types";

export const APPLICATIONS_ROOT = "root.platform.applications";
export const REPORTS_ROOT = "root.platform.reports";

export type CreateDialogMode =
  | "object"
  | "application"
  | "operator-app"
  | "alert-rule"
  | "correlator"
  | "report";

function isPlatformReportsFolder(path: string): boolean {
  return path === REPORTS_ROOT || (path.endsWith(".reports") && !path.startsWith(`${APPLICATIONS_ROOT}.`));
}

function isPlatformCatalogContainer(path: string): boolean {
  if (path === "root" || path === "root.platform") {
    return true;
  }
  return (
    path.endsWith(".devices")
    || path.endsWith(".models")
    || path.endsWith(".dashboards")
    || isPlatformReportsFolder(path)
    || path.endsWith(".workflows")
    || path.endsWith(".alert-rules")
    || path.endsWith(".correlators")
  );
}

export function resolveCreateDialogMode(parentPath: string): CreateDialogMode {
  if (parentPath === APPLICATIONS_ROOT) {
    return "application";
  }
  if (parentPath === OPERATOR_APPS_ROOT) {
    return "operator-app";
  }
  if (parentPath === ALERT_RULES_ROOT) {
    return "alert-rule";
  }
  if (parentPath === CORRELATORS_ROOT) {
    return "correlator";
  }
  if (isPlatformReportsFolder(parentPath)) {
    return "report";
  }
  return "object";
}

export function createActionLabel(parentPath: string): string {
  switch (resolveCreateDialogMode(parentPath)) {
    case "application":
      return "+ Deploy-приложение";
    case "operator-app":
      return "+ Operator app";
    case "alert-rule":
      return "+ Правило алерта";
    case "correlator":
      return "+ Коррелятор";
    case "report":
      return "+ Отчёт";
    default:
      return "+ Объект";
  }
}

/** Whether the selected tree node can have a child created under it. */
export function canCreateChildAt(path: string, objectType: ObjectType | undefined): boolean {
  if (!path) {
    return false;
  }
  if (
    path === APPLICATIONS_ROOT
    || path === OPERATOR_APPS_ROOT
    || path === ALERT_RULES_ROOT
    || path === CORRELATORS_ROOT
  ) {
    return true;
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

  return (
    path.endsWith(".devices")
    || path.endsWith(".models")
    || path.endsWith(".dashboards")
    || path.endsWith(".reports")
    || path.endsWith(".workflows")
    || path.endsWith(".alert-rules")
    || path.endsWith(".correlators")
  );
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
  return "CUSTOM";
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
