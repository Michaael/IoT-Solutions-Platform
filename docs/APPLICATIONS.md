# Приложения на платформе (REQ-PF)

Платформенный слой для развёртывания прикладных решений **без Java-кода отрасли в `ispf-server`**. Соответствует ADR-0008 и требованиям REQ-PF.

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

## Регистрация приложения

```http
POST /api/v1/applications
Content-Type: application/json

{
  "appId": "myapp",
  "displayName": "My Application",
  "tablePrefix": "myapp_",
  "schemaName": "myapp"
}
```

## Миграции данных (REQ-PF-02)

SQL-скрипты приложения **не** попадают в Flyway платформы. Деплой через API в **изолированной schema** (`schemaName`, по умолчанию `app_{appId}`):

```http
POST /api/v1/applications/myapp/data/migrate
Content-Type: application/json

{
  "version": "1.0.0",
  "scripts": [
    { "id": "items", "sql": "CREATE TABLE IF NOT EXISTS demo_item (...);" }
  ]
}
```

Повторный вызов с тем же `version` + `id` — **идемпотентен** (скрипт пропускается).

**Guard:** DDL не может создавать platform-таблицы (`applications`, `workflow_*`, …). При непустом `tablePrefix` имена таблиц должны начинаться с префикса.

```http
GET /api/v1/applications/myapp/data/status
```

### Seed (smoke)

```http
POST /api/v1/applications/myapp/data/seed
Content-Type: application/json

{ "profile": "smoke-demo" }
```

Встроенный профиль `smoke-demo` — идемпотентные INSERT в generic-таблицы (`demo_category`, `demo_item`, `demo_metric`). Повторный вызов пропускает уже применённые seed-блоки.

```http
GET /api/v1/applications/myapp/data/status
```

## Деплой функций (REQ-PF-01)

Функции — JSON **script** с шагами:

| Шаг | Назначение |
|-----|------------|
| `selectOne` | Одна строка SQL → var (`Map`) |
| `selectMany` | Список строк SQL → var (`List<Map>`) |
| `exec` | DML/DDL с `params` |
| `setVar` | Присвоение литерала или `${path}` |
| `buildRecord` | Сборка `Map` в var из `fields` (field mapping) |
| `map` | Преобразование списка: `source` + `fields` с контекстом `${item.*}` |
| `invoke_function` | Вызов другой deploy-функции; propagate `error_code` |
| `cancel_workflows` | Отмена workflow instances по `workflowPath` + `statusIn` |
| `failIfNull` | Выход с `error_code` / `error_message` |
| `failIfNotEquals` | Проверка значения var |
| `return` | Сборка output (`fields`); массивы через `${var}` |

Один invoke = одна JDBC-транзакция (rollback при необработанном exception). Вложенные `invoke_function` — до 8 уровней. Невалидный script → **400** на deploy.

