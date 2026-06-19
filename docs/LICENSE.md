# Лицензия и границы поставки

## Что входит в «ядро» (ветка `main`)

**IoT Solutions Platform Framework (ISPF)** — открытое ядро платформы:

| Путь | Лицензия |
|------|----------|
| `packages/ispf-*` | [Apache 2.0](../LICENSE) |
| `apps/web-console/` (платформенная консоль) | [Apache 2.0](../LICENSE) |
| `docs/` | [Apache 2.0](../LICENSE) |

Copyright: **© 2026 ISPF Core Contributors** — см. [LICENSE](../LICENSE) и [NOTICE](../NOTICE).

Ядро можно свободно использовать, изменять и распространять (включая коммерческие продукты) при соблюдении Apache License 2.0: сохранение LICENSE, NOTICE и атрибуций зависимостей.

### Патентная оговорка Apache 2.0

Лицензия включает **grant of patent license** на вклад в код ISPF и механизм прекращения лицензии при патентном иске против пользователей. Это стандартная защита для enterprise-пользователей открытого ядра.

## Что не входит в ядро

Следующее **не должно** попадать в `main` и **не** покрывается Apache 2.0 ядра:

| Тип | Где живёт | Лицензия |
|-----|-----------|----------|
| Отраслевые reference-стенды (нефтебаза P-301, oil-terminal) | Ветка `feature/oil-terminal-reference` | По условиям ветки / отдельный EULA |
| Коммерческие плагины и прикладные решения | Отдельные репозитории или ветки | **Явно указана** в `LICENSE` / `license.json` / README плагина |
| App bundle конкретного заказчика (michaael, terminal) | Репозиторий проекта, не framework | По договору с заказчиком |

Коммерческий плагин обязан содержать **собственный** файл лицензии или `license.json`. Ядро исполняет расширения через REQ-PF, но **не включает** их исходники в свой Apache 2.0-дистрибутив.

## Политика веток

```
main                          → только Apache 2.0 ядро платформы
feature/oil-terminal-reference → reference oil-terminal (не merge в main)
<plugin-repo>                 → коммерческий или открытый плагин со своей лицензией
```

## Обязательства при распространении ядра

1. Сохраните [LICENSE](../LICENSE) и [NOTICE](../NOTICE).
2. Приложите [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) к бинарным сборкам.
3. Соблюдайте условия зависимостей (особенно **bpmn-js** — watermark).

## Зависимости третьих сторон

Apache 2.0 распространяется **только на код ISPF**, не на весь стек. Подробный список: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Отказ от гарантий

Как в [LICENSE](../LICENSE): ПО «как есть», без гарантий.

## Связанные документы

- [PLUGINS.md](PLUGINS.md) — куда класть плагины и reference-стенды
- [APPLICATIONS.md](APPLICATIONS.md) — REQ-PF API
- [ARCHITECTURE.md](ARCHITECTURE.md) — reference stands
