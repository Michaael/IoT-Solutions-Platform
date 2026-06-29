# ADR-0019: Единая модель Platform Rule (dashboard + bindings)

Статус: **Accepted** (фазы 0–2 реализованы; фаза 3 — deprecation legacy mini-DSL)

## Контекст

На платформе накопилось несколько **несовместимых способов** выразить логику:

| Механизм | Где |
|----------|-----|
| Binding rules (`@bindingRules`) | объекты, CEL |
| Alert rules | ALERT, CEL |
| Dashboard session (`selection`, `params`) | web-console, ad-hoc keys |
| `showWhenJson` | function-form fields |
| `payloadFilterExpr` | event-feed widget, mini-DSL |
| Spreadsheet `ISPF()` | ячейки |

Оператор и инженер не могут запомнить десять DSL. AI может, люди — нет.

ADR [0010](0010-binding-rules-only.md) уже зафиксировал **binding rules** как единственный механизм вычисления переменных. Нужно **расширить ту же модель** на UI-логику дашбордов и события, не вводя параллельные системы.

## Решение

1. **Platform Rule = BindingRule** с расширенным `target` и activator `onContextChange`. Одна форма: **когда → если (CEL) → тогда (effect)**.

2. **Три вида effect** (`target.kind`):

   | `kind` | Назначение |
   |--------|------------|
   | `variable` | как сегодня — запись в переменную объекта (`variableName`, `field`) |
   | `context` | запись в `@dashboardContext` на объекте `DASHBOARD` (`path`, dot-notation) |
   | `event` | публикация platform event (`eventName`, optional payload из `expression`) |

   Обратная совместимость: если `kind` отсутствует → `variable` (текущий JSON `{ "variableName", "field" }`).

3. **Dashboard context** — reserved-переменная `@dashboardContext` (JSON) на объекте `DASHBOARD`:

   ```json
   {
     "selection": { "device": "root.platform.devices.snmp-01" },
     "params": { "mode": "normal" },
     "widgets": { "alarm-panel": { "visible": true } },
     "updatedAt": "...",
     "updatedBy": "operator"
   }
   ```

   Web-console `DashboardSession` — **кэш** этой переменной + optimistic update; источник истины — сервер + WebSocket.

4. **Activator `onContextChange`** в `BindingActivators` — правило на объекте `DASHBOARD` пересчитывается при PUT `@dashboardContext` (клик таблицы, форма, nav).

5. **Виджеты не содержат логики** — только layout и адреса данных (`selectionKey`, `paramKey`, `contextPathKey`). Show/hide, switch mode — **правила**, пишущие в `context.widgets.*` или `context.params.*`.

6. **Один редактор** — существующая вкладка «Привязки» / `BindingRulesPanel`; для дашборда — та же вкладка «Правила» в Dashboard Builder.

7. **Legacy migration** (фазы 3+): `showWhenJson`, `payloadFilterExpr` → CEL rules; не добавлять `behaviorJson` на виджеты.

## Последствия

**Плюсы**

- Один язык (CEL), один engine, одна документация ([BINDINGS.md](../BINDINGS.md), [PLATFORM_LOGIC.md](../PLATFORM_LOGIC.md)).
- Контекст дашборда durable, multi-client через WS; события в journal.
- Таблица→детали остаётся, но семантика = «изменение context», не отдельная подсистема.

**Минусы / риски**

- Latency UI: правила на сервере; нужен optimistic session + reconcile по WS (фаза 1).
- Расширение `BindingRuleEngine` и десериализация `BindingTarget` — breaking-safe только при default `kind=variable`.
- CEL для оператора сложнее JSON-predicate — компенсируется шаблонами правил и AI agent.

## План реализации

| Фаза | Содержание |
|------|------------|
| 0 | ADR, [PLATFORM_LOGIC.md](../PLATFORM_LOGIC.md), обновление BINDINGS/DASHBOARDS |
| 1 | `@dashboardContext`, `target.kind`, `onContextChange`, engine, WS sync |
| 2 | Rules tab в Dashboard Builder, demo layout |
| 3 | Deprecate legacy mini-DSL |

## Связанные материалы

- [0010-binding-rules-only.md](0010-binding-rules-only.md)
- [BINDINGS.md](../BINDINGS.md)
- [DASHBOARDS.md](../DASHBOARDS.md)
- [PLATFORM_LOGIC.md](../PLATFORM_LOGIC.md)
