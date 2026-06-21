import { useCallback, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteObject,
  fetchObjectAudit,
  fetchObjectEditor,
  setVariable,
  updateObject,
  updateVariableDefinition,
  updateVariableHistory,
} from "../api";
import type { ObjectEditorDto, DataRecord, VariableDto } from "../types";
import { fetchAuthMe } from "../api";
import { OBJECT_WS_EVENT, sendPresence, type ObjectWsMessage } from "../hooks/useObjectWebSocket";
import {
  cloneRecord,
  ensureRecord,
  recordsEqual,
  setFieldValue,
} from "../utils/record";
import IconPicker from "./icons/IconPicker";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";
import VariableFieldEditor from "./VariableFieldEditor";
import VariableHistoryFields, {
  formatHistoryRetention,
  type VariableHistoryState,
} from "./VariableHistoryFields";
import { canDeleteObjectPath } from "../utils/platformSystemPaths";
import BindingExpressionField from "./BindingExpressionField";
import CreateVariableDialog from "./CreateVariableDialog";
import ObjectFederationBindSection from "./ObjectFederationBindSection";

interface ObjectPropertiesEditorProps {
  path: string;
  onClose: () => void;
  onDeleted: () => void;
  canManage?: boolean;
}

type SectionKey = "info" | "federation" | "variables" | "events" | "functions" | "history";

interface EditorState {
  displayName: string;
  description: string;
  iconId: string | null;
  variables: Record<string, DataRecord>;
  variableHistory: Record<string, VariableHistoryState>;
  variableBindings: Record<string, string>;
}

function historyFromVariable(v: VariableDto): VariableHistoryState {
  return {
    historyEnabled: v.historyEnabled ?? false,
    historyRetentionDays: v.historyRetentionDays ?? null,
  };
}

function historyEqual(a: VariableHistoryState, b: VariableHistoryState): boolean {
  return (
    a.historyEnabled === b.historyEnabled &&
    a.historyRetentionDays === b.historyRetentionDays
  );
}

function buildState(data: ObjectEditorDto): EditorState {
  const variables: Record<string, DataRecord> = {};
  const variableHistory: Record<string, VariableHistoryState> = {};
  const variableBindings: Record<string, string> = {};
  for (const v of data.variables) {
    if (v.name === "uiIcon") {
      continue;
    }
    variables[v.name] = ensureRecord(v);
    variableHistory[v.name] = historyFromVariable(v);
    variableBindings[v.name] = v.bindingExpression ?? "";
  }
  return {
    displayName: data.object.displayName,
    description: data.object.description,
    iconId: data.object.iconId ?? null,
    variables,
    variableHistory,
    variableBindings,
  };
}

