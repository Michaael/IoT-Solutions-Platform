import { useDashboardContext } from "../components/dashboard/DashboardContext";
import { resolveWidgetPath } from "../components/dashboard/dashboardUtils";

export function useWidgetObjectPath(
  objectPath?: string,
  selectionKey?: string
): string {
  const { selection } = useDashboardContext();
  return resolveWidgetPath(objectPath, selectionKey, selection);
}
