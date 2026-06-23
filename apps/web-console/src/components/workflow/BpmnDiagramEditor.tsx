import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import BpmnModeler from "bpmn-js/lib/Modeler";
import ispfModdle from "../../bpmn/ispf-moddle.json";
import { EMPTY_BPMN } from "../../bpmn/constants";
import { ensureBpmnDiagram } from "../../bpmn/ensureDiagram";
import { WORKFLOW_ISPF_ACTIONS, type WorkflowIspfAction } from "../../types/automation";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css";

interface BpmnDiagramEditorProps {
  xml: string;
  onChange: (xml: string) => void;
}

type BpmnElement = {
  id: string;
  type: string;
  businessObject: {
    get: (key: string) => string | undefined;
    $type: string;
  };
};

const ISPF_ATTRS = [
  "action",
  "objectPath",
  "targetObject",
  "variable",
  "sourceVariable",
  "value",
  "eventName",
  "payloadJson",
  "contextKey",
  "workflowPath",
  "functionName",
  "function",
  "inputMap",
  "message",
  "subject",
  "channel",
] as const;

const ACTION_ATTR_HINTS: Record<WorkflowIspfAction, string[]> = {
  fire_event: ["objectPath", "eventName", "payloadJson"],
  read_variable: ["objectPath", "variable", "contextKey"],
  start_workflow: ["workflowPath", "targetObject"],
  invoke_function: ["objectPath", "functionName", "inputMap"],
  setVariable: ["targetObject", "variable", "value"],
  log: ["message"],
  publishNats: ["subject", "message", "channel"],
};

function isIspfTask(element: BpmnElement | undefined): boolean {
  if (!element?.businessObject) return false;
  const type = element.businessObject.$type ?? element.type ?? "";
  return type.includes("ServiceTask") || type.includes("UserTask");
}

export default function BpmnDiagramEditor({ xml, onChange }: BpmnDiagramEditorProps) {
  const { t } = useTranslation("workflow");
  const containerRef = useRef<HTMLDivElement>(null);
  const modelerRef = useRef<BpmnModeler | null>(null);
  const onChangeRef = useRef(onChange);
  const skipImportRef = useRef(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [selected, setSelected] = useState<BpmnElement | null>(null);
  const [ispfProps, setIspfProps] = useState<Record<string, string>>({});

  onChangeRef.current = onChange;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const modeler = new BpmnModeler({
      container,
      keyboard: { bindTo: document },
      moddleExtensions: { ispf: ispfModdle },
    });
    modelerRef.current = modeler;

    const eventBus = modeler.get("eventBus") as {
      on: (event: string, handler: (event: { newSelection: BpmnElement[] }) => void) => void;
      off: (event: string, handler: (event: { newSelection: BpmnElement[] }) => void) => void;
    };

    const onSelectionChanged = (event: { newSelection: BpmnElement[] }) => {
      const element = event.newSelection[0];
      if (!isIspfTask(element)) {
        setSelected(null);
        setIspfProps({});
        return;
      }
      setSelected(element);
      const next: Record<string, string> = {};
      for (const key of ISPF_ATTRS) {
        const value = element.businessObject.get(key);
        if (value) next[key] = value;
      }
      setIspfProps(next);
    };

    let saveTimer: ReturnType<typeof setTimeout> | null = null;
    const persist = () => {
      if (saveTimer) clearTimeout(saveTimer);
      saveTimer = setTimeout(async () => {
        try {
          const result = await modeler.saveXML({ format: true });
          if (result.xml) {
            skipImportRef.current = true;
            onChangeRef.current(result.xml);
          }
        } catch (error) {
          setImportError((error as Error).message);
        }
      }, 250);
    };

    eventBus.on("commandStack.changed", persist);
    eventBus.on("selection.changed", onSelectionChanged);

    return () => {
      if (saveTimer) clearTimeout(saveTimer);
      eventBus.off("commandStack.changed", persist);
      eventBus.off("selection.changed", onSelectionChanged);
      modeler.destroy();
      modelerRef.current = null;
    };
  }, []);

  useEffect(() => {
    const modeler = modelerRef.current;
    if (!modeler) return;
    if (skipImportRef.current) {
      skipImportRef.current = false;
      return;
    }

    let cancelled = false;
    const raw = xml.trim() || EMPTY_BPMN;

    void (async () => {
      try {
        const content = await ensureBpmnDiagram(raw);
        await modeler.importXML(content);
        if (cancelled) return;
        setImportError(null);
        const canvas = modeler.get("canvas") as { zoom: (mode: string) => void };
        canvas.zoom("fit-viewport");
      } catch (error) {
        if (!cancelled) {
          setImportError((error as Error).message);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [xml]);

  function applyIspfProperties() {
    const modeler = modelerRef.current;
    if (!modeler || !selected) return;
    const modeling = modeler.get("modeling") as {
      updateProperties: (element: BpmnElement, props: Record<string, string | undefined>) => void;
    };
    const props: Record<string, string | undefined> = {};
    for (const key of ISPF_ATTRS) {
      const value = ispfProps[key]?.trim();
      props[key] = value || undefined;
    }
    modeling.updateProperties(selected, props);
  }

  const currentAction = (ispfProps.action as WorkflowIspfAction | undefined) ?? undefined;
  const hintAttrs = currentAction ? ACTION_ATTR_HINTS[currentAction] ?? [] : [];

  return (
    <div className="bpmn-editor-layout">
      <div className="bpmn-editor-wrap">
        {importError && (
          <p className="hint error bpmn-editor-error">
            {t("bpmn.importError", { error: importError })}
          </p>
        )}
        <div ref={containerRef} className="bpmn-editor-canvas" />
      </div>
      {selected && (
        <aside className="bpmn-ispf-panel">
          <h4>ISPF task properties</h4>
          <p className="hint">
            {t("bpmn.element", { type: selected.businessObject.$type, id: selected.id })}
          </p>
          <label>
            ispf:action
            <select
              value={ispfProps.action ?? ""}
              onChange={(e) => setIspfProps((prev) => ({ ...prev, action: e.target.value }))}
            >
              <option value="">—</option>
              {WORKFLOW_ISPF_ACTIONS.map((entry) => (
                <option key={entry.action} value={entry.action}>
                  {entry.action} — {entry.label}
                </option>
              ))}
            </select>
          </label>
          {hintAttrs.map((attr) => (
            <label key={attr}>
              ispf:{attr}
              <input
                value={ispfProps[attr] ?? ""}
                onChange={(e) => setIspfProps((prev) => ({ ...prev, [attr]: e.target.value }))}
                placeholder={attr}
              />
            </label>
          ))}
          <details>
            <summary>{t("bpmn.allAttributes")}</summary>
            {ISPF_ATTRS.filter((a) => a !== "action" && !hintAttrs.includes(a)).map((attr) => (
              <label key={attr}>
                ispf:{attr}
                <input
                  value={ispfProps[attr] ?? ""}
                  onChange={(e) => setIspfProps((prev) => ({ ...prev, [attr]: e.target.value }))}
                />
              </label>
            ))}
          </details>
          <button type="button" className="btn primary" onClick={applyIspfProperties}>
            {t("bpmn.applyToElement")}
          </button>
        </aside>
      )}
    </div>
  );
}
