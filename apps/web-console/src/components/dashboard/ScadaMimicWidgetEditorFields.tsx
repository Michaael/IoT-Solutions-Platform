import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchMimic, saveMimicDiagram } from "../../api";
import type { ScadaMimicWidget } from "../../types/dashboard";
import ScadaMimicEditor from "../scada/ScadaMimicEditor";
import { MINI_TEC_SLD_DOCUMENT_JSON } from "../../scada/templates/miniTecSld";
import { TRANSNEFT_OMSK_DOCUMENT_JSON, TRANSNEFT_OMSK_MIMIC_PATH } from "../../scada/templates/transneftOmskMimic";

interface ScadaMimicWidgetEditorFieldsProps {
  widget: ScadaMimicWidget;
  update: (patch: Partial<ScadaMimicWidget>) => void;
}

export default function ScadaMimicWidgetEditorFields({
  widget,
  update,
}: ScadaMimicWidgetEditorFieldsProps) {
  const { t } = useTranslation("widgets");
  const queryClient = useQueryClient();
  const [editorOpen, setEditorOpen] = useState(false);
  const mimicPath = widget.mimicPath?.trim() ?? "";

  const mimicQuery = useQuery({
    queryKey: ["mimic", mimicPath],
    queryFn: () => fetchMimic(mimicPath),
    enabled: Boolean(mimicPath),
  });

  const saveMimicMutation = useMutation({
    mutationFn: (diagramJson: string) => saveMimicDiagram(mimicPath, diagramJson),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mimic", mimicPath] });
      setEditorOpen(false);
    },
  });

  const editorDiagramJson =
    mimicPath && mimicQuery.data?.diagramJson
      ? mimicQuery.data.diagramJson
      : (widget.diagramJson ?? "{}");

  const handleSave = (diagramJson: string) => {
    if (mimicPath) {
      saveMimicMutation.mutate(diagramJson);
      return;
    }
    update({ diagramJson });
    setEditorOpen(false);
  };

  return (
    <>
      <label>
        {t("editor.scadaMimic.mimicPath")}
        <input
          type="text"
          value={widget.mimicPath ?? ""}
          onChange={(e) => update({ mimicPath: e.target.value || undefined })}
          placeholder="root.platform.mimics.my-mimic"
        />
      </label>
      {mimicPath ? (
        <p className="hint">{t("editor.scadaMimic.mimicPathHint")}</p>
      ) : (
        <p className="hint">{t("editor.scadaMimic.inlineHint")}</p>
      )}
      <label>
        {t("editor.scadaMimic.defaultZoom")}
        <input
          type="number"
          min={0.25}
          max={4}
          step={0.1}
          value={widget.defaultZoom ?? 1}
          onChange={(e) => update({ defaultZoom: Number(e.target.value) })}
        />
      </label>
      <label>
        <input
          type="checkbox"
          checked={widget.panEnabled !== false}
          onChange={(e) => update({ panEnabled: e.target.checked })}
        />
        {t("editor.scadaMimic.panEnabled")}
      </label>
      <div className="widget-editor-actions">
        <button
          type="button"
          onClick={() => setEditorOpen(true)}
          disabled={Boolean(mimicPath && mimicQuery.isLoading)}
        >
          {t("editor.scadaMimic.openEditor")}
        </button>
        <button
          type="button"
          onClick={() => update({ diagramJson: MINI_TEC_SLD_DOCUMENT_JSON, mimicPath: undefined })}
        >
          {t("editor.scadaMimic.insertMiniTecTemplate")}
        </button>
        <button
          type="button"
          onClick={() =>
            update({
              diagramJson: TRANSNEFT_OMSK_DOCUMENT_JSON,
              mimicPath: TRANSNEFT_OMSK_MIMIC_PATH,
            })
          }
        >
          {t("editor.scadaMimic.insertTransneftTemplate")}
        </button>
      </div>
      {saveMimicMutation.isError && (
        <p className="widget-message error">{(saveMimicMutation.error as Error).message}</p>
      )}
      {editorOpen && (
        <ScadaMimicEditor
          diagramJson={editorDiagramJson}
          onSave={handleSave}
          onClose={() => setEditorOpen(false)}
        />
      )}
    </>
  );
}
