# Отчёты приложений (REQ-PF-12)

Generic-слой SQL-отчётов. **Tree-first (Phase 12–14):** определение на объекте `REPORT` в `root.platform.reports.*` (модель `report-v1`); SQL schema — через **`dataSourcePath`** → `root.platform.data-sources.*`.

Legacy API `/api/v1/applications/{appId}/reports/*` сохранён и делегирует в дерево. Импорт bundle: `POST /api/v1/platform/packages/import`.

## Object tree

| Узел | Тип | Модель |
|------|-----|--------|
| `root.platform.reports` | `REPORTS` | — |
| `root.platform.reports.{reportId}` | `REPORT` | `report-v1` |

Переменные объекта:

| Variable | Описание |
|----------|----------|
| `title` | Заголовок |
| `dataSourcePath` | Путь к `DATA_SOURCE` (`root.platform.data-sources.*`) — schema для SQL |
| `query` | SELECT / WITH |
| `parameters` | JSON array имён `?`-параметров |
| `columns` | JSON array `{field, label}` |
| `defaultParameters` | JSON object значений по умолчанию |
| `maxRows` | Лимит строк (default 1000) |
| `refreshIntervalMs` | Auto-refresh в UI (default 30000) |
| `templateFormat` | Формат YARG-шаблона: `xlsx`, `docx`, `html` (пусто = нет шаблона) |

Бинарный шаблон хранится в таблице `report_templates` (не в object_variables).

Web Console: **Report Builder** — structured editor (parameters/columns, без ручного JSON), SQL preview, CSV, вкладка **Шаблон YARG**, export PDF/XLSX/HTML. Переключатель **sql** | **tree-variables**.

### tree-variables (модель `tree-variables-report-v1`)

| Variable | Описание |
|----------|----------|
| `reportType` | `tree-variables` |
| `devicePathPattern` | Префикс или glob пути устройств (`*`, `?`) |
| `variableName` | Имя переменной на каждом объекте |
| `columns` | JSON array `{field, label}` (по умолчанию path, value) |

Сохранение: `PUT /api/v1/reports/by-path/tree-variables-definition?path=...`

## Export matrix (UI)

| Поверхность | CSV | PDF/XLSX/HTML |
|-------------|-----|----------------|
| Report Builder | да | да (если YARG template) |
| Dashboard widget `report` | настраивается (`showCsv`, …) | настраивается + template |
| Operator manifest `screen.report` | да | да (если template) |
| Operator app (ReportBuilder) | да | да (если template) |

## Dashboard widget `type: "report"`

| Поле | Описание |
|------|----------|
| `reportPath` | Путь REPORT |
| `parametersJson` | Статические параметры run |
| `contextParamsJson` | `{reportParam: sessionParamKey}` |
| `showCsv` / `showPdf` / `showXlsx` / `showHtml` | Кнопки export |
| `showTruncatedWarning` | Banner при truncated rows |

## Path-based API (primary)

```http
GET  /api/v1/reports/by-path?path=root.platform.reports.ready-items
PUT  /api/v1/reports/by-path/definition?path=...
PUT  /api/v1/reports/by-path/tree-variables-definition?path=...
POST /api/v1/reports/by-path/run?path=...
GET  /api/v1/reports/by-path/export?path=...&format=csv|pdf|xlsx|html
POST /api/v1/reports/by-path/template?path=...&format=xlsx   (multipart file)
GET  /api/v1/reports/by-path/template?path=...
DELETE /api/v1/reports/by-path/template?path=...
```

### Run (by path)

```http
POST /api/v1/reports/by-path/run?path=root.platform.reports.ready-items
Content-Type: application/json

{ "parameters": { "status": "ready" } }
```

## YARG templates (Phase 13)

Серверный export через [YARG](https://github.com/cuba-platform/yarg) (Apache-2.0):

1. Создайте шаблон в Excel/Word с band **`Band1`** и полями `${Band1.COLUMN}` (имена колонок SQL в **верхнем регистре**, например `${Band1.ITEM_CODE}`). Пример — [YARG smoke sample](https://github.com/cuba-platform/yarg/tree/master/core/modules/core/test/sample).
2. Загрузите файл во вкладке **Шаблон YARG** в Report Builder (`POST .../template`).
3. Export: `GET .../export?format=pdf|xlsx|html` — данные берутся из того же SQL run.

Без шаблона доступен только `format=csv`.

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
GET  /api/v1/applications/{appId}/reports/{reportId}/export?format=csv|pdf|xlsx|html
```

## Operator UI

- **operatorUi `reports[]`:** навигация по path отчётов (как `dashboards[]`).
- **Dashboard widget `type: "report"`** — таблица по `reportPath` с `parametersJson` / session mapping и export toolbar.
- **Legacy manifest** `screen.report` — CSV + PDF/XLSX/HTML при YARG template.

## Права

| Endpoint | Роль |
|----------|------|
| `GET .../reports/by-path`, export | `operator`, `admin` |
| `PUT .../definition`, template upload/delete | `admin` |
| `POST .../run` | `operator`, `admin` |
| `POST .../applications/.../deploy` (reports) | `admin` |

## Ограничения

- Только read-only SQL (без `INSERT`/`UPDATE`/`DELETE`/DDL).
- Запрос выполняется в schema объекта data source (`dataSourcePath`).
- PDF/XLSX/HTML требуют загруженный YARG-шаблон.

## Пример

[examples/demo-app/](../examples/demo-app/) — `POST /api/v1/platform/packages/import?packageId=demo` или legacy deploy на `demo`, отчёты в `root.platform.reports.*`.

## Связанные документы

- [APPLICATIONS.md](APPLICATIONS.md) — bundle deploy
- [DASHBOARDS.md](DASHBOARDS.md) — аналогичная tree-first модель
- [ROADMAP.md](ROADMAP.md) — Phase 12–13
