import type { ImageWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface ImageWidgetViewProps {
  widget: ImageWidget;
  editable?: boolean;
}

export default function ImageWidgetView({ widget, editable }: ImageWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const src = widget.imageUrl?.trim();

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-image"
      editable={editable}
    >
      {src ? (
        <img
          className="dash-widget-image-el"
          src={src}
          alt={widget.alt ?? widget.title}
          style={styles.body}
        />
      ) : (
        <p className="hint">Укажите imageUrl</p>
      )}
    </DashWidgetShell>
  );
}
