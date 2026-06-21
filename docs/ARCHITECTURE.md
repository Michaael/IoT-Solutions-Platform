# ISPF Architecture

## Vision

**IoT Solutions Platform Framework (ISPF)** — middleware-платформа для IoT, промышленной автоматизации и IT-операций. Единая модель данных и API для устройств, HMI-дашбордов, алертов и BPMN-автоматизации.

## Основной принцип: бизнес-логика в механизмах платформы

Бизнес-логика прикладного решения **живёт на платформе** — в declarative-конфигурации **дерева объектов**, а не в отраслевом Java-коде сервера.

| Механизм | Что описывает |
|----------|---------------|
| **Модели** | Blueprint: переменные, события, функции, bindings |
| **Переменные** | Состояние, вычисления (CEL, platform bindings), historian |
| **События** | Типы событий; alert rules и correlators — узлы дерева |
| **Функции** | Вызываемая логика на объекте (script, `INVOKE_FUNCTION`) |
| **Workflow** | BPMN-процессы, user tasks, эскалация |

**Платформа (framework)** реализует **generic-движки один раз**: CEL, bindings, historian, BPMN, script runtime, drivers, event bus.

**Решение (solution)** наполняет эти механизмы конфигурацией: модели, пороги, процессы, функции, дашборды.

Bundle deploy ([APPLICATIONS.md](APPLICATIONS.md)) — **упаковка и доставка** конфигурации в дерево объектов и app schema, а не отдельный runtime вне платформы.

**Запрещено в `main`:** отраслевой Java в `ispf-server`, hardcoded BFF routes, дублирование логики вне object tree.

