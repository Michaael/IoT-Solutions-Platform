import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  applyModel,
  createModel,
  createModelFromObject,
  deleteModel,
  fetchModelByName,
  fetchModelInstances,
  fetchModels,
  instantiateModel,
  updateModel,
  upgradeModel,
  upgradeModelInstances,
} from "../api/models";
import type { EventDescriptor, FunctionDescriptor, ObjectType } from "../types";
import {
  BUILTIN_MODEL_NAMES,
  MODELS_ROOT,
  modelNameFromPath,
  type ModelBindingDefinition,
  type ModelDto,
  type ModelVariableDefinition,
} from "../types/models";
import { recordDisplayValue } from "../utils/tree";
import { formatHistoryRetention } from "./VariableHistoryFields";

interface ModelEditorPanelProps {
  selectedPath: string;
  canManage: boolean;
  onSelectPath?: (path: string) => void;
  onClose?: () => void;
  title?: string;
}

const OBJECT_TYPES: ObjectType[] = [
  "DEVICE",
  "DASHBOARD",
  "WORKFLOW",
  "CUSTOM",
  "APPLICATION",
  "USER",
];

function ModelDetail({
  model,
  canManage,
  onSelectPath,
}: {
  model: ModelDto;
  canManage: boolean;
  onSelectPath?: (path: string) => void;
}) {
  const queryClient = useQueryClient();
  const [description, setDescription] = useState(model.description);
  const [suitability, setSuitability] = useState(model.suitabilityExpression);
  const [applyPath, setApplyPath] = useState("");
  const [parentPath, setParentPath] = useState("root.platform.devices");
  const [instanceName, setInstanceName] = useState("");
  const [variables, setVariables] = useState(model.variables);
  const [bindings, setBindings] = useState(model.bindings);
  const [events, setEvents] = useState(model.events);
  const [functions, setFunctions] = useState(model.functions);
  const isBuiltin = BUILTIN_MODEL_NAMES.has(model.name);

  useEffect(() => {
    setVariables(model.variables);
    setBindings(model.bindings);
    setEvents(model.events);
    setFunctions(model.functions);
  }, [model.variables, model.bindings, model.events, model.functions]);

  const variablesDirty = useMemo(
    () => JSON.stringify(variables) !== JSON.stringify(model.variables),
    [variables, model.variables]
  );
  const bindingsDirty = useMemo(
    () => JSON.stringify(bindings) !== JSON.stringify(model.bindings),
    [bindings, model.bindings]
  );
  const eventsDirty = useMemo(
    () => JSON.stringify(events) !== JSON.stringify(model.events),
    [events, model.events]
  );
  const functionsDirty = useMemo(
    () => JSON.stringify(functions) !== JSON.stringify(model.functions),
    [functions, model.functions]
  );
  const definitionDirty = variablesDirty || bindingsDirty || eventsDirty || functionsDirty;

  const saveMutation = useMutation({
    mutationFn: () =>
      updateModel(model.id, {
        description,
        suitabilityExpression: suitability,
        variables,
        bindings,
        events,
        functions,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["model", model.name] });
      queryClient.invalidateQueries({ queryKey: ["models"] });
    },
  });

  const applyMutation = useMutation({
    mutationFn: () => applyModel(model.id, applyPath.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      if (applyPath.trim() && onSelectPath) {
        onSelectPath(applyPath.trim());
      }
    },
  });

  const instancesQuery = useQuery({
    queryKey: ["model-instances", model.id],
    queryFn: () => fetchModelInstances(model.id),
  });

  const upgradeOneMutation = useMutation({
    mutationFn: () => upgradeModel(model.id, applyPath.trim(), model.parameters.modelVersion),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["model-instances", model.id] });
    },
  });

  const upgradeAllMutation = useMutation({
    mutationFn: () => upgradeModelInstances(model.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["model-instances", model.id] });
    },
  });

  const instantiateMutation = useMutation({
    mutationFn: () =>
      instantiateModel(model.id, parentPath.trim(), instanceName.trim()),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(created.path);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteModel(model.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["models"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(MODELS_ROOT);
    },
  });

  function patchVariableHistory(
    name: string,
    patch: Pick<ModelVariableDefinition, "historyEnabled" | "historyRetentionDays">
  ) {
    setVariables((prev) =>
      prev.map((v) => (v.name === name ? { ...v, ...patch } : v))
    );
  }

  function patchVariableBinding(name: string, defaultBinding: string | null) {
    setVariables((prev) =>
      prev.map((v) =>
        v.name === name ? { ...v, defaultBinding: defaultBinding?.trim() || null } : v
      )
    );
  }

  function addVariable() {
    const baseName = `var${variables.length + 1}`;
    setVariables((prev) => [
      ...prev,
      {
        name: baseName,
        description: "",
        group: "default",
        schema: { name: baseName, fields: [{ name: "value", type: "STRING" }] },
        readable: true,
        writable: false,
        defaultBinding: null,
        defaultValue: null,
        historyEnabled: false,
        historyRetentionDays: null,
      },
    ]);
  }

  function removeVariable(name: string) {
    setVariables((prev) => prev.filter((v) => v.name !== name));
    setBindings((prev) => prev.filter((b) => b.targetVariable !== name));
  }

  function patchBinding(index: number, patch: Partial<ModelBindingDefinition>) {
    setBindings((prev) =>
      prev.map((b, i) => (i === index ? { ...b, ...patch } : b))
    );
  }

  function addBinding() {
    const target = variables[0]?.name ?? "value";
    setBindings((prev) => [...prev, { targetVariable: target, expression: "" }]);
  }

  function removeBinding(index: number) {
    setBindings((prev) => prev.filter((_, i) => i !== index));
  }

  function patchEvent(index: number, patch: Partial<EventDescriptor>) {
    setEvents((prev) => prev.map((e, i) => (i === index ? { ...e, ...patch } : e)));
  }

  function addEvent() {
    setEvents((prev) => [
      ...prev,
      {
        name: `event${prev.length + 1}`,
        description: "",
        payloadSchema: {
          name: "payload",
          fields: [{ name: "message", type: "STRING" }],
        },
        level: "INFO",
      },
    ]);
  }

  function removeEvent(index: number) {
    setEvents((prev) => prev.filter((_, i) => i !== index));
  }

  function patchFunction(index: number, patch: Partial<FunctionDescriptor>) {
    setFunctions((prev) => prev.map((f, i) => (i === index ? { ...f, ...patch } : f)));
  }

  function addFunction() {
    setFunctions((prev) => [
      ...prev,
      {
        name: `fn${prev.length + 1}`,
        description: "",
        inputSchema: { name: "input", fields: [] },
        outputSchema: {
          name: "output",
          fields: [{ name: "ok", type: "BOOLEAN" }],
        },
      },
    ]);
  }

  function removeFunction(index: number) {
    setFunctions((prev) => prev.filter((_, i) => i !== index));
  }

  return (
    <div className="model-detail">
      <div className="model-meta-grid">
        <div>
          <span className="model-meta-label">Тип модели</span>
          <code>{model.type}</code>
        </div>
        <div>
          <span className="model-meta-label">Целевой ObjectType</span>
          <code>{model.targetObjectType}</code>
        </div>
        <div>
          <span className="model-meta-label">Путь в дереве</span>
          <code>{model.objectPath}</code>
        </div>
        <div>
          <span className="model-meta-label">Встроенная</span>
          <span>{isBuiltin ? "да" : "нет"}</span>
        </div>
        {model.parameters.modelVersion && (
          <div>
            <span className="model-meta-label">modelVersion</span>
            <code>{model.parameters.modelVersion}</code>
          </div>
        )}
        {model.parameters.extendsModelId && (
          <div>
            <span className="model-meta-label">extendsModelId</span>
            <code>{model.parameters.extendsModelId}</code>
          </div>
        )}
      </div>

      {canManage && (
        <form
          className="model-edit-form"
          onSubmit={(e) => {
            e.preventDefault();
            saveMutation.mutate();
          }}
        >
          <label>
            Описание
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              readOnly={isBuiltin}
            />
          </label>
          <label>
            Условие применимости (CEL)
            <input
              value={suitability}
              onChange={(e) => setSuitability(e.target.value)}
              placeholder="пусто = по targetObjectType"
            />
          </label>
          {!isBuiltin && (
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              Сохранить метаданные
            </button>
          )}
          {!isBuiltin && definitionDirty && (
            <p className="hint">Изменено определение модели — сохраните изменения.</p>
          )}
          {saveMutation.error && (
            <p className="hint error">{String(saveMutation.error)}</p>
          )}
        </form>
      )}

      <section className="model-section">
        <div className="model-section-header">
          <h4>Переменные ({variables.length})</h4>
          {canManage && !isBuiltin && (
            <button type="button" className="btn small" onClick={addVariable}>
              + Переменная
            </button>
          )}
        </div>
        {variables.length === 0 ? (
          <p className="hint">Нет переменных</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Имя</th>
                <th>Группа</th>
                <th>Запись</th>
                <th>История</th>
                <th>Хранение</th>
                <th>По умолчанию</th>
                <th>Binding</th>
                {canManage && !isBuiltin && <th />}
              </tr>
            </thead>
            <tbody>
              {variables.map((v) => (
                <tr key={v.name}>
                  <td>
                    {canManage && !isBuiltin ? (
                      <input
                        className="model-inline-input"
                        value={v.name}
                        onChange={(e) =>
                          setVariables((prev) =>
                            prev.map((item) =>
                              item.name === v.name ? { ...item, name: e.target.value } : item
                            )
                          )
                        }
                      />
                    ) : (
                      <code>{v.name}</code>
                    )}
                    {v.description && (
                      <span className="model-var-desc">{v.description}</span>
                    )}
                  </td>
                  <td>{v.group}</td>
                  <td>{v.writable ? "да" : "нет"}</td>
                  <td>
                    {canManage && !isBuiltin ? (
                      <label className="checkbox-label inline">
                        <input
                          type="checkbox"
                          checked={v.historyEnabled ?? false}
                          onChange={(e) =>
                            patchVariableHistory(v.name, {
                              historyEnabled: e.target.checked,
                              historyRetentionDays: v.historyRetentionDays ?? null,
                            })
                          }
                        />
                        да
                      </label>
                    ) : (
                      v.historyEnabled ? "да" : "нет"
                    )}
                  </td>
                  <td>
                    {canManage && !isBuiltin && v.historyEnabled ? (
                      <input
                        type="number"
                        min={1}
                        max={3650}
                        className="model-retention-input"
                        placeholder="90"
                        value={v.historyRetentionDays ?? ""}
                        onChange={(e) => {
                          const raw = e.target.value.trim();
                          patchVariableHistory(v.name, {
                            historyEnabled: true,
                            historyRetentionDays: raw === "" ? null : Number.parseInt(raw, 10),
                          });
                        }}
                      />
                    ) : v.historyEnabled ? (
                      formatHistoryRetention(v.historyRetentionDays)
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="mono small">
                    {v.defaultValue ? recordDisplayValue(v.defaultValue) : "—"}
                  </td>
                  <td className="mono small">
                    {canManage && !isBuiltin ? (
                      <input
                        className="model-inline-input mono"
                        value={v.defaultBinding ?? ""}
                        placeholder="CEL / counterRate(...)"
                        onChange={(e) => patchVariableBinding(v.name, e.target.value)}
                      />
                    ) : (
                      v.defaultBinding ?? "—"
                    )}
                  </td>
                  {canManage && !isBuiltin && (
                    <td>
                      <button
                        type="button"
                        className="btn small danger"
                        onClick={() => removeVariable(v.name)}
                      >
                        ✕
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {canManage && !isBuiltin && variables.length > 0 && (
          <div className="model-var-history-actions">
            <button
              type="button"
              className="btn"
              disabled={!definitionDirty || saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              Сохранить определение модели
            </button>
          </div>
        )}
      </section>

      <section className="model-section">
        <div className="model-section-header">
          <h4>Bindings ({bindings.length})</h4>
          {canManage && !isBuiltin && (
            <button type="button" className="btn small" onClick={addBinding}>
              + Binding
            </button>
          )}
        </div>
        {bindings.length === 0 ? (
          <p className="hint">Нет вычисляемых привязок</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Переменная</th>
                <th>CEL-выражение</th>
                {canManage && !isBuiltin && <th />}
              </tr>
            </thead>
            <tbody>
              {bindings.map((b, index) => (
                <tr key={`${b.targetVariable}-${index}`}>
                  <td>
                    {canManage && !isBuiltin ? (
                      <input
                        className="model-inline-input"
                        value={b.targetVariable}
                        onChange={(e) => patchBinding(index, { targetVariable: e.target.value })}
                      />
                    ) : (
                      <code>{b.targetVariable}</code>
                    )}
                  </td>
                  <td className="mono small">
                    {canManage && !isBuiltin ? (
                      <input
                        className="model-inline-input mono"
                        value={b.expression}
                        onChange={(e) => patchBinding(index, { expression: e.target.value })}
                      />
                    ) : (
                      b.expression
                    )}
                  </td>
                  {canManage && !isBuiltin && (
                    <td>
                      <button
                        type="button"
                        className="btn small danger"
                        onClick={() => removeBinding(index)}
                      >
                        ✕
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="model-section model-section-inline">
        <div>
          <div className="model-section-header">
            <h4>События ({events.length})</h4>
            {canManage && !isBuiltin && (
              <button type="button" className="btn small" onClick={addEvent}>
                + Событие
              </button>
            )}
          </div>
          {events.length === 0 ? (
            <p className="hint">Нет событий</p>
          ) : (
            <ul className="event-list editable-list">
              {events.map((e, index) => (
                <li key={`${e.name}-${index}`}>
                  {canManage && !isBuiltin ? (
                    <>
                      <input
                        className="model-inline-input"
                        value={e.name}
                        onChange={(ev) => patchEvent(index, { name: ev.target.value })}
                      />
                      <input
                        className="model-inline-input"
                        value={e.description}
                        placeholder="описание"
                        onChange={(ev) => patchEvent(index, { description: ev.target.value })}
                      />
                      <button
                        type="button"
                        className="btn small danger"
                        onClick={() => removeEvent(index)}
                      >
                        ✕
                      </button>
                    </>
                  ) : (
                    <>
                      <code>{e.name}</code>
                      <span className="model-var-desc">{e.description}</span>
                    </>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <div className="model-section-header">
            <h4>Функции ({functions.length})</h4>
            {canManage && !isBuiltin && (
              <button type="button" className="btn small" onClick={addFunction}>
                + Функция
              </button>
            )}
          </div>
          {functions.length === 0 ? (
            <p className="hint">Нет функций</p>
          ) : (
            <ul className="event-list editable-list">
              {functions.map((f, index) => (
                <li key={`${f.name}-${index}`}>
                  {canManage && !isBuiltin ? (
                    <>
                      <input
                        className="model-inline-input"
                        value={f.name}
                        onChange={(ev) => patchFunction(index, { name: ev.target.value })}
                      />
                      <input
                        className="model-inline-input"
                        value={f.description}
                        placeholder="описание"
                        onChange={(ev) => patchFunction(index, { description: ev.target.value })}
                      />
                      <button
                        type="button"
                        className="btn small danger"
                        onClick={() => removeFunction(index)}
                      >
                        ✕
                      </button>
                    </>
                  ) : (
                    <>
                      <code>{f.name}</code>
                      <span className="model-var-desc">{f.description}</span>
                    </>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      {canManage && (
        <section className="model-section model-actions">
          <h4>Действия</h4>
          <div className="model-action-block">
            <p className="hint">
              <strong>Apply</strong> — влить переменные, события и bindings в существующий объект (RELATIVE).
            </p>
            <div className="model-action-row">
              <input
                value={applyPath}
                onChange={(e) => setApplyPath(e.target.value)}
                placeholder="objectPath, напр. root.platform.devices.my-sensor"
              />
              <button
                type="button"
                className="btn primary"
                disabled={!applyPath.trim() || applyMutation.isPending}
                onClick={() => applyMutation.mutate()}
              >
                Применить
              </button>
            </div>
            {applyMutation.error && (
              <p className="hint error">{String(applyMutation.error)}</p>
            )}
          </div>

          <div className="model-action-block">
            <p className="hint">
              <strong>Upgrade</strong> — пере-применить текущую версию модели к экземплярам (
              {model.parameters.modelVersion ? `v${model.parameters.modelVersion}` : "без version tag"}).
            </p>
            {instancesQuery.data && instancesQuery.data.length > 0 && (
              <ul className="model-instance-list">
                {instancesQuery.data.map((row) => (
                  <li key={row.objectPath}>
                    <code>{row.objectPath}</code>
                  </li>
                ))}
              </ul>
            )}
            <div className="model-action-row">
              <button
                type="button"
                className="btn"
                disabled={!applyPath.trim() || upgradeOneMutation.isPending}
                onClick={() => upgradeOneMutation.mutate()}
              >
                Upgrade path
              </button>
              <button
                type="button"
                className="btn primary"
                disabled={upgradeAllMutation.isPending || (instancesQuery.data?.length ?? 0) === 0}
                onClick={() => upgradeAllMutation.mutate()}
              >
                Upgrade all ({instancesQuery.data?.length ?? 0})
              </button>
            </div>
            {(upgradeOneMutation.error || upgradeAllMutation.error) && (
              <p className="hint error">
                {String(upgradeOneMutation.error ?? upgradeAllMutation.error)}
              </p>
            )}
            {upgradeAllMutation.data && (
              <p className="hint">
                Обновлено экземпляров: {upgradeAllMutation.data.count}
              </p>
            )}
          </div>

          {model.type === "INSTANCE" && (
            <div className="model-action-block">
              <p className="hint">
                <strong>Instantiate</strong> — создать новый дочерний объект по модели.
              </p>
              <div className="model-action-row">
                <input
                  value={parentPath}
                  onChange={(e) => setParentPath(e.target.value)}
                  placeholder="parentPath"
                />
                <input
                  value={instanceName}
                  onChange={(e) => setInstanceName(e.target.value)}
                  placeholder="имя экземпляра"
                />
                <button
                  type="button"
                  className="btn primary"
                  disabled={!parentPath.trim() || !instanceName.trim() || instantiateMutation.isPending}
                  onClick={() => instantiateMutation.mutate()}
                >
                  Создать экземпляр
                </button>
              </div>
              {instantiateMutation.error && (
                <p className="hint error">{String(instantiateMutation.error)}</p>
              )}
            </div>
          )}

          {!isBuiltin && (
            <div className="model-action-block">
              <button
                type="button"
                className="btn danger"
                disabled={deleteMutation.isPending}
                onClick={() => {
                  if (confirm(`Удалить модель «${model.name}»?`)) {
                    deleteMutation.mutate();
                  }
                }}
              >
                Удалить модель
              </button>
            </div>
          )}
        </section>
      )}
    </div>
  );
}

function ModelsCatalog({
  canManage,
  selectedPath,
  onSelectPath,
}: {
  canManage: boolean;
  selectedPath: string;
  onSelectPath?: (path: string) => void;
}) {
  const queryClient = useQueryClient();
  const modelsQuery = useQuery({
    queryKey: ["models"],
    queryFn: fetchModels,
  });

  const createMutation = useMutation({
    mutationFn: createModel,
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["models"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(created.objectPath);
    },
  });

  const fromObjectMutation = useMutation({
    mutationFn: ({
      sourcePath,
      modelName,
      description,
      type,
    }: {
      sourcePath: string;
      modelName: string;
      description: string;
      type: "RELATIVE" | "INSTANCE";
    }) => createModelFromObject(sourcePath, modelName, description, type),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["models"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(created.objectPath);
    },
  });

  const sorted = useMemo(
    () => [...(modelsQuery.data ?? [])].sort((a, b) => a.name.localeCompare(b.name)),
    [modelsQuery.data]
  );

  return (
    <div className="models-catalog">
      <p className="hint">
        Модели — чертежи структуры объектов. Узлы в дереве — закладки; полное определение здесь.
      </p>
      {modelsQuery.error && (
        <p className="hint error">{String(modelsQuery.error)}</p>
      )}
      <table className="data-table">
        <thead>
          <tr>
            <th>Имя</th>
            <th>Тип</th>
            <th>ObjectType</th>
            <th>Переменные</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((m) => (
            <tr key={m.id}>
              <td>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectPath?.(m.objectPath)}
                >
                  <code>{m.name}</code>
                </button>
              </td>
              <td>{m.type}</td>
              <td>{m.targetObjectType}</td>
              <td>{m.variables.length}</td>
              <td className="mono small">{m.description}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {canManage && (
        <>
          <form
            className="model-create-form"
            onSubmit={(e) => {
              e.preventDefault();
              const form = e.currentTarget;
              const data = new FormData(form);
              createMutation.mutate({
                name: String(data.get("name") ?? ""),
                description: String(data.get("description") ?? ""),
                type: String(data.get("type") ?? "RELATIVE") as "RELATIVE" | "INSTANCE",
                targetObjectType: String(data.get("targetObjectType") ?? "CUSTOM") as ObjectType,
              });
              form.reset();
            }}
          >
            <h4>Новая пустая модель</h4>
            <div className="model-form-grid">
              <input name="name" placeholder="имя-модели" required pattern="[a-zA-Z0-9._-]+" />
              <input name="description" placeholder="описание" />
              <select name="type" defaultValue="RELATIVE">
                <option value="RELATIVE">RELATIVE</option>
                <option value="INSTANCE">INSTANCE</option>
                <option value="ABSOLUTE">ABSOLUTE</option>
              </select>
              <select name="targetObjectType" defaultValue="CUSTOM">
                {OBJECT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
              <button type="submit" className="btn primary" disabled={createMutation.isPending}>
                Создать
              </button>
            </div>
            {createMutation.error && (
              <p className="hint error">{String(createMutation.error)}</p>
            )}
          </form>

          <form
            className="model-create-form"
            onSubmit={(e) => {
              e.preventDefault();
              const form = e.currentTarget;
              const data = new FormData(form);
              fromObjectMutation.mutate({
                sourcePath: String(data.get("sourcePath") ?? ""),
                modelName: String(data.get("modelName") ?? ""),
                description: String(data.get("description") ?? ""),
                type: String(data.get("type") ?? "RELATIVE") as "RELATIVE" | "INSTANCE",
              });
              form.reset();
            }}
          >
            <h4>Сохранить объект как модель</h4>
            <p className="hint">
              Снимок переменных, событий, функций и bindings с объекта{" "}
              {selectedPath !== MODELS_ROOT ? (
                <code>{selectedPath}</code>
              ) : (
                "(укажите путь)"
              )}
            </p>
            <div className="model-form-grid">
              <input
                name="sourcePath"
                placeholder="sourcePath"
                defaultValue={selectedPath !== MODELS_ROOT ? selectedPath : ""}
                required
              />
              <input name="modelName" placeholder="имя новой модели" required />
              <input name="description" placeholder="описание" />
              <select name="type" defaultValue="RELATIVE">
                <option value="RELATIVE">RELATIVE</option>
                <option value="INSTANCE">INSTANCE</option>
              </select>
              <button type="submit" className="btn" disabled={fromObjectMutation.isPending}>
                Экспортировать
              </button>
            </div>
            {fromObjectMutation.error && (
              <p className="hint error">{String(fromObjectMutation.error)}</p>
            )}
          </form>
        </>
      )}
    </div>
  );
}

export default function ModelEditorPanel({
  selectedPath,
  canManage,
  onSelectPath,
  onClose,
  title,
}: ModelEditorPanelProps) {
  const modelName = modelNameFromPath(selectedPath);
  const isCatalog = selectedPath === MODELS_ROOT;
  const headerTitle = isCatalog
    ? "Каталог моделей"
    : modelName
      ? `Модель ${modelName}`
      : (title ?? selectedPath.split(".").pop() ?? "Модель");

  const modelQuery = useQuery({
    queryKey: ["model", modelName],
    queryFn: () => fetchModelByName(modelName!),
    enabled: !!modelName,
  });

  return (
    <div className="model-editor-shell">
      <header className="model-editor-header">
        <div>
          <h2>{headerTitle}</h2>
          <code className="path-code">{selectedPath}</code>
        </div>
        {onClose && (
          <button type="button" className="btn" onClick={onClose}>
            Закрыть
          </button>
        )}
      </header>

      <div className="model-editor-body">
        {isCatalog ? (
          <ModelsCatalog
            canManage={canManage}
            selectedPath={selectedPath}
            onSelectPath={onSelectPath}
          />
        ) : !modelName ? (
          <p className="hint">Выберите модель в дереве</p>
        ) : (
          <>
            {!canManage && (
              <p className="hint">Редактирование моделей доступно только admin.</p>
            )}
            {modelQuery.isLoading && <p className="hint">Загрузка модели…</p>}
            {modelQuery.error && (
              <p className="hint error">
                Модель не найдена в реестре. Возможно, узел дерева не синхронизирован.
                <br />
                {String(modelQuery.error)}
              </p>
            )}
            {modelQuery.data && (
              <ModelDetail
                model={modelQuery.data}
                canManage={canManage}
                onSelectPath={onSelectPath}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}
