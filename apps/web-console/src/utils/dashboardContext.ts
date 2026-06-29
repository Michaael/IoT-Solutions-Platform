import type { DashboardSession } from "../components/dashboard/DashboardContext";

export const DASHBOARD_CONTEXT_VARIABLE = "@dashboardContext";

export interface DashboardContextView {
  path: string;
  context: Record<string, unknown>;
  contextJson: string;
}

export interface DashboardContextPatch {
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  widgets?: Record<string, { visible?: boolean }>;
}

export function emptyServerContext(): DashboardContextPatch {
  return { selection: {}, params: {}, widgets: {} };
}

export function sessionFromServerContext(context: Record<string, unknown>): DashboardSession {
  const selection =
    context.selection && typeof context.selection === "object"
      ? (context.selection as Record<string, string>)
      : {};
  const params =
    context.params && typeof context.params === "object"
      ? (context.params as Record<string, unknown>)
      : {};
  const widgets =
    context.widgets && typeof context.widgets === "object"
      ? (context.widgets as Record<string, { visible?: boolean }>)
      : {};
  return { selection, params, widgets };
}

export function patchFromSession(session: DashboardSession): DashboardContextPatch {
  return {
    selection: session.selection,
    params: session.params,
    widgets: session.widgets,
  };
}

export function sessionsEqual(left: DashboardSession, right: DashboardSession): boolean {
  return (
    JSON.stringify(left.selection) === JSON.stringify(right.selection)
    && JSON.stringify(left.params) === JSON.stringify(right.params)
    && JSON.stringify(left.widgets ?? {}) === JSON.stringify(right.widgets ?? {})
  );
}
