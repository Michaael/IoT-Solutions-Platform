# Спецификация для разработчика платформы ISPF

**Цель:** generic-слой в **iot-solutions-platform-framework**, чтобы прикладные решения наполняли **механизмы платформы** (модели, переменные, события, функции, workflow) через deploy API, без:

- отраслевого Java-кода в `ispf-server`;
- Flyway-миграций таблиц приложений в platform repo;
- дополнительных industry plugin-JAR в дереве `main`.

**Решение проекта:** ADR-0008, ADR-0009 (app/platform boundary, dogfooding через reference apps вне `main`).

**Аудитория:** разработчики ISPF (framework).  
**Версия платформы (baseline):** `main`, миграция `V10__application_platform.sql`, лицензия Apache 2.0, docs [APPLICATIONS.md](APPLICATIONS.md), [PLUGINS.md](PLUGINS.md).  
**Дополнительные требования platform (REQ-FW):** **§12**; сводная матрица — **§3**; Sprint E–G — **§8**.

---

## 0. Основной принцип и обобщение

### 0.1 Бизнес-логика в механизмах платформы

**North star ISPF:** бизнес-логика решения живёт **на платформе** — в моделях, переменных, событиях, функциях и workflow **дерева объектов**, а не в отраслевом Java сервера.

| Слой | Содержит | Не содержит |
| ---- | -------- | ----------- |
| **Platform (framework, `main`)** | Generic-движки: CEL, bindings, historian, BPMN, script runtime, drivers, deploy API | Правила конкретной отрасли («если T > 80 → эскалация») |
| **Solution (bundle / конфигурация)** | Модели, пороги, BPMN, script-функции, объекты в `root.platform.*` | Custom Java в `ispf-server`, hardcoded BFF routes |

REQ-PF и bundle deploy — способ **загрузить** declarative-конфигурацию в платформу, а не параллельная архитектура. После deploy логика исполняется движками ISPF на узлах object tree.

Критерий нового REQ-PF: *можно ли выразить потребность через существующий или обобщённый механизм дерева объектов?* Если да — расширяем механизм; если нет — gate ADR-0009.

