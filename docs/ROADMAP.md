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
| 16.9 | MCP adapter over agent tools (0006) | AI | Done (v0.7.7) |
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
| 17.5 | 0011 typed model catalogs + 0012 visual groups acceptance | Done |

## Phase 18 — Frontend e2e & demand-driven drivers

Волна после Phase 17/19: **Playwright smoke** (хвост Phase 3.4) и **promotion драйверов по запросу** app-команды. Dogfooding gate — [0002](decisions/0002-dogfooding-gate.md).

| # | Тема | Статус |
|---|------|--------|
| 18.1 | Playwright admin e2e smoke (Explorer, operator deep link) | Partial |
| 18.2 | Driver stub promotion — первый кандидат по запросу app-команды | Planned |

**Снято с roadmap (не актуально):** acceptance mini-TEC (`MiniTecPlatformApiTest`, SLD), разовый v0.8.0 prod rollout с пересозданием БД. Mini-TEC остаётся optional demo — [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md), [examples/mini-tec/](../examples/mini-tec/). Runbook миграции pre-0.8.0 — [DEPLOYMENT.md § v0.8.0+](DEPLOYMENT.md#обновление-до-v080).

## Phase 19 — Web Console i18n (UI localization)

Перевод интерфейса Web Console: **выпадающий выбор языка в шапке** (admin + operator + login), сохранение в `localStorage` и `?lang=`, **эталон `en`**, fallback `en`.

| # | Тема | Статус |
|---|------|--------|
| 19.1 | 0013 + `react-i18next`, `locales/{en,ru,de,zh}/*.json`, `LocaleSwitcher` | Done |
| 19.2 | Shell: login, навигация, Explorer, System | Done |
| 19.3 | Инспекторы, Models, Dashboard/Widget editor, Report Builder | Done |
| 19.4 | Operator HMI, automation, federation, AI Studio | Done |
| 19.5 | Языки: **en** (canonical), **ru**, **de**, **zh** | Done |
| 19.6 | `npm run i18n:check` (ключи vs en) | Done |

**In scope:** статический UI Web Console (labels, buttons, tabs, empty states, validation).

**Out of scope (отдельно):** пользовательский контент дерева (`displayName`, BPMN, bundle dashboards), server error messages, AI agent replies.

Track: **UI** (не REQ-PF — не меняет platform API). См. [0013](decisions/0013-web-console-i18n.md).

## Phase 20 — Code audit backlog (UI, drivers, scale)

Волна после code audit (2026-06-28): закрытие пробелов **backend ↔ frontend**, polish HMI, driver write, scale integrations. Полный реестр — [CODE_AUDIT_BACKLOG.md](CODE_AUDIT_BACKLOG.md).

**Дополнение (июнь 2026, prod 0.9.31–0.9.33):** журналы — fix function audit opt-in, отображение списка, сортировка newest-first, drill-down (payload / input·output / before·after, Flyway V51). См. [OBJECT_FUNCTIONS.md](OBJECT_FUNCTIONS.md).

| # | Тема | BL | P | Статус |
|---|------|----|---|--------|
| 20.1 | Correlator actions `SET_VARIABLE`, `OPEN_OPERATOR_REPORT` в UI | BL-01 | P0 | Done |
| 20.2 | Workflow actions `log`, `publishNats` в ISPF справочнике | BL-02 | P0 | Done |
| 20.3 | `DataRecordValueEditor` в inline variable editor | BL-03 | P1 | Done |
| 20.4 | Platform Change Sets UI | BL-04 | P1 | Done |
| 20.5 | Edit lease indicator + acquire/release | BL-05 | P1 | Done |
| 20.6 | Chart types: убрать нереализованные options (opt. B) | BL-06 | P1 | Done |
| 20.7 | i18n: widget type labels + binding hints | BL-07 | P1 | Done |
| 20.8 | Application Event Catalog viewer | BL-08 | P1 | Done |
| 20.9 | Binding expression builder (18 platform functions) | BL-09 | P2 | Done |
| 20.10 | Widgets: network-graph layout, gantt interactive, history-table window | BL-10…12 | P2 | Done |
| 20.11 | System settings: Redis/NATS/ClickHouse/AI/MCP toggles | BL-13 | P2 | Done |
| 20.12 | Automation index + journals (export/diff, invoke audit payloads, drill-down) | BL-14…16 | P2 | Done |
| 20.23 | Chart `range` min/max band | BL-63 | P2 | Done |
| 20.24 | Chart `candlestick` OHLC | BL-64 | P2 | Done |
| 20.25 | Chart `bubble` / `radar` multi-axis | BL-65 | P3 | Done |
| 20.13 | Driver write: Modbus, S7, OPC UA | BL-20…22 | P1 | Done |
| 20.14 | Driver write: BACnet, IEC104, DNP3 poll, DLMS | BL-23…25 | P2 | Done |
| 20.15 | Driver maturity sync + write UI + tests | BL-27,28,30 | P2 | Partial |
| 20.16 | ClickHouse variable history | BL-40 | P2 | Partial |
| 20.17 | Scale ops: Redis/NATS health, YARG PDF hint | BL-41…43 | P2 | Partial |
| 20.18 | Notifications, federation polish, backup/restore, MCP admin | BL-44…48 | P3 | Done (BL-44…48) |
| 20.19 | Playwright e2e (см. также Phase 18.1) | BL-50 | P1 | Done |
| 20.20 | Operator manifest screens + spreadsheet history binding | BL-51…54 | P3 | Done |
| 20.21 | Frontend component tests (widgets, inspector) | BL-55 | P2 | Done |
| 20.22 | Haystack/Brick semantic layer (ADR, tags mixin, export) | BL-56…62 | P3 | Deferred |

**Partial — расшифровка:** 20.15 — **BL-28 Done** (write UI), BL-27/30 Planned; 20.16 — settings UI Done, backend write/query Planned; 20.17 — **BL-41,42 Done**, BL-43 Planned; 20.19 = Phase 18.1 smoke baseline; 20.20 — **BL-53 Done**, BL-51/52/54 Planned.

**Следующие приоритеты:** BL-30 (BACnet property exchange / driver tail), BL-56+ semantic layer (deferred).

**Спринты:** см. [CODE_AUDIT_BACKLOG.md § Sprint planning](CODE_AUDIT_BACKLOG.md#sprint-planning-рекомендация).

## Phase 21 — Time & timezones

Контракт store-UTC / display-local: per-user TZ для UI, per-device TZ для нормализации edge-данных, опционально `observedAt` в historian и `occurredAt` в events. ADR — [0020](decisions/0020-time-and-timezones.md). Полный реестр — [CODE_AUDIT_BACKLOG.md § Wave H](CODE_AUDIT_BACKLOG.md#wave-h--time--timezones).

| # | Тема | BL | P | Статус |
|---|------|----|---|--------|
| 21.1 | ADR time & timezones + spike | BL-66 | P2 | Done |
| 21.2 | User timezone preference + UI formatting | BL-67 | P2 | Done |
| 21.3 | Device timezone metadata + inheritance | BL-68 | P2 | Done |
| 21.4 | Historian observedAt / driver SPI | BL-69 | P3 | Done |
| 21.5 | Calendar-boundary history & reports | BL-70 | P3 | Done |
| 21.6 | Event fire occurredAt override | BL-71 | P3 | Done |

**Зависимости:** 21.1 → 21.2, 21.3; 21.3 → 21.4; 21.2 → 21.5.

Track: **platform contract** (REQ-FW-60) + UI/drivers/history. Не меняет хранение с UTC на local.

## Platform baseline

| # | Тема | Статус |
|---|------|--------|
| P.1 | Java 25 toolchain + CI | Done |
| P.2 | Spring Boot 4.0.7 migration | Done |
| P.3 | Jackson 3 native (`tools.jackson`) | Done |

## Связанные документы

- [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) — REQ-PF + REQ-FW (§12)
- [CODE_AUDIT_BACKLOG.md](CODE_AUDIT_BACKLOG.md) — BL-01…71, code audit 2026-06-28 + Wave H timezones
- [GAP_REGISTRY.md](GAP_REGISTRY.md) — sprint planning, живой срез пробелов
- [APPLICATIONS.md](APPLICATIONS.md) — deploy API
- [DEPLOYMENT.md](DEPLOYMENT.md) — prod topology
- [SECURITY.md](SECURITY.md) — auth/RBAC
