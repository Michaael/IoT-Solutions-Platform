# Спецификация для разработчика платформы ISPF

**Цель:** реализовать в **iot-solutions-platform-framework** generic-слой, чтобы приложение **terminal / michaael** (P-301, SCR-00…07) работало **только через deploy API**, без:

- Java-кода отрасли в `ispf-server` (`plugin/terminal`, industry BFF);
- Flyway-миграций таблиц приложения в platform repo;
- дополнительных **отраслевых** plugin-JAR от команды проекта.

**Команда проекта** после закрытия backlog деплоит bundle из репозитория michaael (`deploy/terminal/`) и **не меняет** исходники platform.

**Решение проекта:** ADR-0008, ADR-0009 (dogfooding через reference app, не industry plugin).

**Аудитория:** разработчики ISPF (framework).  
**Версия платформы (baseline):** `main`, миграция `V10__application_platform.sql`, лицензия Apache 2.0, docs [APPLICATIONS.md](APPLICATIONS.md), [PLUGINS.md](PLUGINS.md).

> Канон в репозитории michaael: `platform/requirements/PLATFORM-DEVELOPER-BACKLOG.md` — при расхождении синхронизировать оба.

---

## 0. Dogfooding: terminal драйвит, platform обобщает

Terminal (P-301) — **reference application** для проверки зрелости ISPF. Развитие platform идёт **через dogfooding**, но с **gate обобщения** (ADR-0009):

```text
Боль из DD terminal  →  REQ-PF (generic)  →  PR в platform main  →  script в michaael deploy  →  smoke
```

### Gate обобщения (обязателен для каждого PR в platform)

| # | Вопрос | Если «нет» |
|:---:|:---|:---|
| 1 | API работает для **любого** `appId`, без `terminal` в Java? | Оставить в michaael bundle |
| 2 | App-команда использует только **deploy REST**, без fork server? | Доработать API |
| 3 | Есть **второй** сценарий (не нефтебаза) на том же API? | Переформулировать абстракцию |

### Запрещено vs правильно

| Запрещено в `main` | Правильное обобщение |
|:---|:---|
| `plugin/terminal`, `Terminal*Handler` | `ApplicationFunctionHandler` + script deploy |
| `/bff/terminal/*` | `/bff/invoke` + wire profile |
| Flyway `dispatch_order` в platform | `applications/{id}/data/migrate` |
| `@Scheduled` ERP import | `platform_schedules` |
| Step `pullOilTerminalOrders` | `selectMany` + `exec` + app SQL |

### Суть platform, которую расширяем (не «модуль MES»)

| Столп ISPF | REQ-PF | Terminal лишь потребляет |
|:---|:---|:---|
| Callable logic on objects | PF-01 script runtime | 17× `terminal_*` scripts |
| App-owned data | PF-02 data layer | schema dispatch/incident |
| Package deploy | PF-03 bundle | `deploy/terminal/` |
| Process + operator tasks | PF-04, PF-10 workflow | P-301 BPMN |
| Cron / integration tick | PF-05 scheduler | ERP feed |
| HMI wire | PF-06 BFF gateway | SCR wire |
| Models survive restart | PF-07 | terminal templates |
| Telemetry → HMI | PF-08, PF-09 bindings/simulator | demo dashboards, meter |

**Критерий зрелости:** второй app (`warehouse`, `appId=wh`) собирается **тем же** API без нового Java в platform.

---

## 1. Definition of Done (platform)

Считаем цель достигнутой, когда выполнены **все** пункты:

1. На чистом `main` ISPF (без `plugin/terminal`, без V6–V9 terminal Flyway) одна команда выполняет:
   ```http
   POST /api/v1/applications/terminal/deploy
   ```
   + REST deploy objects/dashboards/BPMN из bundle michaael.

2. Smoke P-301 e2e проходит: SCR-00…07 через `POST /api/v1/bff/invoke` (Operator Web Console).

3. Все функции `terminal_*` из DD — **script/deploy**, не `FunctionHandler` в platform repo.

