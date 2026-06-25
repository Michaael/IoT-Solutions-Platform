# Load testing ISPF automation

Нагрузочные сценарии для измерения пропускной способности **HTTP events API** и **внутреннего конвейера автоматизации** (driver → alert rule → event journal).

Baseline зафиксирован на prod VPS `ispf.iot-solutions.ru`, версия **0.9.18**, июнь 2026.

См. также [OBSERVABILITY.md](OBSERVABILITY.md) — Prometheus scrape и OTLP export.

## Три контура

| Контур | Скрипт | Что измеряет |
|--------|--------|--------------|
| **HTTP** | `deploy/events-load-test.py` | `POST /api/v1/events/fire` + `GET /api/v1/events` с клиента |
| **Internal (poll)** | `deploy/events-internal-load-test.py` | Virtual driver → `sineWave` → alert → journal |
| **MQTT ingress (subscribe)** | `deploy/mqtt-ingress-load-test.py` | Реальный брокер → mqtt driver **подписка** → alert → journal |
| **MQTT ingress (push, lab)** | `--mode push` | Синтетический publisher → local Mosquitto |

**Перед каждым прогоном** скрипты останавливают фоновую нагрузку и loadtest-фикстуры:

- **Драйверы:** все DEVICE (рекурсивно: mini-TEC, demo-sensor, mqtt-lab, …), кроме `platform-metrics-probe`
- **Alert rules:** отключаются все не-loadtest; loadtest rules удаляются (mqtt-тест)
- **Correlators / schedules:** отключаются все не-loadtest

```powershell
python deploy/loadtest-cleanup.py
python deploy/loadtest-cleanup.py --purge-mqtt --purge-virtual
python deploy/loadtest-cleanup.py --keep-background   # только loadtest, без отключения demo
```

После теста demo-объекты остаются в дереве; alert rules и schedules нужно включить вручную или перезапустить сервер (bootstrap mini-TEC не пересоздаёт уже существующие правила, но драйверы mini-TEC снова стартуют при рестарте).

## MQTT ingress (subscribe — default)

ISPF **не публикует** в брокер — mqtt driver **подписывается** на живые топики. Топики: `deploy/mqtt-real-topics.json` (брокер `m5.wqtt.ru`). ISPF-сервер должен достучаться до брокера. Lab на VPS: `mqtt-local-topics.json` + `--mode push`.

```powershell
python deploy/mqtt-ingress-load-test.py --mode subscribe --devices 4 --phase-seconds 60 --skip-monitor-setup
```

| Флаг | Default | Описание |
|------|---------|----------|
| `--mode` | `subscribe` | `subscribe` = реальный брокер; `push` = lab publisher |
| `--topics-file` | `mqtt-real-topics.json` | brokerUrl + topics |
| `--skip-cleanup` | false | Не останавливать `loadtest-dev-*` |

Lab push (локальный Mosquitto, `--mode push`) — только для синтетики, см. `mqtt-loadtest-publisher.py`.

## Internal load test (virtual driver)

## Подготовка окружения

### 1. Loadtest-устройства

```powershell
python deploy/vps-load-test.py --seed-only --devices 60
```

Создаёт `root.platform.devices.loadtest-dev-*` (шаблон `virtual-lab-v1`).

### 2. Мониторинг (probe + dashboard)

```powershell
python deploy/setup-platform-metrics-monitor.py --base-url https://ispf.iot-solutions.ru
```

- Probe: `root.platform.devices.platform-metrics-probe`
- Dashboard: `root.platform.dashboards.platform-metrics`
- Синхронизирует `GET /api/v1/platform/metrics` → переменные probe (events/s, alert fires/s, heap, DB pool, queue depth)

Для prod включите probe syncer на VPS или запускайте syncer из load-test скриптов (они делают это автоматически).

### 3. Platform metrics API

Admin-only: `GET /api/v1/platform/metrics` — секция `automation`:

| Поле | Смысл |
|------|--------|
| `eventHistoryRecords` | Размер журнала событий (PostgreSQL) |
| `alertFiresTotal` | Счётчик срабатываний alert rules (in-memory, platform-wide) |
| `objectChangeQueueSize` | Глубина async-очереди object-change bus |
| `eventJournalQueueSize` | Очередь async writer журнала |

Prometheus: `/actuator/prometheus` (admin role) — counters `ispf.events.fired.total`, `ispf.alert.fires.total`, gauges `ispf.object_change.queue.size{lane=telemetry|automation|total}`, `ispf.event_history.records`, `ispf.workflow_instances.running`, `ispf.variable_history.samples`, `ispf.drivers.active`, `ispf.database.connections.*`.

## HTTP load test

```powershell
python deploy/events-load-test.py `
  --base-url https://ispf.iot-solutions.ru `
  --concurrency 40 `
  --duration-seconds 60
```

**Baseline (0.9.5, 60 devices, concurrency 40):** ~147–164 RPS на `POST /events/fire`.

JUnit-аналог: `EventFireLoadTest` (150 concurrent HTTP).

## Internal load test

```powershell
# Авто-cleanup: останавливает mqtt loadtest, пересоздаёт virtual alert rules
python deploy/events-internal-load-test.py --skip-monitor-setup --poll-ms 1000 --phase-seconds 60
```

Файл `deploy/loadtest-sinewave-condition.txt`:

```cel
self.sineWave["value"] > -1000.0
```

### Параметры

