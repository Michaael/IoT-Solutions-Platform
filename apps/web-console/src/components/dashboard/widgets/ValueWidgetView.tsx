import type { ValueWidget } from "../../../types/dashboard";
import { useTranslation } from "react-i18next";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface ValueWidgetViewProps {
  widget: ValueWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ValueWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ValueWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { operatorMode } = useDashboardContext();
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { rawValue, variable, isLoading, isError } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const unitRow = variable?.value?.rows[0];
  const legacySuffix = (widget as ValueWidget & { suffix?: string }).suffix;
  const unit =
    widget.unit ??
    legacySuffix ??
    (widget.unitField && unitRow ? String(unitRow[widget.unitField] ?? "") : "");

  let display = "—";
  if (isLoading) {
    display = "…";
  } else if (isError) {
    display = "!";
  } else if (typeof rawValue === "number") {
    const decimals = widget.decimals ?? 1;
    display = rawValue.toFixed(decimals);
  } else if (rawValue != null) {
    display = String(rawValue);
  }

  const isMetric =
    typeof rawValue === "number" ||
    (typeof rawValue === "string" &&
      rawValue.trim() !== "" &&
      /^-?\d+(\.\d+)?$/.test(rawValue.trim()));

  const valueClass = isMetric
    ? "dash-widget-metric"
    : display.length > 48
      ? "dash-widget-text dash-widget-text-multiline"
      : "dash-widget-text";

  const metaFooter =
    operatorMode || !widget.variableName
      ? undefined
      : `${(objectPath || widget.objectPath || "—").split(".").pop()}.${widget.variableName}`;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-value"
      editable={editable}
      footer={metaFooter}
    >
      {!objectPath && widget.selectionKey ? (
        <p className="hint">{t("view.selectDevice")}</p>
      ) : (
        <div
          className={`dash-widget-value-body${isMetric ? "" : " is-text"}`}
          style={styles.body}
        >
          <span
            className={valueClass}
            style={styles.value}
            title={display.length > 24 ? display : undefined}
          >
            {display}
          </span>
          {unit ? (
            <span className="dash-widget-unit" style={styles.unit}>
              {unit}
            </span>
          ) : null}
        </div>
      )}
    </DashWidgetShell>
  );
}
