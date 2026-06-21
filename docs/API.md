# REST API (v1)

Базовый URL: `http://localhost:8080`

Авторизация: JWT (Keycloak) или заголовок `X-ISPF-Role: admin|operator` (профиль `local`).  
Матрица ролей: [SECURITY.md](SECURITY.md).

## Платформа

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/info` | public | Имя, версия, `javaVersion`, `springBootVersion`, `capabilities[]` |
| GET | `/api/v1/platform/metrics` | admin | Сводные метрики платформы (runtime, БД, дерево, драйверы, подключения, безопасность, historian, автоматизация) |
| GET | `/api/v1/platform/update/status` | admin | Проверка обновлений с GitHub Releases |
| POST | `/api/v1/platform/update/check` | admin | Принудительная проверка релиза |
| POST | `/api/v1/platform/update/apply` | admin | Скачать релиз и перезапустить сервер (VPS, `apply-enabled=true`) |
| GET | `/api/v1/auth/me` | public | Principal и роли |
| POST | `/api/v1/expressions/validate` | admin | Валидация CEL |

## Объекты

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/objects` | operator+ | Список (`?parent=` — дочерние) |
| GET | `/api/v1/objects/by-path` | operator+ | Объект по `path` |
| GET | `/api/v1/objects/by-path/editor` | operator+ | Объект + events + functions |
| POST | `/api/v1/objects` | admin | Создать объект |
| PATCH | `/api/v1/objects/by-path` | admin | displayName, description |
| DELETE | `/api/v1/objects/by-path` | admin | Удалить поддерево |
| PUT | `/api/v1/objects/reorder` | admin | Порядок дочерних (`parentPath`, `orderedPaths`) |
| GET | `/api/v1/objects/by-path/variables` | operator+ | Список переменных |
| GET | `/api/v1/objects/by-path/variables/detail` | operator+ | Одна переменная (`name`) |
| GET | `/api/v1/objects/by-path/variables/history` | operator+ | История переменной (`path`, `name`, `field`, `from`, `to`, `limit`; только при `historyEnabled`) |
| GET | `/api/v1/objects/by-path/variables/history/aggregate` | operator+ | Агрегаты (`bucket`, `from`, `to`, `limit` — до 2000 интервалов; `avg`/`min`/`max`/`count`) |
| GET | `/api/v1/objects/by-path/variables/history/export` | operator+ | Скачать историю (`format=csv\|json`, те же фильтры, `limit` до 10000) |
| PATCH | `/api/v1/objects/by-path/variables/history` | admin | `historyEnabled`, `historyRetentionDays` |
| PUT | `/api/v1/objects/by-path/variables` | admin | Записать значение |

### Создание объекта

```http
POST /api/v1/objects
Content-Type: application/json

{
  "parentPath": "root.platform.devices",
  "name": "pump-01",
  "type": "DEVICE",
  "displayName": "Pump 01",
  "description": "",
  "templateId": "mqtt-sensor-v1"
}
```

## Функции

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| POST | `/api/v1/objects/by-path/functions/invoke` | operator+ | Вызов (`path`, `name`, body) |

## Дашборды

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/dashboards/by-path` | operator+ | title, layoutJson, refreshIntervalMs |
| PUT | `/api/v1/dashboards/by-path/layout` | admin | Сохранить layout JSON |
| PUT | `/api/v1/dashboards/by-path/title` | admin | Заголовок экрана |

## Workflows

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/workflows/by-path` | operator+ | BPMN, status, instance state |
| PUT | `/api/v1/workflows/by-path/bpmn` | admin | Сохранить BPMN XML |
| PUT | `/api/v1/workflows/by-path/status` | admin | `DRAFT` / `ACTIVE` / `STOPPED` |
| POST | `/api/v1/workflows/by-path/run` | admin | Запуск экземпляра |
| POST | `/api/v1/workflows/instances/{instanceId}/cancel` | operator+ | Отмена экземпляра |

## Приложения (REQ-PF)

