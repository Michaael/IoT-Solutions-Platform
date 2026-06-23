import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { createAlertRule, validateExpression } from "../../api";
import type { CreateAlertRulePayload } from "../../types/automation";

interface CreateAlertRuleDialogProps {
  onClose: () => void;
  onCreated: () => void;
}

const DEFAULT: CreateAlertRulePayload = {
  name: "",
  objectPath: "root.platform.devices.demo-sensor-01",
  watchVariable: "temperature",
  conditionExpr: "temperature.value > threshold.value",
  eventName: "thresholdExceeded",
  payloadVariable: "",
  enabled: true,
  edgeTrigger: true,
};

export default function CreateAlertRuleDialog({ onClose, onCreated }: CreateAlertRuleDialogProps) {
  const { t } = useTranslation(["automation", "common"]);
  const [form, setForm] = useState<CreateAlertRulePayload>({ ...DEFAULT });
  const [exprError, setExprError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      createAlertRule({
        ...form,
        payloadVariable: form.payloadVariable?.trim() || undefined,
      }),
    onSuccess: () => onCreated(),
  });

  const validateMutation = useMutation({
    mutationFn: () => validateExpression(form.conditionExpr),
    onSuccess: (result) => setExprError(result.valid ? null : result.error),
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{t("automation:alertRule.newTitle")}</h3>
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
            {t("automation:alertRule.event")}
            <input
              value={form.eventName}
              onChange={(e) => setForm((f) => ({ ...f, eventName: e.target.value }))}
              required
            />
          </label>
          <label className="full">
            {t("automation:alertRule.targetObject")}
            <input
              value={form.objectPath}
              onChange={(e) => setForm((f) => ({ ...f, objectPath: e.target.value }))}
              required
            />
          </label>
          <label>
            {t("automation:alertRule.variable")}
            <input
              value={form.watchVariable}
              onChange={(e) => setForm((f) => ({ ...f, watchVariable: e.target.value }))}
              required
            />
          </label>
          <label>
            {t("automation:alertRule.payloadVariable")}
            <input
              value={form.payloadVariable ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, payloadVariable: e.target.value }))}
              placeholder={t("automation:alertRule.optionalPlaceholder")}
            />
          </label>
          <label className="full">
            {t("automation:alertRule.celCondition")}
            <textarea
              rows={3}
              value={form.conditionExpr}
              onChange={(e) => {
                setForm((f) => ({ ...f, conditionExpr: e.target.value }));
                setExprError(null);
              }}
              onBlur={() => validateMutation.mutate()}
              required
            />
            {exprError && <span className="hint error">{exprError}</span>}
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
            />
            {t("automation:alertRule.enabled")}
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.edgeTrigger}
              onChange={(e) => setForm((f) => ({ ...f, edgeTrigger: e.target.checked }))}
            />
            {t("automation:alertRule.edgeTriggerHint")}
          </label>
          {mutation.error && (
            <p className="hint error full">{(mutation.error as Error).message}</p>
          )}
          <footer className="full form-actions">
            <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
            <button
              type="submit"
              className="btn primary"
              disabled={mutation.isPending || !form.name || !!exprError}
            >
              {t("common:action.create")}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