Подробнее: [ARCHITECTURE.md § Основной принцип](ARCHITECTURE.md#основной-принцип-бизнес-логика-в-механизмах-платформы).

### 0.2 Принцип обобщения (dogfooding)

Развитие platform идёт через **dogfooding** с обязательным gate (ADR-0009):

```text
Потребность app-команды  →  REQ-PF (generic)  →  PR в platform main  →  bundle deploy  →  smoke
```

### Gate обобщения (обязателен для каждого PR в platform)


| #   | Вопрос                                                           | Если «нет»                   |
| --- | ---------------------------------------------------------------- | ---------------------------- |
| 1   | Потребность выражается через **механизм object tree** (или обобщённый REQ-PF), без отраслевых имён в Java? | Оставить в declarative-конфигурации решения |
| 2   | App-команда использует только **deploy REST**, без fork server?  | Доработать API               |
| 3   | Есть **второй** сценарий на том же API?                          | Переформулировать абстракцию |


### Запрещено vs правильно


| Запрещено в `main`            | Правильное обобщение                         |
| ----------------------------- | -------------------------------------------- |
| Industry `*Handler` в server  | `ApplicationFunctionHandler` + script deploy |
| Отраслевые BFF routes         | `/bff/invoke` + wire profile                 |
| App tables в platform Flyway  | `applications/{id}/data/migrate`             |
| `@Scheduled` import jobs      | `platform_schedules`                         |
| Hardcoded domain script steps | `selectMany` + `exec` + app SQL              |


### Суть platform (не «модуль MES»)


| Столп ISPF                | REQ-PF                          | Потребитель       |
| ------------------------- | ------------------------------- | ----------------- |
| Callable logic on objects | PF-01 script runtime            | app functions     |
| App-owned data            | PF-02 data layer                | app schema        |
| Package deploy            | PF-03 bundle                    | app manifest      |
| Process + operator tasks  | PF-04, PF-10 workflow           | app BPMN          |
| Cron / integration tick   | PF-05 scheduler                 | app schedules     |
| HMI wire                  | PF-06 BFF gateway               | operator dashboards |
| Models survive restart    | PF-07                           | app models        |
| Telemetry → HMI           | PF-08, PF-09 bindings/simulator | demo dashboards   |


**Критерий зрелости:** второй app (`warehouse`, `appId=wh`) собирается **тем же** API без нового Java в platform.

---

## 1. Definition of Done (platform)

Считаем REQ-PF закрытым на `main`, когда:

1. `POST /api/v1/applications/{appId}/deploy` поднимает metadata + migrations + functions + objects/dashboards/workflows из bundle.
2. Operator UI — дашборды из дерева (`operatorUi` / `dashboards[]` в bundle, `GET .../operator-ui`); BFF — для виджетов/функций app, без отраслевых маршрутов в server.
3. App-функции — **script/deploy**, не `FunctionHandler` в platform repo.
4. Расписания — `platform_schedules`, не `@Scheduled` в Java.
5. В `main` **нет** отраслевого кода и BFF (чеклист [PLUGINS.md](PLUGINS.md)).
6. `ApplicationPlatformApiTest` покрывает generic platform API.

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
│  App bundle (вне `main`) — БЕЗ Java в platform               │
│  • SQL migrations, JSON script functions                    │
│  • objects, dashboards, BPMN, schedules, operator manifest  │
│  • Web Console → /bff/invoke only                           │
└─────────────────────────────────────────────────────────────┘
```

**Запрещено смешивать:** отраслевой код в `packages/ispf-server/` под видом «временного» reference.

**Допустимо в platform:** только **generic** механизмы (REQ-PF ниже), применимые к любому `appId`.

---

## 3. Сводная матрица REQ-PF


| ID        | Capability                   | Статус на `main` | Осталось сделать                                | P   |
| --------- | ---------------------------- | ---------------- | ----------------------------------------------- | --- |
| **PF-01** | Application Function Runtime | **Готово**       | — (§4.1 acceptance закрыт v0.2.0)               | P0  |
| **PF-02** | Application Data Layer       | **Готово**       | —                                               | P0  |
| **PF-03** | Application Package Deploy   | **Готово**       | — (deprecation path в [APPLICATIONS.md](APPLICATIONS.md), v0.3.0) | P1  |
| **PF-04** | BPMN `invoke_function`       | **Готово**       | —                                               | P1  |
| **PF-05** | Platform Scheduler           | **Готово**       | —                                               | P1  |
| **PF-06** | BFF Wire Gateway             | **Готово**       | —                                               | P1  |
| **PF-07** | Model Registry Persistence   | **Готово**       | — (inheritance, version, bulk upgrade API)      | P2  |
| **PF-08** | Variable ↔ SQL sync          | **Готово**       | — (`on_event`, typed values, `sqlBinding()`)    | P2  |
| **PF-09** | Integration Simulator SPI    | **Готово**       | — (virtual profiles bundle + acceptance v0.3.0) | P2  |
| **PF-10** | Workflow cancel + signal     | **Готово**       | —                                               | P1  |
| **PF-11** | Function rollback / versions | **Готово**       | — (API + Web Console deploy tab v0.3.0)         | P2  |
| **PF-12** | Application SQL reports      | **Готово**       | Tree-first reports + YARG PDF/XLSX export (Phase 12–13) | P2  |
| **PF-12b** | Reports product polish    | **Готово**       | Report Builder UX, dashboard widget params/exports, tree-variables UI, agent tools | P2  |
| **PF-14** | Device driver catalog        | **Готово**       | 58 `driverId` в `main` (§10)                    | P3+ |

### Дополнительные требования (REQ-FW)

Детали и acceptance — **§12**.

| ID         | Capability                         | Статус      | Track | P   |
| ---------- | ---------------------------------- | ----------- | ----- | --- |
| **FW-01**  | ADR `docs/decisions/`              | **Done** | DOC   | P2  |
| **FW-02**  | Platform gap-registry              | **Done** | DOC   | P2  |
| **FW-10**  | RSA licensing (commercial bundle)  | **Done** | LIC   | P2  |
| **FW-11**  | `installationId` + LicenseBuilder  | **Done** | LIC   | P2  |
| **FW-12**  | Bundle dependency manifest         | **Done** | LIC   | P3  |
| **FW-20**  | MES reference walkthrough + demo   | **Done** | REF   | P2  |
| **FW-30**  | Solution developer public API doc  | **Done** | API   | P2  |
| **FW-31**  | Event catalog в bundle             | **Done** | API   | P3  |
| **FW-32**  | Event bus vs sync RPC (контракт)   | **Done** | NET   | P3  |
| **FW-40**  | AI Layer: `LlmProvider` SPI        | **Done** | AI    | P3+ |
| **FW-41**  | ContextPack для LLM                | **Done** | AI    | P3+ |
| **FW-42**  | ToolRegistry (validate/deploy)     | **Done** | AI    | P3+ |
| **FW-43**  | Platform Studio / AI copilot UI  | **Done** | AI    | P3+ |
| **FW-44**  | Tree-first agent (sessions, tools, reliability) | **Done** | AI | P3+ |
| **FW-45**  | Platform knowledge for agent (briefing + tools) | **Done** | AI | P3+ |
| **FW-46**  | Agent action tools (invoke, search, events, models) | **Done** | AI | P3+ |
| **FW-47**  | Agent discovery tools (functions, events, schemas) | **Done** | AI | P3+ |
| **FW-48**  | Agent automation tools (alert, correlator, operator UI, bindings) | **Done** | AI | P3+ |
| **FW-50**  | Licensed driver JAR contract       | **Done** | DRV   | P3+ |


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

**Нужно доработать (P2):**

#### PF-01a — `selectMany` ✅

#### PF-01b — `invoke_function` ✅

#### PF-01c — `map` / `buildRecord`

Построение output row из vars (field mapping без copy-paste SQL).

#### PF-01d — `setVar` / literals

Присвоение констант и простых выражений в vars.

#### PF-01e — транзакция script ✅

Один invoke = одна JDBC transaction (rollback при необработанном exception).

**Acceptance PF-01:**

- [x] Типовой набор app-функций реализуем **только** JSON script + SQL (без Java в platform).
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
{ "profile": "smoke-demo" }
```

Idempotent INSERT для smoke (`demo_category`, `demo_item`, `demo_metric`).

#### PF-02c — миграции в app bundle ✅

Миграции **не** в Flyway platform — только через `POST .../data/migrate` / bundle `migrations[]`.

**Acceptance PF-02:**

- [x] Fresh ISPF + app deploy → изолированная app schema.
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

**Acceptance:** один `POST .../deploy` поднимает app metadata + logic.

---

### 4.4 REQ-PF-06 — BFF Wire Gateway

**Уже есть:** `POST /api/v1/bff/invoke` → `{ error_code, error_message, result }`.

**Нужно (P1):**

#### PF-06a — wire profile `anima-operator-v1` ✅

По контракту wire profile `anima-operator-v1` (см. [APPLICATIONS.md](APPLICATIONS.md)):


| Правило | Реализация                                                  |
| ------- | ----------------------------------------------------------- |
| Успех   | `error_code === "OK"`, `error_message === ""`               |
| Ошибка  | фронт не читает `result`                                    |
| Таблица | `result` = массив строк напрямую                            |
| Подписи | `result_field_labels` из descriptor функции или profile map |


```http
POST /api/v1/bff/invoke
{ "wireProfile": "anima-operator-v1", ... }
```

**Acceptance:** Web Console operator — manifest shell + `POST /api/v1/bff/invoke` (без отраслевых BFF routes в server).

---

### 4.5 REQ-PF-08 — Variable ↔ SQL / Event Sync

**Нужно (P2 — demo dashboards):**

Declarative binding на object variable:

```json
{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variable": "readyCount",
  "binding": {
    "type": "sql",
    "appId": "myapp",
    "query": "SELECT COUNT(*) AS value FROM demo_item WHERE status = 'ready'",
    "refresh": "on_schedule|on_function_success"
  }
}
```

**Acceptance:** legacy dashboard KPI без Java `JdbcDemoSyncService`.

---

### 4.6 REQ-PF-09 — Integration Simulator SPI

**Нужно (P2 — demo dashboards / virtual driver):**

Расширение **virtual driver** или object profile `simulator`:


| Signal          | Поведение                                        |
| --------------- | ------------------------------------------------ |
| Meter           | `litersPerSecond`, elapsed from `filling` status |
| Weighbridge     | `tareKg + actualLiters * density`                |
| Gas / grounding | boolean по rackId                                |


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
  "workflowPath": "root.platform.myapp.workflows.main",
  "statusIn": ["WAITING", "RUNNING"],
  "reason": "incident",
  "detail": { "incidentId": "${input.incidentId}" }
}
```

#### PF-10b (P2) — BPMN signal catch ✅

`intermediateCatchEvent` + `ispf:signal` — движок, API `POST .../instances/{id}/signal` и `POST .../workflows/signal`.

**Acceptance:** экземпляр в `WAITING` на signal catch продолжается после доставки сигнала.

---

### 4.8 REQ-PF-11 — Function versioning & rollback (P2)

```http
GET  /api/v1/applications/{appId}/functions?objectPath=&name=
POST /api/v1/applications/{appId}/functions/rollback
{ "objectPath", "functionName", "version": "2" }
```

---

## 5. Уже реализовано — не дублировать, только поддерживать


| Компонент              | Где                                                                   |
| ---------------------- | --------------------------------------------------------------------- |
| BPMN `invoke_function` | `WorkflowActionType.INVOKE_FUNCTION`, `docs/WORKFLOWS.md`             |
| Scheduler              | `PlatformSchedulerService`, `platform_schedules`, `/api/v1/schedules` |
| Model persist          | `model_definitions`, restore on startup                               |
| Generic BFF invoke     | `BffController` `/api/v1/bff/invoke`                                  |
| Platform tables V10    | `V10__application_platform.sql`                                       |
| Smoke baseline         | `ApplicationPlatformApiTest`                                          |


---

## 6. Operator UI (контракт для platform)

Web Console (`apps/web-console`):

- В `main`: dashboard shell (`?mode=operator&app=<appId>`), навигация в `operatorUi` (дашборды `DASHBOARD` из дерева).
- Platform **не** знает про конкретные экраны app — только deploy bundle, dashboards API и generic BFF invoke.

---

## 7. Smoke checklist (platform QA)

Выполнить на H2/PostgreSQL без industry Java:


| #   | Step                              | Expected                   |
| --- | --------------------------------- | -------------------------- |
| 1   | `POST /applications`              | 200                        |
| 2   | `POST .../deploy` bundle          | status OK                  |
| 3   | `GET .../data/status`             | migrations applied         |
| 4   | `POST /bff/invoke` list function  | error_code OK, result      |
| 5   | `POST .../data/seed` smoke-demo   | idempotent applied/skipped |
| 6   | `POST /schedules` invoke_function | tick fires                 |
| 7   | `POST .../workflows/.../run`      | instance progresses        |
| 8   | `POST .../workflows/.../signal`   | WAITING instance resumes   |
| 9   | `POST .../deploy/rollback`        | previous bundle restored   |
| 10  | Operator manifest UI              | `bff/invoke` без 404       |


Automate: `ApplicationPlatformApiTest`, `WorkflowSignalApiTest`.

---

## 8. Приоритет реализации (roadmap)

```text
Sprint A (P0) — без этого app не стартует
  PF-01a selectMany
  PF-01b invoke_function
  PF-01e transaction
  PF-02a schema isolation
  PF-02b seed API

