import { useTranslation } from "react-i18next";

export default function WidgetDragHandle({ visible = true }: { visible?: boolean }) {
  const { t } = useTranslation("widgets");
  if (!visible) return null;
  return (
    <div className="dash-widget-drag-handle" title={t("dragHandle.title")} aria-hidden>
      ⋮⋮
    </div>
  );
}