function VariableEditorRow({
  variable,
  record,
  baseline,
  history,
  historyBaseline,
  bindingExpression,
  bindingBaseline,
  onChange,
  onHistoryChange,
  onBindingChange,
}: {
  variable: VariableDto;
  record: DataRecord;
  baseline: DataRecord;
  history: VariableHistoryState;
  historyBaseline: VariableHistoryState;
  bindingExpression: string;
  bindingBaseline: string;
  onChange: (next: DataRecord) => void;
  onHistoryChange: (next: VariableHistoryState) => void;
  onBindingChange: (next: string) => void;
}) {
  const [showBinding, setShowBinding] = useState(false);
  const [showJson, setShowJson] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const dirty = !recordsEqual(record, baseline);
  const historyDirty = !historyEqual(history, historyBaseline);
  const bindingDirty = bindingExpression.trim() !== bindingBaseline.trim();
  const row = record.rows[0] ?? {};
  const disabled = !variable.writable || Boolean(bindingBaseline.trim());

  return (
    <article className={`property-card ${dirty || historyDirty || bindingDirty ? "dirty" : ""}`}>
      <header className="property-card-header">
        <div>
          <code className="property-name">{variable.name}</code>
          <span className="property-badges">
            {variable.readable && <span className="badge">R</span>}
            {variable.writable && <span className="badge w">W</span>}
            {variable.bindingExpression && <span className="badge b">Σ</span>}
            {history.historyEnabled && (
              <span className="badge hist" title={formatHistoryRetention(history.historyRetentionDays)}>
                H
              </span>
            )}
          </span>
        </div>
        <div className="property-card-tools">
          <button type="button" className="btn tiny" onClick={() => setShowHistory((v) => !v)}>
            История
          </button>
          <button type="button" className="btn tiny" onClick={() => setShowBinding((v) => !v)}>
            Привязка
          </button>
          <button type="button" className="btn tiny" onClick={() => setShowJson((v) => !v)}>
            JSON
          </button>
        </div>
      </header>

      {showHistory && (
        <div className="binding-panel">
          <VariableHistoryFields
            idPrefix={`prop-${variable.name}`}
            value={history}
            onChange={onHistoryChange}
          />
        </div>
      )}

      {showBinding && (
        <div className="binding-panel">
          <span className="field-label">Выражение привязки (CEL)</span>
          <BindingExpressionField
            value={bindingExpression}
            onChange={onBindingChange}
          />
        </div>
      )}

      {showJson ? (
        <textarea
          className="json-editor compact"
          rows={6}
          disabled={disabled}
          value={JSON.stringify(record, null, 2)}
          onChange={(e) => {
            try {
              onChange(JSON.parse(e.target.value));
            } catch {
              // ignore parse errors while typing
            }
          }}
        />
      ) : (
        <div className="property-fields">
          {record.schema.fields.map((field) => (
            <VariableFieldEditor
              key={field.name}
              field={field}
              value={row[field.name]}
              disabled={disabled}
              onChange={(val) => onChange(setFieldValue(record, field.name, val))}
            />
          ))}
          {record.schema.fields.length === 0 && (
            <p className="hint">Схема переменной пуста</p>
          )}
        </div>
      )}
    </article>
  );
}