Sprint B (P1) — operator wire + bundle
  PF-06a wire profile
  PF-03 objects/dashboards/workflows in bundle
  PF-10a cancel_workflows script
  Web Console → generic bff ✅ (manifest shell)

Sprint C (P2) — demo parity
  PF-08 SQL bindings ✅
  PF-09 simulator SPI ✅ (virtual profiles)
  PF-11 function rollback ✅

Sprint D (P2) — polish
  PF-03b bundle rollback ✅
  ApplicationPlatformApiTest (generic platform API) ✅
  PF-10b BPMN signals ✅

Post-PF (P3+) — platform evolution (см. §8.1, §9, §10)
  Phase 5 — усиление механизмов object tree (north star)
  PF-13 distributed topology & object federation (vision)
  PF-14 device driver catalog — 62 кандидата (§10)

Post-PF (P2–P3+) — REQ-FW (§12)
  Sprint E (P2) — документация и коммерция
    FW-01 ADR docs/decisions/
    FW-02 gap-registry
    FW-10 RSA license verify on bundle deploy
    FW-11 installationId + LicenseBuilder
    FW-20 MES reference walkthrough + synthetic demo
    FW-30 solution developer public API boundary
  Sprint F (P3) — контракты и шины
    FW-31 event catalog in bundle
    FW-32 event bus vs sync RPC contract
    FW-12 bundle dependency manifest
  Sprint G (P3+) — AI и licensed drivers
    FW-40…43 AI Development Layer + Studio
    FW-50 licensed driver JAR contract
