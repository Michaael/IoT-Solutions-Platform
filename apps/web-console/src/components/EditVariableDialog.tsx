import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { setVariable, updateVariableHistory } from "../api";
import type { DataRecord, VariableDto } from "../types";
import VariableHistoryFields, {
  type VariableHistoryState,
} from "./VariableHistoryFields";

interface EditVariableDialogProps {
  objectPath: string;
  variable: VariableDto;
  canManageHistory?: boolean;
  canEditDefinition?: boolean;
  onClose: () => void;
  onSaved: () => void;
}

function historyFromVariable(variable: VariableDto): VariableHistoryState {
  return {
    historyEnabled: variable.historyEnabled ?? false,
    historyRetentionDays: variable.historyRetentionDays ?? null,
  };
}

function historyEqual(a: VariableHistoryState, b: VariableHistoryState): boolean {
  return (
    a.historyEnabled === b.historyEnabled &&
    a.historyRetentionDays === b.historyRetentionDays
  );
}

export default function EditVariableDialog({
  objectPath,
  variable,
  canManageHistory = true,
  canEditDefinition = false,
  onClose,
  onSaved,
}: EditVariableDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const [jsonText, setJsonText] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);
  const [history, setHistory] = useState<VariableHistoryState>(() => historyFromVariable(variable));
  const initialHistory = useMemo(() => historyFromVariable(variable), [variable]);

  const canEditValue = variable.writable;
  const historyDirty = !historyEqual(history, initialHistory);

  useEffect(() => {
    if (variable.value) {
      setJsonText(JSON.stringify(variable.value, null, 2));
    } else if (variable.value === null) {
      setJsonText('{"schema":{"name":"value","fields":[]},"rows":[]}');
    }
    setHistory(historyFromVariable(variable));
  }, [variable]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (canManageHistory && historyDirty) {
        await updateVariableHistory(objectPath, variable.name, history);
      }
      if (canEditValue) {
        const parsed = JSON.parse(jsonText) as DataRecord;
        await setVariable(objectPath, variable.name, parsed);
      }
    },
    onSuccess: onSaved,
  });

  function handleSave() {
    const hasChanges = canEditValue || (canManageHistory && historyDirty);
    if (!hasChanges) {
      onClose();
      return;
    }
    try {
      if (canEditValue) {
        JSON.parse(jsonText) as DataRecord;
      }
      setParseError(null);
      mutation.mutate();
    } catch {
      setParseError(t("common:error.invalidJson"));
    }
  }

  const title = canEditValue
    ? t("variables.editTitle", { name: variable.name })
    : t("variables.settingsTitle", { name: variable.name });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        {!variable.writable && (
          <p className="hint">{t("variables.computedSettingsHint")}</p>
        )}

        {canManageHistory && (
          <section className="modal-section">
            <h4>{t("objectEditor.historyBtn")}</h4>
            <VariableHistoryFields
              idPrefix={`edit-${variable.name}`}
              value={history}
              onChange={setHistory}
            />
          </section>
        )}

        {canEditValue && (
          <section className="modal-section">
            <p className="hint">{t("variables.dataRecordHint")}</p>
            <textarea
              className="json-editor"
              rows={14}
              value={jsonText}
              onChange={(e) => setJsonText(e.target.value)}
            />
          </section>
        )}

        {!canEditValue && !canManageHistory && !canEditDefinition && (
          <p className="hint">{t("variables.noActions")}</p>
        )}

        {parseError && <p className="hint error">{parseError}</p>}
        {mutation.error && <p className="hint error">{(mutation.error as Error).message}</p>}
        <footer>
          <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
          <button
            type="button"
            className="btn primary"
            onClick={handleSave}
            disabled={mutation.isPending || (!canEditValue && !historyDirty)}
          >
            {t("common:action.save")}
          </button>
        </footer>
      </div>
    </div>
  );
}