4. ERP import по расписанию — `platform_schedules`, не `@Scheduled` в Java.

5. В `main` **нет** кода `terminal`, `oil-terminal`, отраслевых BFF (чеклист `docs/PLUGINS.md`).

6. Документация framework обновлена; smoke-тест `ApplicationPlatformApiTest` расширен до parity terminal.

---

## 2. Архитектурный принцип

```text
┌─────────────────────────────────────────────────────────────┐
│  ISPF platform (framework) — реализует ОДИН РАЗ            │
│  • Application Function Runtime (script engine)             │
│  • Application Data Layer (migrate API)                     │
│  • Bundle deploy, Scheduler, BFF gateway, BPMN hooks      │
│  • Generic simulators / SQL bindings (demo)                 │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ REST deploy (admin)
                              │
┌─────────────────────────────────────────────────────────────┐
│  michaael app bundle — БЕЗ Java в platform                  │
│  • SQL migrations, JSON script functions                    │
│  • objects, dashboards, BPMN, schedules                     │
│  • Web Console → /bff/invoke only                           │
└─────────────────────────────────────────────────────────────┘
```

**Запрещено смешивать:** отраслевой код в `packages/ispf-server/` под видом «временного» reference.

**Допустимо в platform:** только **generic** механизмы (REQ-PF ниже), применимые к любому appId, не только terminal.

---

## 3. Сводная матрица REQ-PF

| ID | Capability | Статус на `main` | Осталось сделать | P |
|:---|:---|:---|:---|:---:|
| **PF-01** | Application Function Runtime | **Частично** | `map`/`buildRecord` (PF-01c), regression parity | P0 |
| **PF-02** | Application Data Layer | **Частично** | Канон terminal migrations в michaael bundle (PF-02c) | P0 |
| **PF-03** | Application Package Deploy | **Частично** | models в bundle (P2) | P1 |
| **PF-04** | BPMN `invoke_function` | **Готово** | Regression tests с app functions | P1 |
| **PF-05** | Platform Scheduler | **Готово** | — | P1 |
| **PF-06** | BFF Wire Gateway | **Частично** | Custom field labels map (P2) | P1 |
| **PF-07** | Model Registry Persistence | **Готово** | — | P2 |
| **PF-08** | Variable ↔ SQL sync | **Нет** | Declarative bindings (§4.5) | P2 |
| **PF-09** | Integration Simulator SPI | **Нет** | Device/simulator profiles (§4.6) | P2 |
| **PF-10** | Workflow cancel | **Частично** | BPMN signal catch (§4.7b, P2) | P1 |
| **PF-11** | Function rollback / versions | **Нет** | Deploy previous version (§4.8) | P2 |

---

## 4. Требования к реализации (детально)

### 4.1 REQ-PF-01 — Application Function Runtime (+ расширения)

**Уже есть (`main`):**

- `POST /api/v1/applications/{appId}/functions/deploy`
- `POST /api/v1/applications/{appId}/deploy` (functions в bundle)
- `ApplicationFunctionHandler` + `FunctionScriptEngine`
- Шаги script: `selectOne`, `exec`, `failIfNull`, `failIfNotEquals`, `return`
- Audit: `function_invoke_audit`
- Invoke без restart: `POST .../functions/invoke`, `POST /api/v1/bff/invoke`

**Нужно доработать (P0 — блокер terminal):**

#### PF-01a — `selectMany` ✅

#### PF-01b — `invoke_function` ✅

#### PF-01c — `map` / `buildRecord`

Построение output row из vars (field mapping без copy-paste SQL).

#### PF-01d — `setVar` / literals

Присвоение констант и простых выражений в vars.

#### PF-01e — транзакция script ✅

Один invoke = одна JDBC transaction (rollback при необработанном exception).

**Acceptance PF-01:**

- [ ] 16 функций `terminal_*` (§6) реализуемы **только** JSON script + SQL.
- [x] Deploy идемпотентен; invalid script → 400 на deploy.
- [x] RBAC как у `functions/invoke`.
- [x] Документация: `docs/APPLICATIONS.md` § Script steps (полный список).

