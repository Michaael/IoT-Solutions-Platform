# Platform gap registry

Живой реестр пробелов ISPF. Обновлять при закрытии REQ-PF / REQ-FW и при изменении [ROADMAP.md](ROADMAP.md).

**Источник требований:** [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) (§3 REQ-PF, §12 REQ-FW).

## Правило обновления

1. Закрыли REQ — обновить строку в таблице ниже (% и главный пробел).
2. Крупный gap — добавить строку со ссылкой на код или ADR.
3. Sprint planning — достаточно этого файла + §3 backlog.

## Сводка по приоритету

| Приоритет | Область | Суть |
|-----------|---------|------|
| **Низкий** | REQ-PF baseline | Stub drivers по demand |
| **Средний** | Scale | p99 `list_devices` при высокой concurrency |
| **Средний** | Sprint G | AI Layer, licensed driver JAR packs |

## Таблица подсистем

| Подсистема | Готовность | Главный пробел | REQ |
| ---------- | ---------- | -------------- | --- |
| REQ-PF baseline | ~100% | polish stub drivers | PF-14, backlog §10 |
| ADR discipline | ~90% | новые ADR по мере фич | [decisions/](decisions/) |
| Gap-registry process | ~100% | этот документ | FW-02 |
| Commercial licensing | ~100% | production key rotation policy (ops) | FW-10…11, [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) |
| MES reference | ~100% | optional workflow step in walkthrough | FW-20, [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) |
| Solution public API doc | ~100% | semver policy для bundle schema | [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md) |
| Event catalog (declarative) | ~100% | payload schema validation at fire-time | FW-31, [MESSAGING.md](MESSAGING.md) |
| Messaging contract doc | ~100% | external NATS consumers guide | FW-32, [MESSAGING.md](MESSAGING.md) |
| AI Development Layer | ~100% | optional live tenant context in ContextPack | FW-40…43, [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) |
| Licensed driver packs | 0% | все drivers in-tree | FW-50 |
| Scale (load test) | ~70% | p99 `list_devices` при 150 concurrent | ROADMAP Phase 6+ |

## История

| Дата | Изменение |
|------|-----------|
| 2026-06-22 | Sprint G (FW-40…43): AI Layer — LlmProvider SPI, ContextPack, ToolRegistry, Studio |
| 2026-06-22 | Sprint F (FW-12,31,32): events catalog, requires[], MESSAGING.md |
