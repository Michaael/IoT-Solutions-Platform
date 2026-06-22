# IoT Solutions Platform Framework (ISPF)

Современная IoT/SCADA платформа на cloud-native стеке 2026 года.

**Статус:** активная разработка, `main` — рабочая ветка с полным циклом admin + operator HMI, application platform (REQ-PF) и production-ready local/dev профилями.

## Концепция

**Основной принцип:** бизнес-логика решения живёт **на платформе** — в моделях, переменных, событиях, функциях и workflow дерева объектов. Ядро — generic-движки; решение — declarative-конфигурация (в т.ч. через bundle deploy). См. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#основной-принцип-бизнес-логика-в-механизмах-платформы).

ISPF строится вокруг **иерархического дерева объектов** с типизированными переменными, событиями, функциями и вычисляемыми привязками:

| Концепция | Реализация |
|-----------|------------|
| Дерево объектов | `ObjectTree` / REST API, drag-and-drop порядок соседей |
| Типизированные данные | `DataRecord` + `DataSchema` |
| Вычисляемые привязки | Google CEL (`ispf-expression`) |
| Драйверы устройств | `DeviceDriver` SPI — **58 driverId** (Modbus, OPC UA, SNMP, MQTT, JDBC, …) |
| HMI | Dashboard builder — 14 типов виджетов, история переменных на графиках |
| Автоматизация | BPMN workflow, alert rules и correlators **как узлы дерева** |
| Прикладной слой | Application platform: deploy bundle, функции, отчёты, BFF, scheduler |
| Хранение | PostgreSQL + TimescaleDB (prod), H2 (local/test); Redis, NATS — опционально |
| UI | Spring Boot 4.0 + Java 25 + React 19 (Vite) |
| Интеграция | REST + WebSocket |

## Текущий статус (июнь 2026)

### Web Console (админ)

| Область | Состояние |
|---------|-----------|
| **Обозреватель** | Дерево объектов, инспекторы, создание дочерних узлов, **перетаскивание порядка** на одном уровне |
| **Система** | Вкладка метрик платформы: runtime, БД, драйверы, automation, security |
| Alert rules / correlators | Папки `root.platform.alert-rules` и `root.platform.correlators`, инспекторы в Explorer |
| Security | Пользователи и роли в дереве (`root.platform.security`) |
| Applications | Deploy bundle, функции, отчёты, operator screens — узлы под `root.platform.applications` |
| Operator Apps | Конфигурация HMI: `root.platform.operator-apps` |
| Models / Dashboards / Workflows | Редакторы в workspace-вкладках |

### Backend

| Область | Состояние |
|---------|-----------|
| Object tree | JPA-персистентность, `sortOrder`, семантические `ObjectType` для системных узлов |
| Variable historian | Запись samples, export CSV/JSON, агрегации, виджеты с историей |
| Automation | CEL alert rules → события; correlators COUNT/SEQUENCE → workflow |
| Workflow | BPMN engine, work queue, user tasks, сигналы |
| Platform metrics | `GET /api/v1/platform/metrics` (admin) |
| Application platform | REQ-PF: migrate, deploy, functions, reports, BFF, schedules, SQL bindings |
| Security / OIDC | Local token auth + Keycloak JWT (resource server), per-object ACL |
| Federation | Peer registry, catalog sync, proxy read (objects, dashboards, history) |
| Platform baseline | Java 25, Spring Boot 4.0.7, Jackson 3 (`tools.jackson`) |

### Типы системных узлов (вместо `CUSTOM`)

| Путь | Тип |
|------|-----|
| `root.platform` | `PLATFORM` |
| `root.platform.devices` | `DEVICES` |
| `root.platform.dashboards` | `DASHBOARDS` |
| `root.platform.workflows` | `WORKFLOWS` |
| `root.platform.alert-rules` | `ALERT_RULES` |
| `root.platform.correlators` | `CORRELATORS` |
| `root.platform.applications` | `APPLICATIONS` |
| `root.platform.operator-apps` | `OPERATOR_APPS` |
| `root.platform.security` | `SECURITY` |

Экземпляры: `DEVICE`, `DASHBOARD`, `WORKFLOW`, `ALERT`, `CORRELATOR`, `APPLICATION`, `REPORT`, `FUNCTION`, …

## Документация

**[Обзор продукта → docs/PRODUCT.md](docs/PRODUCT.md)** — возможности, сценарии, роли.

