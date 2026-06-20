import { createContext, useContext, useMemo, useState, type ReactNode } from "react";

export type DashboardOpenMode = "navigate" | "modal";

export interface DashboardContextValue {
  selection: Record<string, string>;
  setSelection: (key: string, path: string) => void;
  navigateToDashboard: (path: string) => void;
  openDashboardModal: (path: string, title?: string) => void;
}

const noop = () => {};

const defaultValue: DashboardContextValue = {
  selection: {},
  setSelection: noop,
  navigateToDashboard: noop,
  openDashboardModal: noop,
};

const DashboardContext = createContext<DashboardContextValue>(defaultValue);

interface DashboardProviderProps {
  children: ReactNode;
  value?: DashboardContextValue;
  selection?: Record<string, string>;
  onSelectionChange?: (next: Record<string, string>) => void;
  onNavigateDashboard?: (path: string) => void;
  onOpenDashboardModal?: (path: string, title?: string) => void;
}

export function DashboardProvider({
  children,
  value,
  selection: controlledSelection,
  onSelectionChange,
  onNavigateDashboard,
  onOpenDashboardModal,
}: DashboardProviderProps) {
  const [internalSelection, setInternalSelection] = useState<Record<string, string>>({});

  const derivedValue = useMemo<DashboardContextValue>(() => {
    if (value) {
      return value;
    }

    const selection = controlledSelection ?? internalSelection;
    const setSelection = (key: string, path: string) => {
      const next = { ...selection, [key]: path };
      if (onSelectionChange) {
        onSelectionChange(next);
      } else {
        setInternalSelection(next);
      }
    };

    return {
      selection,
      setSelection,
      navigateToDashboard: onNavigateDashboard ?? noop,
      openDashboardModal: onOpenDashboardModal ?? noop,
    };
  }, [
    value,
    controlledSelection,
    internalSelection,
    onSelectionChange,
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
  actions: Pick<DashboardContextValue, "navigateToDashboard" | "openDashboardModal">
): boolean {
  const path = targetPath?.trim();
  if (!path) {
    return false;
  }
  if (mode === "modal") {
    actions.openDashboardModal(path, title);
  } else {
    actions.navigateToDashboard(path);
  }
  return true;
}
