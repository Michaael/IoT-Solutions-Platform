# Platform gap registry

Живой реестр пробелов ISPF. Обновлять при закрытии REQ-PF / REQ-FW и при изменении [ROADMAP.md](ROADMAP.md).

**Источник требований:** [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) (§3 REQ-PF, §12 REQ-FW).

## Sprint planning

Для планирования спринта достаточно **двух документов**:

1. **Этот файл** — сводка пробелов по подсистемам и приоритетам.
2. **[PLATFORM_DEVELOPER_BACKLOG.md §3](PLATFORM_DEVELOPER_BACKLOG.md#3-сводная-матрица-req-pf)** — матрица REQ-PF/FW и acceptance-критерии.

Текущая волна — [ROADMAP.md § Phase 20](ROADMAP.md#phase-20--code-audit-backlog-ui-drivers-scale) (code audit backlog) + [Phase 18.1](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers) (Playwright). Детальный реестр — [CODE_AUDIT_BACKLOG.md](CODE_AUDIT_BACKLOG.md). Phase 19 i18n — **Done** ([0013](decisions/0013-web-console-i18n.md)). Gate обобщения — [0002](decisions/0002-dogfooding-gate.md): каждый PR в platform проходит три вопроса (object tree, deploy REST only, второй сценарий).

## Правило обновления

1. Закрыли REQ — обновить строку в таблице ниже (% и главный пробел).
2. Крупный gap — добавить строку со ссылкой на код или ADR.
3. Sprint planning — достаточно этого файла + §3 backlog.

## Сводка по приоритету

| Приоритет | Область | Суть | BL / Phase |
|-----------|---------|------|------------|
| **P0** | Automation UI | Correlator `SET_VARIABLE` / `OPEN_OPERATOR_REPORT` — backend есть, UI нет | BL-01, 20.1 |
| **P0** | Workflow UI | Actions `log`, `publishNats` не в ISPF справочнике | BL-02, 20.2 |
| **P1** | UI ↔ API | Change sets, edit leases, event catalog viewer | BL-04,05,08 |
| **P1** | Inspector | RECORD inline — JSON textarea вместо `DataRecordValueEditor` | BL-03, 20.3 |
| **P2** | Dashboard | Chart candlestick / bubble / radar | BL-64…65, 20.24…25 |
| **P1** | Dashboard | gantt interactive timeline | BL-11, 20.10 |
| **P1** | i18n tails | `WIDGET_TYPES` и binding hints на русском в TS | BL-07, 20.7 |
| **P1** | Playwright e2e | Admin smoke (Phase 18.1 = BL-50) | BL-50, 18.1 |
| **P1** | Driver write | Modbus, S7, OPC UA — `not implemented` | BL-20…22 |
| **P2** | Bindings UX | Каталог platform bindings в UI | — |
| **P2** | History scale | ClickHouse только для events, не variables | BL-40 |
| **P2** | System ops | Redis/NATS/AI/MCP toggles в UI | BL-13 Done |
| **P3** | Federation | Dashboard write read-only на proxy; sync conflicts | BL-45,46 |
| **P3** | Notifications | Нет webhook/email из alert/correlator | BL-44 |
| **P3** | Semantic (Haystack/Brick) | Нет overlay тегов / export; tree-only semantics | BL-56…62, 20.22 |
| **Низкий** | Driver stubs | STUB/BETA → PRODUCTION по запросу ([DRIVERS.md § Stub promotion](DRIVERS.md#stub-promotion-demand-driven)) | BL-26, 18.2 |
| **Низкий** | CWMP write | `SetParameterValues` — read-only | BL-29 |

## Таблица подсистем

| Подсистема | Готовность | Главный пробел | REQ |
| ---------- | ---------- | -------------- | --- |
| REQ-PF baseline | ~100% | — | PF-14, backlog §10 |
| ADR discipline | ~100% | новые ADR по мере фич | [decisions/](decisions/) incl. **0017–0019** |
| Gap-registry process | ~100% | этот документ | FW-02 |
| Binding rules (0010) | ~100% | — | [BINDINGS.md](BINDINGS.md), V41 |
| Model semantics (0011) | ~100% | — | [MODELS.md](MODELS.md) |
| Visual groups (0012) | ~100% | — | [OBJECT_MODEL.md](OBJECT_MODEL.md) |
| Commercial licensing | ~100% | — | FW-10…11, [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) |
| MES reference | ~100% | — | FW-20, [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) |
| Solution public API doc | ~100% | — | [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md) |
| Event catalog (declarative) | ~100% | — | FW-31, `EventCatalogPayloadValidator` |
| Messaging contract doc | ~100% | — | FW-32, [MESSAGING.md](MESSAGING.md) |
| AI Development Layer | ~100% | — | FW-40…47, [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) |
| MCP adapter (0006) | ~100% | — | ContextPack `resources/list` + `resources/read` |
| Tree-first agent (FW-44) | ~100% | — | [0005](decisions/0005-tree-first-ai-agent.md), **FW-45** briefing |
| Licensed driver packs | ~100% | — | FW-50, [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md) |
| Driver stub catalog | ~95% | write path + 6 STUB/BETA promotion | BL-20…30, [DRIVERS.md](DRIVERS.md) |
| Driver maturity labels | ~70% | PRODUCTION в каталоге при read-only v0.1 | BL-27 |
| Frontend e2e (Playwright) | ~0% | Admin critical paths | BL-50, Phase 18.1 |
| Web Console i18n | ~95% | widget labels + binding hints в TS, не в locale | BL-07, Phase 19 |
| UI ↔ API parity | ~75% | change sets, leases, correlator actions, event catalog | BL-01…08, Phase 20 |
| Dashboard widgets (advanced) | ~90% | chart OHLC/bubble-radar, gantt | BL-64…65,11 |
| Variable inline editor | ~85% | RECORD → structured editor | BL-03 |
| Binding rules UX | ~90% | activators UI | BL-18 |
| Journals | ~90% | export, diff view | BL-15,16 |
| ClickHouse (variables) | ~0% | только event journal | BL-40 |
| Optional backends UI | ~70% | Redis, NATS, ClickHouse journal, AI, MCP в System | BL-41,42,48 |
| Platform change management | ~50% | API без UI | BL-04 |
| Collaboration (leases) | ~50% | API без UI | BL-05 |
| Notifications | ~0% | webhook/email | BL-44 |
| Federation UX | ~85% | dashboard write, sync conflicts | BL-45,46 |
| Platform backup | ~0% | export/import tree | BL-47 |
| Operator manifest | ~85% | chart/map screen types | BL-51 |
| Spreadsheet widget | ~90% | Excel functions tail, history bind | BL-53,54 |
| Frontend component tests | ~15% | vitest utils/sheet only | BL-55 |
| Semantic interoperability | ~0% | Haystack tags, Brick export — deferred | BL-56…62, Phase 20.22 |
| Scale (load test) | ~100% | — | `ListDevicesLoadTest`, `ISPF_LOAD_P99_CEILING_MS` |

## История

| Дата | Изменение |
|------|-----------|
| 2026-06-28 | BL-10: network-graph Cytoscape layout (nodes/edges RECORD_LIST, cose/circle/grid/breadthfirst) |
| 2026-06-28 | Wave G: Haystack/Brick → BL-56…62 (P3 deferred), ROADMAP 20.22 |
| 2026-06-28 | Code audit → [CODE_AUDIT_BACKLOG.md](CODE_AUDIT_BACKLOG.md) (BL-01…55), ROADMAP Phase 20, gap-registry расширен |
| 2026-06-28 | Phase 18 сужена: mini-TEC acceptance и v0.8.0 prod rollout сняты; остались Playwright e2e + demand-driven drivers |
| 2026-06-23 | Phase 19 Done: i18n en/ru/de/zh, LocaleSwitcher, 0013 |
| 2026-06-23 | Phase 19 draft: Web Console i18n (locale dropdown, ru/en, waves 19.1–19.6) |
| 2026-06-23 | Phase 18 kickoff: mini-TEC walkthrough, ROADMAP 18.1–18.6 |
| 2026-06-23 | Phase 17.2: drop binding_expr (V41, V1 cleanup); dev — пересоздание БД |
| 2026-06-23 | Phase 17 formalized; MCP ContextPack resources; sprint planning section; ADR 0017–0019 |
| 2026-06-23 | v0.8.0: BindingRuleEngine only — удалён bindingExpression; cross-object propagation; 0010 |
| 2026-06-22 | FW-47: list_functions, get_function, list_event_catalog, get_event_schema, describe_variables |
| 2026-06-22 | FW-46: invoke_bff, search_objects, list_object_models, fire_event, list_events |
| 2026-06-22 | FW-45: PlatformBriefingService, ContextPack indices, agent knowledge tools, search_context v2 |
| 2026-06-21 | Backlog tails close: ADR 0014–0016, semver/NATS/MES docs, fire-time payloadSchema, key rotation ops, opc-bridge/vmware/smi-s → BETA, v0.7.8 |
| 2026-06-21 | Sprint G close: MCP adapter (0006), FW-50 pilot pack test, p99 CI gate, corba/ethernet-ip/opc-da → BETA |
| 2026-06-21 | FW-44 agent v0.7.5: sessions, reliability layer; FW-50 spike; persistent sessions; MCP 0006 |
| 2026-06-22 | Sprint G (FW-40…43): AI Layer — LlmProvider SPI, ContextPack, ToolRegistry, Studio |
| 2026-06-22 | Sprint F (FW-12,31,32): events catalog, requires[], MESSAGING.md |
