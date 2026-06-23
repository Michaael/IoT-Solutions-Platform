import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchDataSource, updateDataSource } from "../../api/platformSql";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";

interface DataSourceEditorProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
}

export default function DataSourceEditor({ path, onClose, onOpenProperties }: DataSourceEditorProps) {
  const { t } = useTranslation(["platform", "common"]);
  const queryClient = useQueryClient();
  const dataQuery = useQuery({
    queryKey: ["data-source", path],
    queryFn: () => fetchDataSource(path),
  });

  const [displayName, setDisplayName] = useState("");
  const [schemaName, setSchemaName] = useState("");
  const [description, setDescription] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (!dataQuery.data) {
      return;
    }
    setDisplayName(dataQuery.data.variableDisplayName || dataQuery.data.displayName);
    setSchemaName(dataQuery.data.schemaName);
    setDescription(dataQuery.data.description ?? "");
  }, [dataQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateDataSource(path, {
        displayName: displayName.trim(),
        schemaName: schemaName.trim(),
        description,
      }),
    onSuccess: async () => {
      setSaveError(null);
      await queryClient.invalidateQueries({ queryKey: ["data-source", path] });
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  if (dataQuery.isLoading) {
    return <p className="hint">{t("platform:dataSource.loading")}</p>;
  }

  if (dataQuery.error) {
    return <div className="op-alert op-alert-error">{String(dataQuery.error)}</div>;
  }

  return (
    <PlatformSqlEditorShell
      title={displayName || dataQuery.data?.displayName || t("platform:dataSource.title")}
      subtitle={t("platform:dataSource.subtitle")}
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={
        <button
          type="button"
          className="btn primary"
          disabled={saveMutation.isPending || !schemaName.trim()}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
        </button>
      }
    >
      <form
        className="form-grid report-builder-form"
        onSubmit={(e) => {
          e.preventDefault();
          saveMutation.mutate();
        }}
      >
        <label>
          {t("platform:dataSource.displayName")}
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
        <label>
          {t("platform:dataSource.schema")}
          <input
            value={schemaName}
            onChange={(e) => setSchemaName(e.target.value)}
            required
            placeholder={t("platform:dataSource.schemaPlaceholder")}
          />
        </label>
        <label className="full">
          {t("common:field.description")}
          <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        {saveError && <p className="hint error full">{saveError}</p>}
        {saveMutation.isSuccess && <p className="hint full">{t("common:action.saved")}</p>}
      </form>
    </PlatformSqlEditorShell>
  );
}
