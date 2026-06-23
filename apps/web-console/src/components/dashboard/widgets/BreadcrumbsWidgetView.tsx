import type { BreadcrumbsWidget } from "../../../types/dashboard";
import { useWidgetSession } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface BreadcrumbsWidgetViewProps {
  widget: BreadcrumbsWidget;
  editable?: boolean;
}

export default function BreadcrumbsWidgetView({ widget, editable }: BreadcrumbsWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useWidgetSession();
  const path = widget.pathKey ? selection[widget.pathKey] : "";
  const parts = path ? path.split(".") : [];
  const sep = widget.separator ?? " › ";

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-breadcrumbs"
      editable={editable}
    >
      <p className="dash-breadcrumbs" style={styles.value}>
        {parts.length > 0 ? parts.join(sep) : "—"}
      </p>
    </DashWidgetShell>
  );
}
