export const OPERATOR_APPS_ROOT = "root.platform.operator-apps";

export function isOperatorAppsRootPath(path: string): boolean {
  return path === OPERATOR_APPS_ROOT;
}

export function isOperatorAppChildPath(path: string): boolean {
  return path.startsWith(`${OPERATOR_APPS_ROOT}.`);
}

export function isOperatorAppsPath(path: string): boolean {
  return isOperatorAppsRootPath(path) || isOperatorAppChildPath(path);
}

/** Leaf segment under Operator Apps (sanitized app id in tree). */
export function operatorAppLeafFromPath(path: string): string | null {
  if (!isOperatorAppChildPath(path)) {
    return null;
  }
  return path.slice(OPERATOR_APPS_ROOT.length + 1);
}

export function resolveOperatorAppId(
  pathLeaf: string,
  apps: { appId: string }[]
): string {
  return (
    apps.find((app) => app.appId === pathLeaf)?.appId ??
    apps.find((app) => app.appId.replace(/[^a-zA-Z0-9_-]/g, "_") === pathLeaf)?.appId ??
    pathLeaf
  );
}
