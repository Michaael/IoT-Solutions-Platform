# Mini-TEC reference walkthrough

Сквозной **optional demo** ISPF для **АСУ ТП мини-ТЭЦ**: 3×ГПУ, ГРПБ, РУМБ, ДГУ, нагрузочный модуль, станционный hub, защиты, operator HMI с однолинейной схемой. Без custom Java в `ispf-server` (bootstrap + bundle). **Не входит в platform roadmap** — см. [ROADMAP.md § Phase 18](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers).

Артефакты: [examples/mini-tec/](../examples/mini-tec/), `appId` = `mini-tec`.

См. также [examples/mini-tec/README.md](../examples/mini-tec/README.md), agent playbook `miniTecReference()` в `AgentPlaybooks.java`.

## Домен

| Сущность | Object path | Модель |
|----------|-------------|--------|
| **ГПУ 1–3** | `root.platform.devices.mini-tec-plant.gpu-0N` | `mini-tec-gpu-v1` |
| **ГРПБ** | `...grpb` | `mini-tec-grpb-v1` |
| **РУМБ 10/0,4 кВ** | `...rumb-10kv` | `mini-tec-rumb-v1` |
| **ДГУ** | `...dgu` | `mini-tec-dgu-v1` |
| **Нагрузочный модуль** | `...load-module` | `mini-tec-load-module-v1` |
| **Станционный hub** | `...station-hub` | `mini-tec-station-hub-v1` |

Установленная мощность: **4440 кВт** (3×1480). Virtual driver profiles: `tec-gpu`, `tec-grpb`, `tec-rumb`, `tec-dgu`, `tec-load`.

## Шаги сценария

| # | Действие | API / UI | Эффект |
|---|----------|----------|--------|
| 1 | Старт server | `MiniTecPlatformBootstrap` | Модели, объекты, dashboards, workflows, automation, operator UI |
| 2 | Operator HMI | `?mode=operator&app=mini-tec` | Меню дашбордов станции |
| 3 | Станционная сводка | dashboard `mini-tec-overview` | KPI hub + ГПУ |
| 4 | Однолинейная схема | dashboard `mini-tec-single-line`, widget `mini-tec-sld` | Live SVG SLD по переменным устройств |
| 5 | Запуск ГПУ | workflow `mini-tec-gpu-start-sequence` | GRPB check → user task → start |
| 6 | Авария по газу | correlator + workflow `mini-tec-gas-emergency-trip` | Эскалация, trip |
| 7 | SQL журнал | app schema `app_mini_tec`, `tec_daily_journal` | Эксплуатационные итоги |
| 8 | Redeploy bundle | `POST /api/v1/applications/mini-tec/deploy` | Idempotent sync из `examples/mini-tec/bundle.json` |

## Дашборды

| Path | Назначение |
|------|------------|
| `root.platform.dashboards.mini-tec-overview` | Станционная сводка |
| `root.platform.dashboards.mini-tec-single-line` | Однолинейная схема (SLD widget) |
| `root.platform.dashboards.mini-tec-gpu-detail` | ГПУ — детально |
| `root.platform.dashboards.mini-tec-grpb` | ГРПБ |
| `root.platform.dashboards.mini-tec-rumb` | РУМБ |
| `root.platform.dashboards.mini-tec-dgu` | ДГУ |
| `root.platform.dashboards.mini-tec-load` | Нагрузочный модуль |
| `root.platform.dashboards.mini-tec-protections` | Защиты |
| `root.platform.dashboards.mini-tec-exploitation` | Эксплуатация |

## Привязка к REQ-PF / механизмам

| Механизм | Использование |
|----------|---------------|
| **Models** | 6 INSTANCE-моделей + binding rules на hub |
| **Virtual driver** | Профили `tec-*`, poll в `VirtualTecPoll.java` |
| **Binding rules** | Cross-object агрегаты на `station-hub` (0010) |
| **Workflow** | 4 BPMN: gas trip, load unload, GPU start, ack protection |
| **Automation** | Alert rules + correlators на защитах |
| **PF-02** | App SQL: `tec_daily_journal`, `tec_consumer_load` |
| **Operator UI** | `root.platform.operator-apps` → mini-tec menus |
