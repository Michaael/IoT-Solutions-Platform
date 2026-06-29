# AGENT_KNOWLEDGE — справочник внутреннего агента ISPF

Документ для **tree-first agent**, AI Studio и MCP-клиентов. Описывает **все подходы к созданию приложений/решений** и даёт **карту документации** платформы.

**Как читать:** `search_context(query=..., topic=...)` → полный текст срезов в ContextPack. Этот файл — **маршрутизатор**: что выбрать и куда смотреть дальше.

См. также [APPLICATION_PRINCIPLES.md](APPLICATION_PRINCIPLES.md) (канонический свод P1–P10), [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md), [0001](decisions/0001-app-platform-boundary.md), [0005](decisions/0005-tree-first-ai-agent.md).

---

## Северная звезда

Каноническая формулировка и развёрнутые правила: [APPLICATION_PRINCIPLES.md](APPLICATION_PRINCIPLES.md).

1. **Бизнес-логика — в механизмах object tree**, не в Java `ispf-server` и не в React платформы.
2. **Runtime один:** invoke, workflow, alerts, dashboards, bindings — через **дерево объектов** и platform API.
3. **Application record** (`applications` table) — **реестр + изолированная SQL-schema**, не параллельный движок.
4. **Bundle deploy** — доставка declarative-конфигурации в дерево; после deploy всё адресуется tree paths.
5. **Агент не пишет Java/React в `main`:** только validated JSON (bundle, layout, rules, models) и вызовы platform tools.

Запрещено: platform Flyway для app-таблиц, hardcoded BFF routes, отраслевой Java в server.

---

## Подходы к созданию приложений (выбор стратегии)

| # | Подход | Когда использовать | Доставка | Operator UI |
|---|--------|-------------------|----------|-------------|
| **A** | **Tree-first (agent tools / Explorer)** | Демо, SNMP, lab, быстрый POC, пошаговая настройка с пользователем | `create_object`, `set_variable`, `configure_driver`, dashboards tools | `configure_operator_ui` |
| **B** | **Admin Console (ручная сборка)** | Инженер без bundle, итеративный HMI | UI: Models, Dashboard Builder, Inspector | Operator Apps panel |
| **C** | **Bundle deploy (manifest)** | Production solution, CI/CD, повторяемый релиз | `POST .../applications/{id}/deploy` или `import_package` | `operatorUi` в manifest |
| **D** | **Пошаговый REST API** | Автomation без ZIP, поэтапная интеграция | register → migrate → functions → deploy sections | `PUT operator-apps/.../ui` |
| **E** | **AI Studio (generate → validate → import)** | Черновик manifest из промпта | `validate_bundle` → `dry_run_deploy` → `import_package` | из сгенерированного `operatorUi` |
| **F** | **Reference example** | Обучение, MES/lab шаблон | `get_example_bundle` → adapt → import | из example manifest |
| **G** | **Platform HMI only** | Только мониторинг без app schema | dashboards + binding rules на tree | встроенный `platform` operator app |
| **H** | **Commercial bundle** | Лицензируемое решение | signed bundle + license gate | как в C |

### Дерево решений (кратко)

```
Нужна изолированная SQL-schema приложения (orders, batches, …)?
  ├─ ДА → C/D/E/F/H (bundle или пошаговый API) + migrations[]
  └─ НЕТ → A/B/G (tree-only: devices, dashboards, rules, workflows на platform tree)

Нужен повторяемый релиз / CI?
  └─ C или E (bundle + validate gates)

Интерактивная сессия с пользователем в AI Studio?
  └─ A (tools) — предпочтительно; bundle import только после validate+dry_run

Полноценный MES/terminal с BFF-таблицами?
  └─ F (mes-reference) или C с functions[] + operatorUi + dashboards[]
```

---

## A. Tree-first (инструменты агента)

**Суть:** собрать решение **на живом дереве** без deploy ZIP. Подходит для «создай SNMP localhost и дашборд», virtual lab, alert на hub.

