import type { ContextListWidget } from "../../../types/dashboard";
import { useWidgetSession } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface ContextListWidgetViewProps {
  widget: ContextListWidget;
  editable?: boolean;
}

export default function ContextListWidgetView({ widget, editable }: ContextListWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection, params } = useWidgetSession();

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-context-list"
      editable={editable}
    >
      <dl className="dash-context-list" style={styles.body}>
        <dt>selection</dt>
        <dd>
          <pre>{JSON.stringify(selection, null, 2)}</pre>
        </dd>
        <dt>params</dt>
        <dd>
          <pre>{JSON.stringify(params, null, 2)}</pre>
        </dd>
      </dl>
    </DashWidgetShell>
  );
}