**Не принимается как решение для app-команды:** hot-load **отраслевого** JAR в platform repo.

---

### 4.2 REQ-PF-02 — Application Data Layer

**Уже есть:**

- `POST /api/v1/applications` — register `appId`, `tablePrefix`
- `POST .../data/migrate` — SQL scripts, idempotent by `(version, script_id)`
- `GET .../data/status`

**Нужно доработать (P0):**

#### PF-02a — изоляция schema ✅

- `schemaName` (default `app_{appId}`) + `SET SCHEMA` / `search_path` на время migrate/invoke/seed.
- Guard DDL: reserved platform tables + `tablePrefix` validation.

#### PF-02b — seed API ✅

```http
POST /api/v1/applications/{appId}/data/seed
{ "profile": "smoke-p301" }
```

Idempotent INSERT для smoke (смена, наряды DO-2026-*, tanks).

#### PF-02c — канон terminal

Миграции **не** в Flyway platform — только через app deploy. Таблицы по канону michaael:

`dispatch_order`, `tank_balance`, `sample`, `incident` (+ поля weighbridge, erp_ref).

**Acceptance PF-02:**

- [ ] Fresh ISPF + app deploy → schema terminal без platform Flyway V6–V9.
- [x] Повтор migrate/seed — no duplicate rows.
- [x] Две app schema не пересекаются.

---

### 4.3 REQ-PF-03 — Application Package Deploy

**Уже есть:** bundle `migrations` + `functions` + `schedules`.

**Нужно (P1):**

Расширить `BundleManifest`:

```json
{
  "version": "1.0.0",
  "objects": [ { "parentPath", "name", "type", "displayName", "functions": [...] } ],
  "dashboards": [ { "path", "title", "layoutJson" } ],
  "workflows": [ { "path", "bpmnXml", "status" } ],
  "models": [ ... ]
}
```

- Идempotent deploy каждой секции.
- Ответ: `{ applied, skipped, errors }` по секциям.
- **PF-03b (P2):** rollback bundle version.

**Acceptance:** один `POST .../deploy` поднимает terminal metadata + logic.

---

### 4.4 REQ-PF-06 — BFF Wire Gateway

**Уже есть:** `POST /api/v1/bff/invoke` → `{ error_code, error_message, result }`.

**Нужно (P1):**

#### PF-06a — wire profile `anima-operator-v1` ✅

По канону michaael `core/front-back-contract.md`:

| Правило | Реализация |
|:---|:---|
| Успех | `error_code === "OK"`, `error_message === ""` |
| Ошибка | фронт не читает `result` |
| Таблица | `result` = массив строк напрямую |
| Подписи | `result_field_labels` из descriptor функции или profile map |

```http
POST /api/v1/bff/invoke
{ "wireProfile": "anima-operator-v1", ... }
```

**Acceptance:** Web Console operator без `/api/v1/bff/terminal/*`; в `main` — manifest shell + generic invoke.

---

### 4.5 REQ-PF-08 — Variable ↔ SQL / Event Sync

**Нужно (P2 — demo dashboards):**

Declarative binding на object variable:

```json
{
  "objectPath": "root.platform.terminal.demo.dispatch-summary",
  "variable": "activeOrders",
  "binding": {
    "type": "sql",
    "appId": "terminal",
    "query": "SELECT COUNT(*) AS value FROM dispatch_order WHERE status IN (...)",
    "refresh": "on_schedule|on_function_success"
  }
}
```

**Acceptance:** legacy dashboard KPI без Java `JdbcDemoSyncService`.

---

### 4.6 REQ-PF-09 — Integration Simulator SPI

**Нужно (P2 — demo SCR-03/07):**

Расширение **virtual driver** или object profile `simulator`:

| Signal | Поведение |
|:---|:---|
| Meter | `litersPerSecond`, elapsed from `filling` status |
| Weighbridge | `tareKg + actualLiters * density` |
| Gas / grounding | boolean по rackId |

Deploy profile из app bundle; prod — замена на real driver **без** смены function contract.