```

### 8.1 Усиление механизмов (Phase 5)

Приоритеты развития platform после закрытия REQ-PF baseline. Критерий каждого REQ: *логика остаётся в механизмах object tree* (§0.1). Roadmap: [ROADMAP.md § Phase 5](ROADMAP.md#phase-5--усиление-механизмов-north-star).

#### 5.1 Модели

| Тема | Цель | Кандидаты REQ |
| ---- | ---- | ------------- |
| **Bindings** | Больше platform functions, композиция CEL + bindings в модели | Расширение [BINDINGS.md](BINDINGS.md), `ModelBindingDefinition` |
| **Наследование** | Базовая модель + расширение (override переменных, добавление событий) | `extendsModelId`, merge при apply |
| **Версионирование** | Эволюция шаблона без поломки экземпляров | `modelVersion`, migration apply, diff в UI |

**Acceptance:** типовой семейный датчик (base + vendor extension) создаётся из моделей без дублирования blueprint; смена версии модели — controlled upgrade экземпляров.

**Stateful bindings (Phase 10.1, Done):** stateful platform bindings (`hysteresis`, `deadband`, `movingAvg`, `counterRate`, …) персистятся в `@bindingState` на объекте — см. [BINDINGS.md](BINDINGS.md) §Stateful bindings.

#### 5.2 Функции

| Тема | Цель | Связь |
| ---- | ---- | ----- |
| **Script steps** | `setVar`, literals, условные ветки без SQL | PF-01d; полный список в [APPLICATIONS.md](APPLICATIONS.md) |
| **Declarative SQL bindings** | Синхронизация variable ↔ app table без imperative script | PF-08 §4.5 |
| **Меньше custom code** | Типовой CRUD/отчёт/интеграция — только JSON script + SQL | Acceptance PF-01 |

**Acceptance:** reference app (`warehouse-app`) не требует новых Java steps в platform для типовых сценариев.

#### 5.3 События + correlators

| Тема | Цель | Связь |
| ---- | ---- | ----- |
| **Паттерны** | Окна времени, пороги COUNT за interval, SEQUENCE с gap | Расширение `CorrelatorPatternType` |
| **Цепочки** | Correlator → workflow → fire event → следующий correlator без Java | Узлы `CORRELATOR` + `WORKFLOW` в дереве |
| **Alert rules** | Составные условия, hysteresis, rate limit | CEL + переменные состояния на `ALERT` |

**Acceptance:** сценарий «N событий за T → эскалация → user task» конфигурируется в дереве, без listener в Java.

#### 5.4 Workflow

| Тема | Цель | Связь |
| ---- | ---- | ----- |
| **Service tasks** | Fire event, read/write variable, invoke function, start child workflow | `ispf:action` в [WORKFLOWS.md](WORKFLOWS.md) |
| **Platform primitives** | Новые actions — generic hooks, не industry plugins | `ispf-plugin-workflow` |
| **Не plugins** | Отраслевой шаг = script function + `INVOKE_FUNCTION`, не JAR | §0.1, [PLUGINS.md](PLUGINS.md) |

**Acceptance:** BPMN demo-alarm-handler и app-процессы используют только встроенные ISPF actions + `INVOKE_FUNCTION`.

#### 5.5 Bundle / application layer

| Было (анти-паттерн) | Цель (north star) |
| ------------------- | ----------------- |
| Application = параллельный runtime | Application = реестр + `app_{id}` schema; логика на узлах `root.platform.*` |
| Bundle = отдельный «мир» API | Bundle = manifest: objects, models, dashboards, workflows, functions → object tree |
| BFF routes под отрасль | `/bff/invoke` + wire profile; данные из variables / script |

**Acceptance:** после `POST .../deploy` все артеfactы решения адресуются через object tree API; `applications` table — metadata и schema isolation only.

---

## 9. Распределённая архитектура и федерация (roadmap, P3+)

**REQ-PF-13** — spike **Done** в `main` (peers, proxy read/write, catalog sync, WS fan-out). **Phase 7 Done:** auth auto-refresh (`SERVICE_ACCOUNT`), outbound WebSocket tunnel для NAT edge (см. [FEDERATION.md](FEDERATION.md)).

**REQ-PF-13b acceptance (Phase 7):**

1. Peer с service account автоматически обновляет token до expiry; после simulated expiry связь восстанавливается без ручного вмешательства.
2. Local edge за NAT подключается только исходящим WS; remote hub видит federated catalog и выполняет read/write без inbound доступа к local.
3. Reconnect после разрыва WS ≤ 30s (exponential backoff agent-side).

**REQ-PF-13c acceptance (Phase 8 — federation bind):**

1. Admin bind существующего local DEVICE к remote через loopback/tunnel peer — в Explorer локальный path, `federated: true`, remote metadata.
2. `GET /api/v1/objects/by-path?path=<local>` возвращает `path` = local; variable write проксируется на remote.
3. Rebind меняет `remotePath` без смены local path; unbind снимает overlay, local shell остаётся.
4. Create+bind и «Разместить локально» из sync mirror; документация в [FEDERATION.md](FEDERATION.md).

### Текущее состояние


| Аспект                                    | Сейчас                                                                                                               |
| ----------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Путь объекта (`root.platform.devices...`) | Локальный идентификатор узла в **одном** экземпляре ISPF                                                             |
| `root.platform`                           | Namespace (область) внутри дерева, **не** hostname и не URL другого сервера                                          |
| Горизонтальное масштабирование            | Несколько реплик `ispf-server` + общая PostgreSQL — **одно** дерево, те же пути ([ARCHITECTURE.md](ARCHITECTURE.md)) |
| Типы `TENANT`, `AGENT`                    | Зарезервированы в модели ([OBJECT_MODEL.md](OBJECT_MODEL.md)), без runtime federation                                |


Запрос `GET /api/v1/objects/by-path?path=...` всегда обслуживается **тем сервером**, к которому подключена консоль.

### Целевые сценарии (кандидаты на дизайн)

```text
┌──────────────────────────────────────────────────────────────┐
│  Центральная консоль / catalog                               │
│  Единое дерево навигации (логическое, не обязательно одна БД)│
└────────────┬─────────────────────────────┬───────────────────┘
             │                             │
     ┌───────▼────────┐            ┌───────▼────────┐
     │  ISPF site A   │            │  ISPF site B   │
     │  root.platform │            │  root.platform │
     │  (локальное    │            │  (edge agent)  │
     │   дерево)      │            │                │
     └────────────────┘            └────────────────┘
