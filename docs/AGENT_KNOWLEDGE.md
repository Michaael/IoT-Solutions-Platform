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

## SIF — Specification Intake Framework

Универсальный intake для **любого** задания (полное ТЗ, короткий промпт, follow-up). Насосная станция — reference fixture, не special case.

### Pipeline (complex assignments)

1. **Classify** → `assignmentType`; **decompose implicit phrases** → `specBrief` (entities, FR-* с `sourcePhrase`)
2. **Discover** → recipes, profiles, `list_objects`, `get_automation_schema`
3. **Scope** → `intent_scope` связывает FR со слоями; `assumptions[]` для выведенного
4. **Gap matrix** → FR → capability (`full` / `out_of_scope`)
5. **Questions** → ≤3/ход; пользователь может набрать несколько ответов в чате
6. **Plan** → `plan.sections[]` поэтапно (≤2/ход) → **SYNTHESIS** обогащает секции → approval когда analytical gate OK

UI: `executiveSummary`, таблица FR в `specBrief`, `gapMatrix`, список `planCompletenessGaps` до «Утвердить полный план».

**Fast path:** `monitoring_lab`, `explore_readonly`, `follow_up` — без 5-turn torture.

### Domain adapters

| Adapter | Playbook | Template |
|---------|----------|----------|
| `industrial_oil_gas` | `virtualPumpStation()` + abbrev lexicon | `scada-facility-overview` |
| `snmp_lab` | SNMP playbooks | `snmp-host-monitoring` |
| `mes_terminal` | `mesReferenceLifecycle()` | — |
| `_default` | `projectBlueprintGuide()` | `monitoring-overview` |

### Guards & preflight

- **Approval:** «Да, начинаем», «Утверждаю», primary suggestion → execution без re-plan
- **Preflight:** перед mutation — hint + `suggestedDiscovery` если parent/path не grounded
- **Path casing:** case-insensitive match, hint «Use exact path: …»
- **Finish:** block при ERROR в turn, пустой mimic, dashboard без widgets, chart без history
- **Judge:** pre-finish verdict `approve | rework | gap_required | user_moderation_required`

### Reference fixtures (tests)

- Pump station TZ → `industrial_facility`
- «SNMP localhost мониторинг» → `monitoring_lab` fast path
- «Разверни MES demo» → `application_bundle`

См. `AgentPlaybooks.specIntakeGuide()`, `SpecIntakeScenarioTest`.

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

**Playbooks в system prompt:** SNMP, virtual cluster, Modbus, MES, reports, widgets, SCADA mimic — см. `AgentPlaybooks.*`.

### Project blueprint (8 слоёв)

Полноценный проект на tree-first = **8 слоёв** (см. `get_automation_schema topic=projectBlueprint`):

| # | Слой | Путь | Tools |
|---|------|------|-------|
| 1 | Hub (ABSOLUTE) | `root.platform.instances.{project}` | `ensure_absolute_instance`, binding rules, `refAt` |
| 2 | Устройства | `root.platform.devices.{project}/*` | `instantiate_instance_type`, `apply_relative_model`, `create_virtual_device` |
| 3 | Dashboard | `root.platform.dashboards.{project}-*` | `set_dashboard_layout`, `add_dashboard_widget` |
| 4 | SCADA | `root.platform.mimics.{project}-*` | `save_mimic_diagram`, `get_mimic_diagram` |
| 5 | Alerts | `root.platform.alert-rules.{project}-*` | `configure_alert` |
| 6 | Correlators | `root.platform.correlators.{project}-*` | `configure_correlator` |
| 7 | Workflows | `root.platform.workflows.{project}-*` | `save_workflow_bpmn` |
| 8 | Reports | `root.platform.reports.{project}-*` | `configure_report` |

**Три вида моделей:** `get_automation_schema topic=instanceTypes` — RELATIVE (mixin на существующий объект), INSTANCE (новый объект), ABSOLUTE (singleton hub).

