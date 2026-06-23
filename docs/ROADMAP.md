# Platform Roadmap

Единый roadmap ISPF: application platform (REQ-PF), production ops и Web Console.

Статус baseline: `main`, июнь 2026.

## Phase 0 — Stabilization

| # | Тема | Статус |
|---|------|--------|
| 0.1 | GitHub Actions CI (server + web build) | Done |
| 0.2 | Gradle test memory limits | Done |
| 0.3 | PF-01c `map` / `buildRecord` | Done |
| 0.4 | PF-03 `models[]` в bundle deploy | Done |
| 0.5 | Leader lock для schedulers | Done |
| 0.6 | WebSocket auth (token query param) | Done |
| 0.7 | OperatorUi `eventJournalObjectPath` | Done |
| 0.8 | Reference app #2 (`examples/warehouse-app`) | Done |
| 0.9 | System folder list panels в Explorer | Done |

## Phase 1 — Application platform closure

| # | Тема | Статус |
|---|------|--------|
| 1.1 | Acceptance PF-01: типовой app только JSON+SQL | Done |
| 1.2 | Dogfooding warehouse + demo на одном API | Done (bundle) |
| 1.3 | PF-06 custom field labels map | Done |
| 1.4 | Bundle rollback UX в admin | Done |

## Phase 2 — Production gate

| # | Тема | Статус |
|---|------|--------|
| 2.1 | Keycloak/OIDC login в Web Console | Done |
| 2.2 | TimescaleDB hypertables + retention policies | Done |
| 2.3 | Per-object ACL (REQ новый) | Done |
| 2.4 | NATS event bus между репликами | Done |

## Phase 3 — Connectivity & HMI

| # | Тема | Статус |
|---|------|--------|
| 3.1 | Driver maturity labels (production/beta/stub) | Done |
| 3.2 | Stub-драйверы → production по demand | Done (CWMP→PRODUCTION + flexible/gps-tracker acceptance) |
| 3.3 | React Router / deep links в admin | Done |
| 3.4 | Frontend smoke tests (vitest/playwright) | Done (vitest) |
| 3.5 | Legacy operator manifest deprecation | Done (console warn) |

## Phase 4 — Scale & topology (P3+)

| # | Тема | Статус |
|---|------|--------|
| 4.1 | PF-13 federation design + spike | Done (peers + proxy read + catalog sync) |
| 4.2 | Multi-tenant tree namespaces | Done (spike: root.tenant.* + scope) |

## Phase 5 — Усиление механизмов (north star)

