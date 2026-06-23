import { useMutation, useQueryClient } from "@tanstack/react-query";
import { setVariable } from "../../../api";
import type { IndicatorWidget } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { cloneRecord, setFieldValue } from "../../../utils/record";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface IndicatorWidgetViewProps {
  widget: IndicatorWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function IndicatorWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: IndicatorWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { rawValue, isLoading, isError } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const active = rawValue === true || rawValue === "true" || rawValue === 1;
  const label = active
    ? (widget.trueLabel ?? "Активно")
    : (widget.falseLabel ?? "Норма");

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className={`dash-widget dash-widget-indicator ${active ? "active" : "inactive"}`}
      editable={editable}
      rootStyle={{
        borderColor: active
          ? (widget.trueColor ?? "var(--danger)")
          : (widget.falseColor ?? "var(--success)"),
      }}
    >
      {!objectPath && widget.selectionKey ? (
        <p className="hint">Выберите устройство</p>
      ) : (
        <div className="dash-widget-indicator-body" style={styles.body}>
          <span
            className={`dash-indicator-dot ${active ? "on" : "off"}`}
            style={styles.dot}
          />
          <span style={styles.label}>
            {isLoading ? "…" : isError ? "Ошибка" : label}
          </span>
        </div>
      )}
    </DashWidgetShell>
  );
}

interface ToggleWidgetViewProps {
  widget: import("../../../types/dashboard").ToggleWidget;
  refreshIntervalMs: number;
}

export function ToggleWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ToggleWidgetViewProps & { editable?: boolean }) {
  const styles = useWidgetStyles(widget.stylesJson);
  const queryClient = useQueryClient();
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey, widget.contextPathKey);
  const { rawValue, variable, writable, isLoading } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const active = rawValue === true || rawValue === "true";

  const mutation = useMutation({
    mutationFn: async (next: boolean) => {
      if (!variable?.value) {
        throw new Error("Variable not loaded");
      }
      const field = widget.valueField ?? "value";
      const record = setFieldValue(cloneRecord(variable.value), field, next);
      return setVariable(objectPath, widget.variableName ?? "", record);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
    },
  });

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-toggle"
      editable={editable}
      footer={!writable ? "только чтение" : undefined}
    >
      {!objectPath && widget.selectionKey ? (
        <p className="hint">Выберите устройство</p>
      ) : (
      <button
        type="button"
        className={`dash-toggle-btn ${active ? "on" : "off"}`}
        style={styles.value}
        disabled={editable || !writable || isLoading || mutation.isPending}
        onClick={() => mutation.mutate(!active)}
      >
        {active ? (widget.trueLabel ?? "Вкл") : (widget.falseLabel ?? "Выкл")}
      </button>
      )}
    </DashWidgetShell>
  );
}
