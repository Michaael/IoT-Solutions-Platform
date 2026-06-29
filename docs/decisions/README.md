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
| [0001](0001-app-platform-boundary.md) | Граница platform vs solution | Accepted |
| [0002](0002-dogfooding-gate.md) | Gate обобщения перед PR в platform | Accepted |
| [0003](0003-commercial-bundle-licensing.md) | RSA-лицензирование commercial bundle | Accepted |
| [0004](0004-ai-artifact-generation-gates.md) | AI artifact generation + validation gates | Accepted |
| [0005](0005-tree-first-ai-agent.md) | Tree-first AI agent (FW-44) | Accepted |
| [0006](0006-mcp-agent-tool-adapter.md) | MCP adapter over platform agent tools | Accepted |
| [0007](0007-bundle-tree-packaging.md) | Bundle = tree packaging | Accepted |
| [0008](0008-federation-topology.md) | Federation hub–spoke topology | Accepted |
| [0009](0009-timescaledb-retention.md) | TimescaleDB variable history retention | Accepted |
| [0010](0010-binding-rules-only.md) | Binding rules only (v0.8.0) | Accepted |
| [0011](0011-model-type-semantics.md) | Blueprint + три вида моделей (RELATIVE/INSTANCE/ABSOLUTE) | Accepted |
| [0012](0012-visual-groups.md) | Visual groups | Accepted |
| [0013](0013-web-console-i18n.md) | Web Console i18n | Accepted |
| [0014](0014-automation-pipeline-evolution.md) | Automation pipeline (dual-lane bus, indexes) | Accepted |
| [0015](0015-event-history-timescale.md) | TimescaleDB event journal tier (P3a) | Accepted |
| [0016](0016-clickhouse-event-journal.md) | ClickHouse event journal SPI (P3b) | Accepted |
| [0017](0017-telemetry-ingest-pipeline.md) | MQTT gateway, thread pools, JDBC historian | Accepted |
| [0018](0018-fixture-models-and-cel-applicability.md) | Fixture-модели + CEL applicability для RELATIVE | Accepted |
| [0019](0019-platform-rule-unification.md) | Единая Platform Rule (dashboard context + bindings) | Proposed |

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
