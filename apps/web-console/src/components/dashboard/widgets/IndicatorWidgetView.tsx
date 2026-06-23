import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { rawValue, isLoading, isError } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const active = rawValue === true || rawValue === "true" || rawValue === 1;
  const alarmMode = widget.alarmMode ?? false;
  let borderColor: string;
  let dotClass: string;
  if (alarmMode) {
    if (active) {
      borderColor = widget.trueColor ?? "var(--danger)";
      dotClass = "alarm";
    } else {
      borderColor = widget.falseColor ?? "var(--success)";
      dotClass = "ok";
    }
  } else if (active) {
    borderColor = widget.trueColor ?? "var(--success)";
    dotClass = "ok";
  } else {
    borderColor = widget.falseColor ?? "var(--border)";
    dotClass = "idle";
  }
  const label = active
    ? (widget.trueLabel ?? (alarmMode ? t("view.indicator.alarm") : t("view.indicator.active")))
    : (widget.falseLabel ?? t("view.indicator.normal"));

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className={`dash-widget dash-widget-indicator ${dotClass}`}
      editable={editable}
      rootStyle={{ borderColor }}
    >
      {!objectPath && widget.selectionKey ? (
        <p className="hint">{t("view.selectDevice")}</p>
      ) : (
        <div className="dash-widget-indicator-body" style={styles.body}>
          <span
            className={`dash-indicator-dot ${dotClass}`}
            style={styles.dot}
          />
          <span style={styles.label}>
            {isLoading ? "…" : isError ? t("view.errorGeneric") : label}
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
  const { t } = useTranslation("widgets");
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
      footer={!writable ? t("view.readOnly") : undefined}
    >
      {!objectPath && widget.selectionKey ? (
        <p className="hint">{t("view.selectDevice")}</p>
      ) : (
      <button
        type="button"
        className={`dash-toggle-btn ${active ? "on" : "off"}`}
        style={styles.value}
        disabled={editable || !writable || isLoading || mutation.isPending}
        onClick={() => mutation.mutate(!active)}
      >
        {active ? (widget.trueLabel ?? t("view.indicator.on")) : (widget.falseLabel ?? t("view.indicator.off"))}
      </button>
      )}
    </DashWidgetShell>
  );
}
