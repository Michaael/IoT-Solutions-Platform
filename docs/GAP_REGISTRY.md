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
| **Низкий** | MCP optional | MCP `resources` for ContextPack slices (ADR-0013 follow-up) |

## Таблица подсистем

| Подсистема | Готовность | Главный пробел | REQ |
| ---------- | ---------- | -------------- | --- |
| REQ-PF baseline | ~100% | — | PF-14, backlog §10 |
| ADR discipline | ~100% | новые ADR по мере фич | [decisions/](decisions/) incl. **0014–0016** |
| Gap-registry process | ~100% | этот документ | FW-02 |
| Commercial licensing | ~100% | — | FW-10…11, [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) |
| MES reference | ~100% | — | FW-20, [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) |
| Solution public API doc | ~100% | — | [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md) |
| Event catalog (declarative) | ~100% | — | FW-31, `EventCatalogPayloadValidator` |
| Messaging contract doc | ~100% | — | FW-32, [MESSAGING.md](MESSAGING.md) |
| AI Development Layer | ~100% | optional MCP resources | FW-40…47, [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) |
| Tree-first agent (FW-44) | ~100% | — | [ADR-0012](decisions/0012-tree-first-ai-agent.md), **FW-45** briefing |
| Licensed driver packs | ~100% | — | FW-50, [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md) |
| Scale (load test) | ~100% | — | `ListDevicesLoadTest`, `ISPF_LOAD_P99_CEILING_MS` |

## История

| Дата | Изменение |
|------|-----------|
| 2026-06-22 | FW-47: list_functions, get_function, list_event_catalog, get_event_schema, describe_variables |
| 2026-06-22 | FW-46: invoke_bff, search_objects, list_object_models, fire_event, list_events |
| 2026-06-22 | FW-45: PlatformBriefingService, ContextPack indices, agent knowledge tools, search_context v2 |
| 2026-06-21 | Backlog tails close: ADR 0014–0016, semver/NATS/MES docs, fire-time payloadSchema, key rotation ops, opc-bridge/vmware/smi-s → BETA, v0.7.8 |
| 2026-06-21 | Sprint G close: MCP adapter (ADR-0013), FW-50 pilot pack test, p99 CI gate, corba/ethernet-ip/opc-da → BETA |
| 2026-06-21 | FW-44 agent v0.7.5: sessions, reliability layer; FW-50 spike; persistent sessions; MCP ADR-0013 |
| 2026-06-22 | Sprint G (FW-40…43): AI Layer — LlmProvider SPI, ContextPack, ToolRegistry, Studio |
| 2026-06-22 | Sprint F (FW-12,31,32): events catalog, requires[], MESSAGING.md |
