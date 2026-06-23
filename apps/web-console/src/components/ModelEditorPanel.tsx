import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  applyModel,
  createModel,
  createModelFromObject,
  deleteModel,
  fetchModelByName,
  fetchModelInstances,
  fetchAbsoluteModelInstance,
  fetchModelDiff,
  fetchModelMergePreview,
  fetchRelativeModels,
  fetchAbsoluteModels,
  fetchInstanceTypes,
  instantiateModel,
  updateModel,
  upgradeModel,
  upgradeModelInstances,
} from "../api/models";
import type { EventDescriptor, FunctionDescriptor, ObjectType } from "../types";
import {
  ABSOLUTE_MODELS_ROOT,
  BUILTIN_MODEL_NAMES,
  RELATIVE_MODELS_ROOT,
  catalogRootForModelType,
  isModelCatalogRoot,
  modelNameFromPath,
  type ModelBindingRule,
  type ModelCatalogRoot,
  type ModelDto,
  type ModelType,
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
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [description, setDescription] = useState(model.description);
  const [suitability, setSuitability] = useState(model.suitabilityExpression);
  const [applyPath, setApplyPath] = useState("");
  const [theirsModelId, setTheirsModelId] = useState("");
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

  const diffQuery = useQuery({
    queryKey: ["model-diff", model.id, applyPath],
    queryFn: () => fetchModelDiff(model.id, applyPath.trim()),
    enabled: Boolean(applyPath.trim()),
  });

  const mergePreviewQuery = useQuery({
    queryKey: ["model-merge-preview", model.id, theirsModelId, applyPath],
    queryFn: () => fetchModelMergePreview(model.id, theirsModelId.trim(), applyPath.trim()),
    enabled: Boolean(applyPath.trim() && theirsModelId.trim()),
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

  const absoluteSingletonMutation = useMutation({
    mutationFn: () => fetchAbsoluteModelInstance(model.id),
    onSuccess: (instance) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(instance.path);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteModel(model.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["models"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(catalogRootForModelType(model.type));
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

  function patchBinding(index: number, patch: Partial<ModelBindingRule>) {
    setBindings((prev) =>
      prev.map((b, i) => (i === index ? { ...b, ...patch } : b))
    );
  }

  function addBinding() {
    const target = variables[0]?.name ?? "value";
    const id = `rule-${target}`;
    setBindings((prev) => [
      ...prev,
      {
        id,
        name: target,
        enabled: true,
        order: prev.length,
        expression: "",
        targetVariable: target,
        targetField: "value",
      },
    ]);
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
          <span className="model-meta-label">{t("inspector:model.meta.type")}</span>
          <code>{model.type}</code>
        </div>
        <div>
          <span className="model-meta-label">{t("inspector:model.meta.targetObjectType")}</span>
          <code>{model.targetObjectType}</code>
        </div>
        <div>
          <span className="model-meta-label">{t("inspector:model.meta.treePath")}</span>
          <code>{model.objectPath}</code>
        </div>
        <div>
          <span className="model-meta-label">{t("inspector:model.meta.builtin")}</span>
          <span>{isBuiltin ? t("common:action.yes") : t("common:action.no")}</span>
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
            {t("common:field.description")}
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              readOnly={isBuiltin}
            />
          </label>
          <label>
            {t("inspector:model.applicabilityCel")}
            <input
              value={suitability}
              onChange={(e) => setSuitability(e.target.value)}
              placeholder={t("inspector:model.applicabilityPlaceholder")}
            />
          </label>
          {!isBuiltin && (
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              {t("inspector:model.saveMetadata")}
            </button>
          )}
          {!isBuiltin && definitionDirty && (
            <p className="hint">{t("inspector:model.definitionChanged")}</p>
          )}
          {saveMutation.error && (
            <p className="hint error">{String(saveMutation.error)}</p>
          )}
        </form>
      )}

      <section className="model-section">
        <div className="model-section-header">
          <h4>{t("inspector:model.variablesTitle", { count: variables.length })}</h4>
          {canManage && !isBuiltin && (
            <button type="button" className="btn small" onClick={addVariable}>
              {t("inspector:variables.add")}
            </button>
          )}
        </div>
        {variables.length === 0 ? (
          <p className="hint">{t("inspector:variables.empty")}</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>{t("common:table.name")}</th>
                <th>{t("inspector:variables.column.group")}</th>
                <th>{t("inspector:variables.column.record")}</th>
                <th>{t("inspector:variables.column.history")}</th>
                <th>{t("inspector:variables.column.storage")}</th>
                <th>{t("inspector:variables.column.default")}</th>
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
                  <td>{v.writable ? t("common:action.yes") : t("common:action.no")}</td>
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
                        {t("common:action.yes")}
                      </label>
                    ) : (
                      v.historyEnabled ? t("common:action.yes") : t("common:action.no")
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
              {t("inspector:model.saveDefinition")}
            </button>
          </div>
        )}
      </section>

      <section className="model-section">
        <div className="model-section-header">
          <h4>{t("inspector:model.bindingsTitle", { count: bindings.length })}</h4>
          {canManage && !isBuiltin && (
            <button type="button" className="btn small" onClick={addBinding}>
              + Binding
            </button>
          )}
        </div>
        {bindings.length === 0 ? (
          <p className="hint">{t("inspector:bindings.computedEmpty")}</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>{t("common:table.id")}</th>
                <th>{t("inspector:bindings.column.variable")}</th>
                <th>{t("inspector:bindings.column.expression")}</th>
                {canManage && !isBuiltin && <th />}
              </tr>
            </thead>
            <tbody>
              {bindings.map((b, index) => (
                <tr key={`${b.id}-${index}`}>
                  <td>
                    {canManage && !isBuiltin ? (
                      <input
                        className="model-inline-input mono"
                        value={b.id}
                        onChange={(e) => patchBinding(index, { id: e.target.value })}
                      />
                    ) : (
                      <code>{b.id}</code>
                    )}
                  </td>
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
            <h4>{t("inspector:model.eventsTitle", { count: events.length })}</h4>
            {canManage && !isBuiltin && (
              <button type="button" className="btn small" onClick={addEvent}>
                {t("inspector:events.add")}
              </button>
            )}
          </div>
          {events.length === 0 ? (
            <p className="hint">{t("inspector:events.empty")}</p>
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
                        placeholder={t("inspector:model.descriptionPlaceholder")}
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
            <h4>{t("inspector:model.functionsTitle", { count: functions.length })}</h4>
            {canManage && !isBuiltin && (
              <button type="button" className="btn small" onClick={addFunction}>
                {t("inspector:functions.add")}
              </button>
            )}
          </div>
          {functions.length === 0 ? (
            <p className="hint">{t("inspector:functions.empty")}</p>
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
                        placeholder={t("inspector:model.descriptionPlaceholder")}
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
          <h4>{t("inspector:model.actionsTitle")}</h4>
          <div className="model-action-block">
            <p className="hint">
              {t("inspector:model.applyHint")}
            </p>
            <div className="model-action-row">
              <input
                value={applyPath}
                onChange={(e) => setApplyPath(e.target.value)}
                placeholder={t("inspector:model.applyPathPlaceholder")}
              />
              <button
                type="button"
                className="btn primary"
                disabled={!applyPath.trim() || applyMutation.isPending}
                onClick={() => applyMutation.mutate()}
              >
                {t("inspector:model.apply")}
              </button>
            </div>
            {applyMutation.error && (
              <p className="hint error">{String(applyMutation.error)}</p>
            )}
          </div>

          <div className="model-action-block">
            <p className="hint">
              {t("inspector:model.upgradeHint", {
                version: model.parameters.modelVersion
                  ? `v${model.parameters.modelVersion}`
                  : t("inspector:model.upgradeNoVersion"),
              })}
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
            {applyPath.trim() && (
              <label className="full">
                Merge preview — theirs model ID
                <input
                  value={theirsModelId}
                  onChange={(e) => setTheirsModelId(e.target.value)}
                  placeholder="UUID vendor extension model"
                />
              </label>
            )}
            {mergePreviewQuery.data && mergePreviewQuery.data.conflictCount > 0 && (
              <div className="model-diff-preview hint">
                <strong>Merge conflicts</strong> ({mergePreviewQuery.data.conflictCount})
                <ul>
                  {mergePreviewQuery.data.variableConflicts.map((c) => (
                    <li key={c.name}>
                      <code>{c.name}</code>: {c.baseSchema} vs {c.theirsSchema}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {applyPath.trim() && diffQuery.data && (
              <div className="model-diff-preview hint">
                <strong>Diff preview</strong> (v{diffQuery.data.modelVersion} →{" "}
                <code>{diffQuery.data.objectPath}</code>)
                <ul>
                  {diffQuery.data.variablesToAdd.length > 0 && (
                    <li>+ variables: {diffQuery.data.variablesToAdd.join(", ")}</li>
                  )}
                  {diffQuery.data.eventsToAdd.length > 0 && (
                    <li>+ events: {diffQuery.data.eventsToAdd.join(", ")}</li>
                  )}
                  {diffQuery.data.functionsToAdd.length > 0 && (
                    <li>+ functions: {diffQuery.data.functionsToAdd.join(", ")}</li>
                  )}
                  {diffQuery.data.variablesOnlyOnObject.length > 0 && (
                    <li>object-only variables: {diffQuery.data.variablesOnlyOnObject.join(", ")}</li>
                  )}
                  {diffQuery.data.variablesToAdd.length === 0 &&
                    diffQuery.data.eventsToAdd.length === 0 &&
                    diffQuery.data.functionsToAdd.length === 0 && (
                      <li>{t("inspector:model.upgradeNoNewFields")}</li>
                    )}
                </ul>
              </div>
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
                {t("inspector:model.upgradeCount", { count: upgradeAllMutation.data.count })}
              </p>
            )}
          </div>

          {model.type === "INSTANCE" && (
            <div className="model-action-block">
              <p className="hint">
                {t("inspector:model.instantiateHint")}
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
                  placeholder={t("inspector:model.instanceNamePlaceholder")}
                />
                <button
                  type="button"
                  className="btn primary"
                  disabled={!parentPath.trim() || !instanceName.trim() || instantiateMutation.isPending}
                  onClick={() => instantiateMutation.mutate()}
                >
                  {t("inspector:model.createInstance")}
                </button>
              </div>
              {instantiateMutation.error && (
                <p className="hint error">{String(instantiateMutation.error)}</p>
              )}
            </div>
          )}

          {model.type === "ABSOLUTE" && (
            <div className="model-action-block">
              <p className="hint">
                {t("inspector:model.singletonHint")}{" "}
                <code>
                  {model.parameters.absoluteInstancePath ||
                    `root.platform.instances.${model.name}`}
                </code>
              </p>
              <button
                type="button"
                className="btn primary"
                disabled={absoluteSingletonMutation.isPending}
                onClick={() => absoluteSingletonMutation.mutate()}
              >
                {t("inspector:model.openSingleton")}
              </button>
              {absoluteSingletonMutation.error && (
                <p className="hint error">{String(absoluteSingletonMutation.error)}</p>
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
                  if (confirm(t("inspector:model.confirmDelete", { name: model.name }))) {
                    deleteMutation.mutate();
                  }
                }}
              >
                {t("inspector:model.delete")}
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
  catalogRoot,
  onSelectPath,
}: {
  canManage: boolean;
  selectedPath: string;
  catalogRoot: ModelCatalogRoot;
  onSelectPath?: (path: string) => void;
}) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const modelsQuery = useQuery({
    queryKey: ["models", catalogRoot],
    queryFn: () => {
      if (catalogRoot === RELATIVE_MODELS_ROOT) {
        return fetchRelativeModels();
      }
      if (catalogRoot === ABSOLUTE_MODELS_ROOT) {
        return fetchAbsoluteModels();
      }
      return fetchInstanceTypes();
    },
  });

  const defaultCreateType: ModelType =
    catalogRoot === RELATIVE_MODELS_ROOT
      ? "RELATIVE"
      : catalogRoot === ABSOLUTE_MODELS_ROOT
        ? "ABSOLUTE"
        : "INSTANCE";

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
        {t("inspector:model.listHint")}
      </p>
      {modelsQuery.error && (
        <p className="hint error">{String(modelsQuery.error)}</p>
      )}
      <table className="data-table">
        <thead>
          <tr>
            <th>{t("common:table.name")}</th>
            <th>{t("common:table.type")}</th>
            <th>ObjectType</th>
            <th>{t("inspector:model.variablesColumn")}</th>
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
            <h4>{t("inspector:model.newEmptyTitle")}</h4>
            <div className="model-form-grid">
              <input name="name" placeholder={t("inspector:model.namePlaceholder")} required pattern="[a-zA-Z0-9._-]+" />
              <input name="description" placeholder={t("inspector:model.descriptionPlaceholder")} />
              <select name="type" defaultValue={defaultCreateType}>
                <option value="RELATIVE">RELATIVE</option>
                <option value="INSTANCE">INSTANCE</option>
                <option value="ABSOLUTE">ABSOLUTE</option>
              </select>
              <select name="targetObjectType" defaultValue="CUSTOM">
                {OBJECT_TYPES.map((objectType) => (
                  <option key={objectType} value={objectType}>
                    {objectType}
                  </option>
                ))}
              </select>
              <button type="submit" className="btn primary" disabled={createMutation.isPending}>
                {t("common:action.create")}
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
            <h4>{t("inspector:model.exportObjectTitle")}</h4>
            <p className="hint">
              {t("inspector:model.exportObjectHint")}{" "}
              {selectedPath !== catalogRoot ? (
                <code>{selectedPath}</code>
              ) : (
                t("inspector:model.exportPathMissing")
              )}
            </p>
            <div className="model-form-grid">
              <input
                name="sourcePath"
                placeholder="sourcePath"
                defaultValue={selectedPath !== catalogRoot ? selectedPath : ""}
                required
              />
              <input name="modelName" placeholder={t("inspector:model.newModelNamePlaceholder")} required />
              <input name="description" placeholder={t("inspector:model.descriptionPlaceholder")} />
              <select
                name="type"
                defaultValue={defaultCreateType === "ABSOLUTE" ? "INSTANCE" : defaultCreateType}
              >
                <option value="RELATIVE">RELATIVE</option>
                <option value="INSTANCE">INSTANCE</option>
              </select>
              <button type="submit" className="btn" disabled={fromObjectMutation.isPending}>
                {t("inspector:model.export")}
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
  const { t } = useTranslation(["inspector", "common"]);
  const modelName = modelNameFromPath(selectedPath);
  const catalogRoot = isModelCatalogRoot(selectedPath) ? selectedPath : null;
  const isCatalog = catalogRoot != null;
  const headerTitle = isCatalog
    ? catalogRoot === RELATIVE_MODELS_ROOT
      ? t("inspector:model.relativeTitle")
      : catalogRoot === ABSOLUTE_MODELS_ROOT
        ? t("inspector:model.absoluteTitle")
        : t("inspector:model.objectTypesTitle")
    : modelName
      ? t("inspector:model.titleNamed", { name: modelName })
      : (title ?? selectedPath.split(".").pop() ?? t("inspector:model.titleDefault"));

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
            {t("common:action.close")}
          </button>
        )}
      </header>

      <div className="model-editor-body">
        {isCatalog && catalogRoot ? (
          <ModelsCatalog
            canManage={canManage}
            selectedPath={selectedPath}
            catalogRoot={catalogRoot}
            onSelectPath={onSelectPath}
          />
        ) : !modelName ? (
          <p className="hint">{t("inspector:model.selectInTree")}</p>
        ) : (
          <>
            {!canManage && (
              <p className="hint">{t("inspector:model.adminOnly")}</p>
            )}
            {modelQuery.isLoading && <p className="hint">{t("inspector:model.loading")}</p>}
            {modelQuery.error && (
              <p className="hint error">
                {t("inspector:model.notFound")}
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
