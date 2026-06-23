import type { LabelWidget } from "../../../types/dashboard";
import { resolveContextParam } from "../dashboardUtils";
import { useWidgetSession } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface LabelWidgetViewProps {
  widget: LabelWidget;
  editable?: boolean;
}

export default function LabelWidgetView({ widget, editable }: LabelWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { params } = useWidgetSession();
  const paramValue = widget.paramKey ? resolveContextParam(widget.paramKey, params) : undefined;
  const text =
    paramValue != null
      ? String(paramValue)
      : widget.text ?? widget.textJson ?? "—";

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-label"
      editable={editable}
    >
      <p className="dash-label-text" style={styles.value}>
        {text}
      </p>
    </DashWidgetShell>
  );
}
