import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchVariables } from "../../api";
import { fetchSqlBinding, refreshSqlBinding, updateSqlBinding } from "../../api/platformSql";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";
import { useDataSourceOptions } from "./useDataSourceOptions";

const REFRESH_MODES = ["manual", "on_schedule", "on_function_success"] as const;

interface SqlBindingEditorProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
}

export default function SqlBindingEditor({ path, onClose, onOpenProperties }: SqlBindingEditorProps) {
  const { t } = useTranslation(["platform", "common"]);
  const queryClient = useQueryClient();
  const bindingQuery = useQuery({
    queryKey: ["sql-binding", path],
    queryFn: () => fetchSqlBinding(path),
  });
  const dataSourcesQuery = useDataSourceOptions();

  const [targetObjectPath, setTargetObjectPath] = useState("");
  const [variable, setVariable] = useState("value");
  const [dataSourcePath, setDataSourcePath] = useState("");
  const [query, setQuery] = useState("");
  const [valueField, setValueField] = useState("value");
  const [refresh, setRefresh] = useState<string>("manual");
  const [refreshIntervalMs, setRefreshIntervalMs] = useState(30_000);
  const [triggerObjectPath, setTriggerObjectPath] = useState("");
  const [triggerFunctionName, setTriggerFunctionName] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  useEffect(() => {
    if (!bindingQuery.data) {
      return;
    }
    const data = bindingQuery.data;
    setTargetObjectPath(data.targetObjectPath);
    setVariable(data.variable);
    setDataSourcePath(data.dataSourcePath);
    setQuery(data.query);
    setValueField(data.valueField);
    setRefresh(data.refresh);
    setRefreshIntervalMs(data.refreshIntervalMs);
    setTriggerObjectPath(data.triggerObjectPath);
    setTriggerFunctionName(data.triggerFunctionName);
    setEnabled(data.enabled);
  }, [bindingQuery.data]);

  const targetVarsQuery = useQuery({
    queryKey: ["variables", targetObjectPath],
    queryFn: () => fetchVariables(targetObjectPath),
    enabled: Boolean(targetObjectPath.trim()),
  });

  const targetPreview = targetVarsQuery.data?.find((v) => v.name === variable);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateSqlBinding(path, {
        targetObjectPath: targetObjectPath.trim(),
        variable: variable.trim(),
        dataSourcePath: dataSourcePath.trim(),
        query,
        valueField: valueField.trim() || "value",
        refresh,
        refreshIntervalMs,
        triggerObjectPath: triggerObjectPath.trim(),
        triggerFunctionName: triggerFunctionName.trim(),
        enabled,
      }),
    onSuccess: async () => {
      setSaveError(null);
      await queryClient.invalidateQueries({ queryKey: ["sql-binding", path] });
      if (targetObjectPath.trim()) {
        await queryClient.invalidateQueries({ queryKey: ["variables", targetObjectPath] });
      }
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  const refreshMutation = useMutation({
    mutationFn: () => refreshSqlBinding(path),
    onSuccess: async () => {
      setRefreshError(null);
      await queryClient.invalidateQueries({ queryKey: ["sql-binding", path] });
      if (targetObjectPath.trim()) {
        await queryClient.invalidateQueries({ queryKey: ["variables", targetObjectPath] });
      }
    },
    onError: (error: Error) => setRefreshError(error.message),
  });

  if (bindingQuery.isLoading) {
    return <p className="hint">{t("platform:sqlBinding.loading")}</p>;
  }

  if (bindingQuery.error) {
    return <div className="op-alert op-alert-error">{String(bindingQuery.error)}</div>;
  }

  return (
    <PlatformSqlEditorShell
      title={bindingQuery.data?.bindingId ?? t("platform:sqlBinding.title")}
      subtitle={t("platform:sqlBinding.subtitle")}
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={
        <>
          <button
            type="button"
            className="btn primary"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
          </button>
          <button
            type="button"
            className="btn"
            disabled={refreshMutation.isPending || !query.trim() || !dataSourcePath.trim()}
            onClick={() => refreshMutation.mutate()}
          >
            {refreshMutation.isPending ? t("platform:sqlBinding.refreshing") : t("platform:sqlBinding.refreshNow")}
          </button>
        </>
      }
    >
      <form
        className="form-grid report-builder-form"
        onSubmit={(e) => {
          e.preventDefault();
          saveMutation.mutate();
        }}
      >
        <label className="full">
          Target object path *
          <input
            value={targetObjectPath}
            onChange={(e) => setTargetObjectPath(e.target.value)}
            placeholder="root.platform.devices.demo-sensor-01"
            required
          />
        </label>
        <label>
          Variable *
          <input value={variable} onChange={(e) => setVariable(e.target.value)} required />
        </label>
        <label>
          Value field
          <input value={valueField} onChange={(e) => setValueField(e.target.value)} />
        </label>
        <label className="full">
          Data source *
          <select value={dataSourcePath} onChange={(e) => setDataSourcePath(e.target.value)} required>
            <option value="">{t("platform:sqlBinding.selectPlaceholder")}</option>
            {(dataSourcesQuery.data ?? []).map((ds) => (
              <option key={ds.path} value={ds.path}>
                {ds.displayName} ({ds.path})
              </option>
            ))}
          </select>
        </label>
        <label className="full">
          SQL query (SELECT) *
          <textarea
            className="mono"
            rows={8}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            spellCheck={false}
            required
          />
        </label>
        <label>
          Refresh mode
          <select value={refresh} onChange={(e) => setRefresh(e.target.value)}>
            {REFRESH_MODES.map((mode) => (
              <option key={mode} value={mode}>
                {mode}
              </option>
            ))}
          </select>
        </label>
        <label>
          Refresh interval (ms)
          <input
            type="number"
            min={1000}
            step={1000}
            value={refreshIntervalMs}
            onChange={(e) => setRefreshIntervalMs(Number(e.target.value) || 30_000)}
          />
        </label>
        <label className="full">
          Trigger object path
          <input
            value={triggerObjectPath}
            onChange={(e) => setTriggerObjectPath(e.target.value)}
            placeholder={t("platform:sqlBinding.triggerPlaceholder")}
          />
        </label>
        <label className="full">
          Trigger function name
          <input
            value={triggerFunctionName}
            onChange={(e) => setTriggerFunctionName(e.target.value)}
            placeholder={t("platform:sqlBinding.triggerPlaceholder")}
          />
        </label>
        <label className="full">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          {" "}Enabled
        </label>
        {bindingQuery.data?.lastRefreshedAt && (
          <p className="hint full">{t("platform:sqlBinding.lastRefreshed", { time: bindingQuery.data.lastRefreshedAt })}</p>
        )}
        {targetPreview?.value?.rows?.[0] && (
          <p className="hint full mono small">
            {t("platform:sqlBinding.currentValue", {
              variable,
              value: JSON.stringify(targetPreview.value.rows[0]),
            })}
          </p>
        )}
        {saveError && <p className="hint error full">{saveError}</p>}
        {refreshError && <p className="hint error full">{refreshError}</p>}
        {saveMutation.isSuccess && <p className="hint full">{t("common:action.saved")}</p>}
        {refreshMutation.isSuccess && <p className="hint full">{t("platform:sqlBinding.valueUpdated")}</p>}
      </form>
    </PlatformSqlEditorShell>
  );
}
