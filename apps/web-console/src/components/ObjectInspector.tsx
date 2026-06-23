import { Fragment, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteEvent,
  deleteFunction,
  deleteObject,
  fetchObject,
  fetchObjectEditor,
  fetchVariables,
  updateObject,
} from "../api";
import type { EventDescriptor, FunctionDescriptor, ObjectSummary, VariableDto } from "../types";
import CreateVariableDialog from "./CreateVariableDialog";
import EditDescriptorDialog from "./EditDescriptorDialog";
import FireEventDialog from "./runtime/FireEventDialog";
import InvokeFunctionDialog from "./runtime/InvokeFunctionDialog";
import EventJournalPanel from "./operator/EventJournalPanel";
import { recordCompactValue } from "../utils/tree";
import EditVariableDialog from "./EditVariableDialog";
import DeviceDriverPanel from "./DeviceDriverPanel";
import IconPicker from "./icons/IconPicker";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";
import VariableHistoryPanel from "./VariableHistoryPanel";
import { formatHistoryRetention } from "./VariableHistoryFields";
import { historizableFieldsFromVariable } from "../utils/variableHistoryFields";
import { canDeleteObjectPath } from "../utils/platformSystemPaths";
import ApplicationDeployPanel from "./ApplicationDeployPanel";
import PackageImportPanel from "./PackageImportPanel";
import ObjectAclPanel from "./ObjectAclPanel";
import { resolveApplicationAppId } from "../utils/applicationPath";
import ObjectFederationBindSection from "./ObjectFederationBindSection";
import BindingRulesPanel from "./BindingRulesPanel";

interface ObjectInspectorProps {
  path: string;
  onDeleted: () => void;
  canManage?: boolean;
}

type Tab = "general" | "federation" | "variables" | "bindings" | "events" | "functions" | "driver" | "deploy" | "access";

