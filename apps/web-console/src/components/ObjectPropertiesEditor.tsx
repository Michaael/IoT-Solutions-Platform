import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteEvent,
  deleteFunction,
  deleteObject,
  fetchObjectEditor,
  setVariable,
  updateObject,
  updateVariableHistory,
} from "../api";
import { inspectorQueryLoading, resolveInspectorEditor, useInspectorObjectEditor } from "../hooks/useInspectorQueries";
import { useVariablesQuery } from "../hooks/useVariablesQuery";
import type {
  EventDescriptor,
  FunctionDescriptor,
  ObjectEditorDto,
  DataRecord,
  VariableDto,
} from "../types";
import { fetchAuthMe } from "../api";
import { OBJECT_WS_EVENT, sendPresence, type ObjectWsMessage } from "../hooks/useObjectWebSocket";
import {
  cloneRecord,
  ensureRecord,
  recordsEqual,
  setFieldValue,
} from "../utils/record";
import { applyRemoteVariables } from "../utils/objectPropertiesRemoteSync";
import IconPicker from "./icons/IconPicker";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";
import VariableFieldEditor from "./VariableFieldEditor";
import VariableHistoryFields, {
  formatHistoryRetention,
  type VariableHistoryState,
} from "./VariableHistoryFields";
import { canDeleteObjectPath } from "../utils/platformSystemPaths";
import { localizedSystemObjectDescription } from "../utils/systemFolderI18n";
import CreateVariableDialog from "./CreateVariableDialog";
import EditDescriptorDialog from "./EditDescriptorDialog";
import EditVariableDialog from "./EditVariableDialog";
import InvokeFunctionDialog from "./runtime/InvokeFunctionDialog";
import FireEventDialog from "./runtime/FireEventDialog";
import ObjectFederationBindSection from "./ObjectFederationBindSection";
import DeviceDriverPanel from "./DeviceDriverPanel";
import HaystackMetadataPanel from "./HaystackMetadataPanel";
import ResolvedTimeZoneBadge from "./ResolvedTimeZoneBadge";
import ApplicationDeployPanel from "./ApplicationDeployPanel";
import TreeSubtreeExportPanel from "./TreeSubtreeExportPanel";
import PackageImportPanel from "./PackageImportPanel";
import ObjectAclPanel from "./ObjectAclPanel";
import BindingRulesPanel from "./BindingRulesPanel";
import EventJournalPanel from "./operator/EventJournalPanel";
import FunctionInvokeJournalPanel from "./runtime/FunctionInvokeJournalPanel";
import BindingInvokeJournalPanel from "./runtime/BindingInvokeJournalPanel";
import ObjectChangeHistoryPanel from "./journal/ObjectChangeHistoryPanel";
import VariableHistoryPanel from "./VariableHistoryPanel";
import { historizableFieldsFromVariable } from "../utils/variableHistoryFields";
import { resolveApplicationAppId } from "../utils/applicationPath";
import EditLeaseBanner from "./EditLeaseBanner";

interface ObjectPropertiesEditorProps {
  path: string;
  onClose?: () => void;
  onDeleted: () => void;
  canManage?: boolean;
  /** Render inside Explorer detail pane (no breadcrumb). */
  embedded?: boolean;
}

