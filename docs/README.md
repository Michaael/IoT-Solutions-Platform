# Документация ISPF

**IoT Solutions Platform Framework** — middleware-платформа для IoT, SCADA и промышленной автоматизации.

## Документация по продукту

| Документ | Аудитория | Описание |
|----------|-----------|----------|
| **[Обзор продукта](PRODUCT.md)** | Все | Возможности, сценарии, карта документации |
| [Руководство оператора](OPERATOR_GUIDE.md) | Оператор | HMI, work queue, события |
| [Руководство разработчика решений](SOLUTION_DEVELOPER_GUIDE.md) | App developer | Deploy приложений, operator UI, bundle |
| **[Принципы создания приложения](APPLICATION_PRINCIPLES.md)** | App developer, Agent | Свод P1–P10: north star, выбор подхода, анти-паттерны |
| [Public API (solution developer)](SOLUTION_DEVELOPER_PUBLIC_API.md) | App developer | Стабильная граница platform ↔ bundle |
| [Глоссарий](GLOSSARY.md) | Все | Термины и определения |

## Техническая документация

| Раздел | Описание |
|--------|----------|
| [Быстрый старт](GETTING_STARTED.md) | Установка, профили, первый запуск |
| [Архитектура](ARCHITECTURE.md) | Видение, **основной принцип**, слои, расширяемость |
| [Модель объектов](OBJECT_MODEL.md) | Дерево, переменные, события, функции |
| [Функции на объектах](OBJECT_FUNCTIONS.md) | Примеры: встроенные handlers, script, Java, invoke |
| [Привязки переменных](BINDINGS.md) | CEL, platform bindings (`counterRate`, `scale`, …) |
| [Единая логика платформы](PLATFORM_LOGIC.md) | Platform Rule: bindings + dashboard context + events |
| [История переменных](VARIABLE_HISTORY.md) | Time-series, флаги, retention, roadmap |
| [REST API](API.md) | Полный справочник endpoints |
| [Приложения (REQ-PF)](APPLICATIONS.md) | Deploy функций, миграций, bundle, BFF, scheduler |
| [Отчёты (REQ-PF-12)](REPORTS.md) | SQL reports, CSV export, operator manifest |
| [Backlog разработчика platform](PLATFORM_DEVELOPER_BACKLOG.md) | Статус REQ-PF, REQ-FW (§12), [gap registry](GAP_REGISTRY.md), sprint roadmap |
| [ADR (архитектурные решения)](decisions/README.md) | ADR-0001…0019 |
| [Roadmap](ROADMAP.md) | Единый roadmap platform + production ops; **Phase 5** — усиление механизмов |
| [Эволюция платформы](PLATFORM_EVOLUTION.md) | Ретроспективный чеклист: что сделано по порядку, как ISPF развивалась |
| [WebSocket](API.md#websocket) | Live-обновления объектов |
| [Модели (Models)](MODELS.md) | Шаблоны объектов, типы, встроенные модели |
| [Драйверы](DRIVERS.md) | 58 встроенных драйверов — полный каталог REQ-PF-14 |
| [Дашборды и виджеты](DASHBOARDS.md) | HMI builder, layout JSON, `objectPath` / `selectionKey` |
| **[Справочник виджетов](WIDGETS.md)** | Все типы: настройки, использование на HMI, примеры JSON |
| [Spreadsheet widget](SPREADSHEET_WIDGET.md) | Формулы, функции `SUM`/`ISPREF`, binding-ячейки, сохранение таблиц |
| [Lab Training (18 заданий)](LAB_TRAINING.md) | Virtual lab device, bundle import, упражнения Phase 15 |
| [MES reference walkthrough](REFERENCE_MES_WALKTHROUGH.md) | Сквозной MES demo (`examples/mes-reference/`) |
| [Mini-TEC reference walkthrough](REFERENCE_MINI_TEC_WALKTHROUGH.md) | Optional demo АСУ ТП мини-ТЭЦ (`examples/mini-tec/`, bootstrap при fixtures) |
| [Messaging contract](MESSAGING.md) | NATS, WebSocket, sync RPC |
| [AI Development Layer](AI_DEVELOPMENT.md) | LlmProvider, ContextPack, ToolRegistry, Studio (FW-40…43) |
| **[Agent knowledge (internal)](AGENT_KNOWLEDGE.md)** | Подходы к созданию приложений A–H, полная карта docs для агента |
| [Commercial bundle licensing](COMMERCIAL_LICENSING.md) | RSA license при deploy commercial bundle |
| [Workflow / BPMN](WORKFLOWS.md) | Движок, ISPF-расширения, work queue |
| [Автоматизация](AUTOMATION.md) | События, alert rules, correlators |
| [Web Console](WEB_CONSOLE.md) | Админка, operator HMI, роли |
| [Безопасность](SECURITY.md) | RBAC, Keycloak, профили |
| [Развёртывание](DEPLOYMENT.md) | Docker, переменные окружения, профили Spring |
| [Тестирование](TESTING.md) | Unit, integration, smoke |
| [Load testing](LOAD_TESTING.md) | HTTP vs internal automation throughput, baselines |
| [Observability](OBSERVABILITY.md) | Prometheus scrape, OTLP metrics export |
| [Лицензия](LICENSE.md) | Apache 2.0 ядро; коммерческие плагины отдельно |
| [Плагины и границы](PLUGINS.md) | Что не входит в `main` |
| [Third-party](THIRD_PARTY_NOTICES.md) | Лицензии зависимостей (bpmn-js, Spring, …) |

## Быстрые ссылки

- Репозиторий: монорепозиторий Gradle + npm
- API: `http://localhost:8080/api/v1`
- Health: `GET /actuator/health`
- Web Console: `http://localhost:5173` (dev)
- Operator HMI: `http://localhost:5173?mode=operator`

## Демо-объекты (после первого запуска)

Требуют `ispf.bootstrap.fixtures-enabled=true` (default). Fixture-модели регистрируются `FixtureModelBootstrap`.

| Путь | Тип | Модель (templateId) |
|------|-----|---------------------|
| `root.platform.devices.demo-sensor-01` | DEVICE | `mqtt-sensor-v1` (fixture) |
| `root.platform.devices.snmp-localhost` | DEVICE | `snmp-agent-v1` (fixture) |
| `root.platform.dashboards.demo-sensor` | DASHBOARD | `dashboard-v1` |
| `root.platform.workflows.demo-alarm-handler` | WORKFLOW | `workflow-v1` |

См. [MODELS.md](MODELS.md), [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

## Структура репозитория

```
packages/
  ispf-core/              # Домен: ObjectTree, DataRecord, PlatformObject
  ispf-expression/        # Google CEL, BindingExpressionEvaluator
  ispf-driver-api/        # SPI DeviceDriver
  ispf-ai-api/            # SPI LlmProvider (FW-40)
  ispf-ai-openai-compatible/  # OpenAI-compatible adapter
  ispf-ai-ollama/         # Ollama adapter
  ispf-driver-*/          # 58 protocol drivers (см. DRIVERS.md)
  ispf-plugin-model/      # Models plugin
  ispf-plugin-workflow/   # BPMN workflow engine (library)
  ispf-server/            # Spring Boot API
apps/
  web-console/            # React 19 + Vite
docs/                     # Эта документация
deploy/                   # Mosquitto config
docker-compose.yml        # PostgreSQL, Redis, NATS, MQTT, Keycloak
```

## Лицензия и границы

- **Ядро** (`main`): [Apache 2.0](LICENSE) + [NOTICE](../NOTICE) — `packages/ispf-*`, web-console, docs
- **Коммерческие плагины и app bundle**: отдельные репозитории, явная лицензия в пакете — [docs/PLUGINS.md](docs/PLUGINS.md)
- Подробнее: [docs/LICENSE.md](docs/LICENSE.md), [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)