```


| Сценарий                        | Идея пути / адресации                                          | Примечание                                                            |
| ------------------------------- | -------------------------------------------------------------- | --------------------------------------------------------------------- |
| **Multi-tenant** (один кластер) | `root.tenant.{tenantId}.devices...`                            | Логическое разделение арендаторов, не отдельный HTTP-host             |
| **Edge / site**                 | Узел типа `AGENT` или `root.platform.sites.{siteId}`           | Локальное дерево на площадке, синхронизация каталога наверх           |
| **Federation**                  | Proxy-узел в дереве + метаданные `remoteBaseUrl`, `remotePath` | Консоль резолвит запросы через gateway, dot-path сам по себе не = URL |
| **Cross-server API**            | Явный endpoint peer: `/api/v1/federation/...`                  | Маршрутизация, trust, кэш, WebSocket fan-out — отдельный слой         |


**Принцип:** object path и service endpoint **разделяются**. Путь остаётся стабильным идентификатором в каталоге; адрес удалённого инстанса — в метаданных узла или в federation registry, не в dot-notation.

### Acceptance (когда брать в работу)

1. Документирован контракт federation (proxy object, registry, auth между инстансами).
2. В дереве можно зарегистрировать удалённую площадку; read path проксируется или кэшируется.
3. Web Console показывает federated узлы без hardcoded URL в UI.
4. Горизонтальные реплики (shared DB) и multi-site federation описаны как **разные** топологии в [DEPLOYMENT.md](DEPLOYMENT.md).

### Не в scope PF-13 (явно)

- Замена NATS/MQTT messaging на object-path routing.
- Multi-tenant billing / quota (только дерево и ACL).
- Автоматический merge конфликтующих деревьев без оператора.

---

## 10. Каталог драйверов устройств (roadmap)

**REQ-PF-14** — каталог кандидатов на реализацию в ISPF. **58 зарегистрированных `driverId`** покрывают все 62 строки каталога (часть — через общий драйвер или stub v0.1).

### Границы каталога

- Каталог — целевой перечень протоколов connectivity для ISPF (IT, automation, IoT, structured data).
- **Исключено:** vendor-specific драйверы
- Отраслевые реализации по-прежнему могут идти **вне `main`** ([PLUGINS.md](PLUGINS.md)); таблица ниже — generic-каталог platform.
- Каждый новый драйвер: модуль `ispf-driver-*` + SPI `DeviceDriver` + регистрация в `DriverCatalog` ([DRIVERS.md](DRIVERS.md)).

### Уже в `main` (58 `driverId`)

См. полную таблицу в [DRIVERS.md](DRIVERS.md#каталог-зарегистрированных-драйверов-58).

Ключевые группы: Modbus (tcp/rtu/udp), OPC (ua client/server, da stub, bridge), IEC 104 (client/server), IP Host family + ldap/dhcp/imap/pop3/radius, интеграция (kafka, jms, cwmp, odbc, graph-db, vmware, smi-s), telecom (sip, xmpp, smpp, asterisk, modem-at).


### Кандидаты (62)


| #   | Протокол / технология    | Драйвер (имя)          | ISPF                          |
| --- | ------------------------ | ---------------------- | ----------------------------- |
| 1   | Bash Script              | Application            | ✅ (`application`)             |
| 2   | Asterisk                 | Asterisk               | ✅ (`asterisk`)                |
| 3   | BACnet IP / MS/TP        | BACnet                 | ✅ (`bacnet`, IP)              |
| 4   | CoAP                     | CoAP                   | ✅ (`coap`)                    |
| 5   | CORBA                    | CORBA                  | ✅ (`corba` stub)              |
| 6   | CWMP (TR-069)            | CWMP                   | ✅ (`cwmp`)                    |
| 7   | SQL (JDBC/ODBC)          | Database               | ✅ (`jdbc`, `odbc`)            |
| 8   | DLMS/COSEM               | DLMS/COSEM             | ✅ (`dlms`)                    |
| 9   | DNP3                     | DNP3                   | ✅ (`dnp3` connectivity)       |
| 10  | Ethernet/IP (CIP)        | Ethernet/IP            | ✅ (`ethernet-ip` stub)        |
| 11  | File System              | File                   | ✅ (`file`)                    |
| 12  | TCP/UDP, Serial          | Flexible Driver        | ✅ (`flexible`)                |
| 13  | File System              | Folder                 | ✅ (`folder`)                  |
| 14  | GPS/GLONASS, M2M         | GPS Tracker            | ✅ (`gps-tracker`)             |
| 15  | Gremlin / TinkerPop      | Graph Database         | ✅ (`graph-db`)                |
| 16  | HTTP/HTTPS               | HTTP                   | ✅ (`http`)                    |
| 17  | HTTP/HTTPS               | HTTP Server            | ✅ (`http-server`)             |
| 18  | IEC 60870-5-104          | IEC 60870-5-104        | ✅ (`iec104`)                  |
| 19  | IEC 60870-5-104          | IEC 60870-5-104 Server | ✅ (`iec104-server`)           |
| 20  | HTTP/HTTPS               | IP Host (web)          | ✅ (`ip-host` HTTP)            |
| 21  | ICMP                     | IP Host (ping)         | ✅ (`icmp`, `ip-host`)         |
| 22  | LDAP                     | IP Host (LDAP)         | ✅ (`ldap`)                    |
| 23  | DHCP                     | IP Host (DHCP)         | ✅ (`dhcp`)                    |
| 24  | DNS                      | IP Host (DNS)          | ✅ (`ip-host` DNS)             |
| 25  | FTP                      | IP Host (FTP)          | ✅ (`ip-host` FTP)             |
| 26  | IMAP                     | IP Host (IMAP)         | ✅ (`imap`)                    |
| 27  | POP3                     | IP Host (POP3)         | ✅ (`pop3`)                    |
| 28  | RADIUS                   | IP Host (Radius)       | ✅ (`radius`)                  |
| 29  | SMB/CIFS                 | IP Host (SMB)          | ✅ (`smb`, `ip-host` частично) |
| 30  | SMTP                     | IP Host (SMTP)         | ✅ (`ip-host` SMTP)            |
| 31  | Telnet                   | IP Host (Telnet)       | ✅ (`telnet`)                  |
| 32  | IPMI                     | IPMI                   | ✅ (`ipmi`)                    |
| 33  | JMX                      | JMX                    | ✅ (`jmx`)                     |
| 34  | Apache Kafka             | Kafka                  | ✅ (`kafka`)                   |
| 35  | JMX (local)              | Local System           | ✅ через `jmx` local           |
| 36  | TCP/UDP, Serial          | Message Stream         | ✅ (`message-stream`)          |
| 37  | M-Bus                    | Meter-Bus              | ✅ (`mbus`)                    |
| 38  | Modbus RTU/ASCII/TCP/UDP | Modbus                 | ✅ (`modbus-tcp/rtu/udp`)      |
| 39  | GSM/GPRS (AT)            | Modem                  | ✅ (`modem-at`)                |
| 40  | MQTT                     | MQTT                   | ✅                             |
| 41  | NMEA 0183                | NMEA                   | ✅ (`nmea`)                    |
| 42  | Omron FINS               | Omron FINS             | ✅ (`omron-fins`)              |
| 43  | ODBC                     | Database (ODBC)        | ✅ (`odbc`)                    |
| 44  | OPC DA 2.0               | OPC                    | ✅ (`opc-da` stub)             |
| 45  | LON / LonTalk            | OPC (bridge)           | ✅ (`opc-bridge` stub)         |
| 46  | OPC DA/AE/HDA            | OPC + OPC Agent        | ✅ (`opc-da` stub)             |
| 47  | OPC UA                   | OPC UA                 | ✅ (`opcua`)                   |
| 48  | OPC UA                   | OPC UA Server          | ✅ (`opcua-server`)            |
| 49  | Siemens S7               | Siemens S7             | ✅ (`s7`)                      |
| 50  | SIP                      | SIP                    | ✅ (`sip`)                     |
| 51  | SMB/CIFS                 | Samba                  | ✅ (`smb`)                     |
| 52  | SMI-S                    | SMI-S                  | ✅ (`smi-s` stub)              |
| 53  | SMPP                     | SMPP                   | ✅ (`smpp`)                    |
| 54  | SNMP v1/v2c/v3           | SNMP                   | ✅ (`snmp`)                    |
| 55  | SOAP                     | SOAP                   | ✅ (`soap`)                    |
| 56  | SSH                      | SSH                    | ✅ (`ssh`)                     |
| 57  | —                        | Virtual Device         | ✅ (`virtual`)                 |
| 58  | VMware SOAP API          | VMware                 | ✅ (`vmware` stub)             |
| 59  | XMPP                     | XMPP                   | ✅ (`xmpp`)                    |
| 60  | JMS                      | WebSphere MQ           | ✅ (`jms`)                     |
| 61  | —                        | Web Transaction        | ✅ (`web-transaction`)         |
| 62  | WMI                      | WMI                    | ✅ (`wmi`, Windows)            |


### Приоритизация (черновик, P3+)

Порядок внедрения уточняется по потребностям app-команд (gate ADR-0009). Типичные кластеры:


| Кластер             | Драйверы (#)             | Заметка                                           |
| ------------------- | ------------------------ | ------------------------------------------------- |
| SCADA / энергетика  | 9, 18–19, 38, 47–49, 54  | DNP3, IEC 104, Modbus полный, OPC UA, S7, SNMP v3 |
| Здания / IoT        | 3, 4, 40, 47             | BACnet, CoAP, MQTT, OPC UA                        |
| IT / NMS            | 20–31, 32–33, 54, 56, 62 | IP Host family, IPMI, JMX, SNMP, SSH, WMI         |
| Интеграция / шины   | 7, 16–17, 34, 55, 60     | HTTP, Kafka, DB, SOAP, MQ                         |
| Edge / полевой слой | 12, 35–36, 39, 41        | Flexible Driver, Message Stream, Modem, NMEA      |
| Симуляция / стенд   | 57                       | virtual (PF-09 ✅)                                 |


### Acceptance (REQ-PF-14)

1. Драйвер зарегистрирован в `DriverCatalog`, документирован в [DRIVERS.md](DRIVERS.md).
2. Есть model или пример `driverConfigJson` / `driverPointMappingsJson`.
3. Управление через Web Console (вкладка «Драйвер») и REST runtime API.
4. Unit/integration test без внешнего железа (mock или loopback).

---

## 11. Вне scope platform (`main`)


| Артефакт                             | Где                         |
| ------------------------------------ | --------------------------- |
| Доменная модель, UC/US, канон БД app | Репозиторий приложения      |
| JSON script bodies, SQL migrations   | App bundle deploy           |
| BPMN процессов app                   | App bundle deploy           |
| Operator UI дашборды app             | `operatorUi` или `dashboards[]` в bundle |
| E2E smoke конкретного заказчика      | CI репозитория приложения   |


Platform **не** содержит отраслевую бизнес-логику в Java — только generic-движки и исполнение declarative-конфигурации (§0.1).

---

## 12. Дополнительные требования platform (REQ-FW)

Следующая волна после закрытия REQ-PF baseline: документация процесса, коммерческие bundle, reference MES, public API, messaging, AI, licensed drivers. North star без изменений — §0.1 (declarative object tree, REST/JSON, PostgreSQL).

### 12.1 Tracks (параллельные потоки)

| Track | Код | Фокус | REQ-FW |
| ----- | --- | ----- | ------ |
| Документация | **DOC** | ADR, gap-registry | FW-01, FW-02 |
| Лицензирование | **LIC** | RSA, installationId, commercial bundle | FW-10…12 |
| Reference | **REF** | MES walkthrough, synthetic demo | FW-20 |
| Public API | **API** | Граница solution developer, event catalog | FW-30, FW-31 |
| Транспорт | **NET** | Event bus vs sync RPC | FW-32 |
| AI | **AI** | LlmProvider, ContextPack, tools, Studio | FW-40…43 |
| Drivers | **DRV** | Licensed driver JAR (опционально) | FW-50 |

### 12.2 REQ-FW-01 — ADR `docs/decisions/`

**Зачем:** архитектурные решения в отдельных ADR с контекстом, альтернативами и последствиями; сейчас ISPF ссылается на ADR-0008/0009 без файлов.

**Задачи:**

- [x] Создать `docs/decisions/` и шаблон ADR (статус, контекст, решение, последствия).
- [x] Перенести в файлы: app/platform boundary (**0008**), dogfooding gate (**0009**), commercial licensing (**0010**).
- [x] ADR: bundle = tree packaging (**0014**), federation topology (**0015**), TimescaleDB retention (**0016**).
- [x] В `ARCHITECTURE.md`, `APPLICATIONS.md`, `PLUGINS.md` — ссылки на `docs/decisions/`, не inline-only.

**Acceptance:** любое архитектурное изменение platform сопровождается ADR; оглавление в `docs/decisions/README.md`.

---

### 12.3 REQ-FW-02 — Platform gap-registry

**Зачем:** единый реестр пробелов — % готовности, что заготовка, ссылка на код.

**Задачи:**

- [x] Поддерживать [GAP_REGISTRY.md](GAP_REGISTRY.md) (вынесено из §12.10).
- [x] Правило: закрытие REQ-PF / REQ-FW → обновление строки + [ROADMAP.md](ROADMAP.md) при необходимости.
- [x] Сводная таблица «высокий / средний / низкий» приоритет.

**Acceptance:** перед sprint planning достаточно одного документа для gap review; нет противоречий «готово / 0%» без пояснения.

---

### 12.4 REQ-FW-10…12 — Коммерческое лицензирование

**Зачем:** проверяемая поставка commercial bundle / driver pack; ядро Apache 2.0 без DRM, контроль на артефакте решения. См. [PLUGINS.md](PLUGINS.md) — сейчас требования к `license.json` без runtime verify.

#### FW-10 — RSA verify при deploy/load

- [x] Формат `license` в bundle manifest: bundleId, minPlatformVersion, expiry, content hash, RSA signature.
- [x] Публичный ключ в platform config (`ispf.license.public-key-pem`); **без** привязки ядра к closed-source.
- [x] `POST .../deploy` → verify до apply; ошибка → 403 + log (при `enforce=true`).

#### FW-11 — `installationId` + LicenseBuilder

- [x] Файл `.ispf-installation-id` (hex) при первом start; `GET /api/v1/platform/installation-id`.
- [x] CLI [tools/license-builder/](../tools/license-builder/): генерация ключей, `sign-bundle.py`.
- [x] Документация [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md).

#### FW-12 — Bundle dependency manifest

- [x] Поле `requires[]` в bundle manifest: appId, minVersion.
- [x] Deploy проверяет порядок и наличие зависимостей (`BundleDependencyVerifier`).

**Acceptance:** сценарий «commercial bundle на стенде A не работает на стенде B без новой лицензии»; Apache core deploy без license — без изменений.

**Не входит:** лицензирование **ядра** ISPF; Java business plugins с логикой в JAR (§0.1).

---

### 12.5 REQ-FW-20 — MES reference walkthrough

**Зачем:** сквозной reference-сценарий от домена до operator screens с критериями готовности.

**Задачи:**

- [x] [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md): процессы, шаги, object paths, события.
- [x] `examples/mes-reference/` — bundle + demo seed + README; `MesReferenceBundleSmokeTest`.
- [x] Привязка шагов к REQ-PF (deploy, correlator, BFF).

**Acceptance:** сценарий «наряд → резервуар → эстакада → завершение» проходит на `local` profile без custom Java; demo data reload idempotent.

---

### 12.6 REQ-FW-30…32 — Public API и шины

#### FW-30 — Solution developer public API boundary

- [x] [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md): разрешено / запрещено, stable REST paths, bundle schema version.
- [x] Таблица контрактов: deploy API, object tree, BFF, drivers SPI.

#### FW-31 — Event catalog в bundle

- [x] Секция `events[]` в bundle: `id`, `roles[]`, optional `payloadSchema`.
- [x] Загрузка в registry при deploy; `GET /api/v1/applications/{appId}/events`.
- [x] WS `subscribe_events` проверяет роль по catalog.

#### FW-32 — Event bus vs sync RPC

- [x] Документ [MESSAGING.md](MESSAGING.md): NATS/event journal = async; function invoke / federation = sync.
- [x] Явные имена топиков/паттернов NATS.

**Acceptance FW-31:** bundle с `events[]` → operator WS subscription отклоняется без роли; admin проходит.

---

### 12.7 REQ-FW-40…43 — AI Development Layer

**Зачем:** LLM через SPI, не в ядре; генерация **валидируемых артефактов** (bundle, model, dashboard), не Java в `ispf-server`.

| ID | Компонент | Задачи |
| -- | --------- | ------ |
| **FW-40** | `LlmProvider` SPI | [x] `ispf-ai-api` + openai-compatible/ollama adapters; Spring profile config |
| **FW-41** | ContextPack | [x] `ai/context/`, `tools/ai-pack/build.py`, classpath pack |
| **FW-42** | ToolRegistry | [x] validate/dry-run/list models; `ai_tool_audit`; REST `/api/v1/ai/**` |
| **FW-43** | Platform Studio | [x] Web Console AI Studio tab; publish = package import |

**Acceptance FW-42:** [x] prompt → generated bundle JSON → `validate` tool → CI green → deploy на local (see `AiToolApiTest`, `BundleManifestValidatorTest`).

**Документация:** [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md), [ADR-0011](decisions/0011-ai-artifact-generation-gates.md), [ADR-0012](decisions/0012-tree-first-ai-agent.md).

**Принцип:** ИИ не пишет произвольный React/Java в `main`; только артефакты + gates.

#### FW-44 — Tree-first agent (v0.7.5)

- [x] `TreeFirstAgentService` + platform tools (`PlatformAgentToolRegistry`)
- [x] Multi-turn in-memory sessions + Web Console chat UI
- [x] Dashboard/SNMP/driver tools + playbooks
- [x] Reliability: parse retries, loop guard, startup validator, graceful ERROR turns
- [x] Persistent DB sessions (`agent_sessions`, `agent_turns`, TTL eviction)
- [x] MCP adapter ([ADR-0013](decisions/0013-mcp-agent-tool-adapter.md))

#### FW-45 — Platform knowledge for agent

- [x] `PlatformBriefingService` — auto-briefing in system prompt (drivers, examples, features, live snapshot)
- [x] Extended ContextPack: `driverCatalog`, `featureIndex`, `exampleSummaries`, `docChunks`; CI rebuild
- [x] Agent tools: `list_drivers`, `get_driver_help`, `list_examples`, `get_example_bundle`, `list_applications`
- [x] `search_context` v2 — scored search with optional `topic`
- [x] Playbooks: virtual meter, mes-reference, modbus-tcp

#### FW-46 — Agent action tools

- [x] `invoke_bff`, `invoke_tree_function` — FunctionService + ACL invoke
- [x] `search_objects` — tree search with tenant/ACL filter
- [x] `list_object_models` — ModelRegistry templates
- [x] `fire_event`, `list_events` — event journal
- [x] MES playbook with real invoke_bff steps

#### FW-47 — Agent discovery tools

- [x] `list_functions`, `get_function` — tree + deployed BFF signatures
- [x] `list_event_catalog`, `get_event_schema` — bundle catalog + object events
- [x] `describe_variables` — variable schemas before `set_variable`
- [x] MES playbook: list_functions → get_function → invoke_bff

#### FW-48 — Agent automation tools

- [x] `configure_alert`, `configure_correlator` — AutomationTreeService wrappers (create + update)
- [x] `configure_operator_ui` — OperatorAppUiService default dashboard + menu
- [x] `create_variable` — refAt/CEL bindings for CUSTOM hub logic
- [x] `list_automation`, `get_automation_schema` — reference for object types and fields
- [x] Dashboard templates: `virtual-cluster-overview`, `virtual-cluster-detail`
- [x] Virtual driver lab profile: `triangleWave`
- [x] Playbook: virtual cluster end-to-end (devices, hub, alert, dashboards, operator UI)
- [x] Agent max steps default 28

---

### 12.8 REQ-FW-50 — Licensed driver JAR contract

**Зачем:** редкие протоколы — commercial driver pack, не расширение stub-каталога в `main`.

- [x] SPI расширение: `DeviceDriver` + license check + `driver-pack.json` manifest (spike).
- [x] Lifecycle: load from `drivers/` dir, совместимость с [DRIVER_PROMOTION.md](DRIVER_PROMOTION.md).
- [x] Документ поставки: JAR + license + min ISPF version — [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md).
- [x] Commercial driver pack pilot (signed JAR on staging — `LicensedDriverPackPilotTest`, [examples/licensed-driver-pack-pilot](../examples/licensed-driver-pack-pilot/)).

**Acceptance:** licensed driver pack загружается при valid license; без license — skip + WARN; core Apache drivers без изменений.

---

### 12.9 Границы REQ-FW (out of scope)

| Тема | Решение ISPF |
| ---- | ------------ |
| Wire format | REST/JSON + WebSocket — primary контракт |
| Storage | PostgreSQL/TimescaleDB; edge — H2/SQLite profile, не смена модели |
| Бизнес-логика | Declarative object tree (§0.1), не Java plugins в server |
| Ядро | Apache 2.0 без DRM |

---

### 12.10 Gap-registry (живой срез)

**Источник правды:** [GAP_REGISTRY.md](GAP_REGISTRY.md). Обновлять при закрытии REQ-FW / REQ-PF.

---

## 13. Связанные документы


| Документ                           | Где                                |
| ---------------------------------- | ---------------------------------- |
| Platform apps API                  | [APPLICATIONS.md](APPLICATIONS.md) |
| Device drivers (реализовано + SPI) | [DRIVERS.md](DRIVERS.md)           |
| Platform developer backlog         | этот файл                          |
| REQ-FW                             | §12 (этот файл)                    |
| PLUGINS / main hygiene             | [PLUGINS.md](PLUGINS.md)           |
| Workflows                          | [WORKFLOWS.md](WORKFLOWS.md)       |


---

## 14. История изменений


| Дата       | Изменение                                                                   |
| ---------- | --------------------------------------------------------------------------- |
| 2026-06-22 | §12 REQ-FW: DOC, LIC, REF, API, NET, AI, DRV; §3 матрица; Sprint E–G; gap-registry §12.10 |
| 2026-06-21 | Phase 12: tree-first reports (`root.platform.reports`, `report-v1`, Report Builder) |
| 2026-06-21 | Phase 13: YARG server export (PDF/XLSX/HTML), template upload, `report_templates` |
| 2026-06-21 | Phase 6 kickoff v0.3.0: PF-03 deprecation docs, ROADMAP §6, doc sync       |
| 2026-06-21 | Phase 5 closure v0.2.0: acceptance §8.1, doc sync, bulk model upgrade, BPMN panel |
| 2026-06-21 | §0.1 north star: бизнес-логика в механизмах object tree; §8.1 Phase 5 priorities |
| 2026-06-20 | REQ-PF-14: финальная волна — 58 `driverId`, каталог §10 закрыт            |
| 2026-06-20 | REQ-PF-14: волны 2–4 — 27 новых драйверов, docs/DRIVERS.md                  |
| 2026-06-20 | REQ-PF-14: каталог 63 device drivers (roadmap)                              |
| 2026-06-19 | REQ-PF-13: distributed topology & object federation — roadmap (P3+, vision) |
| 2026-06-19 | Первая consolidated версия: статус `main` + gap PF-01a…11                   |
| 2026-06-19 | Синхронизация в framework `docs/`; baseline Apache 2.0                      |


