import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  FUNCTION_SCRIPT_STEP_TYPES,
  SCRIPT_TEMPLATES,
  STEP_CATEGORIES,
  applyWhenConditionKind,
  defaultStep,
  newStepId,
  parseScriptBody,
  readKeyValue,
  readNestedSteps,
  readStringList,
  readWhenConditionKind,
  serializeScriptBody,
  serializeStepsArray,
  unwrapSteps,
  wrapSteps,
  writeKeyValue,
  type EditableScriptStep,
  type FunctionScriptStepType,
  type ScriptStep,
  type ScriptStepCategory,
  type WhenConditionKind,
} from "../../utils/functionScriptSteps";

interface FunctionScriptStepsEditorProps {
  value: string;
  onChange: (next: string) => void;
}

interface StepListEditorProps {
  steps: EditableScriptStep[];
  onChange: (next: EditableScriptStep[]) => void;
  nested?: boolean;
}

function patchStep(items: EditableScriptStep[], id: string, patch: Partial<ScriptStep>): EditableScriptStep[] {
  return items.map((item) =>
    item.id === id ? { ...item, step: { ...item.step, ...patch } } : item
  );
}

function moveStep(items: EditableScriptStep[], index: number, delta: number): EditableScriptStep[] {
  const target = index + delta;
  if (target < 0 || target >= items.length) return items;
  const next = [...items];
  const [item] = next.splice(index, 1);
  next.splice(target, 0, item);
  return next;
}

function TextField({
  label,
  value,
  onChange,
  placeholder,
  hint,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  hint?: string;
}) {
  return (
    <label className="field-block">
      <span className="field-label">{label}</span>
      <input
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        spellCheck={false}
      />
      {hint && <span className="hint inline">{hint}</span>}
    </label>
  );
}

function TextAreaField({
  label,
  value,
  onChange,
  rows = 3,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  rows?: number;
  placeholder?: string;
}) {
  return (
    <label className="field-block full">
      <span className="field-label">{label}</span>
      <textarea
        className="json-editor compact"
        rows={rows}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        spellCheck={false}
      />
    </label>
  );
}

