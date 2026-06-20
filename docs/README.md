# Документация ISPF

**IoT Solutions Platform Framework** — middleware-платформа для IoT, SCADA и промышленной автоматизации.

## Содержание

| Раздел | Описание |
|--------|----------|
| [Быстрый старт](GETTING_STARTED.md) | Установка, профили, первый запуск |
| [Архитектура](ARCHITECTURE.md) | Видение, слои, расширяемость |
| [Модель объектов](OBJECT_MODEL.md) | Дерево, переменные, события, функции, CEL |
| [REST API](API.md) | Полный справочник endpoints |
| [Приложения (REQ-PF)](APPLICATIONS.md) | Deploy функций, миграций, bundle, BFF, scheduler |
| [Отчёты (REQ-PF-12)](REPORTS.md) | SQL reports, CSV export, operator manifest |
| [Backlog разработчика platform](PLATFORM_DEVELOPER_BACKLOG.md) | Статус REQ-PF, gap, sprint roadmap, каталог драйверов (§10) |
| [WebSocket](API.md#websocket) | Live-обновления объектов |
| [Модели (Models)](MODELS.md) | Шаблоны объектов, типы, встроенные модели |
| [Драйверы](DRIVERS.md) | 58 встроенных драйверов — полный каталог REQ-PF-14 |
| [Дашборды и виджеты](DASHBOARDS.md) | HMI builder, 14 типов виджетов, layout JSON |
| [Workflow / BPMN](WORKFLOWS.md) | Движок, ISPF-расширения, work queue |
| [Автоматизация](AUTOMATION.md) | События, alert rules, correlators |
| [Web Console](WEB_CONSOLE.md) | Админка, operator HMI, роли |
| [Безопасность](SECURITY.md) | RBAC, Keycloak, профили |
| [Развёртывание](DEPLOYMENT.md) | Docker, переменные окружения, профили Spring |
| [Тестирование](TESTING.md) | Unit, integration, smoke |
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

| Путь | Тип | Модель |
|------|-----|--------|
| `root.platform.devices.demo-sensor-01` | DEVICE | `mqtt-sensor-v1` |
| `root.platform.devices.snmp-localhost` | DEVICE | `snmp-agent-v1` |
| `root.platform.dashboards.demo-sensor` | DASHBOARD | `dashboard-v1` |
| `root.platform.workflows.demo-alarm-handler` | WORKFLOW | `workflow-v1` |

## Структура репозитория

```
packages/
  ispf-core/              # Домен: ObjectTree, DataRecord, PlatformObject
  ispf-expression/        # Google CEL, BindingEvaluator
  ispf-driver-api/        # SPI DeviceDriver
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
