import { useMutation } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  previewFederationCatalogSync,
  syncFederationCatalog,
  type CatalogSyncConflict,
  type CatalogSyncPreview,
  type CatalogSyncResolution,
  type CatalogSyncResolutionAction,
} from "../../api/federation";

interface FederationCatalogSyncDialogProps {
  peerId: string;
  peerName: string;
  onClose: () => void;
  onSynced: (message: string) => void;
  onError: (message: string) => void;
}

export default function FederationCatalogSyncDialog({
  peerId,
  peerName,
  onClose,
  onSynced,
  onError,
}: FederationCatalogSyncDialogProps) {
  const { t } = useTranslation(["federation", "common"]);
  const [preview, setPreview] = useState<CatalogSyncPreview | null>(null);
  const [actions, setActions] = useState<Record<string, CatalogSyncResolutionAction>>({});

  const previewMutation = useMutation({
    mutationFn: () => previewFederationCatalogSync(peerId),
    onSuccess: (data) => {
      setPreview(data);
      const defaults: Record<string, CatalogSyncResolutionAction> = {};
      for (const conflict of data.conflicts) {
        defaults[conflict.localPath] = "SKIP";
      }
      setActions(defaults);
    },
    onError: (error: Error) => onError(error.message),
  });

  const syncMutation = useMutation({
    mutationFn: (resolutions: CatalogSyncResolution[]) => syncFederationCatalog(peerId, resolutions),
    onSuccess: (result) => {
      onSynced(
        t("peers.syncResult", {
          localRoot: result.localRoot,
          created: result.created,
          updated: result.updated,
          remoteCount: result.remoteCount,
          skipped: result.skipped,
        })
      );
      onClose();
    },
    onError: (error: Error) => onError(error.message),
  });

  useEffect(() => {
    previewMutation.mutate();
  }, [peerId]);

  const conflictRows = preview?.conflicts ?? [];

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal federation-sync-dialog"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-head">
          <h3>{t("catalogSync.title", { peer: peerName })}</h3>
          <button type="button" className="btn icon" onClick={onClose} aria-label={t("common:action.close")}>
            ×
          </button>
        </header>
        {previewMutation.isPending && <p className="hint">{t("catalogSync.loading")}</p>}
        {preview && (
          <div className="modal-body">
            <p className="hint">
              {t("catalogSync.summary", {
                localRoot: preview.localRoot,
                createCount: preview.createCount,
                updateCount: preview.updateCount,
                remoteCount: preview.remoteCount,
                conflictCount: preview.conflicts.length,
              })}
            </p>
            {conflictRows.length > 0 ? (
              <div className="op-table-wrap">
                <table className="op-table federation-conflicts-table">
                  <thead>
                    <tr>
                      <th>{t("catalogSync.column.localPath")}</th>
                      <th>{t("catalogSync.column.type")}</th>
                      <th>{t("catalogSync.column.action")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {conflictRows.map((conflict: CatalogSyncConflict) => (
                      <tr key={conflict.localPath}>
                        <td>
                          <code>{conflict.localPath}</code>
                          <div className="op-muted">{conflict.localDisplayName}</div>
                        </td>
                        <td>{t(`catalogSync.conflict.${conflict.type}`)}</td>
                        <td>
                          <select
                            className="table-control"
                            value={actions[conflict.localPath] ?? "SKIP"}
                            onChange={(event) =>
                              setActions((current) => ({
                                ...current,
                                [conflict.localPath]: event.target.value as CatalogSyncResolutionAction,
                              }))
                            }
                          >
                            <option value="SKIP">{t("catalogSync.action.skip")}</option>
                            <option value="BIND">{t("catalogSync.action.bind")}</option>
                          </select>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="hint">{t("catalogSync.noConflicts")}</p>
            )}
          </div>
        )}
        <footer className="modal-foot">
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.cancel")}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!preview || syncMutation.isPending}
            onClick={() => {
              const resolutions = conflictRows.map((conflict) => ({
                localPath: conflict.localPath,
                action: actions[conflict.localPath] ?? "SKIP",
              }));
              syncMutation.mutate(resolutions);
            }}
          >
            {t("catalogSync.apply")}
          </button>
        </footer>
      </div>
    </div>
  );
}
