# Приложения на платформе (REQ-PF)

Платформенный слой для развёртывания прикладных решений (например, **terminal / michaael**) **без Java-кода в `ispf-server`**. Соответствует ADR-0008 и требованиям REQ-PF.

**Полный backlog и gap-анализ:** [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md).

## Обзор

| REQ-PF | Capability | API / компонент |
|--------|------------|-----------------|
| 01 | Application Function Runtime | `POST /applications/{appId}/functions/deploy`, JSON script engine |
| 02 | Application Data Layer | `POST /applications/{appId}/data/migrate` |
| 03 | Application Package Deploy | `POST /applications/{appId}/deploy` |
| 04 | BPMN `invoke_function` | `WorkflowActionType.INVOKE_FUNCTION` |
| 05 | Platform Scheduler | `GET/POST /schedules` |
| 06 | BFF Wire Gateway | `POST /bff/invoke` |
| 07 | Model Registry Persistence | `model_definitions` + автосохранение при CRUD моделей |
| 10 | Workflow Cancel | `POST /workflows/instances/{id}/cancel` |

Пример полного app bundle (oil-terminal) — ветка **`feature/oil-terminal-reference`**, не в `main`. См. [PLUGINS.md](PLUGINS.md).

## Регистрация приложения

```http
POST /api/v1/applications
Content-Type: application/json

{
  "appId": "terminal",
  "displayName": "Oil Terminal",
  "tablePrefix": "terminal_",
  "schemaName": "terminal"
}
```

## Миграции данных (REQ-PF-02)

SQL-скрипты приложения **не** попадают в Flyway платформы. Деплой через API в **изолированной schema** (`schemaName`, по умолчанию `app_{appId}`):

```http
POST /api/v1/applications/terminal/data/migrate
Content-Type: application/json

{
  "version": "1.0.0",
  "scripts": [
    { "id": "dispatch_order", "sql": "CREATE TABLE IF NOT EXISTS dispatch_order (...);" }
  ]
}
```

Повторный вызов с тем же `version` + `id` — **идемпотентен** (скрипт пропускается).

**Guard:** DDL не может создавать platform-таблицы (`applications`, `workflow_*`, …). При непустом `tablePrefix` имена таблиц должны начинаться с префикса.

```http
GET /api/v1/applications/terminal/data/status
```

### Seed (smoke)

```http
POST /api/v1/applications/terminal/data/seed
Content-Type: application/json

{ "profile": "smoke-p301" }
```

Встроенный профиль `smoke-p301` — идемпотентные INSERT для смены, нарядов `DO-2026-*`, tanks. Повторный вызов пропускает уже применённые seed-блоки.

```http
GET /api/v1/applications/terminal/data/status
```

## Деплой функций (REQ-PF-01)

Функции — JSON **script** с шагами:

| Шаг | Назначение |
|-----|------------|
| `selectOne` | Одна строка SQL → var (`Map`) |
| `selectMany` | Список строк SQL → var (`List<Map>`) |
| `exec` | DML/DDL с `params` |
| `setVar` | Присвоение литерала или `${path}` |
| `invoke_function` | Вызов другой deploy-функции; propagate `error_code` |
| `cancel_workflows` | Отмена workflow instances по `workflowPath` + `statusIn` |
| `failIfNull` | Выход с `error_code` / `error_message` |
| `failIfNotEquals` | Проверка значения var |
| `return` | Сборка output (`fields`); массивы через `${var}` |

Один invoke = одна JDBC-транзакция (rollback при необработанном exception). Вложенные `invoke_function` — до 8 уровней. Невалидный script → **400** на deploy.

```http
POST /api/v1/applications/terminal/functions/deploy
Content-Type: application/json

{
  "objectPath": "root.platform.terminal.dispatch",
  "functionName": "terminal_dispatchOrder_startFilling",
  "version": "1",
  "descriptor": {
    "inputSchema": { "name": "in", "fields": [{"name": "orderId", "type": "STRING"}] },
    "outputSchema": {
      "name": "out",
      "fields": [
        {"name": "error_code", "type": "STRING"},
        {"name": "error_message", "type": "STRING"}
      ]
    }
  },
  "source": {
    "type": "script",
    "body": "{ \"steps\": [ ... ] }"
  }
}
```

При первом вызове дескриптор функции автоматически добавляется на объект.

Параметры в SQL: `"${input.orderId}"` или `"$input.orderId"`.

## Bundle deploy (REQ-PF-03)

Один запрос — регистрация, metadata, миграции, функции, расписания:

```http
POST /api/v1/applications/terminal/deploy
Content-Type: application/json

{
  "version": "1.0.0",
  "displayName": "Oil Terminal",
  "tablePrefix": "",
  "schemaName": "terminal",
  "objects": [
    {
      "parentPath": "root.platform",
      "name": "terminal",
      "type": "CUSTOM",
      "displayName": "Oil Terminal"
    }
  ],
  "dashboards": [
    {
      "path": "root.platform.terminal.ops",
      "title": "Operator Board",
      "layoutJson": "{ \"columns\": 12, \"rowHeight\": 72, \"widgets\": [] }"
    }
  ],
  "workflows": [
    {
      "path": "root.platform.terminal.workflows.p301",
      "bpmnXml": "<definitions ...>...</definitions>",
      "status": "ACTIVE"
    }
  ],
  "migrations": [ { "id": "...", "sql": "..." } ],
  "functions": [ ... ],
  "schedules": [
    {
      "scheduleId": "erp-import",
      "enabled": true,
      "intervalMs": 60000,
      "actionType": "invoke_function",
      "action": {
        "objectPath": "root.platform.terminal.erp",
        "functionName": "terminal_erpGateway_importOrders"
      }
    }
  ]
}
```