**Каталог рецептов (1410):** `search_platform_recipes`, `get_automation_schema topic=recipes|projects|recipe/{id}`. **500** готовых отраслевых проектов (`project-{industry}-{archetype}`). Полный индекс: [AGENT_RECIPES.md](AGENT_RECIPES.md).

**Finish guard:** агент не может `finish` без `list_variables` на DEVICE, `configure_alert` при monitoring intent, `get_mimic_diagram elementCount>0` при SCADA.

**Не использовать:** `set_variable name=widgets` на dashboard; layout только в variable `layout`.
**SCADA:** `save_mimic_diagram` / `add_mimic_elements` — не `set_variable name=diagram`.

### Полный каталог agent tools (admin, ~85 tools)

| Область | Tools |
|---------|-------|
| Discovery | `search_context`, `list_drivers`, `get_driver_help`, `list_examples`, `get_example_bundle`, `get_widget_catalog`, `list_applications` |
| Object tree | `list_objects`, `get_object`, `create_object`, `delete_object`, `search_objects`, `search_by_haystack_tags`, `list_object_models` |
| Variables | `list_variables`, `describe_variables`, `set_variable`, `create_variable`, `configure_variable_history` |
| Drivers | `configure_driver`, `driver_control` |
| Bindings | `create_binding_rule`, `list_binding_rules`, `configure_platform_context_rule` |
| Dashboards | `get_dashboard_layout`, `set_dashboard_layout`, `add_dashboard_widget` |
| SCADA | `list_mimic_symbols`, `save_mimic_diagram`, `add_mimic_elements`, `get_mimic_diagram` |
| Reports | `list_reports`, `get_report_schema`, `run_report`, `configure_report` |
| Automation | `configure_alert`, `configure_correlator`, `list_automation`, `get_automation_schema` |
| Workflows | `get_workflow`, `save_workflow_bpmn`, `run_workflow`, `update_workflow_status`, `list_workflow_instances`, `signal_workflow_instance`, `cancel_workflow_instance` |
| Applications | `register_application`, `application_data_*`, `deploy_app_binding`, `deploy_app_function`, `validate_bundle`, `dry_run_deploy`, `import_package`, `export_application_bundle`, `rollback_application_deploy`, `pull_application_from_tree` |
| Functions/events | `list_functions`, `get_function`, `invoke_bff`, `invoke_tree_function`, `fire_event`, `list_events`, `list_event_catalog`, `get_event_schema` |
| Tree functions | `get_function_template`, `deploy_tree_function` (script **or java**), `invoke_tree_function`; app BFF: `deploy_app_function` (script) |
| Operator HMI | `configure_operator_ui` |
| Platform | `list_platform_schedules`, `configure_platform_schedule`, `resolve_timezone`, `export_haystack` |
| Models | `list_relative_models`, `list_instance_types`, `list_absolute_models`, `get_object_model`, `apply_relative_model`, `instantiate_instance_type`, `ensure_absolute_instance`, `create_virtual_device` |
| Recipes | `search_platform_recipes`, `get_automation_schema topic=recipes|projects|recipe/{id}|projectBlueprint|instanceTypes` |

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
| BPMN | Workflow Builder (+ cancel/signal активного instance) |
| Operator shell | `root.platform.operator-apps` → Operator Apps Panel |
| Deploy app (bundle) | `APPLICATION` → Inspector → **Deploy** → Bundle + history + rollback |
| App lifecycle (REST D) | `APPLICATION` → Deploy → **Application lifecycle** (migrate/seed/status, bindings, reports, functions deploy) |
| Platform schedules (DB) | **System → App schedules** (`GET/POST /api/v1/schedules`) |
| Semantic export | **System → Semantic export** (Haystack JSON, Brick JSON-LD/Turtle) |
| Federation bind | Inspector → Federation; peers → FederationPeersPanel |
| Timezone (device) | Inspector DEVICE → resolved TZ badge (`GET /platform/timezone/resolve`) |
| User timezone | Header → TimezoneSwitcher (`PATCH /auth/me/timezone`) |