**Типичная последовательность:**

1. `list_object_models` → `create_object` (DEVICE/DASHBOARD/CUSTOM/WORKFLOW/…)
2. `configure_driver` + `driver_control start` (если DEVICE)
3. `configure_variable_history` (для chart/sparkline)
4. `create_variable` / binding rules (`set_variable` + CEL) на CUSTOM hub
5. `configure_alert` / `configure_correlator`
6. `create_object DASHBOARD` → `set_dashboard_layout template=...` или `add_dashboard_widget`
7. Platform rules (ADR-0019): binding rules на DASHBOARD с `target.kind=context`, `onContextChange`
8. `configure_operator_ui` — default dashboard + menu
9. `list_variables` → `finish` с путями для UI

**Playbooks в system prompt:** SNMP, virtual cluster, Modbus, MES, reports, widgets — см. `AgentPlaybooks.*`.

**Не использовать:** `set_variable name=widgets` на dashboard; layout только в variable `layout`.

Документы: [DASHBOARDS.md](DASHBOARDS.md), [DRIVERS.md](DRIVERS.md), [BINDINGS.md](BINDINGS.md), [PLATFORM_LOGIC.md](PLATFORM_LOGIC.md), [AUTOMATION.md](AUTOMATION.md).

---

## B. Admin Console (ручная сборка)

**Суть:** инженер собирает решение в Web Console без агента и без bundle.

| Задача | UI |
|--------|-----|
| Модель устройства | Explorer → Models → Model Editor |
| Устройство | Explorer → Devices → + Object, Inspector → Driver |
| HMI | Dashboard Builder (Editor → widgets / Rules) |
| Привязки / CEL | Inspector → Bindings |
| BPMN | Workflow Builder |
| Operator shell | `root.platform.operator-apps` → Operator Apps Panel |
| Deploy app | `root.platform.applications` → + Deploy-приложение |

Документы: [WEB_CONSOLE.md](WEB_CONSOLE.md), [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md).

---

## C. Bundle deploy (декларативный manifest)

**Суть:** один JSON/ZIP manifest описывает всё решение. **North star** для production.

**Секции manifest** (см. ContextPack `bundleManifest.fields`):

| Секция | Назначение |
|--------|------------|
| `objects[]` | Узлы дерева (reconcile при redeploy) |
| `models[]` | Blueprints |
| `dashboards[]` | layout JSON на DASHBOARD paths |
| `workflows[]` | BPMN + triggers |
| `migrations[]` | SQL в app schema (не platform Flyway) |
| `functions[]` | Script functions → tree `{appId}.functions.*` |
| `bindings[]` | SQL bindings app schema → variables |
| `reports[]` | SQL reports |
| `schedules[]` | Periodic invoke_function |
| `alertRules[]` / `correlators[]` | Automation nodes |
| `events[]` | Event catalog |
| `operatorUi` | Operator HMI menu (preferred) |
| `operatorManifest` | **Legacy** — deprecated |

**API:**

- `POST /api/v1/applications/{appId}/deploy` — monolithic JSON
- `POST /api/v1/platform/packages/import` — ZIP (agent: `import_package`)
- Gates: `validate_bundle` → `dry_run_deploy` → import

**После deploy:** invoke через `POST /bff/invoke` или `objects/by-path/functions/invoke` по tree path; SQL через `sqlBinding('appId','var')`.

Документы: [APPLICATIONS.md](APPLICATIONS.md), [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md), [SOLUTION_DEVELOPER_GUIDE.md](SOLUTION_DEVELOPER_GUIDE.md).

---

## D. Пошаговый REST API

**Суть:** те же возможности, что bundle, но **по частям** — для скриптов и поэтапной интеграции.

