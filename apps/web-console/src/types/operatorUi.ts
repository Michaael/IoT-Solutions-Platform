/** Operator application shell — navigation over DASHBOARD and REPORT objects from the object tree. */

import type { OperatorAlarmBarConfig } from "./operatorAlarmBar";

export interface OperatorUiDashboard {
  path: string;
  title: string;
}

export interface OperatorUiReport {
  path: string;
  title: string;
}

export interface OperatorUi {
  appId: string;
  title: string;
  defaultDashboard: string;
  dashboards: OperatorUiDashboard[];
  reports?: OperatorUiReport[];
  defaultReport?: string;
  /** Optional object path prefix filter for operator event journal sidebar. */
  eventJournalObjectPath?: string;
  /** Optional workflow path prefix filter for operator work queue sidebar. */
  workQueueWorkflowPathPrefix?: string;
  /** Global popup alarm bar configuration for operator mode. */
  alarmBar?: OperatorAlarmBarConfig;
}

export function resolveOperatorDashboard(
  ui: OperatorUi,
  dashboardPath: string | null
): string {
  if (dashboardPath && ui.dashboards.some((item) => item.path === dashboardPath)) {
    return dashboardPath;
  }
  if (ui.defaultDashboard && ui.dashboards.some((item) => item.path === ui.defaultDashboard)) {
    return ui.defaultDashboard;
  }
  return ui.dashboards[0]?.path ?? "";
}

export function resolveOperatorReport(
  ui: OperatorUi,
  reportPath: string | null
): string {
  const reports = ui.reports ?? [];
  if (reportPath && reports.some((item) => item.path === reportPath)) {
    return reportPath;
  }
  if (ui.defaultReport && reports.some((item) => item.path === ui.defaultReport)) {
    return ui.defaultReport;
  }
  return reports[0]?.path ?? "";
}
