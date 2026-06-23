import type { DashboardWidget, WidgetType } from "../../types/dashboard";
import { newWidget, WIDGET_HISTORY_RANGE_OPTIONS, WIDGET_TYPES } from "../../types/dashboard";
import { WIDGET_STYLE_KEYS_HINT } from "./widgetStyles";

interface WidgetEditorPanelProps {
  widget: DashboardWidget | null;
  objects: Array<{ path: string; displayName: string; variableNames: string[] }>;
  dashboards?: Array<{ path: string; displayName: string }>;
  onChange: (widget: DashboardWidget) => void;
  onDelete: () => void;
}

export default function WidgetEditorPanel({
  widget,
  objects,
  dashboards = [],
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

  return (
    <aside className="dashboard-sidebar">
      <header className="dashboard-sidebar-head">
        <h4>Редактор виджета</h4>
        <button type="button" className="btn danger small" onClick={onDelete}>
          Удалить
        </button>
      </header>

      <div className="form-grid compact">
        <label>
          Заголовок
          <input
            value={widget.title}
            onChange={(e) => update({ title: e.target.value })}
          />
        </label>
        <label>
          Тип
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
        <label>
          Объект
          <select
            value={widget.objectPath}
            onChange={(e) => update({ objectPath: e.target.value, variableName: "" })}
          >
            <option value="">—</option>
            {objects.map((ctx) => (
              <option key={ctx.path} value={ctx.path}>
                {ctx.displayName}
              </option>
            ))}
          </select>
        </label>
        <label>
          Переменная
          <select
            value={widget.variableName}
            onChange={(e) => update({ variableName: e.target.value })}
            disabled={!variableSelectEnabled}
          >
            <option value="">—</option>
            {variables.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
          {!widget.objectPath && widget.selectionKey && (
            <input
              placeholder="или введите имя переменной"
              value={widget.variableName ?? ""}
              onChange={(e) => update({ variableName: e.target.value })}
            />
          )}
        </label>
        <label>
          Поле значения
          <input
            value={widget.valueField ?? "value"}
            onChange={(e) => update({ valueField: e.target.value })}
          />
        </label>
        <label>
          Ключ выбора (selectionKey)
          <input
            value={widget.selectionKey ?? ""}
            onChange={(e) => update({ selectionKey: e.target.value || undefined })}
            placeholder="device"
          />
        </label>
        <label>
          Образец объекта (modelHintPath)
          <select
            value={widget.modelHintPath ?? ""}
            onChange={(e) => update({ modelHintPath: e.target.value || undefined })}
          >
            <option value="">—</option>
            {objects.map((ctx) => (
              <option key={ctx.path} value={ctx.path}>
                {ctx.displayName}
              </option>
            ))}
          </select>
        </label>
        <label>
          paramKey (из session.params)
          <input
            value={widget.paramKey ?? ""}
            onChange={(e) => update({ paramKey: e.target.value || undefined })}
          />
        </label>
        <label>
          contextPathKey (путь из params)
          <input
            value={widget.contextPathKey ?? ""}
            onChange={(e) => update({ contextPathKey: e.target.value || undefined })}
          />
        </label>
        <p className="hint full">Позицию и размер меняйте перетаскиванием на сетке.</p>
        {(widget.type === "value" || widget.type === "chart" || widget.type === "sparkline") && (
          <label>
            Десятичные знаки
            <input
              type="number"
              min={0}
              max={6}
              value={
                widget.type === "value"
                  ? (widget.decimals ?? 1)
                  : widget.type === "chart"
                    ? (widget.decimals ?? 1)
                    : (widget.decimals ?? 1)
              }
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        )}
        {widget.type === "chart" && (
          <>
            <label>
              Период истории
              <select
                value={widget.historyRange ?? "live"}
                onChange={(e) =>
                  update({ historyRange: e.target.value as typeof widget.historyRange })
                }
              >
                {WIDGET_HISTORY_RANGE_OPTIONS.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              chartType
              <select
                value={widget.chartType ?? widget.chartStyle ?? "area"}
                onChange={(e) =>
                  update({ chartType: e.target.value as typeof widget.chartType })
                }
              >
                <option value="line">line</option>
                <option value="area">area</option>
                <option value="bar">bar</option>
              </select>
            </label>
            <label>
              Стиль графика
              <select
                value={widget.chartStyle ?? "area"}
                onChange={(e) =>
                  update({ chartStyle: e.target.value as "line" | "area" })
                }
              >
                <option value="area">Область</option>
                <option value="line">Линия</option>
              </select>
            </label>
            <label>
              Точек тренда
              <input
                type="number"
                min={10}
                max={500}
                value={widget.maxPoints ?? 120}
                onChange={(e) => update({ maxPoints: Number(e.target.value) })}
              />
            </label>
            <label>
              Цвет
              <input
                type="color"
                value={widget.color ?? "#2f81f7"}
                onChange={(e) => update({ color: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "sparkline" && (
          <>
            <label>
              Период истории
              <select
                value={widget.historyRange ?? "live"}
                onChange={(e) =>
                  update({ historyRange: e.target.value as typeof widget.historyRange })
                }
              >
                {WIDGET_HISTORY_RANGE_OPTIONS.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Точек тренда
              <input
                type="number"
                min={10}
                max={200}
                value={widget.maxPoints ?? 40}
                onChange={(e) => update({ maxPoints: Number(e.target.value) })}
              />
            </label>
            <label>
              Цвет
              <input
                type="color"
                value={widget.color ?? "#3fb950"}
                onChange={(e) => update({ color: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "function" && (
          <>
            <label>
              Имя функции
              <input
                value={widget.functionName}
                onChange={(e) => update({ functionName: e.target.value })}
              />
            </label>
            <label>
              Текст кнопки
              <input
                value={widget.buttonLabel ?? ""}
                onChange={(e) => update({ buttonLabel: e.target.value })}
              />
            </label>
            <label>
              Подтверждение (опционально)
              <input
                value={widget.confirmMessage ?? ""}
                onChange={(e) => update({ confirmMessage: e.target.value })}
              />
            </label>
            <label>
              JSON input (опционально)
              <textarea
                rows={3}
                value={widget.inputJson ?? ""}
                onChange={(e) => update({ inputJson: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "function-form" && (
          <>
            <label>
              Имя функции
              <input
                value={widget.functionName}
                onChange={(e) => update({ functionName: e.target.value })}
              />
            </label>
            <label>
              Текст кнопки
              <input
                value={widget.buttonLabel ?? ""}
                onChange={(e) => update({ buttonLabel: e.target.value })}
              />
            </label>
            <label>
              Поля (JSON)
              <textarea
                rows={5}
                value={widget.fieldsJson ?? "[]"}
                onChange={(e) => update({ fieldsJson: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "progress" && (
          <>
            <label>
              Переменная текущего
              <input
                value={widget.currentVariable}
                onChange={(e) => update({ currentVariable: e.target.value })}
              />
            </label>
            <label>
              Переменная максимума
              <input
                value={widget.maxVariable}
                onChange={(e) => update({ maxVariable: e.target.value })}
              />
            </label>
            <label>
              Единица
              <input
                value={widget.unit ?? ""}
                onChange={(e) => update({ unit: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "object-table" && (
          <>
            <label>
              Родитель (parentPath)
              <input
                value={widget.parentPath}
                onChange={(e) => update({ parentPath: e.target.value })}
              />
            </label>
            <label>
              Колонки (JSON)
              <textarea
                rows={4}
                value={widget.columnsJson ?? "[]"}
                onChange={(e) => update({ columnsJson: e.target.value })}
              />
            </label>
            <label>
              Дашборд по клику на строку (path)
              <input
                value={widget.rowTargetDashboard ?? ""}
                onChange={(e) => update({ rowTargetDashboard: e.target.value || undefined })}
                placeholder="root.platform.dashboards.detail"
              />
            </label>
            <label>
              Режим открытия строки
              <select
                value={widget.rowOpenMode ?? "navigate"}
                onChange={(e) =>
                  update({ rowOpenMode: e.target.value as "navigate" | "modal" })
                }
                disabled={!widget.rowTargetDashboard}
              >
                <option value="navigate">Переход</option>
                <option value="modal">Модальное окно</option>
              </select>
            </label>
            <label>
              rowSelectionKey (ключ в целевом дашборде)
              <input
                value={widget.rowSelectionKey ?? ""}
                onChange={(e) => update({ rowSelectionKey: e.target.value || undefined })}
                placeholder="device"
              />
            </label>
            <label>
              rowParamsJson
              <textarea
                rows={2}
                value={widget.rowParamsJson ?? ""}
                onChange={(e) => update({ rowParamsJson: e.target.value || undefined })}
                placeholder='{"clusterPath":"root..."}'
              />
            </label>
          </>
        )}
        {widget.type === "event-feed" && (
          <>
            <label>
              Префикс пути
              <input
                value={widget.objectPathPrefix ?? ""}
                onChange={(e) => update({ objectPathPrefix: e.target.value })}
              />
            </label>
            <label>
              Имена событий (JSON)
              <textarea
                rows={3}
                value={widget.eventNamesJson ?? "[]"}
                onChange={(e) => update({ eventNamesJson: e.target.value })}
              />
            </label>
            <label>
              Макс. записей
              <input
                type="number"
                min={5}
                max={100}
                value={widget.maxItems ?? 20}
                onChange={(e) => update({ maxItems: Number(e.target.value) })}
              />
            </label>
            <label>
              Фильтр payload
              <input
                value={widget.payloadFilterExpr ?? ""}
                onChange={(e) => update({ payloadFilterExpr: e.target.value || undefined })}
                placeholder="count>10 && name contains abc"
              />
            </label>
          </>
        )}
        {widget.type === "work-queue" && (
          <>
            <label>
              Operator ID
              <input
                value={widget.operatorId ?? "operator"}
                onChange={(e) => update({ operatorId: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "gauge" && (
          <>
            <label>
              minVariable
              <input
                value={widget.minVariable ?? ""}
                onChange={(e) => update({ minVariable: e.target.value })}
              />
            </label>
            <label>
              maxVariable
              <input
                value={widget.maxVariable ?? ""}
                onChange={(e) => update({ maxVariable: e.target.value })}
              />
            </label>
            <label>
              Единица
              <input
                value={widget.unit ?? ""}
                onChange={(e) => update({ unit: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "card-grid" && (
          <>
            <label>
              Родитель (parentPath)
              <input
                value={widget.parentPath}
                onChange={(e) => update({ parentPath: e.target.value })}
              />
            </label>
            <label>
              Переменные (JSON)
              <textarea
                rows={3}
                value={widget.variablesJson ?? "[]"}
                onChange={(e) => update({ variablesJson: e.target.value })}
              />
            </label>
            <label>
              Дашборд по клику на карточку (path)
              <input
                value={widget.cardTargetDashboard ?? ""}
                onChange={(e) => update({ cardTargetDashboard: e.target.value || undefined })}
                placeholder="root.platform.dashboards.detail"
              />
            </label>
            <label>
              Режим открытия карточки
              <select
                value={widget.cardOpenMode ?? "navigate"}
                onChange={(e) =>
                  update({ cardOpenMode: e.target.value as "navigate" | "modal" })
                }
                disabled={!widget.cardTargetDashboard}
              >
                <option value="navigate">Переход</option>
                <option value="modal">Модальное окно</option>
              </select>
            </label>
            <label>
              Ключ выбора при клике (cardSelectionKey)
              <input
                value={widget.cardSelectionKey ?? ""}
                onChange={(e) => update({ cardSelectionKey: e.target.value || undefined })}
                placeholder="device"
                disabled={!widget.cardTargetDashboard}
              />
            </label>
          </>
        )}
        {widget.type === "dashboard-link" && (
          <>
            <label>
              Целевой дашборд (path)
              <input
                value={widget.targetDashboardPath}
                onChange={(e) => update({ targetDashboardPath: e.target.value })}
                placeholder="root.platform.dashboards.detail"
              />
            </label>
            <label>
              Режим
              <select
                value={widget.openMode ?? "navigate"}
                onChange={(e) =>
                  update({ openMode: e.target.value as "navigate" | "modal" })
                }
              >
                <option value="navigate">Переход</option>
                <option value="modal">Модальное окно</option>
              </select>
            </label>
            <label>
              Текст кнопки
              <input
                value={widget.buttonLabel ?? ""}
                onChange={(e) => update({ buttonLabel: e.target.value })}
              />
            </label>
            <label>
              Заголовок модального окна
              <input
                value={widget.modalTitle ?? ""}
                onChange={(e) => update({ modalTitle: e.target.value })}
                disabled={widget.openMode !== "modal"}
              />
            </label>
            <label>
              Подтверждение (опционально)
              <input
                value={widget.confirmMessage ?? ""}
                onChange={(e) => update({ confirmMessage: e.target.value })}
              />
            </label>
            <label>
              contextSelectionJson
              <textarea
                rows={2}
                value={widget.contextSelectionJson ?? ""}
                onChange={(e) =>
                  update({ contextSelectionJson: e.target.value || undefined })
                }
              />
            </label>
            <label>
              contextParamsJson
              <textarea
                rows={2}
                value={widget.contextParamsJson ?? ""}
                onChange={(e) => update({ contextParamsJson: e.target.value || undefined })}
              />
            </label>
          </>
        )}
        {widget.type === "report" && (
          <>
            <label className="full">
              Путь отчёта (reportPath)
              <input
                value={widget.reportPath}
                onChange={(e) => update({ reportPath: e.target.value })}
                placeholder="root.platform.reports.ready-items"
              />
            </label>
            <label>
              Пустое сообщение
              <input
                value={widget.emptyMessage ?? ""}
                onChange={(e) => update({ emptyMessage: e.target.value })}
              />
            </label>
          </>
        )}
        {widget.type === "pie-chart" && (
          <>
            <label>
              Поле подписи (labelField)
              <input
                value={widget.labelField ?? "name"}
                onChange={(e) => update({ labelField: e.target.value })}
              />
            </label>
            <label>
              Десятичные знаки
              <input
                type="number"
                min={0}
                max={6}
                value={widget.decimals ?? 1}
                onChange={(e) => update({ decimals: Number(e.target.value) })}
              />
            </label>
          </>
        )}
        {widget.type === "history-table" && (
          <label>
            Десятичные знаки
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 2}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        )}
        {widget.type === "variable-editor" && (
          <label>
            Переменные (JSON, пусто = все)
            <textarea
              rows={3}
              value={widget.variablesJson ?? "[]"}
              onChange={(e) => update({ variablesJson: e.target.value })}
            />
          </label>
        )}
        {widget.type === "svg-widget" && (
          <>
            <label>
              SVG URL
              <input
                value={widget.svgUrl}
                onChange={(e) => update({ svgUrl: e.target.value })}
                placeholder="/lab-assets/button.svg"
              />
            </label>
            <label>
              Действие по клику
              <select
                value={widget.clickAction ?? ""}
                onChange={(e) =>
                  update({
                    clickAction: (e.target.value || undefined) as "function" | "toggle" | undefined,
                  })
                }
              >
                <option value="">—</option>
                <option value="function">Функция</option>
                <option value="toggle">Toggle переменной</option>
              </select>
            </label>
            {widget.clickAction === "function" && (
              <label>
                Имя функции
                <input
                  value={widget.functionName ?? ""}
                  onChange={(e) => update({ functionName: e.target.value })}
                />
              </label>
            )}
            {widget.clickAction === "toggle" && (
              <label>
                Переменная toggle
                <input
                  value={widget.toggleVariable ?? widget.variableName ?? ""}
                  onChange={(e) => update({ toggleVariable: e.target.value })}
                />
              </label>
            )}
            <label>
              Подтверждение (опционально)
              <input
                value={widget.confirmMessage ?? ""}
                onChange={(e) => update({ confirmMessage: e.target.value })}
              />
            </label>
          </>
        )}
        {(widget.type === "composite-widget" ||
          widget.type === "panel" ||
          widget.type === "drawer-panel") && (
          <label className="full">
            Дочерние виджеты (childrenJson)
            <textarea
              rows={8}
              value={widget.childrenJson ?? "[]"}
              onChange={(e) => update({ childrenJson: e.target.value })}
            />
          </label>
        )}
        {widget.type === "sub-dashboard" && (
          <>
            <label>
              Целевой дашборд
              <select
                value={widget.targetDashboardPath ?? ""}
                onChange={(e) =>
                  update({ targetDashboardPath: e.target.value || undefined })
                }
              >
                <option value="">—</option>
                {dashboards.map((d) => (
                  <option key={d.path} value={d.path}>
                    {d.displayName}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Или путь вручную
              <input
                value={widget.targetDashboardPath ?? ""}
                onChange={(e) =>
                  update({ targetDashboardPath: e.target.value || undefined })
                }
              />
            </label>
            <label>
              targetDashboardPathKey (динамический путь)
              <input
                value={widget.targetDashboardPathKey ?? ""}
                onChange={(e) =>
                  update({ targetDashboardPathKey: e.target.value || undefined })
                }
              />
            </label>
            <label>
              <input
                type="checkbox"
                checked={widget.inheritContext !== false}
                onChange={(e) => update({ inheritContext: e.target.checked })}
              />
              Наследовать контекст (inheritContext)
            </label>
          </>
        )}
        {widget.type === "tab-panel" && (
          <label className="full">
            Вкладки (tabsJson)
            <textarea
              rows={8}
              value={widget.tabsJson ?? "[]"}
              onChange={(e) => update({ tabsJson: e.target.value })}
            />
          </label>
        )}
        {widget.type === "map" && (
          <>
            <label>
              parentPath
              <input
                value={widget.parentPath}
                onChange={(e) => update({ parentPath: e.target.value })}
              />
            </label>
            <label>
              latVariable
              <input
                value={widget.latVariable ?? "coordinates"}
                onChange={(e) => update({ latVariable: e.target.value })}
              />
            </label>
          </>
        )}
        <label className="full">
          Стили элементов (stylesJson)
          <textarea
            rows={6}
            value={widget.stylesJson ?? ""}
            onChange={(e) => update({ stylesJson: e.target.value || undefined })}
            placeholder={'{"value":{"fontSize":"0.88rem"},"meta":{"display":"none"}}'}
          />
        </label>
        <p className="hint full">
          Ключи элементов: {WIDGET_STYLE_KEYS_HINT}. Значения — CSS в camelCase
          (fontSize, color, whiteSpace, overflowY…). Пусто — стили по умолчанию.
        </p>
      </div>
    </aside>
  );
}
