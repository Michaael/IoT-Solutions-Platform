/** Declarative operator app — mapping screens to BFF invoke (app bundle / public manifest). */

export interface OperatorManifestAction {
  id: string;
  label: string;
  objectPath: string;
  functionName: string;
  input?: Record<string, unknown>;
  successMessage?: string;
}

export interface OperatorManifestTable {
  objectPath: string;
  functionName: string;
  input?: Record<string, unknown>;
  refreshIntervalMs?: number;
  emptyMessage?: string;
}

export interface OperatorManifestScreen {
  id: string;
  title: string;
  description?: string;
  actions?: OperatorManifestAction[];
  table?: OperatorManifestTable;
}

export interface OperatorManifest {
  appId: string;
  title: string;
  wireProfile?: string;
  defaultScreen: string;
  screens: OperatorManifestScreen[];
}

export function resolveOperatorScreen(manifest: OperatorManifest, screenId: string | null): OperatorManifestScreen {
  const found = manifest.screens.find((screen) => screen.id === screenId);
  if (found) {
    return found;
  }
  const fallback = manifest.screens.find((screen) => screen.id === manifest.defaultScreen);
  if (fallback) {
    return fallback;
  }
  return manifest.screens[0];
}
