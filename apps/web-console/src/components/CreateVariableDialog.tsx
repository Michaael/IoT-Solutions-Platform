import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { createVariable, type CreateVariablePayload } from "../api";
import type { DataSchema } from "../types";
import VariableHistoryFields, { type VariableHistoryState } from "./VariableHistoryFields";

interface CreateVariableDialogProps {
  objectPath: string;
  onClose: () => void;
  onSaved: () => void;
}

const DEFAULT_SCHEMA: DataSchema = {
  name: "value",
  fields: [{ name: "value", type: "STRING" }],
};

export default function CreateVariableDialog({
  objectPath,
  onClose,
  onSaved,
}: CreateVariableDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const [name, setName] = useState("");
  const [writable, setWritable] = useState(false);
  const [history, setHistory] = useState<VariableHistoryState>({
    historyEnabled: false,
    historyRetentionDays: null,
  });

  const mutation = useMutation({
    mutationFn: () => {
      const payload: CreateVariablePayload = {
        name: name.trim(),
        schema: { ...DEFAULT_SCHEMA, name: name.trim() || "value" },
        readable: true,
        writable,
        historyEnabled: history.historyEnabled,
        historyRetentionDays: history.historyRetentionDays,
      };
      return createVariable(objectPath, payload);
    },
    onSuccess: onSaved,
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
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
          <label className="checkbox-label inline full">
            <input
              type="checkbox"
              checked={writable}
              onChange={(e) => setWritable(e.target.checked)}
            />
            {t("variables.writable")}
          </label>
          <p className="hint full">
            {t("variables.computedHint")}
          </p>
          <div className="full">
            <VariableHistoryFields
              idPrefix="create-var"
              value={history}
              onChange={setHistory}
            />
          </div>
        </section>

        {mutation.error && (
          <p className="hint error">{(mutation.error as Error).message}</p>
        )}

        <footer>
          <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={!name.trim() || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {t("common:action.create")}
          </button>
        </footer>
      </div>
    </div>
  );
}