| Флаг | Default | Описание |
|------|---------|----------|
| `--warmup-seconds` | 15 | Ожидание после configure driver (coalesce + async journal) |
| `--skip-cleanup` | false | Не останавливать mqtt loadtest перед прогоном |
| `--condition-expr` | `true` | CEL для alert rules |
| `--condition-expr-file` | — | Условие из файла |
| `--poll-ms` | `3000,1000,500` | Интервалы опроса virtual driver |
| `--telemetry-mix-ratio` | `0` | Доля устройств в `TELEMETRY_ONLY` (0.5 = половина без automation lane) |
| `--max-devices` | 0 (all) | Лимит loadtest-устройств |

### Baseline (0.9.18, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~37.9 | ~25.6 |

*(0.9.18 P3a: `event_history` Timescale hypertable + compression segmentby `object_path`, retention 90d. Report `deploy/events-internal-load-test-report-1782378017.json`. Прирост vs 0.9.17 скромный на 60 dev — основной выигрыш Timescale на больших объёмах journal и retention, не на micro-benchmark.)*

### Baseline (0.9.17, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

Per-device **`telemetryPublishMode`**: `FULL` (default) или `TELEMETRY_ONLY` (RAM + historian, без alert/workflow на coalesced tick).

| Режим | conditionExpr | Events/s | Alert fires/s |
|-------|---------------|----------|---------------|
| all FULL | `self.sineWave["value"] > -1000.0` | ~36.8 | ~24.5 |
| 50% TELEMETRY_ONLY | same | ~30.1 | ~24.7 |

*(0.9.17: выбор режима на driver binding; `PUT /api/v1/drivers/runtime/configure` поля `telemetryPublishMode`, `telemetryCoalesceMs`. Alert fires/s — глобальный счётчик (фон mini-TEC); journal падает при TELEMETRY_ONLY, automation lane разгружается.)*

Configure example:

```json
{
  "driverId": "virtual",
  "pollIntervalMs": 1000,
  "telemetryPublishMode": "TELEMETRY_ONLY",
  "autoStart": true
}
```

### Baseline (0.9.16, 60 devices, poll=1000ms, warmup=45s, coalesce=250ms)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~41.8 | ~29.0 |

*(0.9.16: JDBC batch journal, O(1) event counter, fireAutomation без TX, elastic workers, 6 journal writers.)*

### Baseline (0.9.15, 60 devices, poll=1000ms, warmup=30s, coalesce=250ms)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~39.9 | ~27.6 |

*(0.9.14 reference: ~27.4 events/s — in-memory alert runtime. 0.9.15 adds CEL cache, multi-writer journal, coalesce 250ms.)*

### Baseline (0.9.14, 60 devices, poll=1000ms, warmup=30s)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~27.4 | ~15.7 |

*(0.9.13 reference: ~26.0 events/s — in-memory alert runtime state.)*

### Baseline (0.9.9, 60 devices, poll=1000ms, warmup=15s)

| conditionExpr | Events/s | Alert fires/s |
|---------------|----------|---------------|
| `true` | ~20.7 | ~20.7 |
| `self.sineWave["value"] > -1000.0` | ~21.9 | ~22.1 |

*(0.9.5 reference: `true` ~20.7, realistic CEL ~17.4 events/s — см. историю в git.)*

### Важно для интерпретации

1. **Drivers** должны быть в RUNNING с `autoStart: true` (`PUT /api/v1/drivers/runtime/configure`).
2. **`alertFiresTotal`** — глобальный счётчик; перед прогоном cleanup отключает mini-TEC/demo alerts и останавливает их драйверы.
3. **`eventHistoryRecords`** — async write; используйте warmup перед измерением.
4. **Не смешивайте** virtual poll (`loadtest-dev-*`) и mqtt subscribe (`loadtest-mqtt-dev-*`) без cleanup — скрипты делают это по умолчанию.
5. Dot-notation `self.sineWave.value` в CEL для alert rules ненадёжна; предпочитайте `self.sineWave["value"]` или binding → derived var → alert (как `demo-sensor-01` / `alarmActive`).

## Архитектура конвейера (кратко)

См. [ADR-0021 automation pipeline evolution](decisions/0021-automation-pipeline-evolution.md):

- **Sync:** bindings, WebSocket
- **Async bus (dual lane):** telemetry (historian) vs automation (alerts, workflows, correlators)
- **Coalesce:** `RuntimeTelemetryCoalescer` (global default + per-device override) перед publish `ObjectChangeEvent`
- **Telemetry publish mode:** `FULL` | `TELEMETRY_ONLY` на driver binding — управляет `automationEligible` на coalesced driver telemetry
- **Alert path:** `AlertRuleListener` → CEL → `EventService.fire` → `EventJournalAsyncWriter`
- **Alert runtime state:** in-memory (`AlertRuleRuntimeStore`); periodic flush to object tree (default 30 s), not on every evaluation
- **Event journal storage:** `event_history` — Timescale hypertable on prod ([ADR-0022](decisions/0022-event-history-timescale.md)); retention `ISPF_EVENT_JOURNAL_RETENTION_DAYS` (default 90)

## Связанные документы

- [AUTOMATION.md](AUTOMATION.md) — события, alert rules, correlators
- [DEPLOYMENT.md](DEPLOYMENT.md) — VPS deploy, env vars
- [API.md](API.md) — `/api/v1/platform/metrics`
- [TESTING.md](TESTING.md) — JUnit / CI
