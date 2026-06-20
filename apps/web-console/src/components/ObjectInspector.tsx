import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteObject,
  fetchObject,
  fetchObjectEditor,
  fetchVariables,
  updateObject,
} from "../api";
import type { ObjectSummary, VariableDto } from "../types";
import { recordDisplayValue } from "../utils/tree";
import EditVariableDialog from "./EditVariableDialog";
import DeviceDriverPanel from "./DeviceDriverPanel";
import IconPicker from "./icons/IconPicker";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";

interface ObjectInspectorProps {
  path: string;
  onDeleted: () => void;
  canManage?: boolean;
}

type Tab = "general" | "variables" | "events" | "functions" | "driver";

export default function ObjectInspector({ path, onDeleted, canManage = false }: ObjectInspectorProps) {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("general");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [editingVariable, setEditingVariable] = useState<VariableDto | null>(null);

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
    enabled: tab === "functions",
  });

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

  if (objectQuery.isLoading) {
    return <div className="inspector-empty">Загрузка объекта…</div>;
  }

  if (objectQuery.error || !objectQuery.data) {
    return <div className="inspector-empty error">Не удалось загрузить объект</div>;
  }

  const obj = objectQuery.data;
  const isRoot = path === "root";
  const isDevice = obj.type === "DEVICE";
  const tabs: Tab[] = isDevice
    ? ["general", "driver", "variables", "events", "functions"]
    : ["general", "variables", "events", "functions"];

  const tabLabel = (t: Tab) => {
    switch (t) {
      case "general":
        return "Свойства";
      case "driver":
        return "Драйвер";
      case "variables":
        return "Переменные";
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
            <h2>{obj.displayName}</h2>
            <code className="path-code">{obj.path}</code>
          </div>
        </div>
        <div className="inspector-actions">
          {!isRoot && (
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

      {tab === "general" && (
        <section className="panel">
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
              Модель
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

      {tab === "variables" && (
        <section className="panel">
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
                  <th>Привязка</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {variablesQuery.data
                  .filter((v) => v.name !== "uiIcon")
                  .map((v) => (
                  <tr key={v.name}>
                    <td><code>{v.name}</code></td>
                    <td className="mono">{recordDisplayValue(v.value)}</td>
                    <td>{v.writable ? "да" : "нет"}</td>
                    <td className="mono small">{v.bindingExpression ?? "—"}</td>
                    <td>
                      {v.writable && (
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => setEditingVariable(v)}
                        >
                          Изменить
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </section>
      )}

      {tab === "events" && (
        <section className="panel">
          {obj.eventNames.length === 0 ? (
            <p className="hint">Нет событий</p>
          ) : (
            <ul className="event-list">
              {obj.eventNames.map((name) => (
                <li key={name}>
                  <code>{name}</code>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {tab === "functions" && (
        <section className="panel">
          {editorQuery.isLoading && <p>Загрузка…</p>}
          {editorQuery.data && editorQuery.data.functions.length === 0 && (
            <p className="hint">Нет функций</p>
          )}
          {editorQuery.data && editorQuery.data.functions.length > 0 && (
            <ul className="event-list">
              {editorQuery.data.functions.map((fn) => (
                <li key={fn.name}>
                  <code>{fn.name}</code>
                  {fn.description && <span className="model-var-desc">{fn.description}</span>}
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
          onClose={() => setEditingVariable(null)}
          onSaved={() => {
            queryClient.invalidateQueries({ queryKey: ["variables", path] });
            setEditingVariable(null);
          }}
        />
      )}
    </div>
  );
}
