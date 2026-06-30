import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import {
  downloadBrickExport,
  downloadHaystackExport,
  exportHaystackModel,
} from "../../api/semanticExport";
import { ObjectPathField } from "../../ui";

const PREVIEW_MAX_CHARS = 4000;

export default function SemanticExportPanel() {
  const { t } = useTranslation("system");
  const [rootPath, setRootPath] = useState("root.platform.devices");
  const [includePoints, setIncludePoints] = useState(true);
  const [preview, setPreview] = useState<string | null>(null);
  const [previewTruncated, setPreviewTruncated] = useState(false);

  const previewMutation = useMutation({
    mutationFn: () => exportHaystackModel({ rootPath, includePoints }),
    onSuccess: (data) => {
      const text = JSON.stringify(data, null, 2);
      setPreviewTruncated(text.length > PREVIEW_MAX_CHARS);
      setPreview(text.slice(0, PREVIEW_MAX_CHARS));
    },
  });

  const haystackMutation = useMutation({
    mutationFn: () => downloadHaystackExport({ rootPath, includePoints }),
  });

  const brickJsonMutation = useMutation({
    mutationFn: () => downloadBrickExport("jsonld", { rootPath, includePoints }),
  });

  const brickTurtleMutation = useMutation({
    mutationFn: () => downloadBrickExport("turtle", { rootPath, includePoints }),
  });

  const error =
    haystackMutation.error
    ?? brickJsonMutation.error
    ?? brickTurtleMutation.error
    ?? previewMutation.error;

  return (
    <section className="system-panel semantic-export-panel">
      <header>
        <h3>{t("semanticExport.title")}</h3>
        <p className="op-muted">{t("semanticExport.subtitle")}</p>
      </header>

      <div className="form-grid semantic-export-options">
        <ObjectPathField
          className="full"
          label={t("semanticExport.rootPath")}
          value={rootPath}
          onChange={setRootPath}
          placeholder="root.platform.devices"
        />
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={includePoints}
            onChange={(e) => setIncludePoints(e.target.checked)}
          />
          {t("semanticExport.includePoints")}
        </label>
      </div>

      <div className="semantic-export-actions">
        <div className="form-actions semantic-export-preview-actions">
          <button
            type="button"
            className="btn"
            disabled={previewMutation.isPending}
            onClick={() => previewMutation.mutate()}
          >
            {previewMutation.isPending ? t("semanticExport.previewing") : t("semanticExport.preview")}
          </button>
        </div>
        <div className="form-actions semantic-export-download-actions">
          <button
            type="button"
            className="btn primary"
            disabled={haystackMutation.isPending}
            onClick={() => haystackMutation.mutate()}
          >
            {haystackMutation.isPending ? t("semanticExport.exporting") : t("semanticExport.haystackJson")}
          </button>
          <button
            type="button"
            className="btn"
            disabled={brickJsonMutation.isPending}
            onClick={() => brickJsonMutation.mutate()}
          >
            {brickJsonMutation.isPending ? t("semanticExport.exporting") : t("semanticExport.brickJsonLd")}
          </button>
          <button
            type="button"
            className="btn"
            disabled={brickTurtleMutation.isPending}
            onClick={() => brickTurtleMutation.mutate()}
          >
            {brickTurtleMutation.isPending ? t("semanticExport.exporting") : t("semanticExport.brickTurtle")}
          </button>
        </div>
      </div>

      {error && <div className="op-alert op-alert-error">{String(error)}</div>}
      {(haystackMutation.isSuccess || brickJsonMutation.isSuccess || brickTurtleMutation.isSuccess) && (
        <div className="op-alert op-alert-success">{t("semanticExport.downloadStarted")}</div>
      )}

      {preview && (
        <div className="semantic-export-preview-block panel-card">
          <h4>{t("semanticExport.preview")}</h4>
          {previewTruncated && (
            <p className="op-muted">{t("semanticExport.previewTruncated")}</p>
          )}
          <pre className="semantic-export-preview mono small">{preview}</pre>
        </div>
      )}
    </section>
  );
}