| Раздел | Файл |
|--------|------|
| Обзор продукта | [docs/PRODUCT.md](docs/PRODUCT.md) |
| Руководство оператора | [docs/OPERATOR_GUIDE.md](docs/OPERATOR_GUIDE.md) |
| Разработчик решений | [docs/SOLUTION_DEVELOPER_GUIDE.md](docs/SOLUTION_DEVELOPER_GUIDE.md) |
| Public API (solution) | [docs/SOLUTION_DEVELOPER_PUBLIC_API.md](docs/SOLUTION_DEVELOPER_PUBLIC_API.md) |
| ADR (архитектурные решения) | [docs/decisions/README.md](docs/decisions/README.md) |
| Gap registry | [docs/GAP_REGISTRY.md](docs/GAP_REGISTRY.md) |
| Commercial bundle licensing | [docs/COMMERCIAL_LICENSING.md](docs/COMMERCIAL_LICENSING.md) |
| MES reference walkthrough | [docs/REFERENCE_MES_WALKTHROUGH.md](docs/REFERENCE_MES_WALKTHROUGH.md) |
| Глоссарий | [docs/GLOSSARY.md](docs/GLOSSARY.md) |
| Быстрый старт | [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md) |
| Архитектура | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Модель объектов | [docs/OBJECT_MODEL.md](docs/OBJECT_MODEL.md) |
| История переменных | [docs/VARIABLE_HISTORY.md](docs/VARIABLE_HISTORY.md) |
| REST API | [docs/API.md](docs/API.md) |
| Драйверы | [docs/DRIVERS.md](docs/DRIVERS.md) |
| Модели | [docs/MODELS.md](docs/MODELS.md) |
| Дашборды | [docs/DASHBOARDS.md](docs/DASHBOARDS.md) |
| Workflow / BPMN | [docs/WORKFLOWS.md](docs/WORKFLOWS.md) |
| Приложения (REQ-PF) | [docs/APPLICATIONS.md](docs/APPLICATIONS.md) |
| Плагины и границы | [docs/PLUGINS.md](docs/PLUGINS.md) |
| Автоматизация | [docs/AUTOMATION.md](docs/AUTOMATION.md) |
| Web Console | [docs/WEB_CONSOLE.md](docs/WEB_CONSOLE.md) |
| Безопасность | [docs/SECURITY.md](docs/SECURITY.md) |
| Federation | [docs/FEDERATION.md](docs/FEDERATION.md) |
| Развёртывание | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| Тестирование | [docs/TESTING.md](docs/TESTING.md) |
| Backlog платформы | [docs/PLATFORM_DEVELOPER_BACKLOG.md](docs/PLATFORM_DEVELOPER_BACKLOG.md) |
| Roadmap | [docs/ROADMAP.md](docs/ROADMAP.md) |
| Лицензия | [LICENSE](LICENSE) (Apache 2.0, ядро) |

**[Полный индекс → docs/README.md](docs/README.md)**

## Структура монорепозитория

```
iot-solutions-platform-framework/
├── packages/
│   ├── ispf-core/              # Домен: объекты, DataRecord, ObjectType
│   ├── ispf-expression/        # CEL-движок
│   ├── ispf-driver-api/        # SPI драйверов
│   ├── ispf-driver-*/          # 58 protocol drivers
│   ├── ispf-plugin-model/      # Models plugin
│   ├── ispf-plugin-workflow/   # BPMN engine
│   └── ispf-server/            # Spring Boot API + JPA + Flyway
├── apps/web-console/           # React-консоль (Explorer, System, Operator)
├── examples/                   # demo-app, warehouse-app, mes-reference, lab-training
├── tools/license-builder/      # RSA keys + sign commercial bundle
├── docs/                       # Документация (Apache 2.0)
├── deploy/                     # Mosquitto config
└── docker-compose.yml
```

## Быстрый старт

```bash
# Инфраструктура (опционально для dev)
docker compose up -d

# API (local — без OAuth, H2)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# Web Console
cd apps/web-console && npm install && npm run dev
```

| URL | Назначение |
|-----|------------|
| http://localhost:8080/api/v1/info | Версия и capabilities |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:5173 | Web Console (admin) |
| http://localhost:5173?mode=operator | Operator HMI |

Подробнее: [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)

## Демо-объекты

| Путь | Назначение |
|------|------------|
| `root.platform.devices.demo-sensor-01` | Virtual sensor + alarm |
| `root.platform.devices.snmp-localhost` | SNMP agent (Windows/Linux) |
| `root.platform.dashboards.demo-sensor` | HMI dashboard |
| `root.platform.workflows.demo-alarm-handler` | BPMN demo |
| `root.platform.alert-rules.temperature-threshold-exceeded` | CEL alert rule |
| `root.platform.correlators.alarm-handler-on-threshold-event` | Event correlator → workflow |

## RBAC

| Профиль | Механизм |
|---------|----------|
| `local` | `X-ISPF-Role: admin\|operator` |
| `dev` | JWT Keycloak, realm `ispf` |

См. [docs/SECURITY.md](docs/SECURITY.md).

## Тесты

```bash
./gradlew test
```

## Дорожная карта

### Готово

- [x] Персистентность объектов (JPA ↔ PostgreSQL/H2)
- [x] WebSocket live-updates
- [x] 58 встроенных драйверов (REQ-PF-14)
- [x] Dashboard builder — 14 типов виджетов
- [x] BPMN editor + workflow engine (gateways, user tasks, parallel)
- [x] Operator HMI, work queue, event journal
- [x] Alert rules (CEL) + event correlators **в дереве объектов**
- [x] RBAC admin/operator, users/roles в object tree
- [x] Application platform: deploy, functions, reports, BFF, scheduler
- [x] Variable historian: samples, export, aggregate, dashboard widgets
- [x] Platform metrics (admin System tab)
- [x] Object tree: drag-and-drop порядок, семантические типы узлов
- [x] Документация синхронизирована с object-tree моделью автоматизации
- [x] CI (GitHub Actions), PF-01c map/buildRecord, models[] в bundle, leader locks, WebSocket auth
- [x] **Phase 16 (REQ-FW Sprint E–G):** ADR, licensing, MES reference, public API, events catalog, messaging, bundle `requires[]`, **AI Development Layer**

### В работе / далее

- **Sprint G (REQ-FW):** licensed driver JAR packs (FW-50)
- **Phase 5** — усиление механизмов object tree (модели, функции, события, workflow, bundle как упаковка дерева)

См. [docs/ROADMAP.md](docs/ROADMAP.md).

## Лицензия

**[Apache License 2.0](LICENSE)** — © 2026 ISPF Core Contributors (только ядро в `main`).  
См. также [NOTICE](NOTICE).

Коммерческие плагины и app bundle заказчика — вне `main`, со своей лицензией.  
Подробнее: [docs/LICENSE.md](docs/LICENSE.md), [docs/PLUGINS.md](docs/PLUGINS.md).
