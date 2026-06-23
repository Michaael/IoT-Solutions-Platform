import { useTranslation } from "react-i18next";
import type { EventLevel } from "../../types/event";
import type { OperatorAlarmBarConfig, OperatorAlarmRule } from "../../types/operatorAlarmBar";

const EVENT_LEVELS: EventLevel[] = ["WARNING", "ERROR", "CRITICAL"];

interface OperatorAlarmBarEditorProps {
  value: OperatorAlarmBarConfig | undefined;
  onChange: (value: OperatorAlarmBarConfig) => void;
  disabled?: boolean;
  dashboardPaths: string[];
}

export default function OperatorAlarmBarEditor({
  value,
  onChange,
  disabled,
  dashboardPaths,
}: OperatorAlarmBarEditorProps) {
  const { t } = useTranslation(["operator", "common"]);
  const config: OperatorAlarmBarConfig = value ?? { enabled: true, rules: [] };
  const rules = config.rules ?? [];

  const newRule = (index: number): OperatorAlarmRule => ({
    id: `rule-${index}`,
    minLevel: "ERROR",
    fields: [
      { label: t("alarmBarEditor.field.event"), source: "eventName" },
      { label: t("alarmBarEditor.field.object"), source: "objectPath" },
      { label: t("alarmBarEditor.field.time"), source: "timestamp" },
    ],
    persistUntilDismiss: true,
  });

  const patch = (partial: Partial<OperatorAlarmBarConfig>) => {
    onChange({ ...config, ...partial });
  };

  const patchRule = (index: number, partial: Partial<OperatorAlarmRule>) => {
    const next = rules.map((rule, idx) => (idx === index ? { ...rule, ...partial } : rule));
    patch({ rules: next });
  };

  const addRule = () => {
    patch({ rules: [...rules, newRule(rules.length + 1)] });
  };

  const removeRule = (index: number) => {
    patch({ rules: rules.filter((_, idx) => idx !== index) });
  };

  return (
    <div className="operator-alarm-bar-editor full">
      <strong>{t("alarmBarEditor.title")}</strong>
      <p className="hint">
        {t("alarmBarEditor.hint")}
      </p>

      <label className="operator-alarm-bar-toggle">
        <input
          type="checkbox"
          checked={config.enabled !== false}
          disabled={disabled}
          onChange={(e) => patch({ enabled: e.target.checked })}
        />
        {t("alarmBarEditor.enable")}
      </label>

      <div className="form-grid compact">
        <label>
          {t("alarmBarEditor.minLevelGlobal")}
          <select
            value={config.minLevel ?? "ERROR"}
            disabled={disabled}
            onChange={(e) => patch({ minLevel: e.target.value as EventLevel })}
          >
            {EVENT_LEVELS.map((level) => (
              <option key={level} value={level}>
                {level}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("alarmBarEditor.position")}
          <select
            value={config.position ?? "top"}
            disabled={disabled}
            onChange={(e) => patch({ position: e.target.value as "top" | "bottom" })}
          >
            <option value="top">{t("alarmBarEditor.positionTop")}</option>
            <option value="bottom">{t("alarmBarEditor.positionBottom")}</option>
          </select>
        </label>
        <label>
          {t("alarmBarEditor.soundUrl")}
          <input
            value={config.soundUrl ?? "/sounds/alarm.wav"}
            disabled={disabled}
            onChange={(e) => patch({ soundUrl: e.target.value })}
          />
        </label>
        <label>
          {t("alarmBarEditor.soundRepeatMs")}
          <input
            type="number"
            min={1000}
            step={500}
            value={config.soundRepeatMs ?? 3000}
            disabled={disabled}
            onChange={(e) => patch({ soundRepeatMs: Number(e.target.value) })}
          />
        </label>
        <label className="operator-alarm-bar-toggle">
          <input
            type="checkbox"
            checked={config.soundEnabled !== false}
            disabled={disabled}
            onChange={(e) => patch({ soundEnabled: e.target.checked })}
          />
          {t("alarmBarEditor.soundEnabled")}
        </label>
      </div>

      <div className="operator-alarm-bar-rules">
        <div className="operator-alarm-bar-rules-head">
          <strong>{t("alarmBarEditor.rulesTitle")}</strong>
          {!disabled && (
            <button type="button" className="btn small" onClick={addRule}>
              {t("alarmBarEditor.addRule")}
            </button>
          )}
        </div>
        {rules.length === 0 && (
          <p className="hint">{t("alarmBarEditor.noRulesHint")}</p>
        )}
        {rules.map((rule, index) => (
          <div key={rule.id} className="operator-alarm-bar-rule-card">
            <div className="operator-alarm-bar-rule-head">
              <strong>{rule.id}</strong>
              {!disabled && (
                <button type="button" className="btn small" onClick={() => removeRule(index)}>
                  {t("common:action.delete")}
                </button>
              )}
            </div>
            <div className="form-grid compact">
              <label>
                {t("alarmBarEditor.ruleId")}
                <input
                  value={rule.id}
                  disabled={disabled}
                  onChange={(e) => patchRule(index, { id: e.target.value })}
                />
              </label>
              <label>
                {t("alarmBarEditor.eventNames")}
                <input
                  value={rule.eventNames?.join(", ") ?? ""}
                  disabled={disabled}
                  placeholder="fireAlarm, gasLeak"
                  onChange={(e) =>
                    patchRule(index, {
                      eventNames: e.target.value
                        .split(",")
                        .map((item) => item.trim())
                        .filter(Boolean),
                    })
                  }
                />
              </label>
              <label>
                {t("alarmBarEditor.objectPathPrefix")}
                <input
                  value={rule.objectPathPrefix ?? ""}
                  disabled={disabled}
                  onChange={(e) => patchRule(index, { objectPathPrefix: e.target.value || undefined })}
                />
              </label>
              <label>
                {t("alarmBarEditor.minLevel")}
                <select
                  value={rule.minLevel ?? ""}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, {
                      minLevel: e.target.value ? (e.target.value as EventLevel) : undefined,
                    })
                  }
                >
                  <option value="">{t("alarmBarEditor.globalLevel")}</option>
                  {EVENT_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </select>
              </label>
              <label className="full">
                {t("alarmBarEditor.titleTemplate")}
                <input
                  value={rule.title ?? ""}
                  disabled={disabled}
                  placeholder="{{eventName}}"
                  onChange={(e) => patchRule(index, { title: e.target.value || undefined })}
                />
              </label>
              <label>
                {t("alarmBarEditor.dashboard")}
                <select
                  value={rule.actions?.dashboardPath ?? ""}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, {
                      actions: { ...rule.actions, dashboardPath: e.target.value || undefined },
                    })
                  }
                >
                  <option value="">{t("alarmBarEditor.auto")}</option>
                  {dashboardPaths.map((path) => (
                    <option key={path} value={path}>
                      {path}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                {t("alarmBarEditor.selectionKey")}
                <input
                  value={rule.actions?.selectionKey ?? "objectPath"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, {
                      actions: { ...rule.actions, selectionKey: e.target.value || undefined },
                    })
                  }
                />
              </label>
              <label>
                {t("alarmBarEditor.ackFunction")}
                <input
                  value={rule.actions?.acknowledgeFunction ?? ""}
                  disabled={disabled}
                  placeholder="acknowledgeAlarm"
                  onChange={(e) =>
                    patchRule(index, {
                      actions: { ...rule.actions, acknowledgeFunction: e.target.value || undefined },
                    })
                  }
                />
              </label>
              <label>
                {t("alarmBarEditor.bgColor")}
                <input
                  type="color"
                  value={rule.colors?.background ?? "#450a0a"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, { colors: { ...rule.colors, background: e.target.value } })
                  }
                />
              </label>
              <label>
                {t("alarmBarEditor.textColor")}
                <input
                  type="color"
                  value={rule.colors?.text ?? "#fee2e2"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, { colors: { ...rule.colors, text: e.target.value } })
                  }
                />
              </label>
              <label>
                {t("alarmBarEditor.borderColor")}
                <input
                  type="color"
                  value={rule.colors?.border ?? "#ef4444"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, { colors: { ...rule.colors, border: e.target.value } })
                  }
                />
              </label>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