Ответ: `{ "status": "OK", "applied": [...], "skipped": [...], "errors": [] }`.

## BFF (REQ-PF-06)

Универсальный шлюз для Operator UI:

```http
POST /api/v1/bff/invoke
Content-Type: application/json

{
  "objectPath": "root.platform.terminal.dispatch",
  "functionName": "terminal_dispatchOrder_startFilling",
  "input": {
    "schema": { "name": "in", "fields": [{"name": "orderId", "type": "STRING"}] },
    "rows": [{ "orderId": "..." }]
  },
  "wireProfile": "anima-operator-v1"
}
```

### Wire profile `anima-operator-v1`

| Правило | Поведение |
|---------|-----------|
| Успех | `error_code === "OK"`, `error_message === ""` |
| Ошибка | `result` отсутствует |
| Таблица | `result` = массив строк (unwrap поля `rows`) |
| Подписи | `result_field_labels` из output schema |

Ответ (scalar): `{ "error_code": "OK", "error_message": "", "result": { ... }, "result_field_labels": {...}, "wireProfile": "anima-operator-v1" }`.

Ответ (таблица): `{ "error_code": "OK", "result": [ {...}, ... ], "result_field_labels": {...} }`.

## Расписания (REQ-PF-05)

```http
GET /api/v1/schedules
POST /api/v1/schedules
```

Тик каждые 5 с; действие `invoke_function` с JSON `{ objectPath, functionName, input? }`.

## BPMN invoke_function (REQ-PF-04)

В BPMN service task:

```xml
<ispf:serviceTask action="invoke_function"
  objectPath="root.platform.terminal.dispatch"
  functionName="terminal_dispatchOrder_assign"
  inputMap="orderId=${workflow.orderId}"
  outputMap="assignResult=result" />
```

## Отмена workflow (REQ-PF-10)

```http
POST /api/v1/workflows/instances/{instanceId}/cancel
Content-Type: application/json

{
  "reason": "incident",
  "detailJson": "{\"incidentId\":\"...\"}",
  "cancelledBy": "operator-1"
}
```

## Модели (REQ-PF-07)

Пользовательские модели сохраняются в `model_definitions` и восстанавливаются при старте.

## Права доступа

| Endpoint | Роль |
|----------|------|
| `/applications/**`, `/schedules/**` | `admin` |
| `/applications/*/operator-manifest` | `operator`, `admin` |
| `/bff/invoke`, `/workflows/instances/*/cancel` | `operator`, `admin` |

Пример полного app bundle (oil-terminal) — ветка **`feature/oil-terminal-reference`**, не в `main`. См. [PLUGINS.md](PLUGINS.md).

## SQL bindings (REQ-PF-08)

Декларативная синхронизация object variable ← SQL в app schema:

```http
POST /api/v1/applications/{appId}/bindings/deploy
{
  "objectPath": "root.platform.terminal.demo.dispatch-summary",
  "variable": "activeOrders",
  "query": "SELECT COUNT(*) AS value FROM dispatch_order WHERE status = 'ready'",
  "refresh": "on_schedule",
  "refreshIntervalMs": 30000,
  "valueField": "value"
}
```

| `refresh` | Поведение |
|-----------|-----------|
| `on_schedule` | Периодический poll (`ApplicationSqlBindingScheduler`) |
| `on_function_success` | После успешного invoke функции (опционально `triggerObjectPath` / `triggerFunctionName`) |

Также deploy через bundle: `bindings[]` в manifest.

```http
GET  /api/v1/applications/{appId}/bindings
POST /api/v1/applications/{appId}/bindings/refresh
```

## Function versions (REQ-PF-11)

```http
GET  /api/v1/applications/{appId}/functions?objectPath=&functionName=
POST /api/v1/applications/{appId}/functions/rollback
{ "objectPath", "functionName", "version": "2" }
```

Rollback поднимает `deployed_at` выбранной версии — `findLatest` снова её использует.

## Bundle deploy history & rollback (PF-03b)

Каждый `POST .../deploy` сохраняет snapshot manifest в `application_bundle_deployments`.

```http
GET  /api/v1/applications/{appId}/deploy/history
POST /api/v1/applications/{appId}/deploy/rollback
{ "version": "1.0.0" }
```

Rollback повторно применяет сохранённый manifest (migrations skip, functions re-apply).

## Operator manifest (из bundle)

Поле `operatorManifest` в deploy bundle → `GET /api/v1/applications/{appId}/operator-manifest`.

Web Console: API first, fallback `public/operator-apps/<appId>.manifest.json`.

## Связанная документация

- [API.md](API.md) — таблица endpoints
- [WORKFLOWS.md](WORKFLOWS.md) — BPMN `invoke_function`, отмена экземпляров
- [WEB_CONSOLE.md](WEB_CONSOLE.md) — BPMN editor и auto-layout
- [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) — статус REQ-PF, sprint roadmap
- [PLUGINS.md](PLUGINS.md) — границы ядра и коммерческих плагинов
- [SECURITY.md](SECURITY.md) — матрица RBAC

## Следующие шаги (backlog)

- BPMN signal catch (PF-10b)
- Terminal app full bundle + smoke P-301 e2e (michaael)
- Расширить `TerminalAppParityTest` (weighbridge, incident, P-301 workflow)
