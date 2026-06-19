# IoT Solutions Platform Framework (ISPF)

Современная IoT/SCADA платформа на cloud-native стеке 2026 года.

## Концепция

ISPF строится вокруг **иерархического дерева объектов** с типизированными переменными, событиями, функциями и вычисляемыми привязками:

| Концепция | Реализация |
|-----------|------------|
| Дерево объектов | `ObjectTree` / REST API |
| Типизированные данные | `DataRecord` + `DataSchema` |
| Вычисляемые привязки | Google CEL (`ispf-expression`) |
| Драйверы устройств | `DeviceDriver` SPI (MQTT, Modbus, SNMP, Virtual) |
| HMI | Dashboard builder — 14 типов виджетов |
| Автоматизация | BPMN workflow, alert rules, event correlators |
| Хранение | PostgreSQL + TimescaleDB, Redis, NATS |
| UI | Spring Boot 3.4 + React 19 (Vite) |
| Интеграция | REST + WebSocket |

## Документация

**[Полная документация → docs/README.md](docs/README.md)**

| Раздел | Файл |
|--------|------|
| Быстрый старт | [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md) |
| Архитектура | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Модель объектов | [docs/OBJECT_MODEL.md](docs/OBJECT_MODEL.md) |
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
| Развёртывание | [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) |
| Тестирование | [docs/TESTING.md](docs/TESTING.md) |
| Лицензия | [LICENSE](LICENSE) (Apache 2.0, ядро) |

## Структура монорепозитория

```
iot-solutions-platform-framework/
├── packages/
│   ├── ispf-core/              # Домен: объекты, DataRecord
│   ├── ispf-expression/        # CEL-движок
│   ├── ispf-driver-api/        # SPI драйверов
│   ├── ispf-driver-mqtt/       # MQTT (Paho)
│   ├── ispf-driver-modbus/     # Modbus TCP
│   ├── ispf-driver-snmp/       # SNMP v1/v2c
│   ├── ispf-driver-virtual/    # Симулятор
│   ├── ispf-plugin-model/      # Models plugin
│   ├── ispf-plugin-workflow/   # BPMN engine
│   └── ispf-server/            # Spring Boot API
├── apps/web-console/           # React-консоль
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

- API: http://localhost:8080
- Console: http://localhost:5173
- Operator HMI: http://localhost:5173?mode=operator

Подробнее: [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)

## Демо-объекты

| Путь | Назначение |
|------|------------|
| `root.platform.devices.demo-sensor-01` | Virtual sensor + alarm |
| `root.platform.dashboards.demo-sensor` | HMI dashboard |
| `root.platform.workflows.demo-alarm-handler` | BPMN demo |

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

- [x] Персистентность объектов (JPA ↔ PostgreSQL/H2)
- [x] WebSocket live-updates
- [x] Драйверы: Modbus, SNMP, Virtual, MQTT
- [x] Dashboard builder — 14 типов виджетов
- [x] BPMN editor + workflow engine (gateways, user tasks, parallel)
- [x] Operator HMI, work queue, event journal
- [x] Alert rules (CEL) + event correlators
- [x] RBAC admin/operator

## Лицензия

**[Apache License 2.0](LICENSE)** — © 2026 ISPF Core Contributors (только ядро в `main`).  
См. также [NOTICE](NOTICE).

Коммерческие плагины и reference-стенды (oil-terminal) — вне `main`, со своей лицензией.  
Подробнее: [docs/LICENSE.md](docs/LICENSE.md), [docs/PLUGINS.md](docs/PLUGINS.md).
