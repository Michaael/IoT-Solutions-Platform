# Плагины и прикладные решения

## Принцип

| Слой | Репозиторий / ветка | Лицензия |
|------|---------------------|----------|
| **Ядро ISPF** | `main` | Apache 2.0 |
| **Reference / отраслевые стенды** | Отдельная ветка (не `main`) | Указана в ветке |
| **Коммерческие плагины** | Отдельный репозиторий или артефакт | Commercial / EULA — **явно в пакете** |
| **Проект заказчика** (michaael, terminal) | Репозиторий проекта | По договору |

Ядро **не содержит** бизнес-логику отраслей и **не смешивается** с коммерческими модулями в одном Apache 2.0-дереве `main`.

## Как подключать расширения (без Java в ядре)

С платформенным слоем REQ-PF прикладное решение разворачивается **через API**, не через merge в `ispf-server`:

1. `POST /api/v1/applications` — регистрация `appId`
2. `POST /api/v1/applications/{appId}/data/migrate` — SQL-таблицы приложения
3. `POST /api/v1/applications/{appId}/functions/deploy` — script-функции
4. `POST /api/v1/applications/{appId}/deploy` — bundle одним запросом
5. Объекты, dashboards, BPMN — через существующие REST API платформы

См. [APPLICATIONS.md](APPLICATIONS.md).

## Коммерческий плагин: требования к поставке

Каждый коммерческий плагин **обязан** включать:

- `LICENSE` или `license.json` с явным типом лицензии
- `README` с ограничениями использования и контактом правообладателя
- Версию и список совместимых версий ISPF

Плагин **не коммитится** в `packages/ispf-server/` и **не** вливается в `main` без отдельного решения о open-source.

## Reference: oil-terminal (P-301)

| | |
|---|---|
| Ветка | `feature/oil-terminal-reference` |
| В `main` | **Нет** — ни Java-плагина, ни `examples/terminal-app`, ни operator SCR |
| Назначение | Демо полного процесса для разработчиков и smoke-тестов |

Для работы с нефтебазой переключитесь на ветку:

```bash
git fetch origin
git checkout feature/oil-terminal-reference
```

## Чеклист перед PR в `main`

- [ ] Нет кода `terminal`, `oil-terminal`, отраслевых BFF
- [ ] Нет Flyway-миграций таблиц приложения в `packages/ispf-server/.../db/migration/`
- [ ] Нет `examples/<industry-app>/` в корне репозитория
- [ ] Новые возможности платформы — только generic REQ-PF / API / docs

## Связь

- [LICENSE.md](LICENSE.md) — Apache 2.0 ядра vs коммерческие модули
- [APPLICATIONS.md](APPLICATIONS.md) — deploy API
