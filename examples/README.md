# Examples

В `main` нет отраслевых app bundle — только платформенные демо (`demo-sensor`, `demo-alarm-handler`).

| Пример | Описание |
|--------|----------|
| [demo-app](demo-app/) | SQL-отчёты: bundle + operator manifest для `appId=demo` |
| [mini-tec](mini-tec/) | Эталон мини-ТЭЦ: bundle + auto-bootstrap на старте server |
| [mes-reference](mes-reference/) | MES walkthrough: наряд → резервуар → эстакада |
| [warehouse-app](warehouse-app/) | Reference app #2 (dogfooding REQ-PF) |
| [lab-training](lab-training/) | Importable lab package (Phase 15) |
| [spreadsheet-demo](spreadsheet-demo/) | Эталон: объект с `sheetValues` + виджет spreadsheet |
| [mes-defect-demo](mes-defect-demo/) | MES defect routing demo (bundle + smoke test) |
| [mes-ogp-events](mes-ogp-events/) | UC-25 OGP event registration: wizard, journal, roll map, 1C outbox |

Прикладные bundle разворачиваются через `POST /api/v1/applications/{appId}/deploy` из репозитория проекта.

См. [docs/APPLICATIONS.md](../docs/APPLICATIONS.md), [docs/REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md](../docs/REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md) и [docs/PLUGINS.md](../docs/PLUGINS.md).
