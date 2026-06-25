# ADR-0023: ClickHouse SPI для event journal (P3b)

Статус: **Accepted**  
Дата: 2026-06-25

## Контекст

P3a ([ADR-0022](0022-event-history-timescale.md)) вынес `event_history` в Timescale hypertable в PostgreSQL. На ~40 events/s узким местом остаётся OLTP-запись в PG; для сотен/тысяч events/s нужен column store с batch insert и отдельным масштабированием.

PostgreSQL/Timescale остаётся для relational core (дерево объектов, workflow, RBAC, alert state).

## Решение

1. **`EventJournalStore` SPI** — единая точка append/query для журнала событий.
2. **Backends:**
   - `jdbc` (default) — PostgreSQL/Timescale `event_history`, JDBC batch insert ([P2/P3a](0022-event-history-timescale.md)).
   - `clickhouse` — MergeTree через HTTP (`JSONEachRow`), без дополнительных Java-зависимостей.
3. **Конфиг:** `ispf.event-journal.store=jdbc|clickhouse`, nested `ispf.event-journal.clickhouse.*` (URL, database, table, credentials).
4. **Retention:** JDBC + Timescale → policy; JDBC без Timescale → scheduled purge; ClickHouse → `TTL occurred_at + INTERVAL N DAY` при создании таблицы.
5. **Timescale `event_history` hypertable** пропускается при `store=clickhouse`.
6. **Hot path без изменений:** `EventJournalAsyncWriter` → `EventJournalBatchPersister` → store; `RecentEventCache` для UI/correlator.

## Схема ClickHouse

```sql
CREATE TABLE ispf.event_history (
    id String,
    object_path String,
    event_name String,
    level LowCardinality(String),
    payload_json String,
    occurred_at DateTime64(3, 'UTC')
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (object_path, occurred_at, id)
TTL occurred_at + INTERVAL 90 DAY;
```

## Последствия

- Prod default остаётся `jdbc` (Timescale); ClickHouse — opt-in для high-throughput deployments.
- Локальный CH: [deploy/docker-compose.clickhouse.yml](../../deploy/docker-compose.clickhouse.yml).
- Метрика `ispf.event_history.records` — O(1) counter на обоих backend'ах.
- Correlator payload filter читает последнее событие через store (не JPA).

## Связанные материалы

- [ADR-0022](0022-event-history-timescale.md) — Timescale tier (P3a)
- [ADR-0021](0021-automation-pipeline-evolution.md) — automation pipeline evolution
- [LOAD_TESTING.md](../LOAD_TESTING.md) — throughput baselines
