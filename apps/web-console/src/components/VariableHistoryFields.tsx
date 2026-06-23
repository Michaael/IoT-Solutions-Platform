import { useTranslation } from "react-i18next";
import i18n from "../i18n";

export interface VariableHistoryState {
  historyEnabled: boolean;
  historyRetentionDays: number | null;
}

interface VariableHistoryFieldsProps {
  value: VariableHistoryState;
  onChange: (next: VariableHistoryState) => void;
  disabled?: boolean;
  idPrefix?: string;
}

export function formatHistoryRetention(days: number | null | undefined): string {
  if (days == null || days <= 0) {
    return i18n.t("inspector:variables.historyRetentionPlatform");
  }
  return i18n.t("inspector:variables.historyRetentionDaysShort", { count: days });
}

export default function VariableHistoryFields({
  value,
  onChange,
  disabled = false,
  idPrefix = "var-history",
}: VariableHistoryFieldsProps) {
  const { t } = useTranslation("inspector");
  const retentionId = `${idPrefix}-retention`;

  return (
    <div className="variable-history-fields">
      <label className="checkbox-label">
        <input
          type="checkbox"
          checked={value.historyEnabled}
          disabled={disabled}
          onChange={(e) =>
            onChange({
              ...value,
              historyEnabled: e.target.checked,
            })
          }
        />
        {t("variables.storeHistory")}
      </label>
      <label htmlFor={retentionId}>
        {t("variables.retentionDays")}
        <input
          id={retentionId}
          type="number"
          min={1}
          max={3650}
          placeholder={t("variables.retentionPlaceholder")}
          disabled={disabled || !value.historyEnabled}
          value={value.historyRetentionDays ?? ""}
          onChange={(e) => {
            const raw = e.target.value.trim();
            onChange({
              ...value,
              historyRetentionDays: raw === "" ? null : Number.parseInt(raw, 10),
            });
          }}
        />
      </label>
      <p className="hint">
        {t("variables.retentionHint")}
      </p>
    </div>
  );
}
