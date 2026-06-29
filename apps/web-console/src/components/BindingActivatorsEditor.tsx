import { useTranslation } from "react-i18next";
import type { BindingActivators } from "../types";
import {
  buildRemoteVariableChange,
  CUSTOM_BINDING_EVENT,
  patchBindingActivators,
  remoteActivatorRef,
  resolveOnEventAfterSelect,
  resolveOnEventSelectValue,
} from "./bindingActivatorsUtils";

export { activatorsSummary } from "./bindingActivatorsUtils";

interface BindingActivatorsEditorProps {
  activators: BindingActivators;
  eventNames: string[];
  dashboardMode?: boolean;
  onChange: (activators: BindingActivators) => void;
}

export default function BindingActivatorsEditor({
  activators,
  eventNames,
  dashboardMode = false,
  onChange,
}: BindingActivatorsEditorProps) {
  const { t } = useTranslation("inspector");

  const remoteRef = remoteActivatorRef(activators);
  const remotePath = remoteRef?.objectPath ?? "";
  const remoteVariable = remoteRef?.variableName ?? "";
  const onEventSelectValue = resolveOnEventSelectValue(activators, eventNames);

  const patch = (next: Partial<BindingActivators>) => {
    onChange(patchBindingActivators(activators, next));
  };

  const setRemote = (path: string, variableName: string) => {
    patch({ onVariableChange: buildRemoteVariableChange(path, variableName) });
  };

  return (
    <fieldset className="binding-activators-editor full">
      <legend>{t("bindings.activators.title")}</legend>

      <label className="checkbox-label inline full">
        <input
          type="checkbox"
          checked={activators.onStartup}
          onChange={(e) => patch({ onStartup: e.target.checked })}
        />
        {t("bindings.activators.onStartup")}
      </label>

      {dashboardMode && (
        <label className="checkbox-label inline full">
          <input
            type="checkbox"
            checked={Boolean(activators.onContextChange)}
            onChange={(e) => patch({ onContextChange: e.target.checked })}
          />
          {t("bindings.activators.onContextChange")}
        </label>
      )}

      <label className="full">
        {t("bindings.activators.periodicMs")}
        <input
          type="number"
          min={0}
          step={100}
          value={activators.periodicMs}
          onChange={(e) => patch({ periodicMs: Math.max(0, Number(e.target.value) || 0) })}
        />
        <span className="hint">{t("bindings.activators.periodicMsHint")}</span>
      </label>

      <label className="full">
        {t("bindings.activators.onEvent")}
        <select
          value={onEventSelectValue}
          onChange={(e) => {
            patch({ onEvent: resolveOnEventAfterSelect(activators.onEvent, e.target.value, eventNames) });
          }}
        >
          <option value="">{t("bindings.activators.onEventNone")}</option>
          {eventNames.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
          <option value={CUSTOM_BINDING_EVENT}>{t("bindings.activators.onEventCustom")}</option>
        </select>
      </label>

      {onEventSelectValue === CUSTOM_BINDING_EVENT && (
        <label className="full">
          {t("bindings.activators.onEventName")}
          <input
            value={activators.onEvent ?? ""}
            onChange={(e) => patch({ onEvent: e.target.value.trim() || null })}
            placeholder="alarmRaised"
          />
        </label>
      )}

      <label className="full">
        {t("bindings.activators.remotePath")}
        <input
          value={remotePath}
          placeholder="root.platform.devices.foo"
          onChange={(e) => setRemote(e.target.value, remoteVariable)}
        />
      </label>

      <label className="full">
        {t("bindings.activators.remoteVariable")}
        <input
          value={remotePath ? remoteVariable : ""}
          disabled={!remotePath}
          placeholder="*"
          onChange={(e) => setRemote(remotePath, e.target.value)}
        />
      </label>
    </fieldset>
  );
}