```http
POST /api/v1/applications/myapp/functions/deploy
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "myapp_ping",
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
POST /api/v1/applications/myapp/deploy
Content-Type: application/json

{
  "version": "1.0.0",
  "displayName": "My Application",
  "tablePrefix": "",
  "schemaName": "myapp",
  "objects": [
    {
      "parentPath": "root.platform",
      "name": "myapp",
      "type": "CUSTOM",
      "displayName": "My Application"
    }
  ],
  "dashboards": [
    {
      "path": "root.platform.myapp.ops",
      "title": "Operator Board",
      "layoutJson": "{ \"columns\": 12, \"rowHeight\": 72, \"widgets\": [] }"
    }
  ],
  "workflows": [
    {
      "path": "root.platform.myapp.workflows.main",
      "bpmnXml": "<definitions ...>...</definitions>",
      "status": "ACTIVE"
    }
  ],
  "models": [
    {
      "name": "my-device-v1",
      "description": "Custom device template",
      "type": "DEVICE",
      "targetObjectType": "DEVICE",
      "variables": []
    }
  ],
  "migrations": [ { "id": "...", "sql": "..." } ],
  "functions": [ ... ],
  "schedules": [
    {
      "scheduleId": "import-job",
      "enabled": true,
      "intervalMs": 60000,
      "actionType": "invoke_function",
      "action": {
        "objectPath": "root.platform.myapp.integration",
        "functionName": "myapp_importRecords"
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
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "myapp_processRecord",
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
| Подписи | `result_field_labels`: `description` поля output schema → profile map `anima-operator-v1` → имя поля |

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
  objectPath="root.platform.devices.demo-sensor-01"
  functionName="myapp_acknowledge"
  inputMap="deviceId=${workflow.deviceId}"
  outputMap="ackResult=result" />
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
| `/applications/**`, `/schedules/**` (POST/PUT) | `admin` |
| `/applications/*/operator-ui`, `/applications/*/hmi-ui`, `/applications/*/operator-manifest` (GET) | `operator`, `admin` |
| `/bff/invoke`, `/workflows/instances/*/cancel`, `/applications/*/reports/*/run` | `operator`, `admin` |

## SQL bindings (REQ-PF-08)

Декларативная синхронизация object variable ← SQL в app schema:

```http
POST /api/v1/applications/{appId}/bindings/deploy
{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variable": "readyCount",
  "query": "SELECT COUNT(*) AS value FROM demo_item WHERE status = 'ready'",
  "refresh": "on_schedule",
  "refreshIntervalMs": 30000,
  "valueField": "value"
}
```

| `refresh` | Поведение |
|-----------|-----------|
| `on_schedule` | Периодический poll (`ApplicationSqlBindingScheduler`) |
| `on_function_success` | После успешного invoke функции (опционально `triggerObjectPath` / `triggerFunctionName`) |
| `on_event` | После `EVENT_FIRED` на объекте (опционально фильтр по `triggerObjectPath` / событию) |

**Variable-level binding:** на переменной объекта можно задать `bindingExpression`:

```text
sqlBinding('warehouse-app', 'readyCount')
```

Платформа выполнит SQL binding из таблицы `application_sql_bindings` для этого app и переменной (см. `ServerBindingEvaluator`).

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

## Отчёты (REQ-PF-12)

SQL-отчёты в app schema: deploy через bundle `reports[]` или `POST .../reports/deploy`, выполнение `POST .../reports/{id}/run`, экспорт CSV `GET .../reports/{id}/export`.

Подробнее: [REPORTS.md](REPORTS.md).

## Operator UI (дашборды из дерева)

Единый operator shell: навигация по объектам `DASHBOARD` из дерева, те же виджеты что в Dashboard Builder (read-only).

Контракт `operatorUi`:

```json
{
  "appId": "platform",
  "title": "Platform HMI",
  "defaultDashboard": "root.platform.dashboards.snmp-host-monitoring",
  "dashboards": [
    { "path": "root.platform.dashboards.snmp-host-monitoring", "title": "SNMP Host Monitoring" }
  ]
}
```

Источники (по приоритету):

1. `GET /api/v1/operator-apps/{appId}/ui` — встроенные и настроенные в админке приложения (таблица `operator_app_ui`, узел дерева `root.platform.operator-apps`)
2. Поле `operatorUi` в deploy bundle → `GET /api/v1/applications/{appId}/operator-ui`
3. Автогенерация из `dashboards[]` в bundle (path + title)
4. Legacy fallback: `public/operator-apps/{appId}.ui.json` (только dev)

URL: `?mode=operator&app=platform&dashboard=<path>`.

### Legacy: operator manifest

Поле `operatorManifest` в deploy bundle → `GET /api/v1/applications/{appId}/operator-manifest` — **deprecated** (таблицы/отчёты через BFF). Для новых приложений используйте `operatorUi` + дашборды.

Web Console: API first, fallback `public/operator-apps/<appId>.manifest.json` (только legacy shell).

### Дерево объектов

После register/deploy сущности приложения синхронизируются в `root.platform.applications.{appId}`:

| Папка | Содержимое |
|-------|------------|
| `reports` | SQL-отчёты (`ObjectType.REPORT`) |
| `functions` | Deployed script functions |
| `schedules` | `platform_schedules` |
| `bindings` | SQL bindings |
| `migrations` | Применённые data migrations |
| `screens` | Экраны operator manifest (legacy) |

Синхронизация: при deploy/register/migrate и при старте сервера.

**Пример:** `appId=platform` настраивается в админке: дерево → `root.platform.operator-apps`. Deploy-приложения — `operatorUi` в bundle.

## Связанная документация

- [API.md](API.md) — таблица endpoints
- [WORKFLOWS.md](WORKFLOWS.md) — BPMN `invoke_function`, отмена экземпляров
- [WEB_CONSOLE.md](WEB_CONSOLE.md) — BPMN editor и auto-layout
- [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) — статус REQ-PF, sprint roadmap
- [PLUGINS.md](PLUGINS.md) — границы ядра и коммерческих плагинов
- [SECURITY.md](SECURITY.md) — матрица RBAC

## Следующие шаги (backlog)

См. [ROADMAP.md](ROADMAP.md) — **Phase 5**: усиление механизмов object tree (модели, script steps, correlators, workflow actions, bundle = упаковка дерева). Детали: [PLATFORM_DEVELOPER_BACKLOG.md §8.1](PLATFORM_DEVELOPER_BACKLOG.md#81-усиление-механизмов-phase-5).
