import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchMimic, saveMimicDiagram, saveMimicTitle } from "../../api";
import ScadaMimicEditor from "./ScadaMimicEditor";

interface MimicEditorPanelProps {
  path: string;
  title?: string;
  onClose: () => void;
}

export default function MimicEditorPanel({ path, title, onClose }: MimicEditorPanelProps) {
  const { t } = useTranslation("scada");
  const queryClient = useQueryClient();
  const mimicQuery = useQuery({
    queryKey: ["mimic", path],
    queryFn: () => fetchMimic(path),
  });

  const saveMutation = useMutation({
    mutationFn: (diagramJson: string) => saveMimicDiagram(path, diagramJson),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mimic", path] });
    },
  });

  if (mimicQuery.isLoading) {
    return <div className="loading">{t("editor.loading")}</div>;
  }

  if (mimicQuery.isError) {
    return <div className="error">{(mimicQuery.error as Error).message}</div>;
  }

  const displayTitle = title ?? mimicQuery.data?.title ?? path;

  return (
    <ScadaMimicEditor
      diagramJson={mimicQuery.data?.diagramJson ?? "{}"}
      onSave={(diagramJson) => {
        saveMutation.mutate(diagramJson);
        if (displayTitle !== mimicQuery.data?.title) {
          void saveMimicTitle(path, displayTitle);
        }
        onClose();
      }}
      onClose={onClose}
    />
  );
}