type Tab =
  | "general"
  | "federation"
  | "driver"
  | "haystack"
  | "deploy"
  | "export"
  | "access"
  | "variables"
  | "bindings"
  | "events"
  | "functions"
  | "history";

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
  onOpenSettings,
}: {
  variable: VariableDto;
  record: DataRecord;
  baseline: DataRecord;
  history: VariableHistoryState;
  historyBaseline: VariableHistoryState;
  onChange: (next: DataRecord) => void;
  onHistoryChange: (next: VariableHistoryState) => void;
  onOpenSettings?: () => void;
  canManage?: boolean;
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
          {onOpenSettings && (
            <button type="button" className="btn tiny" onClick={onOpenSettings}>
              {t("variables.settings")}
            </button>
          )}
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
  embedded = false,
}: ObjectPropertiesEditorProps) {
  const { t } = useTranslation(["inspector", "common", "objectTree"]);
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("general");
  const [revision, setRevision] = useState<number>(0);
  const [remoteRevision, setRemoteRevision] = useState<number | null>(null);
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [forceNextSave, setForceNextSave] = useState(false);

  const authQuery = useQuery({
    queryKey: ["auth-me"],
    queryFn: fetchAuthMe,
  });

  const editorQuery = useInspectorObjectEditor(path);
  const variablesQuery = useVariablesQuery(path, 5000, Boolean(path));

  const [state, setState] = useState<EditorState | null>(null);
  const [baseline, setBaseline] = useState<EditorState | null>(null);
  const [showCreateVariable, setShowCreateVariable] = useState(false);
  const [settingsVariable, setSettingsVariable] = useState<VariableDto | null>(null);
  const [descriptorDialog, setDescriptorDialog] = useState<
    { kind: "function" | "event"; initial?: FunctionDescriptor | EventDescriptor } | null
  >(null);
  const [fireEventTarget, setFireEventTarget] = useState<EventDescriptor | null>(null);
  const [invokeFunctionTarget, setInvokeFunctionTarget] = useState<FunctionDescriptor | null>(null);
  const [historyVariable, setHistoryVariable] = useState<string | null>(null);
  const syncedEditorPathRef = useRef<string | null>(null);
  const stateRef = useRef(state);
  const baselineRef = useRef(baseline);
  stateRef.current = state;
  baselineRef.current = baseline;

  useEffect(() => {
    syncedEditorPathRef.current = null;
    setHistoryVariable(null);
    setTab("general");
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

  useEffect(() => {
    const remote = variablesQuery.data;
    if (!remote || syncedEditorPathRef.current !== path) {
      return;
    }
    const current = stateRef.current;
    const base = baselineRef.current;
    if (!current || !base) {
      return;
    }
    const merged = applyRemoteVariables(remote, current, base);
    if (!merged) {
      return;
    }
    setState(merged.state);
    setBaseline(merged.baseline);
  }, [variablesQuery.data, path]);

  const reloadFromEditor = useCallback(async () => {
    syncedEditorPathRef.current = null;
    const { data } = await editorQuery.refetch();
    const resolved = resolveInspectorEditor(path, data);
    if (!resolved) {
      return;
    }
    const next = buildState(resolved);
    setState(next);
    setBaseline(next);
    setRevision(resolved.object.revision ?? 0);
    setRemoteRevision(null);
    setConflictMessage(null);
    syncedEditorPathRef.current = path;
  }, [editorQuery, path]);

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
      onClose?.();
    },
  });

  const deleteEventMutation = useMutation({
    mutationFn: (name: string) => deleteEvent(path, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
      void reloadFromEditor();
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

  const editorData = resolveInspectorEditor(path, editorQuery.data);
  const ctxPreview = editorData?.object;
  const isRootPath = path === "root";
  const isDevicePreview = ctxPreview?.type === "DEVICE";
  const hasHaystackMetadata = Boolean(
    editorQuery.data?.variables?.some((variable) => variable.name === "haystackTags")
  );
  const isApplicationPreview = ctxPreview?.type === "APPLICATION";
  const showFederationTab = canManage && path !== "root" && !isRootPath;
  const showAccessTab = canManage && !isDevicePreview && !isApplicationPreview;

  const tabs = useMemo((): Tab[] => {
    const list: Tab[] = ["general"];
    if (showFederationTab) {
      list.push("federation");
    }
    if (isDevicePreview) {
      list.push("driver");
      if (hasHaystackMetadata) {
        list.push("haystack");
      }
    }
    if (isApplicationPreview) {
      list.push("deploy");
    }
    if (canManage && ctxPreview && path.startsWith("root.platform")) {
      list.push("export");
    }
    if (showAccessTab) {
      list.push("access");
    }
    list.push("variables", "bindings", "events", "functions", "history");
    return list;
  }, [canManage, ctxPreview, hasHaystackMetadata, isApplicationPreview, isDevicePreview, path, showAccessTab, showFederationTab]);

  useEffect(() => {
    if (!tabs.includes(tab)) {
      setTab("general");
    }
  }, [tab, tabs]);

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
  const isPlatformRoot = path === "root.platform";
  const isDevice = ctx.type === "DEVICE";
  const isApplication = ctx.type === "APPLICATION";
  const applicationAppId = isApplication ? resolveApplicationAppId(path, ctx.description) : null;
  const canDelete = canDeleteObjectPath(path, ctx.type);
  const crumbs = path.split(".");
  const headerDescription =
    localizedSystemObjectDescription(t, path, ctx.description)
    || t("objectEditor.defaultDescription");

  const tabLabel = (tabId: Tab) => {
    switch (tabId) {
      case "general":
        return t("tab.general");
      case "federation":
        return t("tab.federation");
      case "driver":
        return t("tab.driver");
      case "haystack":
        return t("tab.haystack");
      case "deploy":
        return t("tab.deploy");
      case "export":
        return t("tab.export");
      case "access":
        return t("tab.access");
      case "variables":
        return t("tab.variables");
      case "bindings":
        return t("tab.bindings");
      case "events":
        return t("tab.events");
      case "functions":
        return t("tab.functions");
      case "history":
        return t("common:section.changeHistory");
    }
  };

  return (
    <div className={`properties-editor inspector${embedded ? " properties-editor-embedded" : ""}`}>
      <div className="properties-editor-toolbar">
        {!embedded && (
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
        )}
        <div className={`toolbar-actions${embedded ? " toolbar-actions-full" : ""}`}>
          <button type="button" className="btn" onClick={() => void reloadFromEditor()}>
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

      <header className="inspector-header">
        <div className="inspector-title-row">
          <ObjectTreeIcon
            path={ctx.path}
            type={ctx.type}
            iconId={state.iconId}
            federated={ctx.federated}
            size={22}
          />
          <div>
            <h2>
              {ctx.displayName}
              {ctx.federated && (
                <span className="inline-badge federated-inline-badge">{t("common:badge.federated")}</span>
              )}
            </h2>
            <code className="path-code">{ctx.path}</code>
            {!embedded && headerDescription && (
              <p className="hint">{headerDescription}</p>
            )}
            {(isDevicePreview || ctx.type === "DEVICE") && (
              <ResolvedTimeZoneBadge objectPath={path} />
            )}
          </div>
        </div>
        {isDirty && <span className="dirty-pill">{t("common:dirty.changed")}</span>}
      </header>

      <EditLeaseBanner
        path={path}
        canManage={canManage}
        username={authQuery.data?.principal}
        isEditing={isDirty}
      />

      {saveMutation.isSuccess && <div className="banner success">{t("common:changes.saved")}</div>}
      {staleRemote && (
        <div className="banner warning">
          {t("objectEditor.staleRemote", { revision: remoteRevision })}
          <button type="button" className="btn small" onClick={() => void reloadFromEditor()}>
            {t("common:action.reload")}
          </button>
          {canManage && (
            <button
              type="button"
              className="btn small"
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

      <div className="tabs-scroll">
        <nav className="tabs">
          {tabs.map((tabId) => (
            <button
              key={tabId}
              type="button"
              className={tab === tabId ? "active" : ""}
              onClick={() => setTab(tabId)}
            >
              {tabLabel(tabId)}
            </button>
          ))}
        </nav>
      </div>

      {tab === "general" && (
        <section className="panel">
          <div className="form-grid">
            {isPlatformRoot && canManage && (
              <div className="full">
                <PackageImportPanel />
              </div>
            )}
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
                rows={3}
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
        </section>
      )}

      {tab === "federation" && (
        <section className="panel">
          <ObjectFederationBindSection path={path} canManage={canManage} object={ctx} />
        </section>
      )}

      {tab === "driver" && isDevice && (
        <section className="panel">
          <DeviceDriverPanel devicePath={path} canManage={canManage} />
        </section>
      )}

      {tab === "haystack" && isDevice && (
        <section className="panel">
          <HaystackMetadataPanel devicePath={path} canManage={canManage} />
        </section>
      )}

      {tab === "deploy" && isApplication && (
        <section className="panel">
          {applicationAppId ? (
            <ApplicationDeployPanel appId={applicationAppId} canManage={canManage} />
          ) : (
            <p className="hint">{t("deploy.appIdMissing")}</p>
          )}
        </section>
      )}

      {tab === "export" && canManage && path.startsWith("root.platform") && (
        <section className="panel">
          <TreeSubtreeExportPanel rootPath={path} canManage={canManage} />
        </section>
      )}

      <>
          <section className="panel" hidden={tab !== "access"}>
            <ObjectAclPanel objectPath={path} canManage={canManage} />
          </section>
          <section className="panel" hidden={tab !== "bindings"}>
            <BindingRulesPanel
              path={path}
              canManage={canManage}
              eventNames={editorData.events.map((event) => event.name)}
              variableNames={editorData.variables.map((variable) => variable.name)}
              functionNames={editorData.functions.map((fn) => fn.name)}
            />
            {canManage && !ctx.federated && (
              <label className="binding-audit-toggle panel-toolbar">
                <input
                  type="checkbox"
                  checked={editorData.object.bindingAuditEnabled ?? false}
                  onChange={async (e) => {
                    const enabled = e.target.checked;
                    await updateObject(path, { bindingAuditEnabled: enabled }, { revision });
                    const fresh = await fetchObjectEditor(path);
                    setRevision(fresh.object.revision ?? revision);
                    await reloadFromEditor();
                    await queryClient.invalidateQueries({ queryKey: ["binding-audit-status", path] });
                  }}
                />
                {t("bindings.auditEnabled")}
              </label>
            )}
            <BindingInvokeJournalPanel objectPath={path} compact scrollMaxHeight={360} />
          </section>
      </>

      {tab === "variables" && (
        <section className="panel property-list">
          {ctx.federated && (
            <p className="hint">{t("variables.federatedHint")}</p>
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
              <div key={variable.name}>
                <VariableEditorRow
                  variable={variable}
                  record={state.variables[variable.name]}
                  baseline={baseline!.variables[variable.name]}
                  history={state.variableHistory[variable.name]}
                  historyBaseline={baseline!.variableHistory[variable.name]}
                  canManage={canManage && !ctx.federated}
                  onOpenSettings={
                    canManage && !ctx.federated
                      ? () => setSettingsVariable(variable)
                      : undefined
                  }
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
                {variable.historyEnabled && (
                  <div className="property-card-tools property-history-chart-row">
                    <button
                      type="button"
                      className={`btn tiny${historyVariable === variable.name ? " primary" : ""}`}
                      onClick={() =>
                        setHistoryVariable((current) =>
                          current === variable.name ? null : variable.name
                        )
                      }
                    >
                      {t("variables.chart")}
                    </button>
                  </div>
                )}
                {historyVariable === variable.name && variable.historyEnabled && (
                  <div className="binding-panel">
                    <VariableHistoryPanel
                      objectPath={path}
                      variableName={variable.name}
                      fields={historizableFieldsFromVariable(variable)}
                    />
                  </div>
                )}
              </div>
            ))}
        </section>
      )}

      {tab === "events" && (
        <section className="panel">
          {canManage && !ctx.federated && (
            <div className="panel-toolbar">
              <button
                type="button"
                className="btn primary small"
                onClick={() => setDescriptorDialog({ kind: "event" })}
              >
                {t("events.add")}
              </button>
            </div>
          )}
          {editorData.events.length === 0 ? (
            <p className="hint">{t("events.empty")}</p>
          ) : (
            <ul className="event-list editable-list">
              {editorData.events.map((ev) => (
                <li key={ev.name}>
                  <code>{ev.name}</code>
                  <span className="model-var-desc">{ev.description || ev.level}</span>
                  <span className="list-actions">
                    <button
                      type="button"
                      className="btn small primary"
                      onClick={() => setFireEventTarget(ev)}
                    >
                      {t("events.publish")}
                    </button>
                    {canManage && !ctx.federated && (
                      <>
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => setDescriptorDialog({ kind: "event", initial: ev })}
                        >
                          {t("common:action.edit")}
                        </button>
                        <button
                          type="button"
                          className="btn small danger"
                          disabled={deleteEventMutation.isPending}
                          onClick={() => {
                            if (confirm(t("common:action.confirmDeleteEvent", { name: ev.name }))) {
                              deleteEventMutation.mutate(ev.name);
                            }
                          }}
                        >
                          {t("common:action.delete")}
                        </button>
                      </>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <EventJournalPanel
            objectPath={path}
            knownEventNames={editorData.events.map((event) => event.name)}
            compact
            scrollMaxHeight={360}
          />
        </section>
      )}

      {tab === "functions" && (
        <section className="panel">
          {canManage && !ctx.federated && (
            <div className="panel-toolbar">
              <button
                type="button"
                className="btn primary small"
                onClick={() => setDescriptorDialog({ kind: "function" })}
              >
                {t("functions.add")}
              </button>
            </div>
          )}
          {editorData.functions.length === 0 ? (
            <p className="hint">{t("functions.empty")}</p>
          ) : (
            <ul className="event-list editable-list">
              {editorData.functions.map((fn) => (
                <li key={fn.name}>
                  <code>{fn.name}</code>
                  {fn.description && <span className="model-var-desc">{fn.description}</span>}
                  {fn.sourceType === "java" ? (
                    <span className="inline-badge">{t("descriptor.sourceTypeJava")}</span>
                  ) : fn.sourceType === "script" ? (
                    <span className="inline-badge">{t("descriptor.sourceTypeScript")}</span>
                  ) : fn.sourceBody ? (
                    <span className="inline-badge">{t("descriptor.sourceTypeScript")}</span>
                  ) : (
                    <span className="inline-badge muted">{t("descriptor.sourceTypeHandler")}</span>
                  )}
                  <span className="list-actions">
                    <button
                      type="button"
                      className="btn small primary"
                      onClick={() => setInvokeFunctionTarget(fn)}
                    >
                      {t("functions.invoke")}
                    </button>
                    {canManage && !ctx.federated && (
                      <>
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => setDescriptorDialog({ kind: "function", initial: fn })}
                        >
                          {t("common:action.edit")}
                        </button>
                        <button
                          type="button"
                          className="btn small danger"
                          onClick={() => {
                            if (confirm(t("common:action.confirmDeleteFunction", { name: fn.name }))) {
                              deleteFunction(path, fn.name).then(() => reloadFromEditor());
                            }
                          }}
                        >
                          {t("common:action.delete")}
                        </button>
                      </>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {canManage && !ctx.federated && (
            <label className="binding-audit-toggle panel-toolbar">
              <input
                type="checkbox"
                checked={editorData.object.functionAuditEnabled ?? false}
                onChange={async (e) => {
                  const enabled = e.target.checked;
                  await updateObject(path, { functionAuditEnabled: enabled }, { revision });
                  const fresh = await fetchObjectEditor(path);
                  setRevision(fresh.object.revision ?? revision);
                  await reloadFromEditor();
                  await queryClient.invalidateQueries({ queryKey: ["function-audit-status", path] });
                }}
              />
              {t("functions.auditEnabled")}
            </label>
          )}
          <FunctionInvokeJournalPanel objectPath={path} compact scrollMaxHeight={360} />
        </section>
      )}

      {tab === "history" && (
        <ObjectChangeHistoryPanel objectPath={path} compact scrollMaxHeight={420} />
      )}

      {showCreateVariable && (
        <CreateVariableDialog
          objectPath={path}
          onClose={() => setShowCreateVariable(false)}
          onSaved={async () => {
            await queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
            await queryClient.invalidateQueries({ queryKey: ["variables", path] });
            await reloadFromEditor();
            setShowCreateVariable(false);
          }}
        />
      )}

      {settingsVariable && (
        <EditVariableDialog
          objectPath={path}
          variable={settingsVariable}
          canManageHistory={canManage}
          canEditDefinition={canManage && !ctx.federated}
          onClose={() => setSettingsVariable(null)}
          onSaved={async () => {
            await queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
            await queryClient.invalidateQueries({ queryKey: ["variables", path] });
            await reloadFromEditor();
            setSettingsVariable(null);
          }}
        />
      )}

      {descriptorDialog && (
        <EditDescriptorDialog
          objectPath={path}
          kind={descriptorDialog.kind}
          initial={descriptorDialog.initial}
          onClose={() => setDescriptorDialog(null)}
          onSaved={async () => {
            await queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
            await reloadFromEditor();
            setDescriptorDialog(null);
          }}
        />
      )}

      {fireEventTarget && (
        <FireEventDialog
          objectPath={path}
          event={fireEventTarget}
          onClose={() => setFireEventTarget(null)}
          onFired={() => {
            queryClient.invalidateQueries({ queryKey: ["events"] });
          }}
        />
      )}

      {invokeFunctionTarget && (
        <InvokeFunctionDialog
          objectPath={path}
          fn={invokeFunctionTarget}
          federated={ctx.federated}
          federationPeerId={ctx.federationPeerId}
          federationRemotePath={ctx.federationRemotePath}
          onClose={() => setInvokeFunctionTarget(null)}
          onInvoked={() => {
            void reloadFromEditor();
            queryClient.invalidateQueries({ queryKey: ["function-invocations"] });
          }}
        />
      )}
    </div>
  );
}
