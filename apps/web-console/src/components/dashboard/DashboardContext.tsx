import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

export type DashboardOpenMode = "navigate" | "modal";

export interface DashboardSession {
  selection: Record<string, string>;
  params: Record<string, unknown>;
}

export interface OpenDashboardOptions {
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
}

export function emptySession(): DashboardSession {
  return { selection: {}, params: {} };
}

export function mergeSession(
  current: DashboardSession,
  patch?: OpenDashboardOptions
): DashboardSession {
  if (!patch) {
    return current;
  }
  return {
    selection: patch.selection ? { ...current.selection, ...patch.selection } : current.selection,
    params: patch.params ? { ...current.params, ...patch.params } : current.params,
  };
}

export interface DashboardContextValue extends DashboardSession {
  setSelection: (key: string, path: string) => void;
  setParams: (patch: Record<string, unknown>) => void;
  navigateToDashboard: (path: string, options?: OpenDashboardOptions) => void;
  openDashboardModal: (path: string, title?: string, options?: OpenDashboardOptions) => void;
}

const noop = () => {};

const defaultValue: DashboardContextValue = {
  selection: {},
  params: {},
  setSelection: noop,
  setParams: noop,
  navigateToDashboard: noop,
  openDashboardModal: noop,
};

const DashboardContext = createContext<DashboardContextValue>(defaultValue);

interface DashboardProviderProps {
  children: ReactNode;
  value?: DashboardContextValue;
  session?: DashboardSession;
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  onSessionChange?: (next: DashboardSession) => void;
  onSelectionChange?: (next: Record<string, string>) => void;
  onParamsChange?: (next: Record<string, unknown>) => void;
  onNavigateDashboard?: (path: string, options?: OpenDashboardOptions) => void;
  onOpenDashboardModal?: (path: string, title?: string, options?: OpenDashboardOptions) => void;
}

export function DashboardProvider({
  children,
  value,
  session: controlledSession,
  selection: controlledSelection,
  params: controlledParams,
  onSessionChange,
  onSelectionChange,
  onParamsChange,
  onNavigateDashboard,
  onOpenDashboardModal,
}: DashboardProviderProps) {
  const [internalSession, setInternalSession] = useState<DashboardSession>(emptySession);

  const derivedValue = useMemo<DashboardContextValue>(() => {
    if (value) {
      return value;
    }

    const session =
      controlledSession ??
      ({
        selection: controlledSelection ?? internalSession.selection,
        params: controlledParams ?? internalSession.params,
      } satisfies DashboardSession);

    const publishSession = (next: DashboardSession) => {
      if (onSessionChange) {
        onSessionChange(next);
      } else {
        if (onSelectionChange) {
          onSelectionChange(next.selection);
        }
        if (onParamsChange) {
          onParamsChange(next.params);
        }
        if (!onSelectionChange && !onParamsChange) {
          setInternalSession(next);
        }
      }
    };

    const setSelection = (key: string, path: string) => {
      publishSession({ ...session, selection: { ...session.selection, [key]: path } });
    };

    const setParams = (patch: Record<string, unknown>) => {
      publishSession({ ...session, params: { ...session.params, ...patch } });
    };

    return {
      selection: session.selection,
      params: session.params,
      setSelection,
      setParams,
      navigateToDashboard: onNavigateDashboard ?? noop,
      openDashboardModal: onOpenDashboardModal ?? noop,
    };
  }, [
    value,
    controlledSession,
    controlledSelection,
    controlledParams,
    internalSession,
    onSessionChange,
    onSelectionChange,
    onParamsChange,
    onNavigateDashboard,
    onOpenDashboardModal,
  ]);

  return <DashboardContext.Provider value={derivedValue}>{children}</DashboardContext.Provider>;
}

export function useDashboardContext(): DashboardContextValue {
  return useContext(DashboardContext);
}

export function triggerDashboardOpen(
  mode: DashboardOpenMode | undefined,
  targetPath: string | undefined,
  title: string | undefined,
  actions: Pick<DashboardContextValue, "navigateToDashboard" | "openDashboardModal">,
  options?: OpenDashboardOptions
): boolean {
  const path = targetPath?.trim();
  if (!path) {
    return false;
  }
  if (mode === "modal") {
    actions.openDashboardModal(path, title, options);
  } else {
    actions.navigateToDashboard(path, options);
  }
  return true;
}

export function useApplyOpenOptions() {
  const ctx = useDashboardContext();
  return useCallback(
    (options?: OpenDashboardOptions) => {
      if (!options) {
        return;
      }
      if (options.selection) {
        for (const [key, path] of Object.entries(options.selection)) {
          ctx.setSelection(key, path);
        }
      }
      if (options.params) {
        ctx.setParams(options.params);
      }
    },
    [ctx]
  );
}