**Развитие platform:** усиление выразительности механизмов object tree (Phase 5), чтобы больше логики выражалось declarative, без custom code. См. [ROADMAP.md § Phase 5](ROADMAP.md#phase-5--усиление-механизмов-north-star), [PLATFORM_DEVELOPER_BACKLOG.md §8.1](PLATFORM_DEVELOPER_BACKLOG.md#81-усиление-механизмов-phase-5).

## Core Domain Model

```mermaid
graph TB
    subgraph ObjectTree["Object Tree"]
        ROOT[root]
        PLATFORM[platform]
        DEVICES[devices]
        DEVICE[device instance]
        ROOT --> PLATFORM --> DEVICES --> DEVICE
    end

    DEVICE --> VARS[Variables]
    DEVICE --> FUNCS[Functions]
    DEVICE --> EVENTS[Events]
    VARS --> DR[DataRecord]
    DR --> SCHEMA[DataSchema]
    VARS --> CEL[CEL Bindings]
```

Подробнее: [OBJECT_MODEL.md](OBJECT_MODEL.md).

### Platform Object

Адресуемый узел: `root.platform.devices.pump-01`. Типы: `DEVICE`, `DASHBOARD`, `WORKFLOW`, `ALERT`, `CORRELATOR`, `PLATFORM`, `ALERT_RULES`, … Системные каталоги имеют семантический `ObjectType`, не `CUSTOM`.

### DataRecord

- `DataSchema` — поля (`FieldType`)
- `DataRecord` — строки с валидацией

### Models (Templates)

`ModelDefinition` — blueprint: variables, events, functions, bindings.  
См. [MODELS.md](MODELS.md).

### Expressions

Google CEL для bindings, alert rules, workflow gateways. Переменные объектов также поддерживают **platform bindings** (`counterRate`, `scale`, `clamp`, …) — см. [BINDINGS.md](BINDINGS.md).

```
self.temperature.value > self.threshold.value
counterRate(ifInOctets)
```

## Runtime Layers

```
┌─────────────────────────────────────────────────────────┐
│  Web Console (React 19 + Vite + TanStack Query)         │
│  Admin │ Operator HMI │ Dashboard/Workflow builders     │
├─────────────────────────────────────────────────────────┤
│  API Layer (Spring Boot 4.0, Java 25)                   │
│  REST / WebSocket / OAuth2 JWT / RBAC                   │
├─────────────────────────────────────────────────────────┤
│  Domain Services                                        │
│  ObjectManager │ EventService │ WorkflowService         │
│  DashboardService │ AlertRuleService │ CorrelatorService│
│  DriverRuntimeService │ ModelEngine                     │
│  ApplicationPlatform (functions, data, BFF, scheduler)  │
├─────────────────────────────────────────────────────────┤
│  Plugins & Libraries                                    │
│  ispf-core │ ispf-expression │ ispf-plugin-model         │
│  ispf-plugin-workflow                                   │
├─────────────────────────────────────────────────────────┤
│  Driver SPI                                             │
│  virtual │ mqtt │ modbus-tcp │ snmp                     │
├─────────────────────────────────────────────────────────┤
│  Persistence & Messaging                                │
│  PostgreSQL/H2 │ Flyway │ NATS* │ MQTT*                 │
└─────────────────────────────────────────────────────────┘
```

## Package Map

| Package | Role |
|---------|------|
| `ispf-core` | ObjectTree, PlatformObject, DataRecord |
| `ispf-expression` | CEL engine, BindingEvaluator |
| `ispf-driver-*` | Device protocol adapters |
| `ispf-plugin-model` | Model registry & engine |
| `ispf-plugin-workflow` | BPMN parser & executor |
| `ispf-server` | Spring Boot wiring, REST, JPA, security |

## Security Model

OAuth2 JWT (Keycloak) или header-based RBAC (`local`).  
Roles: `admin`, `operator`.  
См. [SECURITY.md](SECURITY.md).

## Data Flow: Telemetry

```
DeviceDriver.readPoints()
  → DriverRuntimeService
  → ObjectManager.setVariableValue()
  → BindingEvaluator (CEL)
  → AlertRuleListener
  → ObjectChangeEvent → WebSocket → Web Console
```

## Data Flow: Automation

```
Event fire → event_history
  → EventCorrelatorListener → WorkflowService.run
  → UserTask → WorkQueue → Operator HMI
```

## Deployment Topology

**Development:** `docker compose` + Gradle bootRun + Vite dev server.

**Production (target):**

- `ispf-server` — stateless replicas
- Managed PostgreSQL, Redis, NATS
- Keycloak / OIDC
- Static web-console behind CDN/ingress

**Горизонтальное масштабирование ≠ федерация:** реплики делят одну БД и одно дерево `root.platform.*`. Несколько площадок / edge-агентов с единым каталогом в консоли — **roadmap P3+**, см. [REQ-PF-13](PLATFORM_DEVELOPER_BACKLOG.md#9-распределённая-архитектура-и-федерация-roadmap-p3).

См. [DEPLOYMENT.md](DEPLOYMENT.md).

## Extension Points

1. **DeviceDriver** — новый протокол ([DRIVERS.md](DRIVERS.md))
2. **ModelDefinition** — шаблон устройства/процесса ([MODELS.md](MODELS.md))
3. **FunctionHandler** — бизнес-операции на объектах
4. **Dashboard widgets** — новые типы в web-console ([DASHBOARDS.md](DASHBOARDS.md))
5. **REST / Webhook** — внешние интеграции ([API.md](API.md))
6. **NATS subjects** — messageTask в BPMN ([WORKFLOWS.md](WORKFLOWS.md))
7. **Application bundle** — deploy функций и миграций **вне** ядра ([APPLICATIONS.md](APPLICATIONS.md), [PLUGINS.md](PLUGINS.md))

Коммерческие и отраслевые расширения **не** входят в Apache 2.0-дерево `main`.

## Reference Stands

| Stand | Branch | Description |
|-------|--------|-------------|
| Demo sensor | `main` | virtual driver, alert, workflow |

## Documentation Index

Полный комплект: [docs/README.md](README.md).