Платформенный слой для deploy прикладных решений без Java в `ispf-server`.  
Полное описание: [APPLICATIONS.md](APPLICATIONS.md).

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| POST | `/api/v1/applications` | admin | Регистрация приложения |
| POST | `/api/v1/applications/{appId}/deploy` | admin | Bundle deploy (миграции + функции + schedules + reports) |
| GET | `/api/v1/applications/{appId}/reports` | admin | Список SQL-отчётов |
| POST | `/api/v1/applications/{appId}/reports/deploy` | admin | Deploy одного отчёта |
| POST | `/api/v1/applications/{appId}/reports/{reportId}/run` | operator+ | Выполнить отчёт |
| GET | `/api/v1/applications/{appId}/reports/{reportId}/export` | operator+ | CSV-экспорт |
| POST | `/api/v1/applications/{appId}/data/migrate` | admin | SQL-миграции приложения |
| GET | `/api/v1/applications/{appId}/data/status` | admin | Статус миграций |
| POST | `/api/v1/applications/{appId}/functions/deploy` | admin | Deploy script-функции |
| POST | `/api/v1/bff/invoke` | operator+ | Универсальный BFF-шлюз к функциям |
| GET | `/api/v1/schedules` | admin | Список расписаний |
| POST | `/api/v1/schedules` | admin | Создать/обновить расписание |

Подробнее: [APPLICATIONS.md](APPLICATIONS.md), [REPORTS.md](REPORTS.md), [PLUGINS.md](PLUGINS.md).

## Work Queue

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/work-queue` | operator+ | Открытые user tasks (`limit`) |
| POST | `/api/v1/work-queue/claim` | operator+ | `taskId`, `operatorId` |
| POST | `/api/v1/work-queue/complete` | operator+ | Завершить задачу |

## События

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/events` | operator+ | Журнал (`objectPath`, `limit` ≤ 200) |
| POST | `/api/v1/events/fire` | operator+ | Публикация события |

### Fire event

```http
POST /api/v1/events/fire
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "eventName": "thresholdExceeded",
  "payload": {
    "schema": { "name": "payload", "fields": [...] },
    "rows": [{ "temperature": 95.0, "threshold": 80.0 }]
  }
}
```

## Alert Rules

| Method | Path | Роли |
|--------|------|------|
| GET | `/api/v1/alert-rules` | admin |
| GET | `/api/v1/alert-rules/by-path?path=` | admin |
| POST | `/api/v1/alert-rules` | admin |
| PUT | `/api/v1/alert-rules/by-path?path=` | admin |
| DELETE | `/api/v1/alert-rules/by-path?path=` | admin |

## Event Correlators

| Method | Path | Роли |
|--------|------|------|
| GET | `/api/v1/correlators` | admin |
| GET | `/api/v1/correlators/by-path?path=` | admin |
| POST | `/api/v1/correlators` | admin |
| PUT | `/api/v1/correlators/by-path?path=` | admin |
| DELETE | `/api/v1/correlators/by-path?path=` | admin |

## Драйверы

| Method | Path | Роли | Описание |
|--------|------|------|----------|
| GET | `/api/v1/drivers` | operator+ | Каталог SPI-драйверов |
| GET | `/api/v1/drivers/runtime/status` | operator+ | Статус poll loop |
| POST | `/api/v1/drivers/runtime/start` | admin | Запуск (`devicePath`) |
| POST | `/api/v1/drivers/runtime/stop` | admin | Остановка |
| PUT | `/api/v1/drivers/runtime/configure` | admin | Конфиг + point mappings |

## Models Plugin

| Method | Path | Роли |
|--------|------|------|
| GET | `/api/v1/models` | admin |
| GET | `/api/v1/models/{id}` | admin |
| GET | `/api/v1/models/by-name/{name}` | admin |
| POST | `/api/v1/models` | admin |
| PUT | `/api/v1/models/{id}` | admin |
| DELETE | `/api/v1/models/{id}` | admin |
| POST | `/api/v1/models/{id}/apply?objectPath=` | admin |
| POST | `/api/v1/models/{id}/instantiate` | admin |
| POST | `/api/v1/models/from-object` | admin |
| GET | `/api/v1/models/attachments` | admin |

## Actuator

| Path | Доступ |
|------|--------|
| `/actuator/health` | public |
| `/actuator/prometheus` | public |
| `/actuator/metrics` | authorized |

## WebSocket

**Endpoint:** `WS /ws/objects`

Без аутентификации (dev). Сообщения при изменении объектов:

```json
{
  "type": "VARIABLE_UPDATED | OBJECT_CREATED | OBJECT_DELETED | EVENT_FIRED | ...",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Клиент: `apps/web-console/src/hooks/useObjectWebSocket.ts`.

## Коды ошибок

Стандартные HTTP: `400` (валидация), `404` (ObjectNotFoundException), `403` (RBAC), `500`.
