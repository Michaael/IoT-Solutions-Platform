import { useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { invokeFunction } from "../../api";
import { proxyFederationFunctionInvoke } from "../../api/federation";
import type { DataRecord, FunctionDescriptor } from "../../types";
import DataRecordValueEditor from "../schema/DataRecordValueEditor";
import { emptyRecord } from "../../utils/record";
import { cloneSchema } from "../../utils/dataSchema";

interface InvokeFunctionDialogProps {
  objectPath: string;
  fn: FunctionDescriptor;
  onClose: () => void;
  onInvoked: () => void;
  federated?: boolean;
  federationPeerId?: string | null;
  federationRemotePath?: string | null;
}

function defaultInputRecord(fn: FunctionDescriptor): DataRecord {
  return emptyRecord(cloneSchema(fn.inputSchema));
}

function functionHasImplementation(fn: FunctionDescriptor): boolean {
  if (fn.sourceType === "java" || fn.sourceType === "script") {
    return Boolean(fn.sourceBody?.trim());
  }
  return Boolean(fn.sourceBody?.trim());
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
  federated = false,
  federationPeerId,
  federationRemotePath,
}: InvokeFunctionDialogProps) {
  const { t } = useTranslation(["runtime", "common", "inspector"]);
  const [record, setRecord] = useState<DataRecord>(() => defaultInputRecord(fn));
  const [showJson, setShowJson] = useState(false);
  const [inputJson, setInputJson] = useState(() => JSON.stringify(defaultInputRecord(fn), null, 2));
  const [resultJson, setResultJson] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const hasInputFields = useMemo(() => fn.inputSchema.fields.length > 0, [fn.inputSchema.fields.length]);

  const hasImplementation = useMemo(() => functionHasImplementation(fn), [fn]);

  const mutation = useMutation({
    mutationFn: () => {
      let input: DataRecord | undefined;
      if (hasInputFields) {
        try {
          input = showJson ? (JSON.parse(inputJson) as DataRecord) : record;
        } catch {
          throw new Error(t("common:error.invalidJsonInput"));
        }
        if (input && isEmptyInput(input)) {
          input = undefined;
        }
      }
      return federated && federationPeerId && federationRemotePath
        ? proxyFederationFunctionInvoke(
            federationPeerId,
            federationRemotePath,
            fn.name,
            input as Record<string, unknown> | undefined
          ).then((result) => result as unknown as DataRecord)
        : invokeFunction(objectPath, fn.name, input);
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
          {federated && (
            <p className="hint">{t("runtime:invokeFunction.federatedHint")}</p>
          )}
          {fn.description && <p className="hint">{fn.description}</p>}
          {hasInputFields ? (
            <>
              <div className="section-inline-tools">
                <span className="field-label">{t("runtime:invokeFunction.input")}</span>
                <button type="button" className="btn tiny" onClick={() => setShowJson((v) => !v)}>
                  JSON
                </button>
              </div>
              {showJson ? (
                <textarea
                  rows={8}
                  className="json-editor"
                  value={inputJson}
                  onChange={(e) => setInputJson(e.target.value)}
                  spellCheck={false}
                />
              ) : (
                <DataRecordValueEditor record={record} onChange={setRecord} />
              )}
              <p className="hint">{t("runtime:invokeFunction.inputHint")}</p>
            </>
          ) : (
            <p className="hint">{t("descriptor.emptyInputSchema")}</p>
          )}
          {!hasImplementation && (
            <p className="hint error">{t("descriptor.notInvocable")}</p>
          )}
          {error && <p className="hint error">{error}</p>}
          {resultJson && (
            <label className="full">
              {t("runtime:invokeFunction.result")}
              <textarea rows={8} readOnly value={resultJson} spellCheck={false} className="json-editor" />
            </label>
          )}
        </div>
        <footer className="modal-foot">
          <button type="button" className="btn" onClick={onClose}>{t("common:action.close")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={mutation.isPending || !hasImplementation}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? t("runtime:invokeFunction.invoking") : t("runtime:invokeFunction.invoke")}
          </button>
        </footer>
      </div>
    </div>
  );
}