**Acceptance:** snapshot meter и suggested gross weight без Java imitator.

---

### 4.7 REQ-PF-10 — Workflow Signal / Cancel

**Уже есть:**

- `POST /api/v1/workflows/instances/{instanceId}/cancel`
- `workflow_cancel_journal`
- `WorkflowInstanceCancelService.cancelWaitingByWorkflowPath` (internal)

**Нужно (P1):**

#### PF-10a — script step `cancel_workflows` ✅

```json
{
  "type": "cancel_workflows",
  "workflowPath": "root.platform.terminal.workflows.p301-auto-shipment",
  "statusIn": ["WAITING", "RUNNING"],
  "reason": "incident",
  "detail": { "incidentId": "${input.incidentId}" }
}
```

#### PF-10b (P2) — BPMN signal catch

`intermediateCatchEvent` + `ispf:signal` для P-301 (опционально после cancel API).

**Acceptance:** SCR-06 incident → WAITING P-301 instances → FAILED/CANCELLED из script `terminal_incident_register`.

---

### 4.8 REQ-PF-11 — Function versioning & rollback (P2)

```http
GET  /api/v1/applications/{appId}/functions?objectPath=&name=
POST /api/v1/applications/{appId}/functions/rollback
{ "objectPath", "functionName", "version": "2" }
```

---

## 5. Уже реализовано — не дублировать, только поддерживать

| Компонент | Где |
|:---|:---|
| BPMN `invoke_function` | `WorkflowActionType.INVOKE_FUNCTION`, `docs/WORKFLOWS.md` |
| Scheduler | `PlatformSchedulerService`, `platform_schedules`, `/api/v1/schedules` |
| Model persist | `model_definitions`, restore on startup |
| Generic BFF invoke | `BffController` `/api/v1/bff/invoke` |
| Platform tables V10 | `V10__application_platform.sql` |
| Smoke baseline | `ApplicationPlatformApiTest` |

---

## 6. Каталог функций terminal (must-have script)

Все deploy на objects по `platform_structure.md` / DD michaael.

| Функция | Object path (канон) | Слой | Зависит от |
|:---|:---|:---|:---|
| `terminal_dispatchWorkbench_listActiveOrders` | `…front.dispatchWorkbench` | BFF | PF-01a, PF-02 |
| `terminal_dispatchWorkbench_listReferenceData` | `…front.dispatchWorkbench` | BFF | PF-01a |
| `terminal_dispatchOrder_assignTank` | `…terminal.dispatchOrder` | core | PF-01, PF-02 |
| `terminal_dispatchOrder_startFilling` | `…terminal.dispatchOrder` | core | PF-01b, PF-02 |
| `terminal_dispatchOrder_complete` | `…terminal.dispatchOrder` | core | PF-01, PF-02 |
| `terminal_dispatchOrder_recordWeighbridge` | `…terminal.dispatchOrder` | core | PF-01, PF-09 |
| `terminal_dispatchOrder_close` | `…terminal.dispatchOrder` | core | PF-01b, PF-02 |
| `terminal_qualityGate_assertTankApproved` | `…config.qualityGate` | config | PF-01, PF-02 |
| `terminal_qualityGate_listTankStatus` | `…config.qualityGate` | config | PF-01a, PF-02 |
| `terminal_erpGateway_pullDispatchOrders` | `…config.erpGateway` | config | PF-01, PF-02 |
| `terminal_erpGateway_pullNextDispatchOrder` | `…config.erpGateway` | config | PF-01, PF-05 |
| `terminal_erpGateway_postDispatchFact` | `…config.erpGateway` | config | PF-01 (stub OK) |
| `terminal_incident_register` | `…terminal.incident` | core | PF-01, PF-10a |
| `terminal_incident_close` | `…terminal.incident` | core | PF-01, PF-02 |
| `terminal_shiftDashboard_getShiftSummary` | `…front.shiftDashboard` | BFF | PF-01a, PF-02 |
| `terminal_tankMonitoring_getSummary` | `…terminal.tank` | core | PF-01a, PF-02 |
| `terminal_tankMonitoring_getDetail` | `…terminal.tank` | core | PF-01, PF-02 |

