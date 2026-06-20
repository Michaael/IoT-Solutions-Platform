import { OPERATOR_APPS_ROOT, isOperatorAppChildPath } from "./operatorAppsPath";
import type { ObjectType } from "../types";

export const APPLICATIONS_ROOT = "root.platform.applications";

export type CreateDialogMode = "object" | "application" | "operator-app";

export function resolveCreateDialogMode(parentPath: string): CreateDialogMode {
  if (parentPath === APPLICATIONS_ROOT) {
    return "application";
  }
  if (parentPath === OPERATOR_APPS_ROOT) {
    return "operator-app";
  }
  return "object";
}

export function createActionLabel(parentPath: string): string {
  switch (resolveCreateDialogMode(parentPath)) {
    case "application":
      return "+ Deploy-приложение";
    case "operator-app":
      return "+ Operator app";
    default:
      return "+ Объект";
  }
}

/** Whether the selected tree node can have a child created under it. */
export function canCreateChildAt(path: string, objectType: ObjectType | undefined): boolean {
  if (!path) {
    return false;
  }
  if (path === APPLICATIONS_ROOT || path === OPERATOR_APPS_ROOT) {
    return true;
  }
  if (isOperatorAppChildPath(path)) {
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

  const containerTypes: ObjectType[] = ["ROOT", "TENANT", "CUSTOM", "MODEL"];
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
    || path.endsWith(".workflows")
  );
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
