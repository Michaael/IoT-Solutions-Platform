import { useDashboardContext } from "../components/dashboard/DashboardContext";
import { resolveWidgetPath } from "../components/dashboard/dashboardUtils";

export function useWidgetObjectPath(
  objectPath?: string,
  selectionKey?: string,
  contextPathKey?: string
): string {
  const { selection, params } = useDashboardContext();
  return resolveWidgetPath(objectPath, selectionKey, selection, contextPathKey, params);
}

export function useWidgetSession() {
  return useDashboardContext();
}
