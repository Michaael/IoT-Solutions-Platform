import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { exportPlatformBackup, importPlatformBackup } from "../api/platformBackup";

interface TreeSubtreeExportPanelProps {
  rootPath: string;
  canManage: boolean;
}

export default function TreeSubtreeExportPanel({ rootPath, canManage }: TreeSubtreeExportPanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [jsonText, setJsonText] = useState("");
  const [previewMessage, setPreviewMessage] = useState<string | null>(null);
  const exportLockRef = useRef(false);
  const downloadLockRef = useRef(false);
  const downloadTimerRef = useRef<number | null>(null);
  const [downloadLocked, setDownloadLocked] = useState(false);

  useEffect(() => () => {
    if (downloadTimerRef.current != null) window.clearTimeout(downloadTimerRef.current);
  }, []);

  const exportMutation = useMutation({
    mutationFn: () => exportPlatformBackup(rootPath),
    onSuccess: (payload) => {
      setJsonText(JSON.stringify(payload, null, 2));
      setPreviewMessage(null);
    },
    onSettled: () => {
      exportLockRef.current = false;
    },
  });

  const previewMutation = useMutation({
    mutationFn: () => importPlatformBackup(jsonText, true),
    onSuccess: (result) => {
      const preview = result.preview;
      setPreviewMessage(
        [
          t("treeExport.previewSummary", {
            nodeCount: preview.nodeCount,
            createCount: preview.createCount,
            updateCount: preview.updateCount,
          }),
          ...(preview.warnings ?? []),
        ].join("\n")
      );
    },
    onError: (error) => setPreviewMessage(String(error)),
  });

  const importMutation = useMutation({
    mutationFn: () => importPlatformBackup(jsonText, false),
    onSuccess: (result) => {
      setPreviewMessage(
        t("treeExport.importSummary", {
          created: result.created,
          updated: result.updated,
          skipped: result.skipped,
        })
      );
      void queryClient.invalidateQueries({ queryKey: ["objects"] });
      void queryClient.invalidateQueries({ queryKey: ["object-editor", rootPath] });
    },
    onError: (error) => setPreviewMessage(String(error)),
  });

  const downloadJson = () => {
    if (downloadLockRef.current || !jsonText.trim()) return;
    downloadLockRef.current = true;
    setDownloadLocked(true);
    const blob = new Blob([jsonText], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${rootPath.replace(/\./g, "_")}-export.json`;
    anchor.click();
    URL.revokeObjectURL(url);
    downloadTimerRef.current = window.setTimeout(() => {
      downloadLockRef.current = false;
      setDownloadLocked(false);
    }, 1000);
  };

  const startExport = () => {
    if (exportLockRef.current) return;
    exportLockRef.current = true;
    exportMutation.mutate();
  };

  return (
    <div className="tree-subtree-export-panel">
      <h3>{t("treeExport.title")}</h3>
      <p className="op-muted">{t("treeExport.subtitle", { rootPath })}</p>

      <div className="form-actions">
        <button
          type="button"
          className="btn"
          disabled={exportMutation.isPending}
          onClick={startExport}
        >
          {exportMutation.isPending ? t("treeExport.exporting") : t("treeExport.export")}
        </button>
        <button type="button" className="btn" disabled={!jsonText.trim() || downloadLocked} onClick={downloadJson}>
          {t("treeExport.download")}
        </button>
        {canManage && (
          <>
            <button
              type="button"
              className="btn"
              disabled={previewMutation.isPending || !jsonText.trim()}
              onClick={() => previewMutation.mutate()}
            >
              {previewMutation.isPending ? t("treeExport.previewing") : t("treeExport.preview")}
            </button>
            <button
              type="button"
              className="btn primary"
              disabled={importMutation.isPending || !jsonText.trim()}
              onClick={() => {
                if (!window.confirm(t("treeExport.importConfirm", { rootPath }))) {
                  return;
                }
                importMutation.mutate();
              }}
            >
              {importMutation.isPending ? t("treeExport.importing") : t("treeExport.import")}
            </button>
          </>
        )}
      </div>

      {exportMutation.error && (
        <div className="op-alert op-alert-error">{String(exportMutation.error)}</div>
      )}

      <label className="full">
        JSON
        <textarea
          className="mono"
          rows={12}
          value={jsonText}
          onChange={(event) => setJsonText(event.target.value)}
          spellCheck={false}
          readOnly={!canManage}
        />
      </label>

      {previewMessage && <pre className="mono small validation-output">{previewMessage}</pre>}
    </div>
  );
}
