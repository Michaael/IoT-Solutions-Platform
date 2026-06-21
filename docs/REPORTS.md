# Отчёты приложений (REQ-PF-12)

Generic-слой SQL-отчётов для любого `appId`. **Tree-first (Phase 12):** определение хранится на объекте `REPORT` в каталоге `root.platform.reports.*` (модель `report-v1`), как дашборды в `root.platform.dashboards.*`.

Legacy API `/api/v1/applications/{appId}/reports/*` сохранён и делегирует в дерево.

## Object tree

| Узел | Тип | Модель |
|------|-----|--------|
| `root.platform.reports` | `REPORTS` | — |
| `root.platform.reports.{reportId}` | `REPORT` | `report-v1` |

Переменные объекта:

| Variable | Описание |
|----------|----------|
| `title` | Заголовок |
| `appId` | Schema приложения для SQL |
| `query` | SELECT / WITH |
| `parameters` | JSON array имён `?`-параметров |
| `columns` | JSON array `{field, label}` |
| `defaultParameters` | JSON object значений по умолчанию |
| `maxRows` | Лимит строк (default 1000) |
| `refreshIntervalMs` | Auto-refresh в UI (default 30000) |

Web Console: **Report Builder** при открытии объекта `REPORT` (редактирование SQL, preview, CSV).

## Path-based API (primary)

```http
GET  /api/v1/reports/by-path?path=root.platform.reports.ready-items
PUT  /api/v1/reports/by-path/definition?path=...
POST /api/v1/reports/by-path/run?path=...
GET  /api/v1/reports/by-path/export?path=...&status=ready
```

### Run (by path)

```http
POST /api/v1/reports/by-path/run?path=root.platform.reports.ready-items
Content-Type: application/json

{ "parameters": { "status": "ready" } }
```

## Deploy

Через bundle (`reports[]` создаёт объект в `root.platform.reports.{reportId}`):

```json
{
  "reports": [
    {
      "reportId": "ready-items",
      "title": "Готовые позиции",
      "query": "SELECT item_code, status FROM demo_item WHERE status = ?",
      "parameters": ["status"],
      "columns": [
        { "field": "item_code", "label": "Код" },
        { "field": "status", "label": "Статус" }
      ],
      "maxRows": 500
    }
  ]
}
```

Legacy deploy:

```http
POST /api/v1/applications/{appId}/reports/deploy
```

## Legacy app-scoped API

```http
GET  /api/v1/applications/{appId}/reports
POST /api/v1/applications/{appId}/reports/{reportId}/run
GET  /api/v1/applications/{appId}/reports/{reportId}/export?status=ready
```

## Operator UI

- **operatorUi `reports[]`:** навигация по path отчётов (как `dashboards[]`).
- **Dashboard widget `type: "report"`** — встроенная таблица по `reportPath`.
- **Legacy manifest** `screen.report` — по-прежнему через app API (резолв в `root.platform.reports.{reportId}`).

## Права

| Endpoint | Роль |
|----------|------|
| `GET .../reports/by-path`, export | `operator`, `admin` |
| `PUT .../definition` | `admin` |
| `POST .../run` | `operator`, `admin` |
| `POST .../applications/.../deploy` (reports) | `admin` |

## Ограничения

- Только read-only SQL (без `INSERT`/`UPDATE`/`DELETE`/DDL).
- Запрос выполняется в app schema (`appId` на объекте).
- PDF — вне scope platform.

## Пример

[examples/demo-app/](../examples/demo-app/) — deploy на `demo`, отчёты в `root.platform.reports.*`.

## Связанные документы

- [APPLICATIONS.md](APPLICATIONS.md) — bundle deploy
- [DASHBOARDS.md](DASHBOARDS.md) — аналогичная tree-first модель
- [ROADMAP.md](ROADMAP.md) — Phase 12
