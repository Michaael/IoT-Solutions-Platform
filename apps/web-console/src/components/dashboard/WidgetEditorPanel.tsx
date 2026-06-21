import type { DashboardWidget, WidgetType } from "../../types/dashboard";
import { WIDGET_HISTORY_RANGE_OPTIONS, WIDGET_TYPES } from "../../types/dashboard";
import { WIDGET_STYLE_KEYS_HINT } from "./widgetStyles";

interface WidgetEditorPanelProps {
  widget: DashboardWidget | null;
  objects: Array<{ path: string; displayName: string; variableNames: string[] }>;
  onChange: (widget: DashboardWidget) => void;
  onDelete: () => void;
}

export default function WidgetEditorPanel({
  widget,
  objects,
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

  const variables =
    objects.find((ctx) => ctx.path === widget.objectPath)?.variableNames ?? [];

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
            onChange={(e) => update({ type: e.target.value as WidgetType })}
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
            disabled={!widget.objectPath}
          >
            <option value="">—</option>
            {variables.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
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
            placeholder="order"
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
