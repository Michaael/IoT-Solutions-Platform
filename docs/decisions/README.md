# Architecture Decision Records (ADR)

Архитектурные решения ISPF в формате ADR. Новые решения platform — отдельный файл с монотонным номером.

## Статусы

| Статус | Значение |
|--------|----------|
| **Accepted** | Действует; код и docs должны соответствовать |
| **Proposed** | На обсуждении; не обязательно реализовано |
| **Superseded** | Заменено другим ADR |

## Индекс

| ADR | Тема | Статус |
|-----|------|--------|
| [0008](0008-app-platform-boundary.md) | Граница platform vs solution | Accepted |
| [0009](0009-dogfooding-gate.md) | Gate обобщения перед PR в platform | Accepted |
| [0010](0010-commercial-bundle-licensing.md) | RSA-лицензирование commercial bundle | Accepted |
| [0011](0011-ai-artifact-generation-gates.md) | AI artifact generation + validation gates | Accepted |
| [0012](0012-tree-first-ai-agent.md) | Tree-first AI agent (FW-44) | Accepted |
| [0013](0013-mcp-agent-tool-adapter.md) | MCP adapter over platform agent tools | Accepted |
| [0014](0014-bundle-tree-packaging.md) | Bundle = tree packaging | Accepted |
| [0015](0015-federation-topology.md) | Federation hub–spoke topology | Accepted |
| [0016](0016-timescaledb-retention.md) | TimescaleDB variable history retention | Accepted |
| [0022](0022-event-history-timescale.md) | TimescaleDB event journal tier (P3a) | Accepted |
| [0023](0023-clickhouse-event-journal.md) | ClickHouse event journal SPI (P3b) | Accepted |
| [0017](0017-binding-rules-only.md) | Binding rules only (v0.8.0) | Accepted |
| [0018](0018-model-type-semantics.md) | Blueprint + три вида моделей (RELATIVE/INSTANCE/ABSOLUTE) | Accepted |

## Шаблон нового ADR

```markdown
# ADR-NNNN: Краткий заголовок

Статус: **Proposed** | **Accepted**  
Дата: YYYY-MM-DD

## Контекст

Какая проблема или потребность.

## Решение

Что принято.

## Последствия

Плюсы, минусы, что нужно изменить в коде/docs.

## Связанные материалы

- ссылки
```