Workflow P-301: service tasks → `invoke_function` (PF-04). User tasks → work queue.

---

## 7. Operator UI (контракт для platform)

Web Console (framework `apps/web-console`) после закрытия PF-06:

- **Только** `POST /api/v1/bff/invoke` + `wireProfile: anima-operator-v1`.
- **Удалить** маршруты `/api/v1/bff/terminal/**` (если вернутся в reference-ветке).
- SCR-00…07: список invoke mapping — в bundle michaael `deploy/terminal/bff-map.json` (app), generic gateway (platform).
- В `main`: manifest shell (`?mode=operator&app=<appId>`), без отраслевых SCR-компонентов.

Platform **не** знает про SCR-01/02 — только wire + invoke.

---

## 8. Smoke checklist (platform QA)

Выполнить на H2/PostgreSQL без industry Java:

| # | Step | Expected |
|:---:|:---|:---|
| 1 | `POST /applications` terminal | 200 |
| 2 | `POST .../deploy` full bundle | status OK |
| 3 | `GET .../data/status` | migrations applied |
| 4 | `POST /bff/invoke` listActiveOrders | error_code OK, result[] |
| 5 | assign → startFilling → complete | status transitions in DB |
| 6 | weighbridge + close | order closed, erp stub |
| 7 | `POST /schedules` erp feed | new orders appear |
| 8 | `POST .../workflows/.../run` P-301 | instance progresses |
| 9 | incident_register | order cancelled, workflows cancelled |
| 10 | Web Console SCR-00…07 manual | no 404 on BFF |

Automate: расширить `ApplicationPlatformApiTest` → `TerminalAppParityTest` (optional `@Tag("terminal-parity")`).

---

## 9. Приоритет реализации (roadmap)

```text
Sprint A (P0) — без этого app не стартует
  PF-01a selectMany
  PF-01b invoke_function
  PF-01e transaction
  PF-02a schema isolation
  PF-02b seed API

Sprint B (P1) — e2e P-301
  PF-06a wire profile
  PF-03 objects/dashboards/workflows in bundle
  PF-10a cancel_workflows script
  Web Console → generic bff ✅ (manifest shell)

Sprint C (P2) — demo parity
  PF-08 SQL bindings
  PF-09 simulator SPI
  PF-11 function rollback

Sprint D (P2) — polish
  PF-03b bundle rollback
  PF-10b BPMN signals
```

---

## 10. Explicitly out of scope (команда проекта michaael)

| Артефакт | Кто |
|:---|:---|
| DD, UC/US, канон БД | michaael |
| JSON script bodies функций | michaael `deploy/terminal/functions/` |
| SQL migrations content | michaael |
| BPMN XML P-301 | michaael |
| Dashboard layout SCR | michaael |
| `live_implementation.md`, smoke scripts | michaael |
| Operator UI паспорта SCR | michaael `core/operator_ui/` |

Platform **не** содержит бизнес-правил нефтебазы — только исполнение deploy.

---

## 11. Связанные документы

| Документ | Репозиторий |
|:---|:---|
| ADR-0008 app/platform boundary | michaael `adr/0008-terminal-app-on-platform-only.md` |
| Детальные REQ-PF-01…10 | michaael `platform/requirements/REQ-PF-*.md` |
| Platform apps API | [APPLICATIONS.md](APPLICATIONS.md) |
| Platform developer backlog | этот файл |
| PLUGINS / main hygiene | [PLUGINS.md](PLUGINS.md) |
| DD P-301 | michaael `domain-services/terminal/scenarios/p301-auto-truck-shipment/DD.md` |
| Wire contract | michaael `core/front-back-contract.md` |

---

## 12. История изменений

| Дата | Изменение |
|:---|:---|
| 2026-06-19 | Первая consolidated версия: статус `main` + gap PF-01a…11 |
| 2026-06-19 | Синхронизация в framework `docs/`; baseline Apache 2.0 |
