import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { updateCorrelator } from "../../api";
import type { CorrelatorActionType, CorrelatorPatternType, CreateCorrelatorPayload } from "../../types/automation";
import {
  CORRELATOR_ACTION_LABEL_KEYS,
  CORRELATOR_ACTION_TYPES,
  correlatorActionTargetLabel,
} from "../../utils/correlatorAction";
import { variableBoolean, variableNumber, variableString } from "../../utils/variableFieldValue";
import { inspectorQueryLoading, useInspectorVariables } from "../../hooks/useInspectorQueries";
import ObjectFederationBindSection from "../ObjectFederationBindSection";

interface CorrelatorInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function CorrelatorInspector({ path, canManage = false }: CorrelatorInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const variablesQuery = useInspectorVariables(path);

  const variables = variablesQuery.data ?? [];
  const patternType = (variableString(variables, "patternType") || "COUNT") as CorrelatorPatternType;
  const actionType = (variableString(variables, "actionType") || "RUN_WORKFLOW") as CorrelatorActionType;
  const needsSecondEvent =
    patternType === "SEQUENCE" || patternType === "EVENT_CHAIN";

  const saveMutation = useMutation({
    mutationFn: (payload: Partial<CreateCorrelatorPayload>) => updateCorrelator(path, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  if (inspectorQueryLoading(variablesQuery)) {
    return <p className="hint">{t("automation:correlator.loading")}</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:correlator.title")}</h3>
          <p className="hint">{t("automation:correlator.subtitle")}</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>
      <form
        key={path}
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          if (!canManage) {
            return;
          }
          const data = new FormData(e.currentTarget);
          saveMutation.mutate({
            objectPath: String(data.get("objectPath") ?? "") || undefined,
            patternType: String(data.get("patternType") ?? "COUNT") as CorrelatorPatternType,
            eventName: String(data.get("eventName") ?? ""),
            secondEventName: String(data.get("secondEventName") ?? "") || undefined,
            windowSeconds: Number(data.get("windowSeconds") ?? 0),
            minOccurrences: Number(data.get("minOccurrences") ?? 1),
            cooldownSeconds: Number(data.get("cooldownSeconds") ?? 120),
            actionType: String(data.get("actionType") ?? "RUN_WORKFLOW") as CorrelatorActionType,
            actionTarget: String(data.get("actionTarget") ?? ""),
            enabled: data.get("enabled") === "on",
          });
        }}
      >
        <label className="full">
          {t("automation:correlator.objectFilter")}
          <input
            name="objectPath"
            defaultValue={variableString(variables, "targetObjectPath")}
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:correlator.pattern")}
          <select name="patternType" defaultValue={patternType} disabled={!canManage}>
            <option value="COUNT">COUNT</option>
            <option value="SEQUENCE">SEQUENCE</option>
            <option value="EVENT_CHAIN">EVENT_CHAIN</option>
          </select>
        </label>
        <label>
          {t("automation:correlator.action")}
          <select name="actionType" defaultValue={actionType} disabled={!canManage}>
            {CORRELATOR_ACTION_TYPES.map((type) => (
              <option key={type} value={type}>
                {t(CORRELATOR_ACTION_LABEL_KEYS[type])}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("automation:correlator.event")}
          <input
            name="eventName"
            defaultValue={variableString(variables, "eventName")}
            required
            readOnly={!canManage}
          />
        </label>
        <label>
          {needsSecondEvent
            ? patternType === "EVENT_CHAIN"
              ? t("automation:correlator.eventChain")
              : t("automation:correlator.eventB")
            : t("automation:correlator.secondEventChain")}
          <input
            name="secondEventName"
            defaultValue={variableString(variables, "secondEventName")}
            readOnly={!canManage}
            placeholder={t("automation:correlator.secondEventPlaceholder")}
          />
        </label>
        <label>
          {t("automation:correlator.windowSeconds")}
          <input
            name="windowSeconds"
            type="number"
            min={0}
            defaultValue={variableNumber(variables, "windowSeconds")}
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:correlator.minOccurrences")}
          <input
            name="minOccurrences"
            type="number"
            min={1}
            defaultValue={variableNumber(variables, "minOccurrences", 1)}
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:correlator.cooldownSeconds")}
          <input
            name="cooldownSeconds"
            type="number"
            min={0}
            defaultValue={variableNumber(variables, "cooldownSeconds", 120)}
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {correlatorActionTargetLabel(actionType, t)}
          <input
            name="actionTarget"
            defaultValue={variableString(variables, "actionTarget")}
            required
            readOnly={!canManage}
          />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            name="enabled"
            defaultChecked={variableBoolean(variables, "enabled", true)}
            disabled={!canManage}
          />
          {t("automation:alertRule.enabled")}
        </label>
        {canManage && (
          <div className="form-actions full">
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              {t("common:action.save")}
            </button>
          </div>
        )}
        {saveMutation.error && (
          <p className="hint error full">{String(saveMutation.error)}</p>
        )}
      </form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
