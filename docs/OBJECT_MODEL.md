# Модель объектов

Бизнес-логика ISPF выражается через **состав узлов дерева**: модели (blueprint), переменные, события, функции, workflow и узлы автоматизации. См. [ARCHITECTURE.md § Основной принцип](ARCHITECTURE.md#основной-принцип-бизнес-логика-в-механизмах-платформы).

## Иерархия

Все сущности платформы — **узлы дерева объектов** с dot-нотацией пути:

```
root
└── root.platform
    ├── root.platform.devices
    │   ├── root.platform.devices.demo-sensor-01
    │   └── root.platform.devices.snmp-localhost
    ├── root.platform.models
    ├── root.platform.dashboards
    │   └── root.platform.dashboards.demo-sensor
    ├── root.platform.reports
    │   └── root.platform.reports.ready-items
    ├── root.platform.security
    │   ├── root.platform.security.users
    │   │   ├── root.platform.security.users.admin
    │   │   └── root.platform.security.users.operator
    │   └── root.platform.security.roles
    │       ├── root.platform.security.roles.admin
    │       └── root.platform.security.roles.operator
    ├── root.platform.workflows
    │   └── root.platform.workflows.demo-alarm-handler
    ├── root.platform.alert-rules
    │   └── root.platform.alert-rules.temperature-threshold-exceeded
    └── root.platform.correlators
        └── root.platform.correlators.alarm-handler-on-threshold-event
```

Пути разделяются **точкой** (`.`), не слэшем. Резолвинг дочернего пути: `parentPath + "." + name`.

## Типы объектов (`ObjectType`)

| Тип | Назначение |
|-----|------------|
| `ROOT` | Корень дерева |
| `TENANT` | Арендатор (multi-tenancy, план) |
| `PLATFORM` | Узел `root.platform` |
| `DEVICES` | Каталог устройств |
| `DEVICE` | Устройство с драйвером |
| `DRIVER` | Экземпляр драйвера |
| `MODEL` | Определение модели |
| `DASHBOARDS` | Каталог дашбордов |
| `DASHBOARD` | HMI-экран |
| `WORKFLOWS` | Каталог workflow |
| `WORKFLOW` | BPMN-процесс |
| `ALERT_RULES` | Каталог alert rules |
| `ALERT` | Правило алерта |
| `CORRELATORS` | Каталог correlators |
| `CORRELATOR` | Event correlator |
| `APPLICATIONS` | Каталог приложений |
| `APPLICATION` | Прикладное приложение (bundle) |
| `OPERATOR_APPS` | Каталог operator UI |
| `SECURITY` | Корень RBAC |
| `USERS` / `USER` | Пользователи |
| `ROLES` / `ROLE` | Роли |
| `FUNCTIONS` / `FUNCTION` | Функции приложения |
| `REPORTS` / `REPORT` | Каталог `root.platform.reports` и SQL-отчёты (`report-v1`) |
| `AGENT` | Edge agent |
| `CUSTOM` | Произвольный контейнер (fallback для неизвестных узлов) |

Системные папки (`PLATFORM`, `DEVICES`, `ALERT_RULES`, …) получают семантический тип при bootstrap и миграции `V22__system_object_types.sql`. Пользовательские узлы — по шаблону модели или `CUSTOM`.

## Безопасность в дереве

Узлы `root.platform.security` создаются автоматически при старте и при изменении пользователей:

| Путь | Тип | Переменные |
|------|-----|------------|
| `...security.users.{username}` | `USER` | `username`, `displayName`, `roles`, `enabled` |
| `...security.roles.{role}` | `CUSTOM` | `roleName`, `description` |

CRUD пользователей — через `POST/PUT/DELETE /api/v1/security/users` (роль `admin`). Удаление узла `USER` в дереве также удаляет учётную запись.

## Состав объекта (`PlatformObject`)

Каждый узел содержит:

- **Метаданные:** `id`, `path`, `displayName`, `description`, `templateId`, `sortOrder`, `createdAt`
- **Переменные** (`Variable`) — типизированные значения
- **Функции** (`FunctionDescriptor`) — вызываемые операции
- **События** (`EventDescriptor`) — публикуемые типы событий

## Типизированные данные

### DataSchema

Описание полей записи:

```json
{
  "name": "temperature",
  "fields": [
    { "name": "value", "type": "DOUBLE" },
    { "name": "unit", "type": "STRING" }
  ]
}
```

Типы полей (`FieldType`): `BOOLEAN`, `INTEGER`, `LONG`, `DOUBLE`, `STRING`, `DATETIME`, `BINARY`, `RECORD`, `RECORD_LIST`.

### DataRecord

Табличная структура со строками данных. Типичный формат одной строки для REST:

```json
{
  "schema": { "name": "temperature", "fields": [...] },
  "rows": [{ "value": 23.5, "unit": "C" }]
}
```

Виджеты и API читают поле через `valueField` (по умолчанию `"value"`).

## Переменные

| Свойство | Описание |
|----------|----------|
| `name` | Имя в объекте |
| `schema` | `DataSchema` |
| `readable` / `writable` | Права доступа |
| `bindingExpression` | CEL или platform binding (опционально) |
| `value` | Текущий `DataRecord` |

### Вычисляемые привязки (bindings)

При изменении любой переменной на объекте `BindingEvaluator` пересчитывает все binding-переменные с непустым `bindingExpression`.

Два вида выражений:

| Вид | Пример |
|-----|--------|
| **CEL** | `self.temperature.value > self.threshold.value` |
| **Platform function** | `counterRate(ifInOctets)` |

**Полный справочник** всех функций, параметров, stateful-поведения и примеров: **[BINDINGS.md](BINDINGS.md)**.

Краткий перечень platform bindings:

| Функция | Назначение |
|---------|------------|
| `selectField` | Поле source-переменной |
| `scale` | Линейное отображение диапазона |
| `clamp` | Ограничение min…max |
| `format` | Строка по шаблону `String.format` |
| `delta` | Δ к предыдущему sample (stateful) |
| `counterRate` | B/s из Counter32 с wrap (stateful) |

Проверка: `POST /api/v1/expressions/validate`.

## События

`EventDescriptor` задаёт имя, описание, схему payload и уровень (`DEBUG` … `CRITICAL`).

Публикация:

```http
POST /api/v1/events/fire
```

Событие валидируется по descriptor объекта, пишется в `event_history`, рассылается через WebSocket.

## Функции

`FunctionDescriptor` — имя, input/output `DataSchema`.

Вызов:

```http
POST /api/v1/objects/by-path/functions/invoke?path=...&name=...
Content-Type: application/json

{ "schema": {...}, "rows": [{...}] }
```

Встроенный handler: `acknowledgeAlarm` на `demo-sensor-01` (сброс `alarmActive`).

Расширение: реализуйте `FunctionHandler` в `ispf-server` и зарегистрируйте как Spring `@Component`.

## Модели (шаблоны)

Объекты создаются вручную или из **модели** (`templateId`). Модель задаёт набор переменных, событий, функций и bindings.

См. [MODELS.md](MODELS.md).

## Персистентность

Flyway-миграции (`packages/ispf-server/src/main/resources/db/migration/`):

| Таблица | Содержимое |
|---------|------------|
| `object_nodes` | Узлы дерева (в т.ч. alert rules и correlators) |
| `object_variables` | Значения и binding_expr |
| `variable_samples` | История телеметрии (time-series сэмплы) |
| `event_history` | Журнал событий |
| `workflow_instances` | Экземпляры BPMN |
| `workflow_user_tasks` | Задачи оператора |
| `correlator_hits` | Срабатывания correlators (runtime) |
| `alert_rules` / `event_correlators` | Legacy; мигрируются в дерево при старте |

При старте `ObjectManager` загружает дерево из БД или выполняет `PlatformBootstrap` (пустая БД).

## Live-обновления

`ObjectChangeEvent` публикуется при создании/изменении/удалении объектов и переменных.

WebSocket `WS /ws/objects` рассылает JSON:

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Web Console подписывается через `useObjectWebSocket` и инвалидирует TanStack Query cache.

## CRUD через API

| Операция | Endpoint |
|----------|----------|
| Список | `GET /api/v1/objects?parent=` |
| Получить | `GET /api/v1/objects/by-path?path=` |
| Редактор | `GET /api/v1/objects/by-path/editor?path=` |
| Создать | `POST /api/v1/objects` |
| Обновить | `PATCH /api/v1/objects/by-path?path=` |
| Удалить | `DELETE /api/v1/objects/by-path?path=` |
| Порядок дочерних | `PUT /api/v1/objects/reorder` (`parentPath`, `orderedPaths`) |
| Переменные | `GET/PUT .../variables` |

При создании `DASHBOARD` / `WORKFLOW` автоматически применяется соответствующая built-in модель.

## История переменных

Текущее значение — в `object_variables`. **История** пишется в `variable_samples` только для переменных с `historyEnabled = true` при каждом `VARIABLE_UPDATED` (debounce по `min-interval-ms`).

У каждой переменной (в модели и на объекте):

| Поле | Описание |
|------|----------|
| `historyEnabled` | Записывать ли временной ряд |
| `historyRetentionDays` | Срок хранения в днях; `null` — платформенный default из `ispf.variable-history.retention-days` |

```http
GET /api/v1/objects/by-path/variables/history?path=...&name=temperature&field=value&limit=500
PATCH /api/v1/objects/by-path/variables/history?path=...&name=temperature
Content-Type: application/json

{"historyEnabled": true, "historyRetentionDays": 30}
```

Конфигурация платформы: `ispf.variable-history` в `application.yml` (`enabled`, `min-interval-ms`, `retention-days`).

Графики (`useTrendSeries`) загружают историю с сервера и дополняют live-точками через WebSocket/polling.

Подробный roadmap: [VARIABLE_HISTORY.md](./VARIABLE_HISTORY.md).
