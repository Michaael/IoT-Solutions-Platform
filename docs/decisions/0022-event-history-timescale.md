# ADR-0022: TimescaleDB tier для event journal

Статус: **Accepted**  
Дата: 2026-06-25

## Контекст

Журнал событий (`event_history`) — append-only поток: alert rules, API `POST /events/fire`, correlators. На load test internal automation (~60 virtual devices) узким местом стала запись в PostgreSQL (`INSERT` в `event_history`), хотя relational данные (дерево объектов, workflow, RBAC) остаются OLTP-нагрузкой.

Historian телеметрии (`variable_samples`) уже использует TimescaleDB hypertable ([ADR-0016](0016-timescaledb-retention.md)). Event journal до этого жил в обычной таблице PG.

## Решение

1. **`event_history` — Timescale hypertable** по колонке `occurred_at` (инициализация при старте, как `variable_samples`).
2. **Composite PK** `(occurred_at, id)` — требование Timescale; JPA по-прежнему использует `id` как `@Id` (тот же паттерн, что у historian).
3. **Retention** — `ispf.event-journal.retention-days` (default **90**); на prod с Timescale — `add_retention_policy`; без Timescale — scheduled purge в приложении.
4. **Compression** (optional) — segment by `object_path`, policy после 7 дней; при недоступности функции — skip без падения старта.
5. **PostgreSQL** остаётся для relational core; column store (ClickHouse) — P3b ([ADR-0023](0023-clickhouse-event-journal.md)).

## Последствия

- Prod Docker: образ `timescale/timescaledb` (уже в [DEPLOYMENT.md](../DEPLOYMENT.md)).
- Flyway не вызывает `create_hypertable` — только DDL таблицы; hypertable — `TimescaleHypertableInitializer` на `ApplicationReady`.
- Edge (H2/SQLite): hypertable пропускается; retention через application purge.
- Метрика `ispf.event_history.records` — O(1) counter; retention chunks не требуют `COUNT(*)`.

## Связанные материалы

- [AUTOMATION.md](../AUTOMATION.md) — event journal API
- [LOAD_TESTING.md](../LOAD_TESTING.md) — throughput baselines
- [OBJECT_MODEL.md](../OBJECT_MODEL.md) — persistence tables
