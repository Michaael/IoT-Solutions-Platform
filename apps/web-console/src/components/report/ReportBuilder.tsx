import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  downloadReportCsvByPath,
  fetchReport,
  runReportByPath,
  saveReportDefinition,
  type ReportColumn,
  type SaveReportDefinitionPayload,
} from "../../api/reports";
import BffDataTable from "../operator/BffDataTable";

interface ReportBuilderProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
  operatorMode?: boolean;
}

function parseParametersText(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function parseColumnsJson(text: string): ReportColumn[] {
  const parsed = JSON.parse(text) as ReportColumn[];
  if (!Array.isArray(parsed)) {
    throw new Error("columns must be a JSON array");
  }
  return parsed;
}

export default function ReportBuilder({
  path,
  onClose,
  onOpenProperties,
  operatorMode = false,
}: ReportBuilderProps) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">(operatorMode ? "view" : "view");
  const [draft, setDraft] = useState<SaveReportDefinitionPayload | null>(null);
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [columnsText, setColumnsText] = useState("");
  const [parametersText, setParametersText] = useState("");
  const [defaultParametersText, setDefaultParametersText] = useState("{}");
  const [saveError, setSaveError] = useState<string | null>(null);

  const reportQuery = useQuery({
    queryKey: ["report", path],
    queryFn: () => fetchReport(path),
  });

  useEffect(() => {
    if (!reportQuery.data || draft) return;
    setColumnsText(JSON.stringify(reportQuery.data.columns ?? [], null, 2));
    setParametersText((reportQuery.data.parameters ?? []).join("\n"));
    setDefaultParametersText(JSON.stringify(reportQuery.data.defaultParameters ?? {}, null, 2));
    const defaults = reportQuery.data.defaultParameters ?? {};
    const nextParams: Record<string, string> = {};
    for (const name of reportQuery.data.parameters ?? []) {
      const value = defaults[name];
      nextParams[name] = value != null ? String(value) : "";
    }
    setParamValues(nextParams);
  }, [reportQuery.data, draft]);

  const effectiveDefinition = useMemo(() => {
    if (!reportQuery.data) return null;
    if (!draft) return reportQuery.data;
    return {
      ...reportQuery.data,
      title: draft.title ?? reportQuery.data.title,
      appId: draft.appId ?? reportQuery.data.appId,
      query: draft.query ?? reportQuery.data.query,
      maxRows: draft.maxRows ?? reportQuery.data.maxRows,
      refreshIntervalMs: draft.refreshIntervalMs ?? reportQuery.data.refreshIntervalMs,
    };
  }, [draft, reportQuery.data]);

  const runParameters = useMemo(() => {
    const params: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(paramValues)) {
      if (value.trim()) {
        params[key] = value;
      }
    }
    return params;
  }, [paramValues]);

  const effectiveAppId = effectiveDefinition?.appId?.trim() ?? "";
  const canRun = Boolean(effectiveDefinition?.query?.trim() && effectiveAppId);

  const runQuery = useQuery({
    queryKey: ["report-run", path, runParameters, effectiveDefinition?.query, effectiveAppId],
    queryFn: () => runReportByPath(path, runParameters),
    enabled: canRun,
    refetchInterval: mode === "view" && canRun ? effectiveDefinition?.refreshIntervalMs : false,
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!reportQuery.data) return;
      const appId = (draft?.appId ?? reportQuery.data.appId ?? "").trim();
      const query = (draft?.query ?? reportQuery.data.query ?? "").trim();
      if (!appId) {
        throw new Error("Укажите appId — id deploy-приложения, в схеме которого выполняется SQL (например demo).");
      }
      if (!query) {
        throw new Error("Укажите SQL-запрос (SELECT или WITH).");
      }
      const payload: SaveReportDefinitionPayload = {
        title: draft?.title ?? reportQuery.data.title,
        appId,
        query,
        parameters: parseParametersText(parametersText),
        columns: parseColumnsJson(columnsText),
        defaultParameters: JSON.parse(defaultParametersText) as Record<string, unknown>,
        maxRows: draft?.maxRows ?? reportQuery.data.maxRows,
        refreshIntervalMs: draft?.refreshIntervalMs ?? reportQuery.data.refreshIntervalMs,
      };
      return saveReportDefinition(path, payload);
    },
    onSuccess: async () => {
      setSaveError(null);
      setDraft(null);
      await queryClient.invalidateQueries({ queryKey: ["report", path] });
      await queryClient.invalidateQueries({ queryKey: ["report-run", path] });
      setMode("view");
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  const title = draft?.title ?? reportQuery.data?.title ?? path.split(".").pop() ?? path;
  const isDirty = draft != null;

  const tableLabels = useMemo(() => {
    const labels: Record<string, string> = {};
    for (const col of runQuery.data?.columns ?? []) {
      labels[col.field] = col.label;
    }
    return labels;
  }, [runQuery.data?.columns]);

  return (
    <div className="dashboard-builder report-builder">
      <header className="dashboard-builder-header">
        <div>
          <h2>{title}</h2>
          <p className="hint mono small">{path}</p>
        </div>
        <div className="dashboard-builder-actions">
          {!operatorMode && (
            <button
              type="button"
              className={`btn ${mode === "edit" ? "primary" : ""}`}
              onClick={() => setMode(mode === "edit" ? "view" : "edit")}
            >
              {mode === "edit" ? "Просмотр" : "Редактировать"}
            </button>
          )}
          <button
            type="button"
            className="btn"
            onClick={() => runQuery.refetch()}
            disabled={!canRun || runQuery.isFetching}
          >
            Обновить
          </button>
          <button
            type="button"
            className="btn"
            disabled={!canRun}
            onClick={() =>
              downloadReportCsvByPath(
                path,
                Object.fromEntries(
                  Object.entries(paramValues).filter(([, value]) => value.trim())
                )
              )
            }
          >
            CSV
          </button>
          {onOpenProperties && (
            <button type="button" className="btn" onClick={onOpenProperties}>
              Свойства
            </button>
          )}
          {onClose && (
            <button type="button" className="btn" onClick={onClose}>
              Закрыть
            </button>
          )}
        </div>
      </header>

      {!canRun && !reportQuery.isLoading && (
        <div className="banner warning">
          {!effectiveAppId
            ? "Укажите appId deploy-приложения (например demo) и SQL-запрос в режиме «Редактировать», затем сохраните."
            : "Укажите SQL-запрос (SELECT / WITH) в режиме «Редактировать», затем сохраните."}
        </div>
      )}

      {mode === "edit" && !operatorMode && (
        <div className="report-editor-form section-body">
          <label>
            Заголовок
            <input
              value={draft?.title ?? reportQuery.data?.title ?? ""}
              onChange={(e) =>
                setDraft((prev) => ({
                  query: prev?.query ?? reportQuery.data?.query ?? "",
                  title: e.target.value,
                  appId: prev?.appId ?? reportQuery.data?.appId,
                  maxRows: prev?.maxRows ?? reportQuery.data?.maxRows,
                  refreshIntervalMs: prev?.refreshIntervalMs ?? reportQuery.data?.refreshIntervalMs,
                }))
              }
            />
          </label>
          <label>
            appId *
            <input
              value={draft?.appId ?? reportQuery.data?.appId ?? ""}
              onChange={(e) =>
                setDraft((prev) => ({
                  query: prev?.query ?? reportQuery.data?.query ?? "",
                  appId: e.target.value,
                  title: prev?.title ?? reportQuery.data?.title,
                  maxRows: prev?.maxRows ?? reportQuery.data?.maxRows,
                  refreshIntervalMs: prev?.refreshIntervalMs ?? reportQuery.data?.refreshIntervalMs,
                }))
              }
              placeholder="demo"
            />
          </label>
          <p className="hint full">
            ID deploy-приложения из <code>root.platform.applications</code> — SQL выполняется в его схеме БД.
          </p>
          <label className="full">
            SQL (SELECT / WITH)
            <textarea
              rows={6}
              className="mono"
              value={draft?.query ?? reportQuery.data?.query ?? ""}
              onChange={(e) =>
                setDraft((prev) => ({
                  title: prev?.title ?? reportQuery.data?.title,
                  appId: prev?.appId ?? reportQuery.data?.appId,
                  query: e.target.value,
                  maxRows: prev?.maxRows ?? reportQuery.data?.maxRows,
                  refreshIntervalMs: prev?.refreshIntervalMs ?? reportQuery.data?.refreshIntervalMs,
                }))
              }
            />
          </label>
          <label>
            Параметры (по одному на строку)
            <textarea rows={3} className="mono" value={parametersText} onChange={(e) => setParametersText(e.target.value)} />
          </label>
          <label className="full">
            columns JSON
            <textarea rows={5} className="mono" value={columnsText} onChange={(e) => setColumnsText(e.target.value)} />
          </label>
          <label className="full">
            defaultParameters JSON
            <textarea
              rows={3}
              className="mono"
              value={defaultParametersText}
              onChange={(e) => setDefaultParametersText(e.target.value)}
            />
          </label>
          <label>
            maxRows
            <input
              type="number"
              value={draft?.maxRows ?? reportQuery.data?.maxRows ?? 1000}
              onChange={(e) =>
                setDraft((prev) => ({
                  query: prev?.query ?? reportQuery.data?.query ?? "",
                  title: prev?.title ?? reportQuery.data?.title,
                  appId: prev?.appId ?? reportQuery.data?.appId,
                  maxRows: Number(e.target.value),
                  refreshIntervalMs: prev?.refreshIntervalMs ?? reportQuery.data?.refreshIntervalMs,
                }))
              }
            />
          </label>
          <label>
            refreshIntervalMs
            <input
              type="number"
              value={draft?.refreshIntervalMs ?? reportQuery.data?.refreshIntervalMs ?? 30000}
              onChange={(e) =>
                setDraft((prev) => ({
                  query: prev?.query ?? reportQuery.data?.query ?? "",
                  title: prev?.title ?? reportQuery.data?.title,
                  appId: prev?.appId ?? reportQuery.data?.appId,
                  maxRows: prev?.maxRows ?? reportQuery.data?.maxRows,
                  refreshIntervalMs: Number(e.target.value),
                }))
              }
            />
          </label>
          <div className="form-actions">
            <button
              type="button"
              className="btn primary"
              disabled={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              Сохранить
            </button>
            {isDirty && (
              <button type="button" className="btn" onClick={() => setDraft(null)}>
                Отмена
              </button>
            )}
          </div>
          {saveError && <div className="banner error">{saveError}</div>}
          {saveMutation.isSuccess && <div className="banner success">Сохранено</div>}
        </div>
      )}

      {(reportQuery.data?.parameters ?? []).length > 0 && (
        <div className="report-params section-body">
          {(reportQuery.data?.parameters ?? []).map((name) => (
            <label key={name}>
              {name}
              <input
                value={paramValues[name] ?? ""}
                onChange={(e) => setParamValues((prev) => ({ ...prev, [name]: e.target.value }))}
              />
            </label>
          ))}
        </div>
      )}

      <div className="section-body">
        {reportQuery.isLoading && <p className="hint">Загрузка отчёта…</p>}
        {reportQuery.error && (
          <div className="banner error">{(reportQuery.error as Error).message}</div>
        )}
        {runQuery.error && <div className="banner error">{(runQuery.error as Error).message}</div>}
        {runQuery.data && (
          <>
            {runQuery.data.truncated && (
              <div className="banner warning">Показаны первые {runQuery.data.rowCount} строк (truncated)</div>
            )}
            <BffDataTable
              rows={runQuery.data.rows}
              labels={tableLabels}
              emptyMessage="Нет строк"
            />
          </>
        )}
      </div>
    </div>
  );
}
