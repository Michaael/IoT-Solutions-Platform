# Platform gap registry

Живой реестр пробелов ISPF. Обновлять при закрытии REQ-PF / REQ-FW и при изменении [ROADMAP.md](ROADMAP.md).

**Источник требований:** [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) (§3 REQ-PF, §12 REQ-FW).

## Sprint planning

Для планирования спринта достаточно **двух документов**:

1. **Этот файл** — сводка пробелов по подсистемам и приоритетам.
2. **[PLATFORM_DEVELOPER_BACKLOG.md §3](PLATFORM_DEVELOPER_BACKLOG.md#3-сводная-матрица-req-pf)** — матрица REQ-PF/FW и acceptance-критерии.

Текущая волна — [ROADMAP.md § Phase 18](ROADMAP.md#phase-18--reference-solutions--v080-rollout). Phase 19 i18n — **Done** ([ADR-0020](decisions/0020-web-console-i18n.md)). Gate обобщения — [ADR-0009](decisions/0009-dogfooding-gate.md): каждый PR в platform проходит три вопроса (object tree, deploy REST only, второй сценарий).

## Правило обновления

1. Закрыли REQ — обновить строку в таблице ниже (% и главный пробел).
2. Крупный gap — добавить строку со ссылкой на код или ADR.
3. Sprint planning — достаточно этого файла + §3 backlog.

## Сводка по приоритету

| Приоритет | Область | Суть |
|-----------|---------|------|
| **P1** | Mini-TEC reference | CI smoke + SLD acceptance — [Phase 18](ROADMAP.md#phase-18--reference-solutions--v080-rollout), [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md) |
| **P1** | v0.8.0 prod | DB recreate + deploy — [DEPLOYMENT.md § v0.8.0](DEPLOYMENT.md#обновление-до-v080) |
| **P2** | Playwright e2e | Admin smoke (Phase 18.5) — хвост Phase 3.4 |
| **Низкий** | Driver stubs | STUB/BETA → PRODUCTION по запросу app-команды ([DRIVERS.md § Stub promotion](DRIVERS.md#stub-promotion-demand-driven)) |
| **Низкий** | CWMP write | `SetParameterValues` — read-only сейчас ([DRIVERS.md](DRIVERS.md)) |

## Таблица подсистем

| Подсистема | Готовность | Главный пробел | REQ |
| ---------- | ---------- | -------------- | --- |
| REQ-PF baseline | ~100% | — | PF-14, backlog §10 |
| ADR discipline | ~100% | новые ADR по мере фич | [decisions/](decisions/) incl. **0017–0019** |
| Gap-registry process | ~100% | этот документ | FW-02 |
| Binding rules (ADR-0017) | ~100% | — | [BINDINGS.md](BINDINGS.md), V41 |
| Model semantics (ADR-0018) | ~100% | — | [MODELS.md](MODELS.md) |
| Visual groups (ADR-0019) | ~100% | — | [OBJECT_MODEL.md](OBJECT_MODEL.md) |
| Commercial licensing | ~100% | — | FW-10…11, [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) |
| MES reference | ~100% | — | FW-20, [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) |
| Solution public API doc | ~100% | — | [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md) |
| Event catalog (declarative) | ~100% | — | FW-31, `EventCatalogPayloadValidator` |
| Messaging contract doc | ~100% | — | FW-32, [MESSAGING.md](MESSAGING.md) |
| AI Development Layer | ~100% | — | FW-40…47, [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) |
| MCP adapter (ADR-0013) | ~100% | — | ContextPack `resources/list` + `resources/read` |
| Tree-first agent (FW-44) | ~100% | — | [ADR-0012](decisions/0012-tree-first-ai-agent.md), **FW-45** briefing |
| Licensed driver packs | ~100% | — | FW-50, [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md) |
| Driver stub catalog | ~95% | 6 STUB/BETA drivers по demand | [DRIVERS.md § Stub promotion](DRIVERS.md#stub-promotion-demand-driven) |
| Mini-TEC reference (Phase 18) | ~40% | CI smoke, SLD acceptance, prod deploy | [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md) |
| Frontend e2e (Playwright) | ~0% | Admin critical paths | Phase 18.5 |
| Web Console i18n | ~100% | — | Phase 19, [ADR-0020](decisions/0020-web-console-i18n.md) |
| Scale (load test) | ~100% | — | `ListDevicesLoadTest`, `ISPF_LOAD_P99_CEILING_MS` |

## История

| Дата | Изменение |
|------|-----------|
| 2026-06-23 | Phase 19 Done: i18n en/ru/de/zh, LocaleSwitcher, ADR-0020 |
| 2026-06-23 | Phase 19 draft: Web Console i18n (locale dropdown, ru/en, waves 19.1–19.6) |
| 2026-06-23 | Phase 18 kickoff: mini-TEC walkthrough, ROADMAP 18.1–18.6 |
| 2026-06-23 | Phase 17.2: drop binding_expr (V41, V1 cleanup); dev — пересоздание БД |
| 2026-06-23 | Phase 17 formalized; MCP ContextPack resources; sprint planning section; ADR 0017–0019 |
| 2026-06-23 | v0.8.0: BindingRuleEngine only — удалён bindingExpression; cross-object propagation; ADR-0017 |
| 2026-06-22 | FW-47: list_functions, get_function, list_event_catalog, get_event_schema, describe_variables |
| 2026-06-22 | FW-46: invoke_bff, search_objects, list_object_models, fire_event, list_events |
| 2026-06-22 | FW-45: PlatformBriefingService, ContextPack indices, agent knowledge tools, search_context v2 |
| 2026-06-21 | Backlog tails close: ADR 0014–0016, semver/NATS/MES docs, fire-time payloadSchema, key rotation ops, opc-bridge/vmware/smi-s → BETA, v0.7.8 |
| 2026-06-21 | Sprint G close: MCP adapter (ADR-0013), FW-50 pilot pack test, p99 CI gate, corba/ethernet-ip/opc-da → BETA |
| 2026-06-21 | FW-44 agent v0.7.5: sessions, reliability layer; FW-50 spike; persistent sessions; MCP ADR-0013 |
| 2026-06-22 | Sprint G (FW-40…43): AI Layer — LlmProvider SPI, ContextPack, ToolRegistry, Studio |
| 2026-06-22 | Sprint F (FW-12,31,32): events catalog, requires[], MESSAGING.md |
