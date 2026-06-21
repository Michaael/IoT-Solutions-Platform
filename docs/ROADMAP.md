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
| 3.2 | Stub-драйверы → production по demand | In progress (CWMP→BETA, flexible/gps-tracker→PRODUCTION) |
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

## Platform baseline

| # | Тема | Статус |
|---|------|--------|
| P.1 | Java 25 toolchain + CI | Done |
| P.2 | Spring Boot 4.0.7 migration | Done |
| P.3 | Jackson 3 native (`tools.jackson`) | Done |

## Связанные документы

- [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) — REQ-PF детали
- [APPLICATIONS.md](APPLICATIONS.md) — deploy API
- [DEPLOYMENT.md](DEPLOYMENT.md) — prod topology
- [SECURITY.md](SECURITY.md) — auth/RBAC
