import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import {
  setVariable,
  updateVariableDefinition,
  updateVariableHistory,
} from "../api";
import type { DataRecord, VariableDto } from "../types";
import DataRecordValueEditor from "./schema/DataRecordValueEditor";
import VariableHistoryFields, {
  type VariableHistoryState,
} from "./VariableHistoryFields";
import { cloneRecord, recordsEqual } from "../utils/record";

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
  const [record, setRecord] = useState<DataRecord>(() =>
    variable.value
      ? cloneRecord(variable.value)
      : { schema: { name: variable.name, fields: [] }, rows: [] }
  );
  const [readable, setReadable] = useState(variable.readable);
  const [writable, setWritable] = useState(variable.writable);
  const [history, setHistory] = useState<VariableHistoryState>(() => historyFromVariable(variable));
  const [showJson, setShowJson] = useState(false);
  const [jsonText, setJsonText] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);

  const initialHistory = useMemo(() => historyFromVariable(variable), [variable]);
  const initialRecord = useMemo(
    () =>
      variable.value
        ? cloneRecord(variable.value)
        : { schema: { name: variable.name, fields: [] }, rows: [] },
    [variable]
  );

  const canEditValue = variable.writable;
  const definitionDirty = canEditDefinition && (readable !== variable.readable || writable !== variable.writable);
  const historyDirty = canManageHistory && !historyEqual(history, initialHistory);
  const valueDirty = canEditValue && !recordsEqual(record, initialRecord);

  useEffect(() => {
    const next = variable.value
      ? cloneRecord(variable.value)
      : { schema: { name: variable.name, fields: [] }, rows: [] };
    setRecord(next);
    setJsonText(JSON.stringify(next, null, 2));
    setReadable(variable.readable);
    setWritable(variable.writable);
    setHistory(historyFromVariable(variable));
  }, [variable]);

  useEffect(() => {
    if (!showJson) {
      setJsonText(JSON.stringify(record, null, 2));
    }
  }, [record, showJson]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (canEditDefinition && definitionDirty) {
        await updateVariableDefinition(objectPath, variable.name, { readable, writable });
      }
      if (canManageHistory && historyDirty) {
        await updateVariableHistory(objectPath, variable.name, history);
      }
      if (canEditValue && valueDirty) {
        const payload = showJson ? (JSON.parse(jsonText) as DataRecord) : record;
        await setVariable(objectPath, variable.name, payload);
      }
    },
    onSuccess: onSaved,
  });

  function handleSave() {
    const hasChanges = definitionDirty || historyDirty || valueDirty;
    if (!hasChanges) {
      onClose();
      return;
    }
    try {
      if (canEditValue && valueDirty && showJson) {
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

  const hasListShape =
    record.schema.fields.some((f) => f.type === "RECORD_LIST") || record.rows.length > 1;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal wide variable-editor-modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        <section className="modal-section variable-meta-badges">
          <span className="property-badges">
            {variable.readable && <span className="badge">R</span>}
            {variable.writable && <span className="badge w">W</span>}
            {variable.historyEnabled && <span className="badge hist">H</span>}
          </span>
          {!variable.writable && (
            <p className="hint">{t("variables.computedSettingsHint")}</p>
          )}
        </section>

        {canEditDefinition && (
          <section className="modal-section form-grid">
            <h4 className="full">{t("variables.definitionSection")}</h4>
            <label className="checkbox-label inline">
              <input
                type="checkbox"
                checked={readable}
                onChange={(e) => setReadable(e.target.checked)}
              />
              {t("variables.readable")}
            </label>
            <label className="checkbox-label inline">
              <input
                type="checkbox"
                checked={writable}
                onChange={(e) => setWritable(e.target.checked)}
              />
              {t("variables.writable")}
            </label>
            <p className="hint full">{t("variables.schemaReadOnlyHint")}</p>
          </section>
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
            <div className="section-inline-tools">
              <h4>{t("variables.valuesSection")}</h4>
              <button
                type="button"
                className="btn tiny"
                onClick={() => setShowJson((v) => !v)}
              >
                JSON
              </button>
            </div>
            {showJson ? (
              <textarea
                className="json-editor"
                rows={14}
                value={jsonText}
                onChange={(e) => setJsonText(e.target.value)}
                spellCheck={false}
              />
            ) : (
              <DataRecordValueEditor
                record={record}
                onChange={setRecord}
                allowMultipleRows={hasListShape}
              />
            )}
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
            disabled={
              mutation.isPending ||
              (!definitionDirty && !historyDirty && !valueDirty)
            }
          >
            {t("common:action.save")}
          </button>
        </footer>
      </div>
    </div>
  );
}
