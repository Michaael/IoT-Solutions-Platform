import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

export type DashboardOpenMode = "navigate" | "modal";

export interface DashboardSession {
  selection: Record<string, string>;
  params: Record<string, unknown>;
  widgets?: Record<string, { visible?: boolean }>;
}

export interface OpenDashboardOptions {
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  widgets?: Record<string, { visible?: boolean }>;
}

export function emptySession(): DashboardSession {
  return { selection: {}, params: {}, widgets: {} };
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
    widgets: patch.widgets ? { ...(current.widgets ?? {}), ...patch.widgets } : current.widgets,
  };
}

export interface DashboardContextValue extends DashboardSession {
  operatorMode?: boolean;
  /** Dashboard is rendered inside a modal overlay */
  embeddedModal?: boolean;
  setSelection: (key: string, path: string) => void;
  setParams: (patch: Record<string, unknown>) => void;
  navigateToDashboard: (path: string, options?: OpenDashboardOptions) => void;
  openDashboardModal: (path: string, title?: string, options?: OpenDashboardOptions) => void;
  closeDashboardModal: () => void;
}

const noop = () => {};

const defaultValue: DashboardContextValue = {
  selection: {},
  params: {},
  widgets: {},
  setSelection: noop,
  setParams: noop,
  navigateToDashboard: noop,
  openDashboardModal: noop,
  closeDashboardModal: noop,
};

const DashboardContext = createContext<DashboardContextValue>(defaultValue);

interface DashboardProviderProps {
  children: ReactNode;
  value?: DashboardContextValue;
  operatorMode?: boolean;
  embeddedModal?: boolean;
  closeDashboardModal?: () => void;
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
  operatorMode = false,
  embeddedModal = false,
  closeDashboardModal,
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

  const session =
    controlledSession ??
    ({
      selection: controlledSelection ?? internalSession.selection,
      params: controlledParams ?? internalSession.params,
      widgets: internalSession.widgets,
    } satisfies DashboardSession);

  const sessionRef = useRef(session);
  sessionRef.current = session;

  const derivedValue = useMemo<DashboardContextValue>(() => {
    if (value) {
      return value;
    }

    const publishSession = (next: DashboardSession) => {
      sessionRef.current = next;
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
      const current = sessionRef.current;
      publishSession({
        ...current,
        selection: { ...current.selection, [key]: path },
      });
    };

    const setParams = (patch: Record<string, unknown>) => {
      const current = sessionRef.current;
      publishSession({
        ...current,
        params: { ...current.params, ...patch },
      });
    };

    return {
      operatorMode,
      embeddedModal,
      selection: session.selection,
      params: session.params,
      widgets: session.widgets,
      setSelection,
      setParams,
      navigateToDashboard: onNavigateDashboard ?? noop,
      openDashboardModal: onOpenDashboardModal ?? noop,
      closeDashboardModal: closeDashboardModal ?? noop,
    };
  }, [
    value,
    session,
    onSessionChange,
    onSelectionChange,
    onParamsChange,
    onNavigateDashboard,
    onOpenDashboardModal,
    operatorMode,
    embeddedModal,
    closeDashboardModal,
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