Следующая волна после Phase 0–4: **больше выразительности object tree**, меньше поводов для custom Java и «application layer как отдельного мира». Детали — [PLATFORM_DEVELOPER_BACKLOG.md §8.1](PLATFORM_DEVELOPER_BACKLOG.md#81-усиление-механизмов-phase-5).

| # | Механизм | Направление | Статус |
|---|----------|-------------|--------|
| 5.1 | **Модели** | Богаче bindings; наследование шаблонов; версионирование моделей | Done (`extendsModelId`, bulk upgrade API, vendor demo) |
| 5.2 | **Функции** | Больше script steps (`setVar`, `when`/`if`, …); declarative SQL bindings (PF-08); меньше поводов для custom code | Done |
| 5.3 | **События + correlators** | Сложнее паттерны (окна, цепочки, агрегации); эскалация без Java | Done (`EVENT_CHAIN`, `sequenceGapSeconds`, N-in-window demo) |
| 5.4 | **Workflow** | Больше `serviceTask` через platform primitives (`fire_event`, `read_variable`, `start_workflow`, …) | Done (BPMN properties panel + acceptance tests) |
| 5.5 | **Bundle / application layer** | Bundle = упаковка объектов дерева; tree-first invoke; reconcile `objects[]` | Done |

## Phase 6 — Post-v0.2.0 production (v0.3.0)

Волна после Phase 5: **production readiness**, закрытие хвостов REQ-PF §3, federation hardening. Целевой релиз — **v0.3.0**.

| # | Тема | Статус |
|---|------|--------|
| 6.1 | Doc sync + PF-03 deprecation | Done |
| 6.2 | Driver maturity (CWMP, flexible, gps-tracker) | Done |
| 6.3 | PF-13 federation production | Done |
| 6.4 | PF-09 virtual profiles bundle + PF-11 function rollback UI | Done |
| 6.5 | Phase 5 polish (model diff, warehouse CI, correlator cooldown) | Done |

## Phase 7 — Federation auth + outbound tunnel

| # | Тема | Статус |
|---|------|--------|
| 7.1 | Auth lifecycle (service account auto-refresh, 401 retry) | Done |
| 7.2 | Outbound WebSocket tunnel (NAT edge → public hub, full proxy) | Done |

## Phase 8 — Federation bind (REQ-PF-13c)

| # | Тема | Статус |
|---|------|--------|
| 8.1 | Bind overlay на локальный path + UI | Done |
| 8.2 | Same-path remote overlay + cycle protection | Done (v0.5.1) |
| 8.3 | Unbind restore local metadata snapshot | Done (v0.5.2) |

## Phase 10 — North Star hardening

| # | Тема | Статус |
|---|------|--------|
| 10.1 | Persistent binding state (`@bindingState`, hysteresis/deadband/movingAvg/counterRate) | Done (v0.6.0) |

## Phase 11 — Multi-user collaboration

| # | Тема | Статус |
|---|------|--------|
| 11.1 | Object revision (If-Match) + config audit + stale editor UI | Done |
| 11.2 | WS presence + subtree leases + model merge preview | Done |
| 11.3 | Subtree ownership ACL (OWNER/EDITOR/VIEWER) | Done |
| 11.4 | Change-sets + preview/apply promotion pipeline | Done |

См. [COLLABORATION.md](COLLABORATION.md).

## Phase 12 — Reports tree-first

| # | Тема | Статус |
|---|------|--------|
| 12.1 | `report-v1` model + `root.platform.reports` catalog | Done |
| 12.2 | `ReportService` + `/api/v1/reports/by-path` API | Done |
| 12.3 | Report Builder in Web Console + legacy app API facade | Done |
| 12.4 | Dashboard `report` widget + operatorUi `reports[]` | Done |

См. [REPORTS.md](REPORTS.md).

## Phase 13 — YARG server export

| # | Тема | Статус |
|---|------|--------|
| 13.1 | `com.haulmont.yarg:yarg` + `report_templates` storage | Done |
| 13.2 | `YargReportService` — PDF/XLSX/HTML from SQL + template | Done |
| 13.3 | Template upload API + Report Builder «Шаблон YARG» tab | Done |
| 13.4 | `export?format=pdf\|xlsx\|html` + widget PDF button | Done |

См. [REPORTS.md](REPORTS.md) (секция YARG templates).

## Phase 14 — Tree-first platform catalogs

| # | Тема | Статус |
|---|------|--------|
| 14.1 | `data-source-v1`, `schedule-v1`, `sql-binding-v1`, `migration-v1` models | Done |
| 14.2 | Platform catalogs: `data-sources`, `schedules`, `bindings`, `migrations` | Done |
| 14.3 | Reports: `dataSourcePath` вместо `appId`; `POST /platform/packages/import` | Done |
| 14.4 | Script functions on tree (`FunctionDescriptor.sourceBody`) | Done |
| 14.5 | Web Console: Report Builder data source picker, Package Import, hide legacy applications | Done |

Application API (`/applications/{id}/deploy`) сохранён для совместимости; runtime — только дерево объектов.

## Phase 15 — Lab Training Package

| # | Тема | Статус |
|---|------|--------|
| 15.1 | Virtual driver profile `lab` + model `virtual-lab-v1` (waves, table, events, calculate) | Done |
| 15.2 | Automation v2: alert `delaySeconds`/`sustainWhileTrue`, correlator `payloadFilterExpr`, `SET_VARIABLE`, `OPEN_OPERATOR_REPORT` | Done |
| 15.3 | Report type `tree-variables` (cross-device RECORD_LIST) | Done |
| 15.4 | Web Console widgets: `pie-chart`, `history-table`, `variable-editor`, `svg-widget`, `composite-widget`, event-feed filter | Done |
| 15.5 | Importable bundle `examples/lab-training/` + lab users/ACL bootstrap | Done |
| 15.6 | Docs [LAB_TRAINING.md](LAB_TRAINING.md), integration tests | Done |

См. [LAB_TRAINING.md](LAB_TRAINING.md), [DASHBOARDS.md](DASHBOARDS.md) (Grid Layout form example).

## Phase 16 — Platform evolution (REQ-FW)

Документация процесса, коммерческие bundle, reference MES, public API, messaging, AI, licensed drivers — без смены north star (§0.1). Детали — [PLATFORM_DEVELOPER_BACKLOG.md §12](PLATFORM_DEVELOPER_BACKLOG.md#12-дополнительные-требования-platform-req-fw).

| # | Тема | Track | Статус |
|---|------|-------|--------|
| 16.1 | ADR `docs/decisions/` + gap-registry process | DOC | Done |
| 16.2 | RSA licensing + `installationId` + LicenseBuilder | LIC | Done |
| 16.3 | MES reference walkthrough + synthetic demo | REF | Done |
| 16.4 | Solution public API boundary + event catalog in bundle | API | Done |
| 16.5 | Messaging contract (event bus vs sync RPC) | NET | Done |
| 16.6 | AI Layer (LlmProvider, ContextPack, tools) + Studio | AI | Done |
| 16.8 | Tree-first agent (FW-44): sessions, dashboard/SNMP tools, reliability | AI | Done (v0.7.5) |
| 16.9 | MCP adapter over agent tools (ADR-0013) | AI | Done (v0.7.7) |
| 16.7 | Licensed driver JAR contract + pilot pack | DRV | Done (v0.7.7) |

Sprint E–G в [PLATFORM_DEVELOPER_BACKLOG.md §8](PLATFORM_DEVELOPER_BACKLOG.md#8-приоритет-реализации-roadmap).

## Phase 17 — Post-baseline hardening (v0.8.0+)

Волна после закрытия REQ-PF/FW baseline (Phase 0–16): **doc sync, schema cleanup, MCP resources, demand-driven drivers**. См. [GAP_REGISTRY.md](GAP_REGISTRY.md) для sprint planning.

| # | Тема | Статус |
|---|------|--------|
| 17.1 | Doc sync (APPLICATIONS, gap-registry, sprint planning cross-refs) | Done |
| 17.2 | Flyway drop `binding_expr` column (V41; V1 без колонки; dev — пересоздание БД) | Done |
| 17.3 | MCP ContextPack resources (`resources/list`, `resources/read`) | Done |
| 17.4 | Stub driver promotion process documented (demand-driven) | Done |
| 17.5 | ADR-0018 typed model catalogs + ADR-0019 visual groups acceptance | Done |

## Phase 18 — Reference solutions & v0.8.0 rollout

Волна после Phase 17: **эталон mini-TEC**, production rollout v0.8.0, frontend e2e, demand-driven drivers. Dogfooding gate — [ADR-0009](decisions/0009-dogfooding-gate.md).

| # | Тема | Статус |
|---|------|--------|
| 18.1 | Mini-TEC reference walkthrough | Done (doc) |
| 18.2 | `MiniTecPlatformApiTest` CI smoke | Planned |
| 18.3 | Operator SLD widget + HMI acceptance (`mini-tec-sld`) | Planned |
| 18.4 | v0.8.0 prod rollout (DB recreate, [DEPLOYMENT.md § v0.8.0](DEPLOYMENT.md#обновление-до-v080)) | Planned |
| 18.5 | Playwright admin e2e smoke (Explorer, operator deep link) | Planned |
| 18.6 | Driver stub promotion — первый кандидат по запросу app-команды | Planned |

См. [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md), [examples/mini-tec/](../examples/mini-tec/).

## Phase 19 — Web Console i18n (UI localization)

Перевод интерфейса Web Console: **выпадающий выбор языка в шапке** (admin + operator + login), сохранение в `localStorage` и `?lang=`, **эталон `en`**, fallback `en`.

| # | Тема | Статус |
|---|------|--------|
| 19.1 | ADR-0020 + `react-i18next`, `locales/{en,ru,de,zh}/*.json`, `LocaleSwitcher` | Done |
| 19.2 | Shell: login, навигация, Explorer, System | Done |
| 19.3 | Инспекторы, Models, Dashboard/Widget editor, Report Builder | Done |
| 19.4 | Operator HMI, automation, federation, AI Studio | Done |
| 19.5 | Языки: **en** (canonical), **ru**, **de**, **zh** | Done |
| 19.6 | `npm run i18n:check` (ключи vs en) | Done |

**In scope:** статический UI Web Console (labels, buttons, tabs, empty states, validation).

**Out of scope (отдельно):** пользовательский контент дерева (`displayName`, BPMN, bundle dashboards), server error messages, AI agent replies.

Track: **UI** (не REQ-PF — не меняет platform API). См. [ADR-0020](decisions/0020-web-console-i18n.md).

## Platform baseline

| # | Тема | Статус |
|---|------|--------|
| P.1 | Java 25 toolchain + CI | Done |
| P.2 | Spring Boot 4.0.7 migration | Done |
| P.3 | Jackson 3 native (`tools.jackson`) | Done |

## Связанные документы

- [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) — REQ-PF + REQ-FW (§12)
- [GAP_REGISTRY.md](GAP_REGISTRY.md) — sprint planning, живой срез пробелов
- [APPLICATIONS.md](APPLICATIONS.md) — deploy API
- [DEPLOYMENT.md](DEPLOYMENT.md) — prod topology
- [SECURITY.md](SECURITY.md) — auth/RBAC
