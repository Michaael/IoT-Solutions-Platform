import { useMutation } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { exportPlatformBackup, importPlatformBackup } from "../../api/platformBackup";

export default function PlatformBackupPanel() {
  const { t } = useTranslation("system");
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingJson, setPendingJson] = useState<string | null>(null);
  const [previewText, setPreviewText] = useState<string | null>(null);
  const exportLockRef = useRef(false);

  const exportMutation = useMutation({
    mutationFn: exportPlatformBackup,
    onSuccess: (data) => {
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `ispf-platform-backup-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-")}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
      setFeedback(t("backup.exportDone", { count: data.nodeCount ?? 0 }));
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
    onSettled: () => {
      exportLockRef.current = false;
    },
  });

  const startExport = () => {
    if (exportLockRef.current) return;
    exportLockRef.current = true;
    exportMutation.mutate(undefined);
  };

  const previewMutation = useMutation({
    mutationFn: (json: string) => importPlatformBackup(json, true),
    onSuccess: (result) => {
      setPreviewText(
        t("backup.previewResult", {
          nodeCount: result.preview.nodeCount,
          createCount: result.preview.createCount,
          updateCount: result.preview.updateCount,
          warnings: result.preview.warnings.length,
        })
      );
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
  });

  const importMutation = useMutation({
    mutationFn: (json: string) => importPlatformBackup(json, false),
    onSuccess: (result) => {
      setFeedback(
        t("backup.importDone", {
          created: result.created,
          updated: result.updated,
          skipped: result.skipped,
        })
      );
      setPendingJson(null);
      setPreviewText(null);
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <section className="panel-card platform-backup-panel">
      <h4>{t("backup.title")}</h4>
      <p className="hint">{t("backup.subtitle")}</p>
      {feedback && <div className="op-alert op-alert-success">{feedback}</div>}
      {error && <div className="op-alert op-alert-error">{error}</div>}
      {previewText && <div className="op-alert">{previewText}</div>}
      <div className="form-actions">
        <button
          type="button"
          className="btn primary"
          disabled={exportMutation.isPending}
          onClick={startExport}
        >
          {t("backup.export")}
        </button>
        <button type="button" className="btn" onClick={() => fileInputRef.current?.click()}>
          {t("backup.chooseFile")}
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="application/json,.json"
          hidden
          onChange={async (event) => {
            const file = event.target.files?.[0];
            event.target.value = "";
            if (!file) {
              return;
            }
            const text = await file.text();
            setPendingJson(text);
            previewMutation.mutate(text);
          }}
        />
        {pendingJson && (
          <button
            type="button"
            className="btn danger"
            disabled={importMutation.isPending || previewMutation.isPending}
            onClick={() => importMutation.mutate(pendingJson)}
          >
            {t("backup.importApply")}
          </button>
        )}
      </div>
    </section>
  );
}