function StringListField({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: unknown;
  onChange: (next: string[]) => void;
  placeholder?: string;
}) {
  const { t } = useTranslation("inspector");
  const items = readStringList(value);

  return (
    <div className="widget-editor-structured full">
      <span className="field-caption">{label}</span>
      <div className="widget-editor-list">
        {items.map((item, index) => (
          <div key={index} className="widget-editor-list-row script-step-single-col">
            <input
              value={item}
              placeholder={placeholder}
              onChange={(e) => {
                const next = [...items];
                next[index] = e.target.value;
                onChange(next);
              }}
              spellCheck={false}
            />
            <button
              type="button"
              className="btn small danger"
              aria-label={t("scriptSteps.removeRow")}
              onClick={() => onChange(items.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
      </div>
      <button
        type="button"
        className="btn small"
        onClick={() => onChange([...items, ""])}
      >
        {t("scriptSteps.addItem")}
      </button>
    </div>
  );
}

function KeyValueField({
  label,
  value,
  onChange,
  keyPlaceholder,
  valuePlaceholder,
}: {
  label: string;
  value: unknown;
  onChange: (next: Record<string, string>) => void;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
}) {
  const { t } = useTranslation("inspector");
  const pairs = readKeyValue(value);

  const commit = (rows: { key: string; val: string }[]) => {
    onChange(writeKeyValue(rows));
  };

  return (
    <div className="widget-editor-structured full">
      <span className="field-caption">{label}</span>
      <div className="widget-editor-list">
        {pairs.map((row, index) => (
          <div key={index} className="widget-editor-list-row widget-editor-kv-row">
            <input
              value={row.key}
              placeholder={keyPlaceholder ?? "key"}
              onChange={(e) => {
                const next = [...pairs];
                next[index] = { ...next[index], key: e.target.value };
                commit(next);
              }}
              spellCheck={false}
            />
            <input
              value={row.val}
              placeholder={valuePlaceholder ?? "value"}
              onChange={(e) => {
                const next = [...pairs];
                next[index] = { ...next[index], val: e.target.value };
                commit(next);
              }}
              spellCheck={false}
            />
            <button
              type="button"
              className="btn small danger"
              aria-label={t("scriptSteps.removeRow")}
              onClick={() => commit(pairs.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
      </div>
      <button
        type="button"
        className="btn small"
        onClick={() => commit([...pairs, { key: "", val: "" }])}
      >
        {t("scriptSteps.addPair")}
      </button>
    </div>
  );
}

function NestedStepsField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: unknown;
  onChange: (next: ScriptStep[]) => void;
}) {
  const [nestedSteps, setNestedSteps] = useState<EditableScriptStep[]>(() =>
    wrapSteps(readNestedSteps(value))
  );

  function commit(next: EditableScriptStep[]) {
    setNestedSteps(next);
    onChange(unwrapSteps(next));
  }

  return (
    <div className="script-step-nested-block">
      <span className="field-caption">{label}</span>
      <ScriptStepListEditor nested steps={nestedSteps} onChange={commit} />
    </div>
  );
}

function WhenConditionFields({
  step,
  onPatch,
}: {
  step: ScriptStep;
  onPatch: (patch: Partial<ScriptStep>) => void;
}) {
  const { t } = useTranslation("inspector");
  const kind = readWhenConditionKind(step);

  return (
    <div className="form-grid script-step-fields">
      <label className="field-block">
        <span className="field-label">{t("scriptSteps.field.condition")}</span>
        <select
          value={kind}
          onChange={(e) => onPatch(applyWhenConditionKind(step, e.target.value as WhenConditionKind))}
        >
          <option value="truthy">{t("scriptSteps.condition.truthy")}</option>
          <option value="notNull">{t("scriptSteps.condition.notNull")}</option>
          <option value="equals">{t("scriptSteps.condition.equals")}</option>
          <option value="notEquals">{t("scriptSteps.condition.notEquals")}</option>
          <option value="gt">{t("scriptSteps.condition.gt")}</option>
          <option value="lt">{t("scriptSteps.condition.lt")}</option>
          <option value="gte">{t("scriptSteps.condition.gte")}</option>
          <option value="lte">{t("scriptSteps.condition.lte")}</option>
        </select>
      </label>
      <TextField
        label={t("scriptSteps.field.var")}
        value={String(step.var ?? "")}
        onChange={(next) => onPatch({ var: next })}
        placeholder="input.value"
        hint={t("scriptSteps.hint.varPath")}
      />
      {kind === "notNull" && (
        <label className="field-block checkbox-label inline">
          <input
            type="checkbox"
            checked={step.notNull !== false}
            onChange={(e) => onPatch({ notNull: e.target.checked })}
          />
          {t("scriptSteps.field.expectNotNull")}
        </label>
      )}
      {(kind === "equals" || kind === "notEquals") && (
        <TextField
          label={t("scriptSteps.field.compareValue")}
          value={String(step[kind] ?? "")}
          onChange={(next) => onPatch({ [kind]: next })}
        />
      )}
      {(kind === "gt" || kind === "lt" || kind === "gte" || kind === "lte") && (
        <TextField
          label={t("scriptSteps.field.threshold")}
          value={String(step[kind] ?? "")}
          onChange={(next) => {
            const num = Number(next);
            onPatch({ [kind]: Number.isFinite(num) ? num : next });
          }}
        />
      )}
    </div>
  );
}

function StepFields({
  step,
  onPatch,
}: {
  step: ScriptStep;
  onPatch: (patch: Partial<ScriptStep>) => void;
}) {
  const { t } = useTranslation("inspector");
  const type = step.type;

  if (type === "when" || type === "if") {
    return (
      <>
        <WhenConditionFields step={step} onPatch={onPatch} />
        <NestedStepsField
          label={t("scriptSteps.field.thenSteps")}
          value={step.then}
          onChange={(then) => onPatch({ then })}
        />
        <NestedStepsField
          label={t("scriptSteps.field.elseSteps")}
          value={step.else}
          onChange={(elseSteps) => onPatch({ else: elseSteps })}
        />
      </>
    );
  }

  return (
    <div className="form-grid script-step-fields">
      {(type === "setVar" ||
        type === "buildRecord" ||
        type === "jsonParse" ||
        type === "readVariable" ||
        type === "instantiateModelIfMissing" ||
        type === "selectOne" ||
        type === "selectMany" ||
        type === "map" ||
        type === "invoke_function" ||
        type === "cancel_workflows" ||
        type === "failIfNull" ||
        type === "failIfNotEquals") && (
        <TextField
          label={t("scriptSteps.field.var")}
          value={String(step.var ?? "")}
          onChange={(next) => onPatch({ var: next })}
          placeholder="result"
          hint={type !== "failIfNull" && type !== "failIfNotEquals" ? t("scriptSteps.hint.varName") : t("scriptSteps.hint.varPath")}
        />
      )}

      {type === "setVar" && (
        <>
          <TextField
            label={t("scriptSteps.field.value")}
            value={String(step.value ?? "")}
            onChange={(next) => onPatch({ value: next, expression: undefined })}
            placeholder="literal or ${input.name}"
          />
          <TextField
            label={t("scriptSteps.field.expression")}
            value={String(step.expression ?? "")}
            onChange={(next) => onPatch({ expression: next, value: undefined })}
            placeholder={'greeting + ", " + ${input.name}'}
            hint={t("scriptSteps.hint.expression")}
          />
        </>
      )}

      {(type === "buildRecord" || type === "return" || type === "setDriverTelemetry" || type === "map") && (
        <KeyValueField
          label={t("scriptSteps.field.fields")}
          value={step.fields}
          onChange={(fields) => onPatch({ fields })}
          keyPlaceholder="fieldName"
          valuePlaceholder="${input.value}"
        />
      )}

      {type === "jsonParse" && (
        <>
          <TextField
            label={t("scriptSteps.field.source")}
            value={String(step.source ?? "")}
            onChange={(next) => onPatch({ source: next })}
            placeholder="${input.raw}"
          />
          <StringListField
            label={t("scriptSteps.field.extractFields")}
            value={step.fields}
            onChange={(fields) => onPatch({ fields })}
            placeholder="temperature"
          />
        </>
      )}

      {type === "readVariable" && (
        <>
          <TextField
            label={t("scriptSteps.field.objectPath")}
            value={String(step.objectPath ?? "")}
            onChange={(next) => onPatch({ objectPath: next })}
            placeholder="self"
            hint={t("scriptSteps.hint.objectPath")}
          />
          <TextField
            label={t("scriptSteps.field.variable")}
            value={String(step.variable ?? "")}
            onChange={(next) => onPatch({ variable: next })}
            placeholder="temperature"
          />
          <TextField
            label={t("scriptSteps.field.field")}
            value={String(step.field ?? "value")}
            onChange={(next) => onPatch({ field: next })}
            placeholder="value"
          />
        </>
      )}

      {type === "instantiateModelIfMissing" && (
        <>
          <TextField
            label={t("scriptSteps.field.modelName")}
            value={String(step.modelName ?? "")}
            onChange={(next) => onPatch({ modelName: next })}
            placeholder="mqtt-sensor-v1"
          />
          <TextField
            label={t("scriptSteps.field.parentPath")}
            value={String(step.parentPath ?? "")}
            onChange={(next) => onPatch({ parentPath: next })}
            placeholder="root.platform.devices"
          />
          <TextField
            label={t("scriptSteps.field.instanceName")}
            value={String(step.instanceName ?? "")}
            onChange={(next) => onPatch({ instanceName: next })}
            placeholder="${input.id}"
          />
        </>
      )}

      {type === "setDriverTelemetry" && (
        <>
          <TextField
            label={t("scriptSteps.field.objectPath")}
            value={String(step.objectPath ?? "")}
            onChange={(next) => onPatch({ objectPath: next })}
            placeholder="${instancePath}"
          />
          <TextField
            label={t("scriptSteps.field.variable")}
            value={String(step.variable ?? "temperature")}
            onChange={(next) => onPatch({ variable: next })}
            placeholder="temperature"
          />
        </>
      )}

      {(type === "selectOne" || type === "selectMany" || type === "exec") && (
        <>
          <TextAreaField
            label={t("scriptSteps.field.sql")}
            value={String(step.sql ?? "")}
            onChange={(next) => onPatch({ sql: next })}
            rows={4}
            placeholder="SELECT id FROM orders WHERE id = ?"
          />
          <StringListField
            label={t("scriptSteps.field.params")}
            value={step.params}
            onChange={(params) => onPatch({ params })}
            placeholder="${input.orderId}"
          />
        </>
      )}

      {type === "map" && (
        <TextField
          label={t("scriptSteps.field.source")}
          value={String(step.source ?? "")}
          onChange={(next) => onPatch({ source: next })}
          placeholder="${rows}"
        />
      )}

      {type === "invoke_function" && (
        <>
          <TextField
            label={t("scriptSteps.field.objectPath")}
            value={String(step.objectPath ?? "")}
            onChange={(next) => onPatch({ objectPath: next })}
            placeholder="self"
          />
          <TextField
            label={t("scriptSteps.field.functionName")}
            value={String(step.functionName ?? "")}
            onChange={(next) => onPatch({ functionName: next })}
            placeholder="acknowledgeAlarm"
          />
          <KeyValueField
            label={t("scriptSteps.field.input")}
            value={step.input}
            onChange={(input) => onPatch({ input })}
            keyPlaceholder="param"
            valuePlaceholder="${input.value}"
          />
        </>
      )}

      {type === "cancel_workflows" && (
        <>
          <TextField
            label={t("scriptSteps.field.workflowPath")}
            value={String(step.workflowPath ?? "")}
            onChange={(next) => onPatch({ workflowPath: next })}
            placeholder="root.platform.workflows.demo"
          />
          <StringListField
            label={t("scriptSteps.field.statusIn")}
            value={step.statusIn}
            onChange={(statusIn) => onPatch({ statusIn })}
            placeholder="RUNNING"
          />
          <TextField
            label={t("scriptSteps.field.reason")}
            value={String(step.reason ?? "")}
            onChange={(next) => onPatch({ reason: next })}
            placeholder="cancelled"
          />
        </>
      )}

      {(type === "failIfNull" || type === "failIfNotEquals") && (
        <>
          {type === "failIfNotEquals" && (
            <TextField
              label={t("scriptSteps.field.equals")}
              value={String(step.equals ?? "")}
              onChange={(next) => onPatch({ equals: next })}
            />
          )}
          <TextField
            label={t("scriptSteps.field.errorCode")}
            value={String(step.error_code ?? step.code ?? "")}
            onChange={(next) => onPatch({ error_code: next, code: undefined })}
            placeholder="NOT_FOUND"
          />
          <TextField
            label={t("scriptSteps.field.errorMessage")}
            value={String(step.error_message ?? step.message ?? "")}
            onChange={(next) => onPatch({ error_message: next, message: undefined })}
            placeholder="Required value missing"
          />
        </>
      )}
    </div>
  );
}

function StepTypeSelect({
  value,
  onChange,
}: {
  value: FunctionScriptStepType;
  onChange: (next: FunctionScriptStepType) => void;
}) {
  const { t } = useTranslation("inspector");
  const categories: ScriptStepCategory[] = ["flow", "variables", "data", "platform"];

  return (
    <select
      className="script-step-type-select"
      value={value}
      onChange={(e) => onChange(e.target.value as FunctionScriptStepType)}
    >
      {categories.map((category) => (
        <optgroup key={category} label={t(`scriptSteps.category.${category}`)}>
          {FUNCTION_SCRIPT_STEP_TYPES.filter((type) => STEP_CATEGORIES[type] === category).map((type) => (
            <option key={type} value={type}>
              {t(`scriptSteps.type.${type}`)}
            </option>
          ))}
        </optgroup>
      ))}
    </select>
  );
}

function ScriptStepListEditor({ steps, onChange, nested = false }: StepListEditorProps) {
  const { t } = useTranslation("inspector");

  function updateStep(id: string, patch: Partial<ScriptStep>) {
    onChange(patchStep(steps, id, patch));
  }

  function replaceStepType(id: string, type: FunctionScriptStepType) {
    onChange(
      steps.map((item) =>
        item.id === id ? { ...item, step: defaultStep(type) } : item
      )
    );
  }

  function addStep(type: FunctionScriptStepType = "setVar") {
    onChange([...steps, { id: newStepId(), step: defaultStep(type) }]);
  }

  function removeStep(id: string) {
    onChange(steps.filter((item) => item.id !== id));
  }

  return (
    <div className={`script-steps-list${nested ? " nested" : ""}`}>
      {steps.length === 0 && <p className="hint">{t("scriptSteps.empty")}</p>}
      {steps.map((item, index) => (
        <article key={item.id} className="script-step-card">
          <header className="script-step-card-header">
            <span className="script-step-index">{index + 1}</span>
            <StepTypeSelect
              value={item.step.type}
              onChange={(type) => replaceStepType(item.id, type)}
            />
            <span className="script-step-desc">{t(`scriptSteps.type.${item.step.type}.desc`)}</span>
            <div className="script-step-card-actions">
              <button
                type="button"
                className="btn small"
                disabled={index === 0}
                aria-label={t("scriptSteps.moveUp")}
                onClick={() => onChange(moveStep(steps, index, -1))}
              >
                ↑
              </button>
              <button
                type="button"
                className="btn small"
                disabled={index === steps.length - 1}
                aria-label={t("scriptSteps.moveDown")}
                onClick={() => onChange(moveStep(steps, index, 1))}
              >
                ↓
              </button>
              <button
                type="button"
                className="btn small danger"
                aria-label={t("scriptSteps.removeStep")}
                onClick={() => removeStep(item.id)}
              >
                ×
              </button>
            </div>
          </header>
          <StepFields step={item.step} onPatch={(patch) => updateStep(item.id, patch)} />
        </article>
      ))}
      <div className="script-step-list-actions">
        <button type="button" className="btn small" onClick={() => addStep("setVar")}>
          {t("scriptSteps.addStep")}
        </button>
        {!nested && (
          <button type="button" className="btn small" onClick={() => addStep("return")}>
            {t("scriptSteps.addReturn")}
          </button>
        )}
      </div>
    </div>
  );
}

export default function FunctionScriptStepsEditor({ value, onChange }: FunctionScriptStepsEditorProps) {
  const { t } = useTranslation("inspector");
  const parsed = useMemo(() => parseScriptBody(value), [value]);
  const [steps, setSteps] = useState(parsed.steps);
  const [showJson, setShowJson] = useState(false);
  const [jsonText, setJsonText] = useState(value);
  const [parseError, setParseError] = useState<string | null>(parsed.error ?? null);

  useEffect(() => {
    const next = parseScriptBody(value);
    setSteps(next.steps);
    setJsonText(value.trim() ? value : serializeScriptBody(next.steps));
    setParseError(next.error ?? null);
  }, [value]);

  function commitSteps(next: EditableScriptStep[]) {
    setSteps(next);
    const body = serializeScriptBody(next);
    setJsonText(body);
    setParseError(null);
    onChange(body);
  }

  function applyTemplate(templateId: string) {
    const template = SCRIPT_TEMPLATES.find((item) => item.id === templateId);
    if (!template) return;
    const body = serializeStepsArray(template.steps);
    setJsonText(body);
    setParseError(null);
    onChange(body);
  }

  function applyJson() {
    try {
      JSON.parse(jsonText);
      const next = parseScriptBody(jsonText);
      if (next.error) {
        setParseError(next.error);
        return;
      }
      setSteps(next.steps);
      setParseError(null);
      onChange(jsonText.trim() ? jsonText : serializeScriptBody(next.steps));
    } catch (ex) {
      setParseError((ex as Error).message);
    }
  }

  return (
    <div className="function-script-steps-editor">
      <div className="section-inline-tools">
        <p className="hint">{t("scriptSteps.intro")}</p>
        <div className="script-step-toolbar">
          <label className="field-block compact">
            <span className="field-label">{t("scriptSteps.template")}</span>
            <select
              defaultValue=""
              onChange={(e) => {
                if (e.target.value) applyTemplate(e.target.value);
                e.target.value = "";
              }}
            >
              <option value="">{t("scriptSteps.templatePlaceholder")}</option>
              {SCRIPT_TEMPLATES.map((template) => (
                <option key={template.id} value={template.id}>
                  {t(`scriptSteps.template.${template.id}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="checkbox-label inline">
            <input
              type="checkbox"
              checked={showJson}
              onChange={(e) => setShowJson(e.target.checked)}
            />
            {t("scriptSteps.showJson")}
          </label>
        </div>
      </div>

      {parseError && !showJson && (
        <p className="hint error">{t("scriptSteps.parseError", { message: parseError })}</p>
      )}

      {!showJson ? (
        <ScriptStepListEditor steps={steps} onChange={commitSteps} />
      ) : (
        <>
          <textarea
            className="json-editor"
            rows={14}
            value={jsonText}
            onChange={(e) => {
              setJsonText(e.target.value);
              onChange(e.target.value);
            }}
            spellCheck={false}
          />
          <div className="script-step-list-actions">
            <button type="button" className="btn small" onClick={applyJson}>
              {t("scriptSteps.applyJson")}
            </button>
          </div>
          {parseError && <p className="hint error">{parseError}</p>}
        </>
      )}
    </div>
  );
}