```text
1. POST /applications          — register appId, schemaName, tablePrefix
2. POST .../data/migrate       — SQL migrations
3. POST .../functions/deploy   — script functions
4. POST .../bindings/deploy    — SQL → variable sync
5. POST .../reports/deploy     — reports
6. POST .../deploy             — full bundle (или только недостающие секции)
7. PUT  /operator-apps/{id}/ui — operator menu
```

Документ: [APPLICATIONS.md](APPLICATIONS.md), [API.md](API.md).

---

## E. AI Studio (generate → validate → import)

**Суть:** LLM генерирует manifest JSON; platform gates проверяют до записи в БД.

1. AI Studio → «Пакет bundle» или agent tool chain
2. `validate_bundle` / `dry_run_deploy`
3. `import_package` (agent) или UI «Опубликовать»

**Policy:** `generationPolicy` в ContextPack — allowed/forbidden artifacts.

Документ: [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md), [0004](decisions/0004-ai-artifact-generation-gates.md).

---

## F. Reference examples

| Example | appId | Назначение |
|---------|-------|------------|
| `examples/mes-reference/` | mes-reference | MES orders, BFF functions, workflows |
| `examples/lab-training/` | lab-training | Virtual lab, PF-15 exercises |
| `examples/demo-app/` | demo-app | Minimal bundle |
| `examples/warehouse-app/` | warehouse-app | Warehouse pattern |
| `examples/mini-tec/` | — | Bootstrap demo (fixtures) |

Agent tools: `list_examples`, `get_example_bundle(appId)`.

Walkthroughs: [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md), [LAB_TRAINING.md](LAB_TRAINING.md), [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md).

---

## G. Platform HMI без application schema

**Суть:** мониторинг **без** отдельного appId/SQL — только platform tree.

- Devices + drivers + binding rules + dashboards
- Demo rules on `snmp-host-monitoring` (Platform rules, `@dashboardContext`)
- Operator: `?mode=operator&app=platform`

Не нужны: register app, migrations, bundle functions — если нет прикладных таблиц.

---

## H. Commercial bundle

Signed manifest + `license` block. Deploy через тот же import с проверкой RSA.

Документ: [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md), [0003](decisions/0003-commercial-bundle-licensing.md).

---

## Где выражать логику (не дублировать)

| Задача | Механизм | Документ |
|--------|----------|----------|
| Вычисление переменной | CEL / platform bindings / binding rules | [BINDINGS.md](BINDINGS.md) |
| UI дашборда (show/hide, mode) | Platform Rule → `@dashboardContext` | [PLATFORM_LOGIC.md](PLATFORM_LOGIC.md), [DASHBOARDS.md](DASHBOARDS.md) |
| Порог → событие | ALERT node + CEL | [AUTOMATION.md](AUTOMATION.md) |
| Паттерн событий → workflow | Correlator | [AUTOMATION.md](AUTOMATION.md) |
| Процесс с задачами оператора | BPMN WORKFLOW | [WORKFLOWS.md](WORKFLOWS.md) |
| CRUD по SQL app schema | Script function (steps) | [APPLICATIONS.md](APPLICATIONS.md), [OBJECT_FUNCTIONS.md](OBJECT_FUNCTIONS.md) |
| SQL → variable poll | sqlBinding / bindings[] | [APPLICATIONS.md](APPLICATIONS.md) |
| Телеметрия устройства | Driver + point mappings | [DRIVERS.md](DRIVERS.md) |
| Таблица на HMI | Dashboard widget `object-table` + `selectionKey` | [DASHBOARDS.md](DASHBOARDS.md), [WIDGETS.md](WIDGETS.md) |
| Legacy mini-DSL на виджете | **Deprecated** → Platform rules | [PLATFORM_LOGIC.md](PLATFORM_LOGIC.md) § legacy |

---

## Operator UI — источники (приоритет)

1. `GET /operator-apps/{appId}/ui` — таблица + дерево `root.platform.operator-apps`
2. `operatorUi` в bundle deploy
3. Autogen из `dashboards[]` в bundle
4. Legacy `public/operator-apps/{appId}.ui.json` (dev only)