export default function ObjectInspector({ path, onDeleted, canManage = false }: ObjectInspectorProps) {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("general");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [editingVariable, setEditingVariable] = useState<VariableDto | null>(null);
  const [historyVariable, setHistoryVariable] = useState<string | null>(null);
  const [showCreateVariable, setShowCreateVariable] = useState(false);
  const [descriptorDialog, setDescriptorDialog] = useState<
    { kind: "function" | "event"; initial?: FunctionDescriptor | EventDescriptor } | null
  >(null);
  const [fireEventTarget, setFireEventTarget] = useState<EventDescriptor | null>(null);
  const [invokeFunctionTarget, setInvokeFunctionTarget] = useState<FunctionDescriptor | null>(null);

  const objectQuery = useQuery({
    queryKey: ["object", path],
    queryFn: () => fetchObject(path),
  });

  const variablesQuery = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
    enabled: tab === "variables",
  });

  const editorQuery = useQuery({
    queryKey: ["object-editor", path],
    queryFn: () => fetchObjectEditor(path),
    enabled: tab === "functions" || tab === "events",
  });

  useEffect(() => {
    setHistoryVariable(null);
  }, [path]);

  useEffect(() => {
    if (objectQuery.data) {
      setDisplayName(objectQuery.data.displayName);
      setDescription(objectQuery.data.description);
    }
  }, [objectQuery.data]);

  useEffect(() => {
    if (objectQuery.data?.type !== "DEVICE" && tab === "driver") {
      setTab("general");
    }
  }, [objectQuery.data?.type, path, tab]);

  const saveMutation = useMutation({
    mutationFn: () => updateObject(path, { displayName, description }),
    onSuccess: (updated: ObjectSummary) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.setQueryData(["object", path], updated);
    },
  });

  const iconMutation = useMutation({
    mutationFn: (iconId: string | null) =>
      updateObject(path, { iconId: iconId ?? "" }),
    onSuccess: (updated: ObjectSummary) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.setQueryData(["object", path], updated);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
    },
  });

  const deleteFunctionMutation = useMutation({
    mutationFn: (name: string) => deleteFunction(path, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
      queryClient.invalidateQueries({ queryKey: ["object", path] });
    },
  });

  const deleteEventMutation = useMutation({
    mutationFn: (name: string) => deleteEvent(path, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
      queryClient.invalidateQueries({ queryKey: ["object", path] });
    },
  });

  const refreshObjectEditor = () => {
    queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
    queryClient.invalidateQueries({ queryKey: ["variables", path] });
    queryClient.invalidateQueries({ queryKey: ["object", path] });
  };

  if (objectQuery.isLoading) {
    return <div className="inspector-empty">Загрузка объекта…</div>;
  }

  if (objectQuery.error || !objectQuery.data) {
    return <div className="inspector-empty error">Не удалось загрузить объект</div>;
  }

  const obj = objectQuery.data;
  const isRoot = path === "root";
  const isPlatformRoot = path === "root.platform";
  const canDelete = canDeleteObjectPath(path);
  const isDevice = obj.type === "DEVICE";
  const isApplication = obj.type === "APPLICATION";
  const showFederationBind = canManage && path !== "root" && !isRoot;
  const applicationAppId = isApplication ? resolveApplicationAppId(path, obj.description) : null;
  const tabs = (isDevice
    ? (["general", "federation", "driver", "variables", "bindings", "events", "functions"] as const)
    : isApplication
      ? (["general", "federation", "deploy", "variables", "bindings", "events", "functions"] as const)
      : canManage
        ? (["general", "federation", "access", "variables", "bindings", "events", "functions"] as const)
        : (["general", "federation", "variables", "bindings", "events", "functions"] as const)
  ).filter((tabName): tabName is Tab => tabName !== "federation" || showFederationBind);

  const tabLabel = (t: Tab) => {
    switch (t) {
      case "general":
        return "Свойства";
      case "federation":
        return "Federation";
      case "deploy":
        return "Deploy";
      case "access":
        return "Доступ";
      case "driver":
        return "Драйвер";
      case "variables":
        return "Переменные";
      case "bindings":
        return "Привязки";
      case "events":
        return "События";
      case "functions":
        return "Функции";
    }
  };

  return (
    <div className="inspector">
      <header className="inspector-header">
        <div className="inspector-title-row">
          <ObjectTreeIcon path={obj.path} type={obj.type} iconId={obj.iconId} size={22} />
          <div>
            <h2>
              {obj.displayName}
              {obj.federated && <span className="inline-badge federated-inline-badge">federated</span>}
            </h2>
            <code className="path-code">{obj.path}</code>
          </div>
        </div>
        <div className="inspector-actions">
          {canDelete && (
            <button
              type="button"
              className="btn danger"
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (confirm(`Удалить объект «${obj.displayName}» и все дочерние?`)) {
                  deleteMutation.mutate();
                }
              }}
            >
              Удалить
            </button>
          )}
        </div>
      </header>

      <nav className="tabs">
        {tabs.map((t) => (
          <button
            key={t}
            type="button"
            className={tab === t ? "active" : ""}
            onClick={() => setTab(t)}
          >
            {tabLabel(t)}
          </button>
        ))}
      </nav>

      {tab === "driver" && isDevice && (
        <section className="panel">
          <DeviceDriverPanel devicePath={path} canManage={canManage} />
        </section>
      )}

      {tab === "deploy" && isApplication && applicationAppId && (
        <section className="panel">
          <ApplicationDeployPanel appId={applicationAppId} canManage={canManage} />
        </section>
      )}

      {tab === "deploy" && isApplication && !applicationAppId && (
        <section className="panel">
          <p className="op-muted">Не удалось определить appId для этого приложения.</p>
        </section>
      )}

      {tab === "access" && canManage && (
        <section className="panel">
          <ObjectAclPanel objectPath={path} canManage={canManage} />
        </section>
      )}

      {tab === "general" && (
        <section className="panel">
            {isPlatformRoot && canManage && (
              <section className="panel">
                <PackageImportPanel />
              </section>
            )}
            <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              saveMutation.mutate();
            }}
          >
            <label>
              Имя
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </label>
            <label>
              Тип
              <input value={obj.type} readOnly className="readonly" />
            </label>
            <label className="full">
              Описание
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
            <label className="full">
              Иконка в дереве
              <IconPicker
                path={obj.path}
                type={obj.type}
                value={obj.iconId}
                disabled={isRoot || iconMutation.isPending}
                onChange={(iconId) => iconMutation.mutate(iconId)}
              />
            </label>
            <label>
              ID
              <input value={obj.id} readOnly className="readonly" />
            </label>
            <label>
              Основная модель
              <input
                value={
                  obj.appliedModels?.find((model) => model.primary)?.name ??
                  obj.templateId ??
                  "—"
                }
                readOnly
                className="readonly"
              />
            </label>
            {(obj.appliedModels?.length ?? 0) > 0 && (
              <div className="full">
                <span className="field-label">Применённые модели</span>
                <ul className="applied-models-list">
                  {obj.appliedModels!.map((model) => (
                    <li key={model.id} className={model.primary ? "primary-model" : undefined}>
                      <code>{model.name}</code>{" "}
                      <span className="hint">({model.type}{model.primary ? ", primary" : ""})</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <label className="legacy-template-id">
              templateId
              <input value={obj.templateId ?? "—"} readOnly className="readonly" />
            </label>
            <label>
              Создан
              <input value={new Date(obj.createdAt).toLocaleString()} readOnly className="readonly" />
            </label>
            <div className="full form-actions">
              <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
                Сохранить
              </button>
              {saveMutation.isSuccess && <span className="hint success">Сохранено</span>}
              {saveMutation.error && <span className="hint error">Ошибка сохранения</span>}
            </div>
          </form>
        </section>
      )}

      {tab === "federation" && showFederationBind && (
        <section className="panel">
          <ObjectFederationBindSection path={path} canManage={canManage} object={obj} />
        </section>
      )}

      {tab === "variables" && (
        <section className="panel">
          {canManage && !obj.federated && (
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
          {obj.federated && (
            <p className="hint">Переменные проксируются с remote peer. Локальные переменные скрыты пока активен bind.</p>
          )}
          {variablesQuery.isLoading && <p>Загрузка переменных…</p>}
          {variablesQuery.data && variablesQuery.data.length === 0 && (
            <p className="hint">Нет переменных</p>
          )}
          {variablesQuery.data && variablesQuery.data.length > 0 && (
            <div className="table-scroll">
            <table className="data-table variables-table">
              <thead>
                <tr>
                  <th>Имя</th>
                  <th>Значение</th>
                  <th>Запись</th>
                  <th>История</th>
                  <th aria-label="Действия" />
                </tr>
              </thead>
              <tbody>
                {variablesQuery.data
                  .filter((v) => v.name !== "uiIcon")
                  .map((v) => {
                  const compactValue = recordCompactValue(v.value);
                  return (
                  <Fragment key={v.name}>
                  <tr
                    className={historyVariable === v.name ? "var-row-active" : undefined}
                  >
                    <td className="var-name-cell"><code>{v.name}</code></td>
                    <td className="mono var-value-cell" title={compactValue}>
                      {compactValue}
                    </td>
                    <td className="var-flag-cell">{v.writable ? "да" : "нет"}</td>
                    <td className="var-history-cell">
                      {v.historyEnabled ? (
                        <>
                          <span className="var-flag yes">да</span>
                          <span className="var-history-retention">
                            {formatHistoryRetention(v.historyRetentionDays)}
                          </span>
                        </>
                      ) : (
                        <span className="var-flag no">нет</span>
                      )}
                    </td>
                    <td className="var-actions-cell">
                      {v.historyEnabled && (
                        <button
                          type="button"
                          className={`btn small ${historyVariable === v.name ? "primary" : ""}`}
                          onClick={() =>
                            setHistoryVariable((current) =>
                              current === v.name ? null : v.name
                            )
                          }
                        >
                          График
                        </button>
                      )}
                      {canManage ? (
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => setEditingVariable(v)}
                        >
                          Настройки
                        </button>
                      ) : (
                        v.writable && (
                          <button
                            type="button"
                            className="btn small"
                            onClick={() => setEditingVariable(v)}
                          >
                            Изменить
                          </button>
                        )
                      )}
                    </td>
                  </tr>
                  {historyVariable === v.name && v.historyEnabled && (
                    <tr className="var-history-row">
                      <td colSpan={5}>
                        <VariableHistoryPanel
                          objectPath={path}
                          variableName={v.name}
                          fields={historizableFieldsFromVariable(v)}
                        />
                      </td>
                    </tr>
                  )}
                  </Fragment>
                );
                })}
              </tbody>
            </table>
            </div>
          )}
        </section>
      )}

      {tab === "bindings" && (
        <BindingRulesPanel path={path} canManage={canManage} />
      )}

      {tab === "events" && (
        <section className="panel">
          {canManage && (
            <div className="panel-toolbar">
              <button
                type="button"
                className="btn primary small"
                onClick={() => setDescriptorDialog({ kind: "event" })}
              >
                + Событие
              </button>
            </div>
          )}
          {editorQuery.isLoading && <p>Загрузка…</p>}
          {editorQuery.data && editorQuery.data.events.length === 0 && (
            <p className="hint">Нет событий</p>
          )}
          {editorQuery.data && editorQuery.data.events.length > 0 && (
            <ul className="event-list editable-list">
              {editorQuery.data.events.map((ev) => (
                <li key={ev.name}>
                  <code>{ev.name}</code>
                  <span className="model-var-desc">{ev.description || ev.level}</span>
                  <span className="list-actions">
                    <button
                      type="button"
                      className="btn small primary"
                      onClick={() => setFireEventTarget(ev)}
                    >
                      Опубликовать
                    </button>
                    {canManage && (
                      <>
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => setDescriptorDialog({ kind: "event", initial: ev })}
                        >
                          Изменить
                        </button>
                        <button
                          type="button"
                          className="btn small danger"
                          disabled={deleteEventMutation.isPending}
                          onClick={() => {
                            if (confirm(`Удалить событие «${ev.name}»?`)) {
                              deleteEventMutation.mutate(ev.name);
                            }
                          }}
                        >
                          Удалить
                        </button>
                      </>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <EventJournalPanel objectPath={path} limit={15} />
        </section>
      )}

      {tab === "functions" && (
        <section className="panel">
          {canManage && (
            <div className="panel-toolbar">
              <button
                type="button"
                className="btn primary small"
                onClick={() => setDescriptorDialog({ kind: "function" })}
              >
                + Функция
              </button>
            </div>
          )}
          {editorQuery.isLoading && <p>Загрузка…</p>}
          {editorQuery.data && editorQuery.data.functions.length === 0 && (
            <p className="hint">Нет функций</p>
          )}
          {editorQuery.data && editorQuery.data.functions.length > 0 && (
            <ul className="event-list editable-list">
              {editorQuery.data.functions.map((fn) => (
                <li key={fn.name}>
                  <code>{fn.name}</code>
                  {fn.description && <span className="model-var-desc">{fn.description}</span>}
                  <span className="list-actions">
                    <button
                      type="button"
                      className="btn small primary"
                      onClick={() => setInvokeFunctionTarget(fn)}
                    >
                      Вызвать
                    </button>
                    {canManage && (
                      <>
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => setDescriptorDialog({ kind: "function", initial: fn })}
                        >
                          Изменить
                        </button>
                        <button
                          type="button"
                          className="btn small danger"
                          disabled={deleteFunctionMutation.isPending}
                          onClick={() => {
                            if (confirm(`Удалить функцию «${fn.name}»?`)) {
                              deleteFunctionMutation.mutate(fn.name);
                            }
                          }}
                        >
                          Удалить
                        </button>
                      </>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {editingVariable && (
        <EditVariableDialog
          objectPath={path}
          variable={editingVariable}
          canManageHistory={canManage}
          canEditDefinition={canManage}
          onClose={() => setEditingVariable(null)}
          onSaved={() => {
            refreshObjectEditor();
            setEditingVariable(null);
          }}
        />
      )}

      {showCreateVariable && (
        <CreateVariableDialog
          objectPath={path}
          onClose={() => setShowCreateVariable(false)}
          onSaved={() => {
            refreshObjectEditor();
            setShowCreateVariable(false);
          }}
        />
      )}

      {descriptorDialog && (
        <EditDescriptorDialog
          objectPath={path}
          kind={descriptorDialog.kind}
          initial={descriptorDialog.initial}
          onClose={() => setDescriptorDialog(null)}
          onSaved={() => {
            refreshObjectEditor();
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
          onClose={() => setInvokeFunctionTarget(null)}
          onInvoked={() => {
            queryClient.invalidateQueries({ queryKey: ["function-invocations"] });
            queryClient.invalidateQueries({ queryKey: ["variables", path] });
            queryClient.invalidateQueries({ queryKey: ["objects"] });
          }}
        />
      )}
    </div>
  );
}
