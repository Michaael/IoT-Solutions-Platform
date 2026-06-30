import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { createCorrelator } from "../../api";
import type { CreateCorrelatorPayload, CorrelatorPatternType } from "../../types/automation";
import {
  CORRELATOR_ACTION_LABEL_KEYS,
  CORRELATOR_ACTION_TYPES,
  correlatorActionTargetLabel,
  correlatorActionTargetPlaceholder,
} from "../../utils/correlatorAction";
import { ObjectPathField } from "../../ui";

interface CreateCorrelatorDialogProps {
  onClose: () => void;
  onCreated: () => void;
}

const DEFAULT: CreateCorrelatorPayload = {
  name: "",
  objectPath: "root.platform.devices.demo-sensor-01",
  patternType: "COUNT",
  eventName: "thresholdExceeded",
  secondEventName: "",
  windowSeconds: 0,
  minOccurrences: 1,
  cooldownSeconds: 120,
  sequenceGapSeconds: 0,
  actionType: "RUN_WORKFLOW",
  actionTarget: "root.platform.workflows.demo-alarm-handler",
  enabled: true,
};

export default function CreateCorrelatorDialog({ onClose, onCreated }: CreateCorrelatorDialogProps) {
  const { t } = useTranslation(["automation", "common"]);
  const [form, setForm] = useState<CreateCorrelatorPayload>({ ...DEFAULT });
  const isSequence = form.patternType === "SEQUENCE";
  const isEventChain = form.patternType === "EVENT_CHAIN";
  const needsSecondEvent = isSequence || isEventChain;

  const mutation = useMutation({
    mutationFn: () =>
      createCorrelator({
        ...form,
        objectPath: form.objectPath?.trim() || undefined,
        secondEventName: needsSecondEvent ? form.secondEventName?.trim() || undefined : undefined,
      }),
    onSuccess: () => onCreated(),
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{t("automation:correlator.newTitle")}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate();
          }}
        >
          <label>
            {t("common:table.name")} *
            <input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              required
            />
          </label>
          <label>
            {t("automation:correlator.pattern")}
            <select
              value={form.patternType ?? "COUNT"}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  patternType: e.target.value as CorrelatorPatternType,
                  windowSeconds: e.target.value === "SEQUENCE" && f.windowSeconds <= 0 ? 300 : f.windowSeconds,
                }))
              }
            >
              <option value="COUNT">{t("automation:correlator.patternCount")}</option>
              <option value="SEQUENCE">{t("automation:correlator.patternSequence")}</option>
              <option value="EVENT_CHAIN">{t("automation:correlator.patternEventChain")}</option>
            </select>
          </label>
          <label>
            {isSequence ? t("automation:correlator.eventA") : t("automation:correlator.event")}
            <input
              value={form.eventName}
              onChange={(e) => setForm((f) => ({ ...f, eventName: e.target.value }))}
              required
            />
          </label>
          {needsSecondEvent && (
            <label>
              {isEventChain ? t("automation:correlator.eventChain") : t("automation:correlator.eventB")}
              <input
                value={form.secondEventName ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, secondEventName: e.target.value }))}
                required
                placeholder={isEventChain ? "eventA,eventB,eventC" : "alarmActive"}
              />
            </label>
          )}
          <ObjectPathField
            className="full"
            label={t("automation:correlator.objectPath")}
            value={form.objectPath ?? ""}
            onChange={(objectPath) => setForm((f) => ({ ...f, objectPath }))}
            placeholder={t("automation:correlator.objectPathPlaceholder")}
          />
          <label>
            {t("automation:correlator.windowSec")}
            <input
              type="number"
              min={isSequence ? 1 : 0}
              value={form.windowSeconds}
              onChange={(e) => setForm((f) => ({ ...f, windowSeconds: Number(e.target.value) }))}
            />
          </label>
          {!needsSecondEvent && (
            <label>
              {t("automation:correlator.minRepetitions")}
              <input
                type="number"
                min={1}
                value={form.minOccurrences}
                onChange={(e) => setForm((f) => ({ ...f, minOccurrences: Number(e.target.value) }))}
              />
            </label>
          )}
          <label>
            {t("automation:correlator.cooldownSec")}
            <input
              type="number"
              min={0}
              value={form.cooldownSeconds}
              onChange={(e) => setForm((f) => ({ ...f, cooldownSeconds: Number(e.target.value) }))}
            />
          </label>
          {needsSecondEvent && (
            <label>
              {t("automation:correlator.maxGapSec")}
              <input
                type="number"
                min={0}
                value={form.sequenceGapSeconds ?? 0}
                onChange={(e) => setForm((f) => ({ ...f, sequenceGapSeconds: Number(e.target.value) }))}
              />
            </label>
          )}
          <label>
            {t("automation:correlator.action")}
            <select
              value={form.actionType}
              onChange={(e) =>
                setForm((f) => ({ ...f, actionType: e.target.value as CreateCorrelatorPayload["actionType"] }))
              }
            >
              {CORRELATOR_ACTION_TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`automation:${CORRELATOR_ACTION_LABEL_KEYS[type]}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="full">
            {correlatorActionTargetLabel(form.actionType, t)}
            <input
              value={form.actionTarget}
              onChange={(e) => setForm((f) => ({ ...f, actionTarget: e.target.value }))}
              required
              placeholder={correlatorActionTargetPlaceholder(form.actionType, t)}
            />
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
            />
            {t("automation:alertRule.enabled")}
          </label>
          {mutation.error && (
            <p className="hint error full">{(mutation.error as Error).message}</p>
          )}
          <footer className="full form-actions">
            <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
            <button type="submit" className="btn primary" disabled={mutation.isPending || !form.name}>
              {t("common:action.create")}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
