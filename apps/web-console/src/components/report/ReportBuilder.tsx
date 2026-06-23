import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteReportTemplate,
  downloadReportExportByPath,
  downloadReportTemplate,
  fetchReport,
  runReportByPath,
  saveReportDefinition,
  saveTreeVariablesReportDefinition,
  uploadReportTemplate,
  type ReportColumn,
  type ReportExportFormat,
  type SaveReportDefinitionPayload,
} from "../../api/reports";
import BffDataTable from "../operator/BffDataTable";
import ReportExportControls from "./ReportExportControls";
import PlatformSqlEditorShell from "../platform/PlatformSqlEditorShell";
import { useDataSourceOptions } from "../platform/useDataSourceOptions";
import { DATA_SOURCES_ROOT } from "../../utils/systemFolderConfig";
import {
  buildDefaultParameters,
  defaultParameterValues,
  inferTemplateFormat,
  isTreeVariablesReport,
  normalizeColumn,
  type ReportKind,
  validateColumns,
  validateParameters,
} from "./reportBuilderUtils";

function effectiveDataSourcePath(
  data?: { dataSourcePath?: string; legacyAppId?: string } | null,
  draftPath?: string
): string {
  const fromDraft = draftPath?.trim();
  if (fromDraft) return fromDraft;
  const fromReport = data?.dataSourcePath?.trim();
  if (fromReport) return fromReport;
  const legacy = data?.legacyAppId?.trim();
  if (legacy) return `${DATA_SOURCES_ROOT}.${legacy}`;
  return "";
}

interface ReportBuilderProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
  operatorMode?: boolean;
}

interface SqlDraft {
  title?: string;
  dataSourcePath?: string;
  query?: string;
  maxRows?: number;
  refreshIntervalMs?: number;
}

interface TreeDraft {
  title?: string;
  devicePathPattern?: string;
  variableName?: string;
  maxRows?: number;
  refreshIntervalMs?: number;
}

