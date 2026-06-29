import { useTranslation } from "react-i18next";
import type { MimicAction, MimicConnection, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";
import { createMimicId } from "../../scada/document";
import { getSymbol } from "../../scada/symbols/registry";

interface MimicPropertiesPanelProps {
  document: ScadaMimicDocument;
  selectedElement: MimicElement | null;
  selectedConnection: MimicConnection | null;
  onUpdateElement: (element: MimicElement) => void;
  onUpdateConnection: (connection: MimicConnection) => void;
  onDeleteSelected: () => void;
}

export default function MimicPropertiesPanel({
  document,
  selectedElement,
  selectedConnection,
  onUpdateElement,
  onUpdateConnection,
  onDeleteSelected,
}: MimicPropertiesPanelProps) {
  const { t } = useTranslation("scada");

  if (!selectedElement && !selectedConnection) {
    return (
      <div className="scada-props-panel">
        <h3>{t("props.title")}</h3>
        <p className="hint">{t("props.selectHint")}</p>
        <dl className="scada-doc-meta">
          <dt>{t("props.canvasSize")}</dt>
          <dd>{document.width} × {document.height}</dd>
          <dt>{t("props.elements")}</dt>
          <dd>{document.elements.length}</dd>
          <dt>{t("props.connections")}</dt>
          <dd>{document.connections.length}</dd>
        </dl>
      </div>
    );
  }

  if (selectedConnection) {
    return (
      <div className="scada-props-panel">
        <h3>{t("props.connection")}</h3>
        <label>
          {t("props.strokeWidth")}
          <input
            type="number"
            min={1}
            max={12}
            value={selectedConnection.style?.strokeWidth ?? 3}
            onChange={(e) =>
              onUpdateConnection({
                ...selectedConnection,
                style: { ...selectedConnection.style, strokeWidth: Number(e.target.value) },
              })
            }
          />
        </label>
        <button type="button" className="danger" onClick={onDeleteSelected}>
          {t("props.delete")}
        </button>
      </div>
    );
  }

  if (!selectedElement) return null;
  const symbol = getSymbol(selectedElement.symbolId);
  const action = selectedElement.actions?.[0];

  const updateBinding = (
    key: string,
    field: "objectPath" | "variableName" | "valueField" | "transform",
    value: string
  ) => {
    const bindings = { ...selectedElement.bindings };
    const current = bindings[key] ?? { variableName: "" };
    if (field === "transform") {
      bindings[key] = {
        ...current,
        transform: value ? (value as "bool" | "number" | "string") : undefined,
      };
    } else {
      bindings[key] = { ...current, [field]: value };
    }
    onUpdateElement({ ...selectedElement, bindings });
  };

  const updateAction = (patch: Partial<MimicAction>) => {
    const actions = [...(selectedElement.actions ?? [])];
    if (actions.length === 0) {
      actions.push({
        id: createMimicId("act"),
        type: "invokeFunction",
        ...patch,
      });
    } else {
      actions[0] = { ...actions[0], ...patch };
    }
    onUpdateElement({ ...selectedElement, actions });
  };

  const removeAction = () => {
    onUpdateElement({ ...selectedElement, actions: undefined });
  };

  return (
    <div className="scada-props-panel">
      <h3>{symbol ? t(symbol.nameKey) : selectedElement.symbolId}</h3>
      <label>
        X
        <input
          type="number"
          step={1}
          value={selectedElement.x}
          onChange={(e) => onUpdateElement({ ...selectedElement, x: Number(e.target.value) })}
        />
      </label>
      <label>
        Y
        <input
          type="number"
          step={1}
          value={selectedElement.y}
          onChange={(e) => onUpdateElement({ ...selectedElement, y: Number(e.target.value) })}
        />
      </label>
      <label>
        {t("props.rotation")}
        <select
          value={selectedElement.rotation ?? 0}
          onChange={(e) =>
            onUpdateElement({
              ...selectedElement,
              rotation: Number(e.target.value) as MimicElement["rotation"],
            })
          }
        >
          {[0, 90, 180, 270].map((r) => (
            <option key={r} value={r}>{r}°</option>
          ))}
        </select>
      </label>
      <label>
        {t("props.layer")}
        <select
          value={selectedElement.layerId}
          onChange={(e) => onUpdateElement({ ...selectedElement, layerId: e.target.value })}
        >
          {document.layers.map((layer) => (
            <option key={layer.id} value={layer.id}>{layer.name}</option>
          ))}
        </select>
      </label>

      <h4>{t("props.bindings")}</h4>
      {(symbol?.bindingSchema ?? []).map((slot) => (
        <div key={slot.key} className="scada-binding-slot">
          <strong>{t(slot.labelKey)}</strong>
          <label>
            objectPath
            <input
              type="text"
              className="mono"
              spellCheck={false}
              value={selectedElement.bindings[slot.key]?.objectPath ?? ""}
              onChange={(e) => updateBinding(slot.key, "objectPath", e.target.value)}
            />
          </label>
          <label>
            variableName
            <input
              type="text"
              className="mono"
              spellCheck={false}
              value={selectedElement.bindings[slot.key]?.variableName ?? ""}
              onChange={(e) => updateBinding(slot.key, "variableName", e.target.value)}
            />
          </label>
          <label>
            transform
            <select
              value={selectedElement.bindings[slot.key]?.transform ?? ""}
              onChange={(e) => updateBinding(slot.key, "transform", e.target.value)}
            >
              <option value="">—</option>
              <option value="bool">bool</option>
              <option value="number">number</option>
              <option value="string">string</option>
            </select>
          </label>
        </div>
      ))}

      <h4>{t("props.actions")}</h4>
      {!action ? (
        <button
          type="button"
          className="btn small"
          onClick={() => updateAction({ type: "invokeFunction", functionName: "" })}
        >
          {t("props.addAction")}
        </button>
      ) : (
        <div className="scada-binding-slot">
          <label>
            {t("props.actionType")}
            <select
              value={action.type}
              onChange={(e) => updateAction({ type: e.target.value as MimicAction["type"] })}
            >
              <option value="invokeFunction">invokeFunction</option>
              <option value="setVariable">setVariable</option>
              <option value="toggleVariable">toggleVariable</option>
            </select>
          </label>
          <label>
            objectPath
            <input
              type="text"
              className="mono"
              spellCheck={false}
              value={action.objectPath ?? ""}
              onChange={(e) => updateAction({ objectPath: e.target.value })}
            />
          </label>
          {action.type === "invokeFunction" ? (
            <label>
              functionName
              <input
                type="text"
                className="mono"
                spellCheck={false}
                value={action.functionName ?? ""}
                onChange={(e) => updateAction({ functionName: e.target.value })}
              />
            </label>
          ) : (
            <>
              <label>
                variableName
                <input
                  type="text"
                  className="mono"
                  spellCheck={false}
                  value={action.variableName ?? ""}
                  onChange={(e) => updateAction({ variableName: e.target.value })}
                />
              </label>
              {action.type === "setVariable" && (
                <label>
                  value
                  <input
                    type="text"
                    className="mono"
                    value={action.value == null ? "" : String(action.value)}
                    onChange={(e) => updateAction({ value: e.target.value })}
                  />
                </label>
              )}
            </>
          )}
          <button type="button" className="btn small danger" onClick={removeAction}>
            {t("props.removeAction")}
          </button>
        </div>
      )}

      <button type="button" className="danger" onClick={onDeleteSelected}>
        {t("props.delete")}
      </button>
    </div>
  );
}
