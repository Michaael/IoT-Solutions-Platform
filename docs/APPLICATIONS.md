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
  "tablePrefix": "terminal_"
}
```

## Миграции данных (REQ-PF-02)

SQL-скрипты приложения **не** попадают в Flyway платформы. Деплой через API:

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

```http
GET /api/v1/applications/terminal/data/status
```

## Деплой функций (REQ-PF-01)

Функции — JSON **script** с шагами: `selectOne`, `exec`, `failIfNull`, `failIfNotEquals`, `return`.

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

Один запрос — регистрация, миграции, функции, расписания:

```http
POST /api/v1/applications/terminal/deploy
Content-Type: application/json

{
  "version": "1.0.0",
  "displayName": "Oil Terminal",
  "tablePrefix": "",
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
  "wireProfile": "terminal"
}
```

Ответ: `{ "error_code": "OK", "error_message": "", "result": { ... } }`.

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
| `/bff/invoke`, `/workflows/instances/*/cancel` | `operator`, `admin` |

Пример полного app bundle (oil-terminal) — ветка **`feature/oil-terminal-reference`**, не в `main`. См. [PLUGINS.md](PLUGINS.md).

## Связанная документация

- [API.md](API.md) — таблица endpoints
- [WORKFLOWS.md](WORKFLOWS.md) — BPMN `invoke_function`, отмена экземпляров
- [WEB_CONSOLE.md](WEB_CONSOLE.md) — BPMN editor и auto-layout
- [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) — статус REQ-PF, sprint roadmap
- [PLUGINS.md](PLUGINS.md) — границы ядра и коммерческих плагинов
- [SECURITY.md](SECURITY.md) — матрица RBAC

## Следующие шаги (backlog)

- **REQ-PF-08** — Variable ↔ SQL sync для demo dashboards
- **REQ-PF-09** — Integration Simulator SPI
- Rollback bundle на предыдущую версию (P2)
