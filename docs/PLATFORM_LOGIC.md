# Единая логика платформы (Platform Rule)

Один механизм для привязок переменных, логики дашборда и side-effect событий.

**Статус:** спецификация (ADR [0019](decisions/0019-platform-rule-unification.md), **Proposed**). Runtime расширений — по фазам 1–3.

---

## Модель для человека

```
КОГДА  →  ЕСЛИ (CEL)  →  ТОГДА (записать результат)
```

Тот же mental model, что [binding rules](BINDINGS.md):

- **Binding rule** на устройстве → пишет в переменную.
- **Dashboard rule** на объекте `DASHBOARD` → пишет в `@dashboardContext` или fire event.
- **Alert rule** на `ALERT` → отдельный объект, но condition = CEL (будущая унификация UI — не в scope v1).

---

## JSON: Platform Rule (= BindingRule)

### Effect `variable` (сегодня, default)

```json
{
  "id": "avg-temp",
  "enabled": true,
  "order": 10,
  "activators": {
    "onVariableChange": [{ "objectPath": "self", "variableName": "temperature" }]
  },
  "condition": "",
  "expression": "self.temperature.value",
  "target": {
    "kind": "variable",
    "variableName": "avgTemp",
    "field": "value"
  }
}
```

Legacy без `kind`:

```json
"target": { "variableName": "avgTemp", "field": "value" }
```

### Effect `context` (dashboard UI state)

```json
{
  "id": "alarm-mode",
  "enabled": true,
  "order": 20,
  "activators": {
    "onVariableChange": [
      { "objectPath": "self", "variableName": "@dashboardContext" }
    ],
    "onContextChange": true
  },
  "condition": "context.selection.device != \"\" && refAt(context.selection.device, temperature).value > 80",
  "expression": "\"alarm\"",
  "target": {
    "kind": "context",
    "path": "params.mode"
  }
}
```

`context.*` в CEL — snapshot `@dashboardContext` на момент eval (фаза 1).

### Effect `event` (journal / workflow)

```json
{
  "id": "fire-alarm",
  "enabled": true,
  "order": 30,
  "activators": { "onContextChange": true },
  "condition": "context.params.mode == \"alarm\"",
  "expression": "{\"device\": context.selection.device, \"mode\": \"alarm\"}",
  "target": {
    "kind": "event",
    "eventName": "hmi.dashboard.alarm"
  }
}
```

---

## Activators

| Поле | Статус | Назначение |
|------|--------|------------|
| `onStartup` | есть | старт / attach модели |
| `onVariableChange` | есть | телеметрия, переменные |
| `onEvent` | есть | имя platform event |
| `periodicMs` | есть | периодический пересчёт |
| `onContextChange` | **planned** | изменение `@dashboardContext` |

---

## `@dashboardContext` (planned, фаза 1)

Reserved JSON-переменная на объекте `DASHBOARD` (модель `dashboard-v1`).

| Поле | Тип | Назначение |
|------|-----|------------|
| `selection` | `Record<string, string>` | слоты выбора (`device`, `order`, …) |
| `params` | `Record<string, unknown>` | скаляры, режимы, параметры отчёта |
| `widgets` | `Record<string, { visible?: boolean }>` | runtime visibility по id виджета |
| `updatedAt` | ISO string | audit |
| `updatedBy` | string | username |

Web-console `DashboardSession` зеркалирует эту структуру.

### Публикация контекста (publishers)

| Источник | Действие |
|----------|----------|
| object-table / card-grid / map | `selection.*` + `params.*` из строки |
| report widget | `rowParamsFromRowJson` → `params` |
| function-form | `syncFieldsToSessionJson` → `params` |
| dashboard-link | `contextSelectionJson` / `contextParamsJson` |
| operator URL `?ctx=` | начальный snapshot |

Все publishers (фаза 1+) → **PUT `@dashboardContext`**, не только React state.

### Потребление (consumers)

| Поле виджета | Читает из context |
|--------------|-------------------|
| `selectionKey` | `selection[key]` |
| `paramKey` | `params[key]` |
| `contextPathKey` | `params[key]` как object path |
| visibility | `widgets[widgetId].visible` via rules + runtime merge |

---

## Инвентарь legacy → Platform Rule

| Legacy | Где | Миграция | Статус |
|--------|-----|----------|--------|
| `showWhenJson` | function-form field | CEL rule на form object или rule на dashboard `context.params.*` | runtime сохранён; UI migration — фаза 3 |
| `payloadFilterExpr` | event-feed | CEL `condition` на rule / server-side filter | runtime сохранён; deprecation hint в редакторе |
| `requireSessionParamsJson` | link, form | `condition` на non-empty keys в context | runtime сохранён; deprecation hint в редакторе |
| Dashboard session only (sessionStorage) | operator | `@dashboardContext` + WS | фаза 1 ✅ |
| Per-widget `visible` boolean | layout | static default; runtime → rules → `widgets.*.visible` | фаза 2 ✅ |
| Spreadsheet session blob | `params[sheet:…]` | binding cells + `onContextChange`; optional export rules | planned |
| Alert CEL | ALERT object | остаётся; тот же CEL editor | — |

**Не добавлять:** `behaviorJson`, `visibleWhen` на виджете, отдельный dashboard DSL.

---

## UI

| Место | Компонент |
|-------|-----------|
| Object Inspector | `BindingRulesPanel` |
| Dashboard Builder | `DashboardRulesPanel` + templates (фаза 2) |
| AI agent | `create_binding_rule` + dashboard templates |

---

## REST / engine (planned)

- Существующий CRUD `/binding-rules` — без изменений URL.
- `BindingRuleEngine.evaluateOnContextChange(dashboardPath, contextJson)` — фаза 1.
- CEL validate: `POST /api/v1/expressions/validate` (уже есть).

---

## Пример: SNMP monitoring

**Layout:** object-table `selectionKey: device` + value/chart с тем же key.

**Rules на `root.platform.dashboards.snmp-host-monitoring`:**

1. When temperature on selected device > 80 → `params.mode = "alarm"`.
2. When `params.mode == "alarm"` → `widgets.alarm-panel.visible = true`.
3. When `params.mode == "normal"` → `widgets.alarm-panel.visible = false`.
4. When mode → alarm → fire `hmi.dashboard.alarm`.

Виджеты **без** условных полей в JSON layout.

---

## Связанные документы

- [BINDINGS.md](BINDINGS.md) — binding rules, CEL, API
- [DASHBOARDS.md](DASHBOARDS.md) — layout, widgets, context
- [decisions/0019-platform-rule-unification.md](decisions/0019-platform-rule-unification.md)