Документы: [WEB_CONSOLE.md](WEB_CONSOLE.md), [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md).

---

## Web Console ↔ Platform API (prod **0.9.60**, июнь 2026)

**Parity ~100%** для admin/operator сценариев. Детальный реестр: [GAP_REGISTRY.md](GAP_REGISTRY.md).

### Application platform (`/api/v1/applications/{appId}/…`)

| API | UI |
|-----|-----|
| `POST /applications` | CreateObjectDialog (+ APPLICATION) |
| `POST …/deploy`, rollback, history | ApplicationBundlePanel, ApplicationDeployPanel |
| `GET …/export`, validate, pull-from-tree | ApplicationBundlePanel |
| `POST …/data/migrate`, `…/data/seed`, `GET …/data/status` | ApplicationLifecyclePanel |
| `GET/POST …/bindings/*` | ApplicationLifecyclePanel |
| `GET/POST …/reports/deploy`, run/export | ApplicationLifecyclePanel + operator manifest (legacy path) |
| `POST …/functions/deploy`, versions, rollback | ApplicationLifecyclePanel + ApplicationDeployPanel |
| `GET …/events` | ApplicationDeployPanel (event catalog) |
| `GET …/operator-ui`, `…/hmi-ui` | useOperatorAppsRegistry (fallback) |

### System admin

| API | UI |
|-----|-----|
| `GET/POST /api/v1/schedules` | System → App schedules |
| `GET …/platform/haystack/export`, `…/brick/export` | System → Semantic export |
| `GET/POST …/platform/backup/*` | System → Metrics → Platform backup |
| Change sets, runtime settings, journals | SystemView tabs |
| `GET /ai/models`, `/ai/provider` | AI Studio → Settings |

### Runtime / automation

| API | UI |
|-----|-----|
| `POST /workflows/instances/{id}/cancel\|signal` | WorkflowBuilder → Instance panel |
| `POST /federation/proxy/…/functions/invoke` | InvokeFunctionDialog (federated bind) |
| Federated variable write | Inspector Save → `PUT /objects/…/variables` (server proxy) |
| `POST /bff/invoke` | Operator manifest screens |
| Haystack tag search | HaystackBindDialog (dashboard), HaystackMetadataPanel (device) |

### Намеренно без admin UI (API / MCP / ops)

| API | Кто использует |
|-----|----------------|
| `POST /api/v1/ai/mcp` | Внешние MCP-клиенты (Cursor, SDK) |
| `GET /api/v1/platform/installation-id` | Диагностика / скрипты |
| Пошаговый REST D без bundle | Агент, CI, curl — дублирует UI Application lifecycle |