export default function ObjectPropertiesEditor({
  path,
  onClose,
  onDeleted,
  canManage = false,
}: ObjectPropertiesEditorProps) {
  const queryClient = useQueryClient();
  const [openSections, setOpenSections] = useState<Record<SectionKey, boolean>>({
    info: true,
    federation: true,
    variables: true,
    events: true,
    functions: true,
    history: false,
  });
  const [revision, setRevision] = useState<number>(0);
  const [remoteRevision, setRemoteRevision] = useState<number | null>(null);
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [forceNextSave, setForceNextSave] = useState(false);

  const authQuery = useQuery({
    queryKey: ["auth-me"],
    queryFn: fetchAuthMe,
  });

  const auditQuery = useQuery({
    queryKey: ["object-audit", path],
    queryFn: () => fetchObjectAudit(path),
    enabled: openSections.history,
  });

  const editorQuery = useQuery({
    queryKey: ["object-editor", path],
    queryFn: () => fetchObjectEditor(path),
  });

  const [state, setState] = useState<EditorState | null>(null);
  const [baseline, setBaseline] = useState<EditorState | null>(null);
  const [showCreateVariable, setShowCreateVariable] = useState(false);

  useEffect(() => {
    if (editorQuery.data) {
      const next = buildState(editorQuery.data);
      setState(next);
      setBaseline(next);
      setRevision(editorQuery.data.object.revision ?? 0);
      setRemoteRevision(null);
      setConflictMessage(null);
    }
  }, [editorQuery.data]);

  const isDirty = useMemo(() => {
    if (!state || !baseline) return false;
    if (state.displayName !== baseline.displayName) return true;
    if (state.description !== baseline.description) return true;
    if (state.iconId !== baseline.iconId) return true;
    for (const name of Object.keys(state.variables)) {
      if (!recordsEqual(state.variables[name], baseline.variables[name])) {
        return true;
      }
      if (!historyEqual(state.variableHistory[name], baseline.variableHistory[name])) {
        return true;
      }
      if ((state.variableBindings[name] ?? "") !== (baseline.variableBindings[name] ?? "")) {
        return true;
      }
    }
    return false;
  }, [state, baseline]);

  useEffect(() => {
    const username = authQuery.data?.principal;
    if (!username) return;
    sendPresence(path, username, isDirty ? "edit" : "view");
    const timer = window.setInterval(() => {
      sendPresence(path, username, isDirty ? "edit" : "view");
    }, 15000);
    return () => window.clearInterval(timer);
  }, [authQuery.data?.principal, path, isDirty]);

  useEffect(() => {
    const handler = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (message.path !== path || message.revision == null) return;
      if (message.revision > revision) {
        setRemoteRevision(message.revision);
      }
    };
    window.addEventListener(OBJECT_WS_EVENT, handler);
    return () => window.removeEventListener(OBJECT_WS_EVENT, handler);
  }, [path, revision]);

  const staleRemote = remoteRevision != null && remoteRevision > revision && isDirty;

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!state || !baseline || !editorQuery.data) return;
      const writeOpts = { revision, force: forceNextSave };
      await updateObject(path, {
        displayName: state.displayName,
        description: state.description,
        iconId: state.iconId ?? "",
      }, writeOpts);
      for (const variable of editorQuery.data.variables) {
        const current = state.variables[variable.name];
        const base = baseline.variables[variable.name];
        const currentBinding = state.variableBindings[variable.name] ?? "";
        const baseBinding = baseline.variableBindings[variable.name] ?? "";
        if (currentBinding.trim() !== baseBinding.trim()) {
          await updateVariableDefinition(path, variable.name, {
            bindingExpression: currentBinding.trim() || "",
          }, writeOpts);
        }
        const writableWithoutBinding =
          variable.writable && !currentBinding.trim() && !baseBinding.trim();
        if (writableWithoutBinding && current && base && !recordsEqual(current, base)) {
          await setVariable(path, variable.name, current, writeOpts);
        }
        const currentHistory = state.variableHistory[variable.name];
        const baseHistory = baseline.variableHistory[variable.name];
        if (currentHistory && baseHistory && !historyEqual(currentHistory, baseHistory)) {
          await updateVariableHistory(path, variable.name, currentHistory, writeOpts);
        }
      }
    },
    onSuccess: async () => {
      setForceNextSave(false);
      setConflictMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
      await queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
      await queryClient.invalidateQueries({ queryKey: ["object-audit", path] });
      const fresh = await fetchObjectEditor(path);
      const next = buildState(fresh);
      setState(next);
      setBaseline(next);
      setRevision(fresh.object.revision ?? 0);
      setRemoteRevision(null);
    },
    onError: (error: Error) => {
      if (error.message.startsWith("REVISION_CONFLICT:")) {
        setConflictMessage("Объект изменился другим пользователем. Перезагрузите или перезапишите (admin).");
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
      onClose();
    },
  });

  const revert = useCallback(() => {
    if (baseline) {
      setState({
        displayName: baseline.displayName,
        description: baseline.description,
        iconId: baseline.iconId,
        variables: Object.fromEntries(
          Object.entries(baseline.variables).map(([k, v]) => [k, cloneRecord(v)])
        ),
        variableHistory: Object.fromEntries(
          Object.entries(baseline.variableHistory).map(([k, v]) => [k, { ...v }])
        ),
        variableBindings: { ...baseline.variableBindings },
      });
    }
  }, [baseline]);

  const toggleSection = (key: SectionKey) => {
    setOpenSections((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  if (editorQuery.isLoading || !state || !editorQuery.data) {
    return <div className="editor-loading">Загрузка редактора…</div>;
  }

  if (editorQuery.error) {
    return <div className="editor-loading error">Ошибка загрузки редактора</div>;
  }

  const ctx = editorQuery.data.object;
  const isRoot = path === "root";
  const canDelete = canDeleteObjectPath(path);
  const crumbs = path.split(".");

  return (
    <div className="properties-editor">
      <div className="properties-editor-toolbar">
        <div className="breadcrumb">
          {crumbs.map((part, index) => {
            const crumbPath = crumbs.slice(0, index + 1).join(".");
            return (
              <span key={crumbPath} className="crumb">
                {index > 0 && <span className="crumb-sep">/</span>}
                <span>{part}</span>
              </span>
            );
          })}
        </div>
        <div className="toolbar-actions">
          <button type="button" className="btn" onClick={() => editorQuery.refetch()}>
            Обновить
          </button>
          <button type="button" className="btn" disabled={!isDirty} onClick={revert}>
            Отменить
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!isDirty || saveMutation.isPending || (staleRemote && !forceNextSave)}
            onClick={() => saveMutation.mutate()}
          >
            Сохранить
          </button>
          {canDelete && (
            <button
              type="button"
              className="btn danger"
              onClick={() => {
                if (confirm(`Удалить «${ctx.displayName}»?`)) {
                  deleteMutation.mutate();
                }
              }}
            >
              Удалить
            </button>
          )}
        </div>
      </div>

      <div className="properties-editor-header">
        <span className="ctx-icon">
          <ObjectTreeIcon
            path={ctx.path}
            type={ctx.type}
            iconId={state.iconId}
            federated={ctx.federated}
            size={24}
          />
        </span>
        <div>
          <h2>
            {ctx.displayName}
            {ctx.federated && (
              <span className="inline-badge federated-inline-badge">federated</span>
            )}
          </h2>
          <p className="hint">{ctx.description || "Универсальный редактор объекта"}</p>
        </div>
        {isDirty && <span className="dirty-pill">Изменено</span>}
      </div>

      {saveMutation.isSuccess && <div className="banner success">Изменения сохранены</div>}
      {staleRemote && (
        <div className="banner warning">
          Объект изменился на сервере (revision {remoteRevision}). Сохранение заблокировано до перезагрузки
          или принудительной перезаписи.
          <button type="button" className="btn btn-sm" onClick={() => editorQuery.refetch()}>
            Перезагрузить
          </button>
          {canManage && (
            <button
              type="button"
              className="btn btn-sm"
              onClick={() => {
                setForceNextSave(true);
                saveMutation.mutate();
              }}
            >
              Перезаписать
            </button>
          )}
        </div>
      )}
      {conflictMessage && <div className="banner error">{conflictMessage}</div>}
      {saveMutation.error && !conflictMessage && (
        <div className="banner error">{(saveMutation.error as Error).message}</div>
      )}

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("info")}>
          <span>{openSections.info ? "▾" : "▸"}</span> Информация
        </button>
        {openSections.info && (
          <div className="section-body form-grid">
            <label>
              Отображаемое имя
              <input
                value={state.displayName}
                onChange={(e) => setState((s) => s && { ...s, displayName: e.target.value })}
              />
            </label>
            <label>
              Тип
              <input value={ctx.type} readOnly className="readonly" />
            </label>
            <label>
              Путь
              <input value={ctx.path} readOnly className="readonly" />
            </label>
            <label>
              Модель
              <input value={ctx.templateId ?? "—"} readOnly className="readonly" />
            </label>
            <label>
              Revision
              <input value={String(revision)} readOnly className="readonly" />
            </label>
            <label className="full">
              Описание
              <textarea
                rows={2}
                value={state.description}
                onChange={(e) => setState((s) => s && { ...s, description: e.target.value })}
              />
            </label>
            <label className="full">
              Иконка в дереве
              <IconPicker
                path={ctx.path}
                type={ctx.type}
                value={state.iconId}
                disabled={isRoot}
                onChange={(iconId) => setState((s) => s && { ...s, iconId })}
              />
            </label>
          </div>
        )}
      </section>

      {path !== "root" && (
        <section className="editor-section">
          <button type="button" className="section-toggle" onClick={() => toggleSection("federation")}>
            <span>{openSections.federation ? "▾" : "▸"}</span> Federation
            {ctx.federated && <span className="inline-badge federated-inline-badge">active</span>}
          </button>
          {openSections.federation && (
            <div className="section-body">
              <ObjectFederationBindSection path={path} canManage={canManage} object={ctx} />
            </div>
          )}
        </section>
      )}

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("variables")}>
          <span>{openSections.variables ? "▾" : "▸"}</span> Переменные ({editorQuery.data.variables.length})
        </button>
        {openSections.variables && (
          <div className="section-body property-list">
            {ctx.federated && (
              <p className="hint">
                Переменные проксируются с remote peer. Локальные переменные скрыты пока активен bind.
              </p>
            )}
            {canManage && !ctx.federated && (
              <div className="panel-toolbar">
                <button
                  type="button"
                  className="btn primary small"
                  onClick={() => setShowCreateVariable(true)}
                >
                  + Переменная
                </button>
              </div>
            )}
            {editorQuery.data.variables.length === 0 && (
              <p className="hint">Нет переменных</p>
            )}
            {editorQuery.data.variables
              .filter((variable) => variable.name !== "uiIcon")
              .map((variable) => (
              <VariableEditorRow
                key={variable.name}
                variable={variable}
                record={state.variables[variable.name]}
                baseline={baseline!.variables[variable.name]}
                history={state.variableHistory[variable.name]}
                historyBaseline={baseline!.variableHistory[variable.name]}
                bindingExpression={state.variableBindings[variable.name] ?? ""}
                bindingBaseline={baseline!.variableBindings[variable.name] ?? ""}
                onChange={(next) =>
                  setState((s) =>
                    s ? { ...s, variables: { ...s.variables, [variable.name]: next } } : s
                  )
                }
                onHistoryChange={(next) =>
                  setState((s) =>
                    s
                      ? {
                          ...s,
                          variableHistory: { ...s.variableHistory, [variable.name]: next },
                        }
                      : s
                  )
                }
                onBindingChange={(next) =>
                  setState((s) =>
                    s
                      ? {
                          ...s,
                          variableBindings: { ...s.variableBindings, [variable.name]: next },
                        }
                      : s
                  )
                }
              />
            ))}
          </div>
        )}
      </section>

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("events")}>
          <span>{openSections.events ? "▾" : "▸"}</span> События ({editorQuery.data.events.length})
        </button>
        {openSections.events && (
          <div className="section-body">
            {editorQuery.data.events.length === 0 ? (
              <p className="hint">Нет событий</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Имя</th>
                    <th>Уровень</th>
                    <th>Описание</th>
                  </tr>
                </thead>
                <tbody>
                  {editorQuery.data.events.map((ev) => (
                    <tr key={ev.name}>
                      <td><code>{ev.name}</code></td>
                      <td>{ev.level}</td>
                      <td>{ev.description || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("functions")}>
          <span>{openSections.functions ? "▾" : "▸"}</span> Функции ({editorQuery.data.functions.length})
        </button>
        {openSections.functions && (
          <div className="section-body">
            {editorQuery.data.functions.length === 0 ? (
              <p className="hint">Нет функций</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Имя</th>
                    <th>Описание</th>
                    <th>Вход</th>
                    <th>Выход</th>
                  </tr>
                </thead>
                <tbody>
                  {editorQuery.data.functions.map((fn) => (
                    <tr key={fn.name}>
                      <td><code>{fn.name}</code></td>
                      <td>{fn.description || "—"}</td>
                      <td className="mono small">{fn.inputSchema.name}</td>
                      <td className="mono small">{fn.outputSchema.name}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("history")}>
          <span>{openSections.history ? "▾" : "▸"}</span> История изменений
        </button>
        {openSections.history && (
          <div className="section-body">
            {auditQuery.isLoading && <p className="hint">Загрузка…</p>}
            {auditQuery.data && auditQuery.data.length === 0 && (
              <p className="hint">Записей пока нет</p>
            )}
            {auditQuery.data && auditQuery.data.length > 0 && (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Время</th>
                    <th>Тип</th>
                    <th>Поле</th>
                    <th>Актор</th>
                    <th>Rev</th>
                  </tr>
                </thead>
                <tbody>
                  {auditQuery.data.map((entry) => (
                    <tr key={entry.id}>
                      <td className="mono small">{entry.occurredAt}</td>
                      <td>{entry.changeType}</td>
                      <td>{entry.field || "—"}</td>
                      <td>{entry.actor || "—"}</td>
                      <td className="mono small">
                        {entry.revisionBefore}→{entry.revisionAfter}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>

      {showCreateVariable && (
        <CreateVariableDialog
          objectPath={path}
          onClose={() => setShowCreateVariable(false)}
          onSaved={async () => {
            await queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
            const fresh = await fetchObjectEditor(path);
            const next = buildState(fresh);
            setState(next);
            setBaseline(next);
            setShowCreateVariable(false);
          }}
        />
      )}
    </div>
  );
}