URL: `?mode=operator&app={appId}&dashboard={path}`.

---

## Карта документации (полный индекс)

Используй `search_context` с `topic` или ключевыми словами из таблицы.

### Продукт и разработка решений

| Документ | topic / keywords | Содержание |
|----------|------------------|------------|
| [PRODUCT.md](PRODUCT.md) | product | Обзор продукта, сценарии |
| [APPLICATION_PRINCIPLES.md](APPLICATION_PRINCIPLES.md) | application-principles | Свод P1–P10, north star, анти-паттерны |
| [SOLUTION_DEVELOPER_GUIDE.md](SOLUTION_DEVELOPER_GUIDE.md) | solution, applications | Жизненный цикл решения, 6 шагов |
| [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md) | public-api, bundle | Стабильный контракт manifest |
| [APPLICATIONS.md](APPLICATIONS.md) | applications, bff, bundle | REQ-PF: deploy, functions, SQL, schedules |
| [GLOSSARY.md](GLOSSARY.md) | glossary | Термины |

### Object tree и логика

| Документ | topic | Содержание |
|----------|-------|------------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | architecture | Принципы, слои |
| [OBJECT_MODEL.md](OBJECT_MODEL.md) | object-model, features | Дерево, типы, API |
| [MODELS.md](MODELS.md) | models | Blueprints, templateId |
| [OBJECT_FUNCTIONS.md](OBJECT_FUNCTIONS.md) | functions | script/java handlers, invoke |
| [BINDINGS.md](BINDINGS.md) | bindings, cel | CEL, platform bindings |
| [PLATFORM_LOGIC.md](PLATFORM_LOGIC.md) | platform-logic, rules | Platform Rule, dashboard context |
| [VARIABLE_HISTORY.md](VARIABLE_HISTORY.md) | historian | Time-series |

### HMI и оператор

| Документ | topic | Содержание |
|----------|-------|------------|
| [DASHBOARDS.md](DASHBOARDS.md) | dashboards | Layout, selectionKey, rules tab |
| [WIDGETS.md](WIDGETS.md) | widgets | Каталог виджетов, JSON fields |
| [SPREADSHEET_WIDGET.md](SPREADSHEET_WIDGET.md) | spreadsheet | ISPF(), sheet config |
| [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md) | operator | HMI, work queue |
| [WEB_CONSOLE.md](WEB_CONSOLE.md) | web-console | Admin UI |

### Автоматизация и интеграция

| Документ | topic | Содержание |
|----------|-------|------------|
| [AUTOMATION.md](AUTOMATION.md) | automation, workflows | Alerts, correlators, events |
| [WORKFLOWS.md](WORKFLOWS.md) | workflows | BPMN, ISPF extensions |
| [MESSAGING.md](MESSAGING.md) | messaging, features | NATS, WS, events |
| [FEDERATION.md](FEDERATION.md) | federation | Remote peers |
| [DRIVERS.md](DRIVERS.md) | drivers | 58 drivers, config |
| [REPORTS.md](REPORTS.md) | reports | SQL reports, export |

### AI, deploy, ops

| Документ | topic | Содержание |
|----------|-------|------------|
| [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) | ai, agent | Agent, ContextPack, MCP |
| **AGENT_KNOWLEDGE.md** (этот файл) | agent-knowledge | Подходы к приложениям, индекс |
| [API.md](API.md) | api | REST endpoints |
| [DEPLOYMENT.md](DEPLOYMENT.md) | deployment | Docker, env |
| [SECURITY.md](SECURITY.md) | security | RBAC, Keycloak |
| [TESTING.md](TESTING.md) | testing | Tests |
| [LOAD_TESTING.md](LOAD_TESTING.md) | loadtest | Throughput baselines |

### Reference walkthroughs

