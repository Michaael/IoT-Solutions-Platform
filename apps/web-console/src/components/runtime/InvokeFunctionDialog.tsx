import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { invokeFunction } from "../../api";
import type { DataRecord, DataSchema, FunctionDescriptor } from "../../types";

interface InvokeFunctionDialogProps {
  objectPath: string;
  fn: FunctionDescriptor;
  onClose: () => void;
  onInvoked: () => void;
}

function defaultInputJson(schema: DataSchema): string {
  return JSON.stringify({ schema, rows: [] }, null, 2);
}

function isEmptyInput(input: DataRecord): boolean {
  if (!input.rows || input.rows.length === 0) {
    return true;
  }
  return input.rows.every((row) => Object.keys(row).length === 0);
}

export default function InvokeFunctionDialog({
  objectPath,
  fn,
  onClose,
  onInvoked,
}: InvokeFunctionDialogProps) {
  const { t } = useTranslation(["runtime", "common"]);
  const [inputJson, setInputJson] = useState(() => defaultInputJson(fn.inputSchema));
  const [resultJson, setResultJson] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => {
      let input: DataRecord | undefined;
      const trimmed = inputJson.trim();
      if (trimmed) {
        try {
          input = JSON.parse(trimmed) as DataRecord;
        } catch {
          throw new Error(t("common:error.invalidJsonInput"));
        }
        if (isEmptyInput(input)) {
          input = undefined;
        }
      }
      return invokeFunction(objectPath, fn.name, input);
    },
    onSuccess: (result) => {
      setResultJson(JSON.stringify(result, null, 2));
      setError(null);
      onInvoked();
    },
    onError: (err: Error) => {
      setError(err.message);
      setResultJson(null);
    },
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <header className="modal-head">
          <h3>{t("runtime:invokeFunction.title")}</h3>
          <button type="button" className="btn small" onClick={onClose}>×</button>
        </header>
        <div className="modal-body">
          <p className="hint">
            <code>{objectPath}</code> → <code>{fn.name}</code>
          </p>
          {fn.description && <p className="hint">{fn.description}</p>}
          <label className="full">
            {t("runtime:invokeFunction.input")}
            <textarea
              rows={6}
              value={inputJson}
              onChange={(e) => setInputJson(e.target.value)}
              spellCheck={false}
            />
          </label>
          <p className="hint">{t("runtime:invokeFunction.inputHint")}</p>
          {error && <p className="hint error">{error}</p>}
          {resultJson && (
            <label className="full">
              {t("runtime:invokeFunction.result")}
              <textarea rows={8} readOnly value={resultJson} spellCheck={false} />
            </label>
          )}
        </div>
        <footer className="modal-foot">
          <button type="button" className="btn" onClick={onClose}>{t("common:action.close")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? t("runtime:invokeFunction.invoking") : t("runtime:invokeFunction.invoke")}
          </button>
        </footer>
      </div>
    </div>
  );
}
