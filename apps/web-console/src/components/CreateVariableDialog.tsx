import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { createVariable, type CreateVariablePayload } from "../api";
import type { DataRecord, DataSchema } from "../types";
import DataSchemaEditor from "./schema/DataSchemaEditor";
import DataRecordValueEditor from "./schema/DataRecordValueEditor";
import VariableHistoryFields, { type VariableHistoryState } from "./VariableHistoryFields";
import { emptySchema, syncRecordSchema } from "../utils/dataSchema";

interface CreateVariableDialogProps {
  objectPath: string;
  onClose: () => void;
  onSaved: () => void;
}

export default function CreateVariableDialog({
  objectPath,
  onClose,
  onSaved,
}: CreateVariableDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const [name, setName] = useState("");
  const [readable, setReadable] = useState(true);
  const [writable, setWritable] = useState(false);
  const [schema, setSchema] = useState<DataSchema>(emptySchema("value"));
  const [record, setRecord] = useState<DataRecord>(() => ({
    schema: emptySchema("value"),
    rows: [],
  }));
  const [history, setHistory] = useState<VariableHistoryState>({
    historyEnabled: false,
    historyRetentionDays: null,
  });
  const [setInitialValue, setSetInitialValue] = useState(false);

  const schemaName = useMemo(() => name.trim() || "value", [name]);

  function handleSchemaChange(next: DataSchema) {
    const named = { ...next, name: schemaName };
    setSchema(named);
    setRecord((prev) => syncRecordSchema(prev, named));
  }

  const mutation = useMutation({
    mutationFn: () => {
      const varName = name.trim();
      const finalSchema: DataSchema = { ...schema, name: varName || schema.name };
      const payload: CreateVariablePayload = {
        name: varName,
        schema: finalSchema,
        readable,
        writable,
        historyEnabled: history.historyEnabled,
        historyRetentionDays: history.historyRetentionDays,
      };
      if (setInitialValue && writable && record.rows.length > 0) {
        payload.initialValue = { schema: finalSchema, rows: record.rows };
      }
      return createVariable(objectPath, payload);
    },
    onSuccess: onSaved,
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal wide variable-editor-modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{t("variables.newTitle")}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        <section className="modal-section form-grid">
          <label className="full">
            {t("common:table.name")}
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              pattern="[A-Za-z_][A-Za-z0-9_]*"
              placeholder="myVariable"
              required
            />
          </label>
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
          <p className="hint full">{t("variables.computedHint")}</p>
        </section>

        <section className="modal-section">
          <h4>{t("variables.schemaSection")}</h4>
          <DataSchemaEditor
            value={{ ...schema, name: schemaName }}
            onChange={handleSchemaChange}
            idPrefix="create-var-schema"
          />
        </section>

        <section className="modal-section">
          <VariableHistoryFields
            idPrefix="create-var"
            value={history}
            onChange={setHistory}
          />
        </section>

        {writable && schema.fields.length > 0 && (
          <section className="modal-section">
            <label className="checkbox-label inline">
              <input
                type="checkbox"
                checked={setInitialValue}
                onChange={(e) => setSetInitialValue(e.target.checked)}
              />
              {t("variables.setInitialValue")}
            </label>
            {setInitialValue && (
              <DataRecordValueEditor record={record} onChange={setRecord} />
            )}
          </section>
        )}

        {mutation.error && (
          <p className="hint error">{(mutation.error as Error).message}</p>
        )}

        <footer>
          <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={!name.trim() || schema.fields.length === 0 || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {t("common:action.create")}
          </button>
        </footer>
      </div>
    </div>
  );
}
