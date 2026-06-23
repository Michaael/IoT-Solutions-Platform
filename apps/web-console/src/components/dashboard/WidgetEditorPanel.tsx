import { useTranslation } from "react-i18next";
import type { DashboardWidget, WidgetType } from "../../types/dashboard";
import { newWidget, WIDGET_TYPES } from "../../types/dashboard";
import { WIDGET_STYLE_KEYS_HINT } from "./widgetStyles";
import { widgetTypeI18nKey } from "./widgetI18n";
import {
  FieldPairs,
  WidgetDataSourceFields,
  WidgetTypeSpecificFields,
} from "./widgetEditorFields";

interface WidgetEditorPanelProps {
  widget: DashboardWidget | null;
  objects: Array<{ path: string; displayName: string; variableNames: string[] }>;
  dashboards?: Array<{ path: string; displayName: string }>;
  reports?: Array<{ path: string; displayName: string }>;
  onChange: (widget: DashboardWidget) => void;
  onDelete: () => void;
}

export default function WidgetEditorPanel({
  widget,
  objects,
  dashboards = [],
  reports = [],
  onChange,
  onDelete,
}: WidgetEditorPanelProps) {
  const { t } = useTranslation(["dashboard", "widgets", "common"]);

  if (!widget) {
    return (
      <aside className="dashboard-sidebar">
        <h4>{t("editor.widgetTitle")}</h4>
        <p className="hint">{t("editor.selectWidgetHint")}</p>
      </aside>
    );
  }

  const hintPath = widget.modelHintPath || widget.objectPath;
  const variables =
    objects.find((ctx) => ctx.path === hintPath)?.variableNames ?? [];
  const variableSelectEnabled = Boolean(hintPath) || Boolean(widget.selectionKey);

  const update = (patch: Partial<DashboardWidget>) => {
    onChange({ ...widget, ...patch } as DashboardWidget);
  };

  const fieldCtx = {
    widget,
    objects,
    dashboards,
    reports,
    variables,
    variableSelectEnabled,
    update,
  };

  return (
    <aside className="dashboard-sidebar">
      <header className="dashboard-sidebar-head">
        <h4>{t("editor.widgetEditorTitle")}</h4>
        <button type="button" className="btn danger small" onClick={onDelete}>
          {t("common:action.delete")}
        </button>
      </header>

      <div className="form-grid compact widget-editor-form">
        <FieldPairs>
          <h5 className="widget-editor-section">{t("editor.general")}</h5>
          <label>
            <span className="field-caption">{t("editor.titleField")}</span>
            <input value={widget.title} onChange={(e) => update({ title: e.target.value })} />
          </label>
          <label>
            <span className="field-caption">{t("editor.typeField")}</span>
            <select
              value={widget.type}
              onChange={(e) => {
                const nextType = e.target.value as WidgetType;
                const next = newWidget(nextType, 0);
                onChange({
                  ...next,
                  id: widget.id,
                  title: widget.title,
                  x: widget.x,
                  y: widget.y,
                  w: widget.w,
                  h: widget.h,
                });
              }}
            >
              {WIDGET_TYPES.map((item) => (
                <option key={item.type} value={item.type}>
                  {t(widgetTypeI18nKey(item.type))}
                </option>
              ))}
            </select>
          </label>
          <p className="hint full">{t("editor.layoutHint")}</p>
        </FieldPairs>

        <WidgetDataSourceFields {...fieldCtx} />
        <WidgetTypeSpecificFields {...fieldCtx} />

        <FieldPairs>
          <h5 className="widget-editor-section">{t("editor.styling")}</h5>
          <label className="full">
            stylesJson
            <textarea
              rows={6}
              value={widget.stylesJson ?? ""}
              onChange={(e) => update({ stylesJson: e.target.value || undefined })}
              placeholder={'{"value":{"fontSize":"0.88rem"},"meta":{"display":"none"}}'}
            />
          </label>
          <p className="hint full">
            {t("editor.stylesHint", { keys: WIDGET_STYLE_KEYS_HINT })}
          </p>

          <h5 className="widget-editor-section">{t("editor.advanced")}</h5>
          <label className="full">
            {t("editor.demoPreviewLabel")}
            <textarea
              rows={3}
              value={widget.demoPreviewJson ?? ""}
              onChange={(e) => update({ demoPreviewJson: e.target.value || undefined })}
            />
          </label>
        </FieldPairs>
      </div>
    </aside>
  );
}
