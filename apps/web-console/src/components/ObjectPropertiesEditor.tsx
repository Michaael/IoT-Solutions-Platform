import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteObject,
  fetchObjectAudit,
  fetchObjectEditor,
  setVariable,
  updateObject,
  updateVariableHistory,
} from "../api";
import { inspectorQueryLoading, resolveInspectorEditor, useInspectorObjectEditor } from "../hooks/useInspectorQueries";
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
  for (const v of data.variables) {
    if (v.name === "uiIcon") {
      continue;
    }
    variables[v.name] = ensureRecord(v);
    variableHistory[v.name] = historyFromVariable(v);
  }
  return {
    displayName: data.object.displayName,
    description: data.object.description,
    iconId: data.object.iconId ?? null,
    variables,
    variableHistory,
  };
}

function VariableEditorRow({
  variable,
  record,
  baseline,
  history,
  historyBaseline,
  onChange,
  onHistoryChange,
}: {
  variable: VariableDto;
  record: DataRecord;
  baseline: DataRecord;
  history: VariableHistoryState;
  historyBaseline: VariableHistoryState;
  onChange: (next: DataRecord) => void;
  onHistoryChange: (next: VariableHistoryState) => void;
}) {
  const { t } = useTranslation(["inspector", "common"]);
  const [showHistory, setShowHistory] = useState(false);
  const [showJson, setShowJson] = useState(false);
  const dirty = !recordsEqual(record, baseline);
  const historyDirty = !historyEqual(history, historyBaseline);
  const row = record.rows[0] ?? {};
  const disabled = !variable.writable;

  return (
    <article className={`property-card ${dirty || historyDirty ? "dirty" : ""}`}>
      <header className="property-card-header">
        <div>
          <code className="property-name">{variable.name}</code>
          <span className="property-badges">
            {variable.readable && <span className="badge">R</span>}
            {variable.writable && <span className="badge w">W</span>}
            {history.historyEnabled && (
              <span className="badge hist" title={formatHistoryRetention(history.historyRetentionDays)}>
                H
              </span>
            )}
          </span>
        </div>
        <div className="property-card-tools">
          <button type="button" className="btn tiny" onClick={() => setShowHistory((v) => !v)}>
            {t("objectEditor.historyBtn")}
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
            <p className="hint">{t("objectEditor.emptyVariableSchema")}</p>
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
  const { t } = useTranslation(["inspector", "common"]);
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

  const editorQuery = useInspectorObjectEditor(path);

  const [state, setState] = useState<EditorState | null>(null);
  const [baseline, setBaseline] = useState<EditorState | null>(null);
  const [showCreateVariable, setShowCreateVariable] = useState(false);
  const syncedEditorPathRef = useRef<string | null>(null);

  useEffect(() => {
    syncedEditorPathRef.current = null;
  }, [path]);

  useEffect(() => {
    const data = resolveInspectorEditor(path, editorQuery.data);
    if (!data || syncedEditorPathRef.current === path) {
      return;
    }
    const next = buildState(data);
    setState(next);
    setBaseline(next);
    setRevision(data.object.revision ?? 0);
    setRemoteRevision(null);
    setConflictMessage(null);
    syncedEditorPathRef.current = path;
  }, [editorQuery.data, path]);

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
        if (variable.writable && current && base && !recordsEqual(current, base)) {
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
        setConflictMessage(t("objectEditor.conflictMessage"));
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
      });
    }
  }, [baseline]);

  const toggleSection = (key: SectionKey) => {
    setOpenSections((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const editorData = resolveInspectorEditor(path, editorQuery.data);
  if (editorQuery.error && !editorData) {
    return <div className="editor-loading error">{t("objectEditor.loadError")}</div>;
  }

  if (!editorData && inspectorQueryLoading(editorQuery)) {
    return <div className="editor-loading">{t("objectEditor.loading")}</div>;
  }

  if (!editorData || !state) {
    return <div className="editor-loading">{t("objectEditor.loading")}</div>;
  }

  const ctx = editorData.object;
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
            {t("common:action.refresh")}
          </button>
          <button type="button" className="btn" disabled={!isDirty} onClick={revert}>
            {t("common:action.revert")}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!isDirty || saveMutation.isPending || (staleRemote && !forceNextSave)}
            onClick={() => saveMutation.mutate()}
          >
            {t("common:action.save")}
          </button>
          {canDelete && (
            <button
              type="button"
              className="btn danger"
              onClick={() => {
                if (confirm(t("common:action.confirmDeleteNamed", { name: ctx.displayName }))) {
                  deleteMutation.mutate();
                }
              }}
            >
              {t("common:action.delete")}
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
          <p className="hint">{ctx.description || t("objectEditor.defaultDescription")}</p>
        </div>
        {isDirty && <span className="dirty-pill">{t("common:dirty.changed")}</span>}
      </div>

      {saveMutation.isSuccess && <div className="banner success">{t("common:changes.saved")}</div>}
      {staleRemote && (
        <div className="banner warning">
          {t("objectEditor.staleRemote", { revision: remoteRevision })}
          <button type="button" className="btn btn-sm" onClick={() => editorQuery.refetch()}>
            {t("common:action.reload")}
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
              {t("common:action.overwrite")}
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
          <span>{openSections.info ? "▾" : "▸"}</span> {t("common:section.info")}
        </button>
        {openSections.info && (
          <div className="section-body form-grid">
            <label>
              {t("common:field.displayName")}
              <input
                value={state.displayName}
                onChange={(e) => setState((s) => s && { ...s, displayName: e.target.value })}
              />
            </label>
            <label>
              {t("common:table.type")}
              <input value={ctx.type} readOnly className="readonly" />
            </label>
            <label>
              {t("common:field.path")}
              <input value={ctx.path} readOnly className="readonly" />
            </label>
            <label>
              {t("common:field.primaryModel")}
              <input
                value={
                  ctx.appliedModels?.find((m) => m.primary)?.name ?? ctx.templateId ?? "—"
                }
                readOnly
                className="readonly"
              />
            </label>
            {(ctx.appliedModels?.length ?? 0) > 0 && (
              <div className="full">
                <span className="field-label">{t("common:field.appliedModels")}</span>
                <ul className="applied-models-list">
                  {ctx.appliedModels!.map((model) => (
                    <li key={model.id}>
                      <code>{model.name}</code> ({model.type}
                      {model.primary ? ", primary" : ""})
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <label>
              Revision
              <input value={String(revision)} readOnly className="readonly" />
            </label>
            <label className="full">
              {t("common:field.description")}
              <textarea
                rows={2}
                value={state.description}
                onChange={(e) => setState((s) => s && { ...s, description: e.target.value })}
              />
            </label>
            <label className="full">
              {t("common:field.iconInTree")}
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
          <span>{openSections.variables ? "▾" : "▸"}</span> {t("tab.variables")} ({editorData.variables.length})
        </button>
        {openSections.variables && (
          <div className="section-body property-list">
            {ctx.federated && (
              <p className="hint">
                {t("variables.federatedHint")}
              </p>
            )}
            {canManage && !ctx.federated && (
              <div className="panel-toolbar">
                <button
                  type="button"
                  className="btn primary small"
                  onClick={() => setShowCreateVariable(true)}
                >
                  {t("variables.add")}
                </button>
              </div>
            )}
            {editorData.variables.length === 0 && (
              <p className="hint">{t("variables.empty")}</p>
            )}
            {editorData.variables
              .filter((variable) => variable.name !== "uiIcon")
              .map((variable) => (
              <VariableEditorRow
                key={variable.name}
                variable={variable}
                record={state.variables[variable.name]}
                baseline={baseline!.variables[variable.name]}
                history={state.variableHistory[variable.name]}
                historyBaseline={baseline!.variableHistory[variable.name]}
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
              />
            ))}
          </div>
        )}
      </section>

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("events")}>
          <span>{openSections.events ? "▾" : "▸"}</span> {t("tab.events")} ({editorData.events.length})
        </button>
        {openSections.events && (
          <div className="section-body">
            {editorData.events.length === 0 ? (
              <p className="hint">{t("events.empty")}</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t("common:table.name")}</th>
                    <th>{t("common:field.level")}</th>
                    <th>{t("common:table.description")}</th>
                  </tr>
                </thead>
                <tbody>
                  {editorData.events.map((ev) => (
                    <tr key={ev.name}>
                      <td><code>{ev.name}</code></td>
                      <td>{ev.level}</td>
                      <td>{ev.description || t("common:empty.dash")}</td>
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
          <span>{openSections.functions ? "▾" : "▸"}</span> {t("tab.functions")} ({editorData.functions.length})
        </button>
        {openSections.functions && (
          <div className="section-body">
            {editorData.functions.length === 0 ? (
              <p className="hint">{t("functions.empty")}</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t("common:table.name")}</th>
                    <th>{t("common:table.description")}</th>
                    <th>{t("common:field.input")}</th>
                    <th>{t("common:field.output")}</th>
                  </tr>
                </thead>
                <tbody>
                  {editorData.functions.map((fn) => (
                    <tr key={fn.name}>
                      <td><code>{fn.name}</code></td>
                      <td>{fn.description || t("common:empty.dash")}</td>
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
          <span>{openSections.history ? "▾" : "▸"}</span> {t("common:section.changeHistory")}
        </button>
        {openSections.history && (
          <div className="section-body">
            {auditQuery.isLoading && <p className="hint">{t("common:action.loading")}</p>}
            {auditQuery.data && auditQuery.data.length === 0 && (
              <p className="hint">{t("common:empty.noRecords")}</p>
            )}
            {auditQuery.data && auditQuery.data.length > 0 && (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>{t("common:field.time")}</th>
                    <th>{t("common:table.type")}</th>
                    <th>{t("common:field.field")}</th>
                    <th>{t("common:field.actor")}</th>
                    <th>Rev</th>
                  </tr>
                </thead>
                <tbody>
                  {auditQuery.data.map((entry) => (
                    <tr key={entry.id}>
                      <td className="mono small">{entry.occurredAt}</td>
                      <td>{entry.changeType}</td>
                      <td>{entry.field || t("common:empty.dash")}</td>
                      <td>{entry.actor || t("common:empty.dash")}</td>
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