**Prod:** https://ispf.iot-solutions.ru — `0.9.60`, `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` (демо fixtures только после `vps-factory-reset.sh --fixtures`).

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
3. POST .../data/seed          — demo seed profiles
4. GET  .../data/status        — applied migration version
5. POST .../functions/deploy   — script functions
6. POST .../bindings/deploy    — SQL → variable sync (+ refresh)
7. POST .../reports/deploy      — reports
8. POST .../deploy             — full bundle (или только недостающие секции)
9. PUT  /operator-apps/{id}/ui — operator menu
10. GET/POST /api/v1/schedules — platform_schedules (отдельно от tree SCHEDULE objects)
```

**Web Console (0.9.62):** шаги 2–7 доступны в Inspector → APPLICATION → Deploy → **Application lifecycle**; агент: `register_application`, `application_data_migrate`, `deploy_app_binding`, `deploy_app_function`, …

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
| Bootstrap `tank-farm-demo` | tank-farm-demo | Anonymized tank-farm SCADA mimic (fixtures) |
| Bootstrap `pipeline-scada` | pipeline-scada | РД-029 screen forms (15 mimics, fixtures) |

Agent tools: `list_examples`, `get_example_bundle(appId)`.

Walkthroughs: [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md), [LAB_TRAINING.md](LAB_TRAINING.md), [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md).

### Bootstrap SCADA demos (fixtures, подход G)

При `ispf.bootstrap.fixtures-enabled=true` сервер создаёт готовые мнемосхемы и HMI. **Не использовать реальные названия компаний** в демо-текстах и путях.

| Demo | appId | Устройства | Mimic | Dashboard |
|------|-------|------------|-------|-----------|
| Резервуарный парк | `tank-farm-demo` | `root.platform.devices.tank-farm-demo.*` | `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` |
| СДКУ РД-029 | `pipeline-scada` | `root.platform.devices.pipeline-scada.*` | `root.platform.mimics.pipeline-rp` (+ 14 форм `pipeline-*`) | `root.platform.dashboards.pipeline-scada-hmi` |
| Mini-TEC SLD | — | mini-tec devices | `root.platform.mimics.mini-tec-single-line` | `root.platform.dashboards.mini-tec-single-line` |

**Код и re-export:**

| Demo | TypeScript | Export |
|------|------------|--------|
| tank-farm | `apps/web-console/src/scada/templates/buildTankFarmMimic.ts` | `npx tsx src/scada/templates/exportTankFarmMimic.ts` |
| pipeline-scada | `apps/web-console/src/scada/templates/pipeline-scada/` | `npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts` |

Java bootstrap: `TankFarmPlatformBootstrap`, `PipelineScadaPlatformBootstrap`. Playbook: `AgentPlaybooks.scadaMimicGuide()`.

Operator URL: `?mode=operator&app=tank-farm-demo&dashboard=root.platform.dashboards.tank-farm-hmi`.

**Примечание:** путь `root.platform.mimics.tank-farm-demo` при одновременном bootstrap `pipeline-scada` может быть перезаписан диаграммой РП (deprecated alias). Для полного СДКУ используйте `pipeline-rp` + `pipeline-scada-hmi`.

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
| Мнемосхема / P&ID | Объект `MIMIC` + виджет `scada-mimic` | [SCADA.md](SCADA.md) |
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
| [SCADA.md](SCADA.md) | scada, mimic | Мнемосхемы, MIMIC, bindings, editor (align, resize, snap) |
| [SCADA_MIMIC.md](SCADA_MIMIC.md) | scada-mimic | diagramJson v2, mimic REST API |
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
| [0020](decisions/0020-time-and-timezones.md) | Time & timezones (UTC storage, user/device TZ) |
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
| `scada` | Mimic diagrams, MIMIC objects, scada-mimic widget |
| `platform-logic` | Context, visibility, CEL rules |
| `bindings` | CEL, counterRate, refAt |
| `drivers` | SNMP, Modbus, virtual, MQTT |
| `workflows` | BPMN, instance cancel/signal |
| `automation` | Alerts, correlators |
| `semantic` | Haystack/Brick export, device metadata |
| `timezones` | User TZ, device TZ resolve (ADR-0020) |
| `web-console` | Admin UI, System tabs, Application lifecycle |
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

### «Создай / обнови мнемосхему» (SCADA)

1. `get_automation_schema topic=scada` или `list_mimic_symbols`
2. `create_object` type=MIMIC под `root.platform.mimics`
3. **`save_mimic_diagram`** с непустым `elements[]` (tank, valve, label, pipe…) — обязательно
4. `get_mimic_diagram` — проверить `elementCount > 0`
5. `list_variables` на устройствах → bindings в symbols
6. DASHBOARD + `add_dashboard_widget type=scada-mimic mimicPath=...`
7. **Анонимизация:** без реальных компаний, ФИО, гео-меток в демо

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

*Обновлять при добавлении новых подходов (ADR, REQ-PF), расширении Web Console UI↔API и при пересборке docChunks (`python tools/ai-pack/build.py`).*

**Последнее обновление знаний:** 2026-06-30 — UI↔API parity ~100% (Application lifecycle, platform schedules, semantic export, workflow instance control, federation proxy invoke, device TZ resolve); prod **0.9.60**.
