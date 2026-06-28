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
| **P0** | Automation UI | Correlator `SET_VARIABLE` / `OPEN_OPERATOR_REPORT` | BL-01 Done |
| **P0** | Workflow UI | Actions `log`, `publishNats` в ISPF справочнике | BL-02 Done |
| **P1** | UI ↔ API | Change sets, edit leases, event catalog viewer | BL-04,05,08 Done |
| **P1** | Inspector | RECORD inline editor | BL-03 Done |
| **P2** | Dashboard | Chart bubble / radar | BL-65, 20.25 |
| **P1** | i18n tails | widget types + binding hints в locale | BL-07 Done |
| **P1** | Playwright e2e | Admin smoke baseline (Phase 18.1 = BL-50) | BL-50 Partial, 18.1 |
| **P1** | Driver write | Modbus, S7, OPC UA | BL-20…22 Done |
| **P2** | Driver write (tail) | BACnet, IEC 104, DNP3 poll, DLMS | BL-23…25 |
| **P2** | Bindings UX | Каталог platform bindings + activators UI | BL-09,18 Done; runtime engine in progress |
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
| Driver stub catalog | ~95% | BACnet/IEC104/DNP3/DLMS write; STUB/BETA promotion | BL-23…26, [DRIVERS.md](DRIVERS.md) |
| Driver maturity labels | ~70% | PRODUCTION в каталоге при read-only v0.1 | BL-27 |
| Frontend e2e (Playwright) | ~25% | Smoke only; critical paths TBD | BL-50 Partial, Phase 18.1 |
| Web Console i18n | ~98% | tails | BL-07 Done |
| UI ↔ API parity | ~95% | — | BL-01…18 Done |
| Dashboard widgets (advanced) | ~95% | chart bubble/radar | BL-65 |
| Variable inline editor | ~95% | — | BL-03 Done |
| Binding rules UX | ~95% | runtime `onEvent`/`periodicMs` engine | in progress |
| Journals | ~100% | invoke/binding audit payloads (V51), drill-down UI | BL-15,16 Done |
| ClickHouse (variables) | ~15% | UI settings Done; backend store Planned | BL-40 Partial |
| Optional backends UI | ~100% | — | BL-41,42 Done |
| Platform change management | ~95% | — | BL-04 Done |
| Collaboration (leases) | ~95% | — | BL-05 Done |
| Notifications | ~0% | webhook/email | BL-44 |
| Federation UX | ~85% | dashboard write, sync conflicts | BL-45,46 |
| Platform backup | ~0% | export/import tree | BL-47 |
| Operator manifest | ~85% | chart/map screen types | BL-51 |
| Spreadsheet widget | ~95% | history bind (BL-54) | BL-54 |
| Frontend component tests | ~40% | RTL widgets/inspector dialogs | BL-55 Partial |
| Semantic interoperability | ~0% | Haystack tags, Brick export — deferred | BL-56…62, Phase 20.22 |
| Scale (load test) | ~100% | — | `ListDevicesLoadTest`, `ISPF_LOAD_P99_CEILING_MS` |

## История

| Дата | Изменение |
|------|-----------|
| 2026-06-28 | ROADMAP Phase 20 sync с CODE_AUDIT: BL-20…22/28/41/42 Done; Partial на 20.15–17,19–21; журналы 0.9.33 |
| 2026-06-28 | BL-55 Partial: binding activators, journal export, chart/gantt vitest utils |
| 2026-06-28 | Sprint BL-C close: BL-13…18, journals, system settings, binding activators UI |
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
