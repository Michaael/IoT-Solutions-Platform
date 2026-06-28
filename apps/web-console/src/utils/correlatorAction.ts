import type { TFunction } from "i18next";
import type { CorrelatorActionType } from "../types/automation";

export function correlatorActionTargetLabel(
  actionType: CorrelatorActionType,
  t: TFunction<"automation">
): string {
  switch (actionType) {
    case "FIRE_EVENT":
      return t("correlator.actionTargetEvent");
    case "SET_VARIABLE":
      return t("correlator.actionTargetSetVariable");
    case "OPEN_OPERATOR_REPORT":
      return t("correlator.actionTargetReport");
    default:
      return t("correlator.workflowTarget");
  }
}

export function correlatorActionTargetPlaceholder(
  actionType: CorrelatorActionType,
  t: TFunction<"automation">
): string {
  switch (actionType) {
    case "FIRE_EVENT":
      return t("correlator.actionTargetEventPlaceholder");
    case "SET_VARIABLE":
      return t("correlator.actionTargetSetVariablePlaceholder");
    case "OPEN_OPERATOR_REPORT":
      return t("correlator.actionTargetReportPlaceholder");
    default:
      return t("correlator.actionTargetWorkflowPlaceholder");
  }
}

export const CORRELATOR_ACTION_TYPES: CorrelatorActionType[] = [
  "RUN_WORKFLOW",
  "FIRE_EVENT",
  "SET_VARIABLE",
  "OPEN_OPERATOR_REPORT",
];

export const CORRELATOR_ACTION_LABEL_KEYS: Record<CorrelatorActionType, string> = {
  RUN_WORKFLOW: "correlator.actionRunWorkflow",
  FIRE_EVENT: "correlator.actionFireEvent",
  SET_VARIABLE: "correlator.actionSetVariable",
  OPEN_OPERATOR_REPORT: "correlator.actionOpenOperatorReport",
};
