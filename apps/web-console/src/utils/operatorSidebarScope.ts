import type { ObjectEvent } from "../types/event";
import type { WorkQueueItem } from "../types/operator";
import type { OperatorUi } from "../types/operatorUi";
import { matchAlarmRule, resolveAlarmBarConfig } from "./operatorAlarmBar";

const ALARM_LEVELS = new Set(["WARNING", "ERROR", "CRITICAL"]);

export function isAlarmLevel(level: ObjectEvent["level"]): boolean {
  return ALARM_LEVELS.has(level);
}

export function operatorAppScopeRank(ui: OperatorUi): number {
  if (ui.alarmBar?.rules?.length) {
    return 2;
  }
  if (ui.eventJournalObjectPath?.trim()) {
    return 1;
  }
  return 0;
}

export function eventMatchesOperatorApp(event: ObjectEvent, ui: OperatorUi): boolean {
  if (!isAlarmLevel(event.level)) {
    return false;
  }
  const alarmBar = ui.alarmBar;
  const rules = alarmBar ? resolveAlarmBarConfig(alarmBar).rules : [];
  if (rules.length > 0) {
    const minLevel = alarmBar?.minLevel ?? "WARNING";
    return matchAlarmRule(event, rules, minLevel) !== null;
  }
  const pathPrefix = ui.eventJournalObjectPath?.trim();
  if (pathPrefix) {
    return event.objectPath.startsWith(pathPrefix);
  }
  return false;
}

/** @deprecated Legacy path-prefix filter; prefer workflow.operatorAppId on tasks. */
export function resolveWorkQueueWorkflowPrefix(ui?: OperatorUi, appId?: string): string | undefined {
  const explicit = ui?.workQueueWorkflowPathPrefix?.trim();
  if (explicit) {
    return explicit;
  }
  if (!appId) {
    return undefined;
  }
  if (appId === "platform") {
    return "root.platform.workflows";
  }
  return undefined;
}

export function taskMatchesOperatorApp(task: WorkQueueItem, appId: string, ui?: OperatorUi): boolean {
  const assigned = task.operatorAppId?.trim();
  if (assigned) {
    return assigned === appId;
  }
  if (!ui) {
    return false;
  }
  const prefix = resolveWorkQueueWorkflowPrefix(ui, appId);
  return Boolean(prefix && task.workflowPath.startsWith(prefix));
}

export function collectOperatorAppWatchPaths(ui: OperatorUi, appId?: string): string[] {
  const paths = new Set<string>();
  const journal = ui.eventJournalObjectPath?.trim();
  if (journal) {
    paths.add(journal);
  }
  for (const rule of ui.alarmBar?.rules ?? []) {
    const prefix = rule.objectPathPrefix?.trim();
    if (prefix) {
      paths.add(prefix);
    }
  }
  const workflowPrefix = resolveWorkQueueWorkflowPrefix(ui, appId);
  if (workflowPrefix) {
    paths.add(workflowPrefix);
  }
  return [...paths];
}

function operatorAppsClaimingEvent(event: ObjectEvent, operatorApps: OperatorUi[]): OperatorUi[] {
  return operatorApps.filter((ui) => eventMatchesOperatorApp(event, ui));
}

function currentAppWinsClaim(
  claimants: OperatorUi[],
  appId: string,
  rank: (ui: OperatorUi) => number
): boolean {
  if (claimants.length === 0) {
    return false;
  }
  const bestRank = Math.max(...claimants.map(rank));
  const finalists = claimants.filter((ui) => rank(ui) === bestRank);
  return finalists.some((ui) => ui.appId === appId);
}

export function filterOperatorSidebarEvents(
  events: ObjectEvent[],
  options: {
    appId: string;
    ui: OperatorUi;
    operatorApps: OperatorUi[];
  }
): ObjectEvent[] {
  const { appId, ui, operatorApps } = options;
  const registry = mergeOperatorApps(operatorApps, ui);

  return events.filter((event) => {
    if (!eventMatchesOperatorApp(event, ui)) {
      return false;
    }
    const claimants = operatorAppsClaimingEvent(event, registry);
    return currentAppWinsClaim(claimants, appId, operatorAppScopeRank);
  });
}

export function filterOperatorSidebarTasks(
  tasks: WorkQueueItem[],
  options: {
    appId: string;
    ui: OperatorUi;
    operatorApps: OperatorUi[];
  }
): WorkQueueItem[] {
  const { appId, ui } = options;
  return tasks.filter((task) => taskMatchesOperatorApp(task, appId, ui));
}

function mergeOperatorApps(operatorApps: OperatorUi[], current: OperatorUi): OperatorUi[] {
  const byId = new Map<string, OperatorUi>();
  for (const entry of operatorApps) {
    byId.set(entry.appId, entry);
  }
  byId.set(current.appId, current);
  return [...byId.values()];
}
