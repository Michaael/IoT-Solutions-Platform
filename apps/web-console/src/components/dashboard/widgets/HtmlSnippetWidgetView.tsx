import type { HtmlSnippetWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface HtmlSnippetWidgetViewProps {
  widget: HtmlSnippetWidget;
  editable?: boolean;
}

export default function HtmlSnippetWidgetView({ widget, editable }: HtmlSnippetWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const html = widget.htmlJson ?? "";

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-html"
      editable={editable}
    >
      <div
        className="dash-html-body"
        style={styles.body}
        dangerouslySetInnerHTML={{ __html: html }}
      />
    </DashWidgetShell>
  );
}
