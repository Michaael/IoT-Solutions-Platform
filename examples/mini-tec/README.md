# Мини-ТЭЦ (эталон) на ISPF

Эталонный цифровой двойник АСУ ТП: 3×ГПУ, ГРПБ, РУМБ 10/0,4 кВ, ДГУ, нагрузочный модуль, станционный hub, защиты, operator HMI.

## Быстрый старт

После запуска `ispf-server` решение поднимается автоматически (`MiniTecPlatformBootstrap`).

**Operator UI:** `?mode=operator&app=mini-tec`

**Станционная сводка:** `?mode=operator&app=mini-tec&dashboard=root.platform.dashboards.mini-tec-overview`

## Дерево объектов

| Путь | Назначение |
|------|------------|
| `root.platform.devices.mini-tec-plant` | Папка станции |
| `...gpu-01` … `gpu-03` | Газопоршневые модули |
| `...grpb` | ГРПБ |
| `...rumb-10kv` | РУМБ 10/0,4 кВ |
| `...dgu` | ДГУ |
| `...load-module` | Нагрузочный модуль |
| `...station-hub` | Агрегаты, островной режим, защиты шин |

## Симуляция

Virtual driver profiles: `tec-gpu`, `tec-grpb`, `tec-rumb`, `tec-dgu`, `tec-load` (`VirtualTecPoll.java`).

## Деплой bundle

```http
POST /api/v1/applications/mini-tec/deploy
Content-Type: application/json

<file: examples/mini-tec/bundle.json>
```

## Потребители (номинальная нагрузка)

| Потребитель | Переменная | кВт |
|-------------|------------|-----|
| Потребитель 1 | `consumerLoad1Kw` | 2430 |
| Потребитель 2 | `consumerLoad2Kw` | 1200 |
| Резерв | `consumerLoad3Kw` | 500 |

Установленная мощность станции: **4440 кВт** (3×1480).

## Модели

`mini-tec-gpu-v1`, `mini-tec-grpb-v1`, `mini-tec-rumb-v1`, `mini-tec-dgu-v1`, `mini-tec-load-module-v1`, `mini-tec-station-hub-v1` — см. `MiniTecModelBootstrap.java`.
