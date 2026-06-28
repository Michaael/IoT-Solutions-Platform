import { Suspense, lazy, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { upsertEvent, upsertFunction } from "../api";
import type { DataSchema, EventDescriptor, FunctionDescriptor } from "../types";
import DataSchemaEditor from "./schema/DataSchemaEditor";
import { cloneSchema, emptySchema, normalizeFunctionDescriptor } from "../utils/dataSchema";
import { DEFAULT_JAVA_FUNCTION_TEMPLATE } from "../utils/javaFunctionTemplate";
import { defaultScriptBody } from "../utils/functionScriptSteps";
import FunctionScriptStepsEditor from "./functionScript/FunctionScriptStepsEditor";

const JavaFunctionEditor = lazy(() => import("./functionScript/JavaFunctionEditor"));
type DescriptorKind = "function" | "event";

interface EditDescriptorDialogProps {
  objectPath: string;
  kind: DescriptorKind;
  initial?: FunctionDescriptor | EventDescriptor;
  onClose: () => void;
  onSaved: () => void;
}

function defaultFunction(name = ""): FunctionDescriptor {
  const base = name || "fn";
  return {
    name: base,
    description: "",
    inputSchema: { ...emptySchema(`${base}Input`), fields: [] },
    outputSchema: {
      name: `${base}Output`,
      fields: [{ name: "ok", type: "BOOLEAN", description: "Success", nullable: false }],
    },
    sourceType: null,
    sourceBody: null,
    dataSourcePath: null,
    version: null,
  };
}

function defaultEvent(name = ""): EventDescriptor {
  const base = name || "event";
  return {
    name: base,
    description: "",
    payloadSchema: {
      name: `${base}Payload`,
      fields: [{ name: "message", type: "STRING", description: "Message", nullable: true }],
    },
    level: "INFO",
  };
}

export default function EditDescriptorDialog({
  objectPath,
  kind,
  initial,
  onClose,
  onSaved,
}: EditDescriptorDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const isFunction = kind === "function";
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [level, setLevel] = useState(
    !isFunction && initial ? (initial as EventDescriptor).level : "INFO"
  );
  const [inputSchema, setInputSchema] = useState<DataSchema>(emptySchema("input"));
  const [outputSchema, setOutputSchema] = useState<DataSchema>(emptySchema("output"));
  const [payloadSchema, setPayloadSchema] = useState<DataSchema>(emptySchema("payload"));
  const [sourceType, setSourceType] = useState("");
  const [sourceBody, setSourceBody] = useState("");
  const [dataSourcePath, setDataSourcePath] = useState("");
  const [version, setVersion] = useState("");
  const [showAdvancedJson, setShowAdvancedJson] = useState(false);
  const [schemaJson, setSchemaJson] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);

  useEffect(() => {
    if (isFunction) {
      const fn = (initial as FunctionDescriptor | undefined) ?? defaultFunction(name);
      setInputSchema(cloneSchema(fn.inputSchema));
      setOutputSchema(cloneSchema(fn.outputSchema));
      setSourceType(fn.sourceType ?? "");
      setSourceBody(fn.sourceBody ?? "");
      setDataSourcePath(fn.dataSourcePath ?? "");
      setVersion(fn.version ?? "");
      setSchemaJson(
        JSON.stringify(
          {
            inputSchema: fn.inputSchema,
            outputSchema: fn.outputSchema,
            sourceType: fn.sourceType,
            sourceBody: fn.sourceBody,
            dataSourcePath: fn.dataSourcePath,
            version: fn.version,
          },
          null,
          2
        )
      );
    } else {
      const ev = (initial as EventDescriptor | undefined) ?? defaultEvent(name);
      setPayloadSchema(cloneSchema(ev.payloadSchema));
      setSchemaJson(JSON.stringify(ev.payloadSchema, null, 2));
    }
  }, [initial, isFunction, name]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (isFunction) {
        let fn: FunctionDescriptor;
        if (showAdvancedJson) {
          const parsed = JSON.parse(schemaJson) as FunctionDescriptor;
          fn = normalizeFunctionDescriptor({
            name: name.trim(),
            description: description.trim(),
            inputSchema: parsed.inputSchema,
            outputSchema: parsed.outputSchema,
            sourceType: parsed.sourceType,
            sourceBody: parsed.sourceBody,
            dataSourcePath: parsed.dataSourcePath,
            version: parsed.version,
          });
        } else {
          fn = normalizeFunctionDescriptor({
            name: name.trim(),
            description: description.trim(),
            inputSchema,
            outputSchema,
            sourceType: sourceType || null,
            sourceBody: sourceBody || null,
            dataSourcePath: dataSourcePath || null,
            version: version || null,
          });
        }
        return upsertFunction(objectPath, fn);
      }
      const schema = showAdvancedJson
        ? (JSON.parse(schemaJson) as DataSchema)
        : payloadSchema;
      const ev: EventDescriptor = {
        name: name.trim(),
        description: description.trim(),
        payloadSchema: schema,
        level,
      };
      return upsertEvent(objectPath, ev);
    },
    onSuccess: onSaved,
  });

  function handleSave() {
    try {
      if (showAdvancedJson) {
        JSON.parse(schemaJson);
      }
      if (isFunction && !showAdvancedJson) {
        const st = sourceType.trim();
        if ((st === "java" || st === "script") && !sourceBody.trim()) {
          setParseError(t("descriptor.sourceBodyRequired"));
          return;
        }
      }
      setParseError(null);
      mutation.mutate();
    } catch {
      setParseError(t("descriptor.invalidSchemaJson"));
    }
  }

  const title = initial
    ? isFunction
      ? t("descriptor.functionTitle", { name: initial.name })
      : t("descriptor.eventTitle", { name: initial.name })
    : isFunction
      ? t("descriptor.newFunction")
      : t("descriptor.newEvent");

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal wide descriptor-editor-modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        <section className="modal-section form-grid">
          <label>
            {t("common:table.name")}
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              readOnly={Boolean(initial)}
              pattern="[A-Za-z_][A-Za-z0-9_]*"
              required
            />
          </label>
          {!isFunction && (
            <label>
              {t("common:field.level")}
              <select value={level} onChange={(e) => setLevel(e.target.value)}>
                <option value="DEBUG">DEBUG</option>
                <option value="INFO">INFO</option>
                <option value="WARNING">WARNING</option>
                <option value="ERROR">ERROR</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
            </label>
          )}
          <label className="full">
            {t("common:field.description")}
            <input value={description} onChange={(e) => setDescription(e.target.value)} />
          </label>
        </section>

        {isFunction && !showAdvancedJson && (
          <>
            <section className="modal-section">
              <h4>{t("descriptor.inputSchema")}</h4>
              <DataSchemaEditor
                value={inputSchema}
                onChange={setInputSchema}
                idPrefix="fn-input"
              />
            </section>
            <section className="modal-section">
              <h4>{t("descriptor.outputSchema")}</h4>
              <DataSchemaEditor
                value={outputSchema}
                onChange={setOutputSchema}
                idPrefix="fn-output"
              />
            </section>
            <section className="modal-section form-grid">
              <h4 className="full">{t("descriptor.scriptSection")}</h4>
              <label>
                {t("descriptor.sourceType")}
                <select
                  value={sourceType}
                  onChange={(e) => {
                    const next = e.target.value;
                    setSourceType(next);
                    if (next === "java" && !sourceBody.trim()) {
                      setSourceBody(DEFAULT_JAVA_FUNCTION_TEMPLATE);
                    }
                    if (next === "script" && !sourceBody.trim()) {
                      setSourceBody(defaultScriptBody());
                    }
                  }}
                >
                  <option value="">{t("descriptor.sourceTypeHandler")}</option>
                  <option value="script">{t("descriptor.sourceTypeScript")}</option>
                  <option value="java">{t("descriptor.sourceTypeJava")}</option>
                </select>
              </label>
              <label>
                {t("descriptor.version")}
                <input
                  value={version}
                  onChange={(e) => setVersion(e.target.value)}
                  placeholder="1.0.0"
                />
              </label>
              <label className="full">
                {t("descriptor.dataSourcePath")}
                <input
                  value={dataSourcePath}
                  onChange={(e) => setDataSourcePath(e.target.value)}
                  placeholder="root.platform.data-sources.app_myapp"
                />
              </label>
              {sourceType === "script" && (
                <div className="full">
                  <FunctionScriptStepsEditor
                    value={sourceBody || defaultScriptBody()}
                    onChange={setSourceBody}
                  />
                </div>
              )}
              {sourceType === "java" && (
                <div className="full">
                  <span className="field-label">{t("descriptor.sourceBodyJava")}</span>
                  <Suspense
                    fallback={
                      <textarea
                        className="json-editor"
                        rows={16}
                        value={sourceBody}
                        onChange={(e) => setSourceBody(e.target.value)}
                        spellCheck={false}
                      />
                    }
                  >
                    <JavaFunctionEditor
                      value={sourceBody || DEFAULT_JAVA_FUNCTION_TEMPLATE}
                      onChange={setSourceBody}
                    />
                  </Suspense>
                </div>
              )}
              {sourceType !== "script" && sourceType !== "java" && sourceBody.trim() && (
                <label className="full">
                  {t("descriptor.sourceBody")}
                  <textarea
                    className="json-editor"
                    rows={10}
                    value={sourceBody}
                    onChange={(e) => setSourceBody(e.target.value)}
                    placeholder='{"steps":[{"type":"return","value":{}}]}'
                    spellCheck={false}
                  />
                </label>
              )}
              <p className="hint full">
                {sourceType === "java" ? t("descriptor.javaHint") : t("descriptor.scriptHint")}
              </p>
            </section>
          </>
        )}

        {!isFunction && !showAdvancedJson && (
          <section className="modal-section">
            <h4>{t("descriptor.payloadSchema")}</h4>
            <DataSchemaEditor
              value={payloadSchema}
              onChange={setPayloadSchema}
              idPrefix="ev-payload"
            />
          </section>
        )}

        <section className="modal-section">
          <label className="checkbox-label inline">
            <input
              type="checkbox"
              checked={showAdvancedJson}
              onChange={(e) => setShowAdvancedJson(e.target.checked)}
            />
            {t("descriptor.advancedJson")}
          </label>
          {showAdvancedJson && (
            <textarea
              className="json-editor"
              rows={14}
              value={schemaJson}
              onChange={(e) => setSchemaJson(e.target.value)}
              spellCheck={false}
            />
          )}
        </section>

        {parseError && <p className="hint error">{parseError}</p>}
        {mutation.error && <p className="hint error">{(mutation.error as Error).message}</p>}

        <footer>
          <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={!name.trim() || mutation.isPending}
            onClick={handleSave}
          >
            {t("common:action.save")}
          </button>
        </footer>
      </div>
    </div>
  );
}
