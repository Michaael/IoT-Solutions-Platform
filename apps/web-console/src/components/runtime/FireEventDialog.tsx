import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fireEvent } from "../../api";
import type { EventDescriptor } from "../../types";
import type { DataRecord, DataSchema } from "../../types";

interface FireEventDialogProps {
  objectPath: string;
  event: EventDescriptor;
  onClose: () => void;
  onFired: () => void;
}

function defaultPayloadJson(schema: DataSchema): string {
  return JSON.stringify({ schema, rows: [] }, null, 2);
}

function isEmptyPayload(payload: DataRecord): boolean {
  if (!payload.rows || payload.rows.length === 0) {
    return true;
  }
  return payload.rows.every((row) => Object.keys(row).length === 0);
}

export default function FireEventDialog({ objectPath, event, onClose, onFired }: FireEventDialogProps) {
  const { t } = useTranslation(["runtime", "common"]);
  const [payloadJson, setPayloadJson] = useState(() => defaultPayloadJson(event.payloadSchema));
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => {
      let payload: DataRecord | undefined;
      const trimmed = payloadJson.trim();
      if (trimmed) {
        try {
          payload = JSON.parse(trimmed) as DataRecord;
        } catch {
          throw new Error(t("common:error.invalidJsonPayload"));
        }
        if (isEmptyPayload(payload)) {
          payload = undefined;
        }
      }
      return fireEvent(objectPath, event.name, payload);
    },
    onSuccess: () => {
      onFired();
      onClose();
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <header className="modal-head">
          <h3>{t("runtime:fireEvent.title")}</h3>
          <button type="button" className="btn small" onClick={onClose}>×</button>
        </header>
        <div className="modal-body">
          <p className="hint">
            {t("runtime:fireEvent.object")} <code>{objectPath}</code>
          </p>
          <p className="hint">
            {t("runtime:fireEvent.event")} <code>{event.name}</code> ({event.level})
          </p>
          <label className="full">
            {t("runtime:fireEvent.payload")}
            <textarea
              rows={8}
              value={payloadJson}
              onChange={(e) => setPayloadJson(e.target.value)}
              spellCheck={false}
            />
          </label>
          <p className="hint">{t("runtime:fireEvent.payloadHint")}</p>
          {error && <p className="hint error">{error}</p>}
        </div>
        <footer className="modal-foot">
          <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? t("runtime:fireEvent.publishing") : t("runtime:fireEvent.publish")}
          </button>
        </footer>
      </div>
    </div>
  );
}