function ColumnsEditor({
  columns,
  onChange,
}: {
  columns: ReportColumn[];
  onChange: (columns: ReportColumn[]) => void;
}) {
  return (
    <div className="report-builder-columns full">
      <div className="report-builder-columns-head">
        <span>Колонки</span>
        <button
          type="button"
          className="btn small"
          onClick={() => onChange([...columns, { field: "", label: "" }])}
        >
          + Колонка
        </button>
      </div>
      {columns.length === 0 && <p className="hint">Нет колонок — добавьте field и label.</p>}
      <table className="report-builder-columns-table">
        <thead>
          <tr>
            <th>field</th>
            <th>label</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {columns.map((col, index) => (
            <tr key={index}>
              <td>
                <input
                  className="mono"
                  value={col.field}
                  onChange={(e) => {
                    const next = [...columns];
                    next[index] = { ...col, field: e.target.value };
                    onChange(next);
                  }}
                  placeholder="item_code"
                />
              </td>
              <td>
                <input
                  value={col.label}
                  onChange={(e) => {
                    const next = [...columns];
                    next[index] = { ...col, label: e.target.value };
                    onChange(next);
                  }}
                  placeholder="Код"
                />
              </td>
              <td>
                <button
                  type="button"
                  className="btn small danger"
                  onClick={() => onChange(columns.filter((_, i) => i !== index))}
                >
                  ×
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ParametersEditor({
  parameters,
  onChange,
}: {
  parameters: string[];
  onChange: (parameters: string[]) => void;
}) {
  return (
    <div className="report-builder-parameters">
      <div className="report-builder-columns-head">
        <span>Параметры SQL (?)</span>
        <button
          type="button"
          className="btn small"
          onClick={() => onChange([...parameters, ""])}
        >
          + Параметр
        </button>
      </div>
      {parameters.length === 0 && <p className="hint">Без параметров — статический SELECT.</p>}
      <ul className="report-builder-param-list">
        {parameters.map((name, index) => (
          <li key={index}>
            <input
              className="mono"
              value={name}
              onChange={(e) => {
                const next = [...parameters];
                next[index] = e.target.value;
                onChange(next);
              }}
              placeholder="status"
            />
            <button
              type="button"
              className="btn small danger"
              onClick={() => onChange(parameters.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function ReportBuilder({
  path,
  onClose,
  onOpenProperties,
  operatorMode = false,
}: ReportBuilderProps) {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<"data" | "template">("data");
  const [mode, setMode] = useState<"view" | "edit">(operatorMode ? "view" : "view");
  const [sqlDraft, setSqlDraft] = useState<SqlDraft | null>(null);
  const [treeDraft, setTreeDraft] = useState<TreeDraft | null>(null);
  const [editKind, setEditKind] = useState<ReportKind>("sql");
  const [parameters, setParameters] = useState<string[]>([]);
  const [columns, setColumns] = useState<ReportColumn[]>([]);
  const [defaultParamValues, setDefaultParamValues] = useState<Record<string, string>>({});
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [templateFormat, setTemplateFormat] = useState("xls");
  const [templateError, setTemplateError] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportBusy, setExportBusy] = useState(false);
  const [templateBusy, setTemplateBusy] = useState(false);
  const [previewRequested, setPreviewRequested] = useState(false);

  const reportQuery = useQuery({
    queryKey: ["report", path],
    queryFn: () => fetchReport(path),
  });

  const dataSourcesQuery = useDataSourceOptions();
  const reportKind: ReportKind = isTreeVariablesReport(reportQuery.data?.reportType)
    ? "tree-variables"
    : "sql";
  const activeKind = mode === "edit" ? editKind : reportKind;

  useEffect(() => {
    if (!reportQuery.data) return;
    const data = reportQuery.data;
    setColumns((data.columns ?? []).map(normalizeColumn));
    const paramNames = data.parameters ?? [];
    setParameters(paramNames);
    const defaults = defaultParameterValues(paramNames, data.defaultParameters);
    setDefaultParamValues(defaults);
    setParamValues(defaults);
    setEditKind(isTreeVariablesReport(data.reportType) ? "tree-variables" : "sql");
    setPreviewRequested(false);
  }, [reportQuery.data?.path, reportQuery.dataUpdatedAt]);

  const effectiveSql = useMemo(() => {
    const base = reportQuery.data;
    if (!base) return null;
    return {
      title: sqlDraft?.title ?? base.title,
      dataSourcePath: effectiveDataSourcePath(base, sqlDraft?.dataSourcePath),
      query: sqlDraft?.query ?? base.query ?? "",
      maxRows: sqlDraft?.maxRows ?? base.maxRows,
      refreshIntervalMs: sqlDraft?.refreshIntervalMs ?? base.refreshIntervalMs,
    };
  }, [reportQuery.data, sqlDraft]);

  const effectiveTree = useMemo(() => {
    const base = reportQuery.data;
    if (!base) return null;
    return {
      title: treeDraft?.title ?? base.title,
      devicePathPattern: treeDraft?.devicePathPattern ?? base.devicePathPattern ?? "",
      variableName: treeDraft?.variableName ?? base.variableName ?? "",
      maxRows: treeDraft?.maxRows ?? base.maxRows,
      refreshIntervalMs: treeDraft?.refreshIntervalMs ?? base.refreshIntervalMs,
    };
  }, [reportQuery.data, treeDraft]);

  const canRunSql = Boolean(effectiveSql?.query?.trim() && effectiveSql?.dataSourcePath);
  const canRunTree = Boolean(
    effectiveTree?.devicePathPattern?.trim() && effectiveTree?.variableName?.trim()
  );
  const canRun = activeKind === "tree-variables" ? canRunTree : canRunSql;
  const hasTemplate = Boolean(reportQuery.data?.hasTemplate);

  const runParameters = useMemo(() => {
    const params: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(paramValues)) {
      if (String(value).trim()) {
        params[key] = value;
      }
    }
    return params;
  }, [paramValues]);

  const exportParams = Object.fromEntries(
    Object.entries(paramValues).filter(([, value]) => String(value).trim())
  );

  const runEnabled =
    canRun &&
    activeTab === "data" &&
    (mode === "view" || previewRequested);

  const runQuery = useQuery({
    queryKey: [
      "report-run",
      path,
      runParameters,
      activeKind,
      effectiveSql?.query,
      effectiveSql?.dataSourcePath,
      effectiveTree?.devicePathPattern,
      effectiveTree?.variableName,
      previewRequested,
    ],
    queryFn: () => runReportByPath(path, runParameters),
    enabled: runEnabled,
    refetchInterval:
      mode === "view" && canRun && activeTab === "data"
        ? (activeKind === "tree-variables" ? effectiveTree : effectiveSql)?.refreshIntervalMs
        : false,
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!reportQuery.data) return;
      if (editKind === "tree-variables") {
        const columnError = validateColumns(columns);
        if (columnError) throw new Error(columnError);
        const devicePathPattern = (treeDraft?.devicePathPattern ?? reportQuery.data.devicePathPattern ?? "").trim();
        const variableName = (treeDraft?.variableName ?? reportQuery.data.variableName ?? "").trim();
        if (!devicePathPattern) throw new Error("Укажите devicePathPattern (префикс или glob пути устройств).");
        if (!variableName) throw new Error("Укажите variableName — имя переменной на устройствах.");
        return saveTreeVariablesReportDefinition(path, {
          title: treeDraft?.title ?? reportQuery.data.title,
          devicePathPattern,
          variableName,
          columns,
          maxRows: treeDraft?.maxRows ?? reportQuery.data.maxRows,
          refreshIntervalMs: treeDraft?.refreshIntervalMs ?? reportQuery.data.refreshIntervalMs,
        });
      }

      const dataSourcePath = effectiveDataSourcePath(reportQuery.data, sqlDraft?.dataSourcePath);
      const query = (sqlDraft?.query ?? reportQuery.data.query ?? "").trim();
      if (!dataSourcePath) {
        throw new Error(
          "Укажите data source — объект в root.platform.data-sources (импортируйте пакет или создайте вручную)."
        );
      }
      if (!query) {
        throw new Error("Укажите SQL-запрос (SELECT или WITH).");
      }
      const paramNames = parameters.map((p) => p.trim()).filter(Boolean);
      const paramError = validateParameters(paramNames);
      if (paramError) throw new Error(paramError);
      const columnError = validateColumns(columns);
      if (columnError) throw new Error(columnError);

      const payload: SaveReportDefinitionPayload = {
        title: sqlDraft?.title ?? reportQuery.data.title,
        dataSourcePath,
        query,
        parameters: paramNames,
        columns: columns.map(normalizeColumn),
        defaultParameters: buildDefaultParameters(paramNames, defaultParamValues),
        maxRows: sqlDraft?.maxRows ?? reportQuery.data.maxRows,
        refreshIntervalMs: sqlDraft?.refreshIntervalMs ?? reportQuery.data.refreshIntervalMs,
      };
      return saveReportDefinition(path, payload);
    },
    onSuccess: async () => {
      setSaveError(null);
      setSqlDraft(null);
      setTreeDraft(null);
      setPreviewRequested(false);
      await queryClient.invalidateQueries({ queryKey: ["report", path] });
      await queryClient.invalidateQueries({ queryKey: ["report-run", path] });
      setMode("view");
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  async function handleExport(format: ReportExportFormat) {
    setExportError(null);
    setExportBusy(true);
    try {
      await downloadReportExportByPath(path, format, exportParams);
    } catch (error) {
      setExportError((error as Error).message);
    } finally {
      setExportBusy(false);
    }
  }

  async function handleTemplateUpload(file: File) {
    setTemplateBusy(true);
    setTemplateError(null);
    const inferredFormat = inferTemplateFormat(file.name, templateFormat);
    setTemplateFormat(inferredFormat);
    try {
      await uploadReportTemplate(path, inferredFormat, file);
      await queryClient.invalidateQueries({ queryKey: ["report", path] });
    } catch (error) {
      setTemplateError((error as Error).message);
    } finally {
      setTemplateBusy(false);
    }
  }

  async function handleTemplateDelete() {
    setTemplateBusy(true);
    setTemplateError(null);
    try {
      await deleteReportTemplate(path);
      await queryClient.invalidateQueries({ queryKey: ["report", path] });
    } catch (error) {
      setTemplateError((error as Error).message);
    } finally {
      setTemplateBusy(false);
    }
  }

  const templateFieldHints = useMemo(() => {
    const cols = reportQuery.data?.columns ?? columns;
    if (cols.length === 0) {
      return ["DEVICEPATH", "VALUE"];
    }
    return cols.map((col) => col.field.toUpperCase());
  }, [reportQuery.data?.columns, columns]);

  const title =
    (activeKind === "tree-variables" ? effectiveTree?.title : effectiveSql?.title) ??
    reportQuery.data?.title ??
    path.split(".").pop() ??
    path;
  const isDirty = sqlDraft != null || treeDraft != null;

  const tableLabels = useMemo(() => {
    const labels: Record<string, string> = {};
    for (const col of runQuery.data?.columns ?? []) {
      labels[col.field] = col.label;
    }
    return labels;
  }, [runQuery.data?.columns]);

  const toolbar = (
    <>
      {!operatorMode && (
        <button
          type="button"
          className={`btn ${mode === "edit" ? "primary" : ""}`}
          onClick={() => {
            setMode(mode === "edit" ? "view" : "edit");
            setPreviewRequested(false);
          }}
        >
          {mode === "edit" ? "Просмотр" : "Редактировать"}
        </button>
      )}
      <button
        type="button"
        className="btn"
        onClick={() => {
          setPreviewRequested(true);
          void runQuery.refetch();
        }}
        disabled={!canRun || runQuery.isFetching}
      >
        {runQuery.isFetching ? "Выполнение…" : "Выполнить"}
      </button>
      <ReportExportControls
        disabled={!canRun}
        busy={exportBusy}
        onExport={handleExport}
      />
    </>
  );

  return (
    <PlatformSqlEditorShell
      title={title}
      path={path}
      subtitle={
        activeKind === "tree-variables"
          ? "Отчёт tree-variables — переменные объектов дерева"
          : "SQL-отчёт"
      }
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={toolbar}
    >
      {!canRun && !reportQuery.isLoading && (
        <div className="banner warning report-builder-banner">
          {activeKind === "tree-variables"
            ? "Укажите devicePathPattern и variableName в режиме «Редактировать», затем сохраните."
            : !effectiveSql?.dataSourcePath
              ? "Укажите data source (root.platform.data-sources.*) и SQL-запрос в режиме «Редактировать», затем сохраните."
              : "Укажите SQL-запрос (SELECT / WITH) в режиме «Редактировать», затем сохраните."}
        </div>
      )}

      <div className="report-tabs section-body">
        <button
          type="button"
          className={`btn ${activeTab === "data" ? "primary" : ""}`}
          onClick={() => setActiveTab("data")}
        >
          Данные
        </button>
        {!operatorMode && (
          <button
            type="button"
            className={`btn ${activeTab === "template" ? "primary" : ""}`}
            onClick={() => setActiveTab("template")}
          >
            Шаблон YARG
          </button>
        )}
      </div>

      {exportError && (
        <div className="banner error report-builder-banner">{exportError}</div>
      )}

      {!hasTemplate && canRun && activeTab === "data" && (
        <div className="banner report-builder-banner">
          PDF и XLSX с шаблоном .xls/.docx — через YARG; .xlsx-шаблон для таблицы пока ненадёжен (fallback на таблицу).
        </div>
      )}

      {activeTab === "template" && !operatorMode && (
        <div className="report-template-panel section-body">
          <p className="hint">
            Загрузите Excel/Word-шаблон с плейсхолдерами YARG: band <code>Band1</code>, поля{" "}
            <code>${"{Band1.FIELD}"}</code>. См.{" "}
            <a href="https://github.com/cuba-platform/yarg" target="_blank" rel="noreferrer">
              YARG docs
            </a>
            .
          </p>
          <p className="hint">
            Статус:{" "}
            {hasTemplate
              ? `шаблон загружен (${reportQuery.data?.templateFormat || "?"})`
              : "шаблон не задан — CSV, HTML и XLSX (таблица); PDF — таблица через LibreOffice"}
          </p>
          <p className="hint">
            Имена полей — <strong>верхний регистр</strong> колонок отчёта. Для Excel (.xls) в диапазоне{" "}
            <code>Band1</code> можно <code>${"{"}FIELD{"}"}</code> или <code>${"{"}Band1.FIELD{"}"}</code> (сервер
            перепишет для Excel). Для Word — <code>${"{"}Band1.FIELD{"}"}</code>:{" "}
            {templateFieldHints.map((field) => (
              <code key={field}>${"{"}Band1.{field}{"}"}</code>
            ))}
            . Формат файла — по расширению (<code>.xls</code>, <code>.xlsx</code>, <code>.docx</code>).
          </p>
          <label>
            Формат шаблона (подставится из расширения файла)
            <select value={templateFormat} onChange={(e) => setTemplateFormat(e.target.value)}>
              <option value="xls">xls (рекомендуется)</option>
              <option value="xlsx">xlsx</option>
              <option value="docx">docx</option>
              <option value="html">html</option>
            </select>
          </label>
          <label className="full">
            Файл шаблона
            <input
              type="file"
              accept=".xlsx,.xls,.docx,.doc,.html,.htm"
              disabled={templateBusy}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) void handleTemplateUpload(file);
                e.target.value = "";
              }}
            />
          </label>
          <div className="form-actions">
            {hasTemplate && (
              <>
                <button
                  type="button"
                  className="btn"
                  disabled={templateBusy}
                  onClick={() => void downloadReportTemplate(path)}
                >
                  Скачать шаблон
                </button>
                <button
                  type="button"
                  className="btn"
                  disabled={templateBusy}
                  onClick={() => void handleTemplateDelete()}
                >
                  Удалить шаблон
                </button>
              </>
            )}
          </div>
          {templateError && <div className="banner error">{templateError}</div>}
        </div>
      )}

      {activeTab === "data" && mode === "edit" && !operatorMode && (
        <div className="report-editor-form section-body form-grid">
          <label className="full report-builder-kind">
            Тип отчёта
            <select
              value={editKind}
              onChange={(e) => setEditKind(e.target.value as ReportKind)}
            >
              <option value="sql">SQL (report-v1)</option>
              <option value="tree-variables">tree-variables (tree-variables-report-v1)</option>
            </select>
          </label>

          <label>
            Заголовок
            <input
              value={
                editKind === "tree-variables"
                  ? (treeDraft?.title ?? reportQuery.data?.title ?? "")
                  : (sqlDraft?.title ?? reportQuery.data?.title ?? "")
              }
              onChange={(e) =>
                editKind === "tree-variables"
                  ? setTreeDraft((prev) => ({ ...prev, title: e.target.value }))
                  : setSqlDraft((prev) => ({ ...prev, title: e.target.value }))
              }
            />
          </label>

          {editKind === "sql" ? (
            <>
              <label>
                Data source *
                <select
                  value={sqlDraft?.dataSourcePath ?? effectiveDataSourcePath(reportQuery.data) ?? ""}
                  onChange={(e) =>
                    setSqlDraft((prev) => ({
                      ...prev,
                      dataSourcePath: e.target.value,
                    }))
                  }
                >
                  <option value="">— выберите —</option>
                  {(dataSourcesQuery.data ?? []).map((source) => (
                    <option key={source.path} value={source.path}>
                      {source.displayName} ({source.path})
                    </option>
                  ))}
                </select>
              </label>
              <p className="hint full">
                SQL выполняется в схеме data source. Импорт:{" "}
                <code>POST /api/v1/platform/packages/import</code>.
              </p>
              <label className="full report-builder-sql">
                SQL (SELECT / WITH)
                <textarea
                  className="mono report-builder-sql-input"
                  value={sqlDraft?.query ?? reportQuery.data?.query ?? ""}
                  onChange={(e) => setSqlDraft((prev) => ({ ...prev, query: e.target.value }))}
                />
              </label>
              <ParametersEditor
                parameters={parameters}
                onChange={(next) => {
                  setParameters(next);
                  const names = next.map((p) => p.trim()).filter(Boolean);
                  setDefaultParamValues((prev) => {
                    const merged: Record<string, string> = {};
                    for (const name of names) {
                      merged[name] = prev[name] ?? "";
                    }
                    return merged;
                  });
                }}
              />
              <div className="report-builder-default-params full">
                <span className="field-caption">Значения по умолчанию</span>
                {parameters.filter((p) => p.trim()).length === 0 && (
                  <p className="hint">Добавьте параметры выше.</p>
                )}
                <div className="report-params">
                  {parameters
                    .map((p) => p.trim())
                    .filter(Boolean)
                    .map((name) => (
                      <label key={name}>
                        {name}
                        <input
                          value={defaultParamValues[name] ?? ""}
                          onChange={(e) =>
                            setDefaultParamValues((prev) => ({ ...prev, [name]: e.target.value }))
                          }
                        />
                      </label>
                    ))}
                </div>
              </div>
            </>
          ) : (
            <>
              <label className="full">
                devicePathPattern *
                <input
                  className="mono"
                  value={treeDraft?.devicePathPattern ?? reportQuery.data?.devicePathPattern ?? ""}
                  onChange={(e) =>
                    setTreeDraft((prev) => ({ ...prev, devicePathPattern: e.target.value }))
                  }
                  placeholder="root.devices.* или root.devices.pump-*"
                />
              </label>
              <label>
                variableName *
                <input
                  className="mono"
                  value={treeDraft?.variableName ?? reportQuery.data?.variableName ?? ""}
                  onChange={(e) =>
                    setTreeDraft((prev) => ({ ...prev, variableName: e.target.value }))
                  }
                  placeholder="temperature"
                />
              </label>
              <p className="hint full">
                Собирает значение <code>variableName</code> со всех объектов, чей path совпадает с
                шаблоном (<code>*</code> и <code>?</code>).
              </p>
            </>
          )}

          <ColumnsEditor columns={columns} onChange={setColumns} />

          <label>
            maxRows
            <input
              type="number"
              value={
                editKind === "tree-variables"
                  ? (treeDraft?.maxRows ?? reportQuery.data?.maxRows ?? 1000)
                  : (sqlDraft?.maxRows ?? reportQuery.data?.maxRows ?? 1000)
              }
              onChange={(e) => {
                const maxRows = Number(e.target.value);
                if (editKind === "tree-variables") {
                  setTreeDraft((prev) => ({ ...prev, maxRows }));
                } else {
                  setSqlDraft((prev) => ({ ...prev, maxRows }));
                }
              }}
            />
          </label>
          <label>
            refreshIntervalMs
            <input
              type="number"
              value={
                editKind === "tree-variables"
                  ? (treeDraft?.refreshIntervalMs ?? reportQuery.data?.refreshIntervalMs ?? 30000)
                  : (sqlDraft?.refreshIntervalMs ?? reportQuery.data?.refreshIntervalMs ?? 30000)
              }
              onChange={(e) => {
                const refreshIntervalMs = Number(e.target.value);
                if (editKind === "tree-variables") {
                  setTreeDraft((prev) => ({ ...prev, refreshIntervalMs }));
                } else {
                  setSqlDraft((prev) => ({ ...prev, refreshIntervalMs }));
                }
              }}
            />
          </label>

          <div className="form-actions full">
            {mode === "edit" && (
              <button
                type="button"
                className="btn"
                disabled={!canRun}
                onClick={() => setPreviewRequested(true)}
              >
                Предпросмотр (сохранённое определение)
              </button>
            )}
            <button
              type="button"
              className="btn primary"
              disabled={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              Сохранить
            </button>
            {isDirty && (
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setSqlDraft(null);
                  setTreeDraft(null);
                  setPreviewRequested(false);
                  if (reportQuery.data) {
                    setColumns((reportQuery.data.columns ?? []).map(normalizeColumn));
                    setParameters(reportQuery.data.parameters ?? []);
                    setDefaultParamValues(
                      defaultParameterValues(
                        reportQuery.data.parameters ?? [],
                        reportQuery.data.defaultParameters
                      )
                    );
                    setEditKind(
                      isTreeVariablesReport(reportQuery.data.reportType) ? "tree-variables" : "sql"
                    );
                  }
                }}
              >
                Отмена
              </button>
            )}
          </div>
          {saveError && <div className="banner error full">{saveError}</div>}
          {saveMutation.isSuccess && <div className="banner success full">Сохранено</div>}
        </div>
      )}

      {activeTab === "data" &&
        activeKind === "sql" &&
        (reportQuery.data?.parameters ?? []).length > 0 && (
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

      {activeTab === "data" && (
        <div className="section-body report-builder-results">
          {reportQuery.isLoading && <p className="hint">Загрузка отчёта…</p>}
          {reportQuery.error && (
            <div className="banner error">{(reportQuery.error as Error).message}</div>
          )}
          {mode === "edit" && !previewRequested && !runQuery.data && (
            <p className="hint">
              В режиме редактирования нажмите «Предпросмотр» или «Выполнить» для запуска сохранённого
              определения.
            </p>
          )}
          {runQuery.error && <div className="banner error">{(runQuery.error as Error).message}</div>}
          {runQuery.data && (
            <>
              {runQuery.data.truncated && (
                <div className="banner warning">
                  Показаны первые {runQuery.data.rowCount} строк (truncated)
                </div>
              )}
              <BffDataTable
                rows={runQuery.data.rows}
                labels={tableLabels}
                emptyMessage="Нет строк"
              />
            </>
          )}
        </div>
      )}
    </PlatformSqlEditorShell>
  );
}
