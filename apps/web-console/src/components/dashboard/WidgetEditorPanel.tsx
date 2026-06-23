import type { DashboardWidget, WidgetType } from "../../types/dashboard";
import { newWidget, WIDGET_TYPES } from "../../types/dashboard";
import { WIDGET_STYLE_KEYS_HINT } from "./widgetStyles";
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
  if (!widget) {
    return (
      <aside className="dashboard-sidebar">
        <h4>Виджет</h4>
        <p className="hint">Выберите виджет на сетке или добавьте новый.</p>
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
        <h4>Редактор виджета</h4>
        <button type="button" className="btn danger small" onClick={onDelete}>
          Удалить
        </button>
      </header>

      <div className="form-grid compact widget-editor-form">
        <FieldPairs>
          <h5 className="widget-editor-section">Общее</h5>
          <label>
            <span className="field-caption">Заголовок</span>
            <input value={widget.title} onChange={(e) => update({ title: e.target.value })} />
          </label>
          <label>
            <span className="field-caption">Тип</span>
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
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <p className="hint full">Позицию и размер меняйте перетаскиванием на сетке.</p>
        </FieldPairs>

        <WidgetDataSourceFields {...fieldCtx} />
        <WidgetTypeSpecificFields {...fieldCtx} />

        <FieldPairs>
          <h5 className="widget-editor-section">Оформление</h5>
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
            Ключи элементов: {WIDGET_STYLE_KEYS_HINT}. CSS в camelCase. Пусто — по умолчанию.
          </p>

          <h5 className="widget-editor-section">Расширенное</h5>
          <label className="full">
            demoPreviewJson (превью в редакторе)
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