| Документ | topic |
|----------|-------|
| [LAB_TRAINING.md](LAB_TRAINING.md) | lab, training |
| [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) | mes |
| [REFERENCE_MES_DEFECT_WALKTHROUGH.md](REFERENCE_MES_DEFECT_WALKTHROUGH.md) | mes, defect |
| [REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md](REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md) | mes, ogp |
| [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md) | mini-tec |

### ADR (архитектурные решения)

| ADR | Тема |
|-----|------|
| [0001](decisions/0001-app-platform-boundary.md) | Platform vs solution |
| [0004](decisions/0004-ai-artifact-generation-gates.md) | AI validate gates |
| [0005](decisions/0005-tree-first-ai-agent.md) | Tree-first agent |
| [0006](decisions/0006-mcp-agent-tool-adapter.md) | MCP |
| [0010](decisions/0010-binding-rules-only.md) | Binding rules |
| [0019](decisions/0019-platform-rule-unification.md) | Platform Rule / dashboard |
| [decisions/README.md](decisions/README.md) | Полный список ADR |

### Platform evolution (для контекста, не для генерации)

| Документ | Назначение |
|----------|------------|
| [PLATFORM_EVOLUTION.md](PLATFORM_EVOLUTION.md) | История версий |
| [ROADMAP.md](ROADMAP.md) | Roadmap |
| [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) | REQ-PF статус |
| [GAP_REGISTRY.md](GAP_REGISTRY.md) | Gaps |

---

## search_context — рекомендуемые topics

| topic | Когда |
|-------|-------|
| `application-principles` | North star, P1–P10, «как создать приложение правильно» |
| `agent-knowledge` | Выбор подхода A–H, карта docs |
| `applications` | Bundle, BFF, migrations, functions |
| `public-api` | Контракт manifest |
| `solution` | Жизненный цикл solution developer |
| `dashboards` | Widgets, layout, platform rules |
| `platform-logic` | Context, visibility, CEL rules |
| `bindings` | CEL, counterRate, refAt |
| `drivers` | SNMP, Modbus, virtual, MQTT |
| `workflows` | BPMN |
| `automation` | Alerts, correlators |
| `object-model` | Tree types, variables |
| `ai` | Agent tools, ContextPack |
| `all` | Широкий поиск по docChunks |

---

## Чеклисты для агента

### «Создай приложение / решение» (с SQL)

1. Уточнить appId, нужен ли operator UI
2. `search_context topic=application-principles` + `topic=agent-knowledge`; `get_example_bundle` если похоже на MES/lab
3. register (или включить в bundle) → migrations → functions → objects/dashboards
4. `validate_bundle` → `dry_run_deploy` → `import_package`
5. `configure_operator_ui` если не в manifest
6. `finish` с `?mode=operator&app=...` и путями dashboards

### «Создай мониторинг / SNMP / дашборд» (без app schema)

1. Tree-first playbook (SNMP / virtual cluster)
2. Driver + dashboard template
3. Platform rules при необходимости (detail mode, widget visibility)
4. `configure_operator_ui` для platform app

### «Добавь автоматизацию»

1. `get_automation_schema`
2. Hub CUSTOM + `create_variable` или alert на DEVICE
3. `configure_alert` / `configure_correlator`
4. Optional: WORKFLOW + `operatorAppId`

### «Не ломай platform» (см. [APPLICATION_PRINCIPLES.md](APPLICATION_PRINCIPLES.md) P2, P10)

- Не invent REST paths — только tools
- Bundle import только после validate + dry_run OK в том же run
- Не platform Flyway для app tables
- Prefer `operatorUi` over legacy `operatorManifest`

---

## ContextPack и MCP resources

- Build: `python tools/ai-pack/build.py` → `ai/context-pack.json`
- MCP: `contextpack://doc-chunks`, `contextpack://feature-index`, `contextpack://example-summaries`
- Briefing: `PlatformBriefingService` — drivers, examples, live tree snapshot

---

*Обновлять при добавлении новых подходов (ADR, REQ-PF) и при расширении docChunks в `tools/ai-pack/build.py`.*
