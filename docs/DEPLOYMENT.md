# Развёртывание и конфигурация

## Docker Compose

```bash
docker compose up -d
```

| Сервис | Image | Порты | Credentials |
|--------|-------|-------|-------------|
| postgres | timescale/timescaledb:latest-pg16 | 5432 | ispf/ispf |
| redis | redis:7-alpine | 6379 | — |
| nats | nats:2.10-alpine (-js) | 4222, 8222 | — |
| mosquitto | eclipse-mosquitto:2 | 1883 | config: deploy/mosquitto/ |
| keycloak | keycloak:26.0 dev | 8180 | admin/admin |

Volume: `ispf_pg_data`.

Profiles Compose **не используются** — все сервисы стартуют вместе.

## Spring Boot Server

Артефакт: `:packages:ispf-server:bootRun` или JAR из `build/libs/`.

### Переменные окружения

| Переменная | Default | Описание |
|------------|---------|----------|
| `ISPF_DB_URL` | `jdbc:postgresql://localhost:5432/ispf` | JDBC URL |
| `ISPF_DB_USER` | ispf | DB user |
| `ISPF_DB_PASSWORD` | ispf | DB password |
| `ISPF_SERVER_PORT` | 8080 | HTTP port |
| `ISPF_OAUTH_ISSUER` | Keycloak realm URL | JWT issuer |
| `ISPF_MQTT_ENABLED` | false | MQTT integration |
| `ISPF_MQTT_BROKER` | tcp://localhost:1883 | Broker URL |
| `ISPF_NATS_ENABLED` | false | NATS integration |
| `ISPF_NATS_URL` | nats://localhost:4222 | NATS URL |
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | true | Demo/lab fixture models и demo-узлы (`mqtt-sensor-v1`, …). См. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md) |

### Профили

| Profile | Файл | Сценарий |
|---------|------|----------|
| default | application.yml | PostgreSQL + JWT |
| local | application-local.yml | H2 file, X-ISPF-Role |
| dev | application-dev.yml | Full stack + MQTT/NATS |
| test | application-test.yml | H2 memory, tests |

### База данных

- **Flyway** — миграции при старте (`ddl-auto: validate`)
- **local:** H2 `./data/ispf-local` (PostgreSQL compatibility mode)
- **prod:** PostgreSQL; TimescaleDB extension (docker image `timescale/timescaledb`) — hypertables `variable_samples` и `event_history`, retention 90d ([0009](decisions/0009-timescaledb-retention.md), [0015](decisions/0015-event-history-timescale.md))

### Messaging

| Broker | Включение | Использование |
|--------|-----------|---------------|
| MQTT | `ispf.mqtt.enabled=true` | Device drivers |
| NATS | `ispf.nats.enabled=true` | Workflow messageTask |

## Web Console

Static build:

```bash
cd apps/web-console && npm run build
```

`dist/` — раздавать через nginx/ingress с proxy на API.

Dev: `npm run dev`, proxy на backend.

## Production topology (target)

```
                    ┌─────────────┐
   Clients ────────►│   Ingress   │
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ispf-server x N   web-console    Keycloak
           │
           ├── PostgreSQL (managed)
           ├── Redis
           └── NATS JetStream
```

- Stateless replicas `ispf-server`

## Мониторинг

Actuator endpoints:

- `/actuator/health` — liveness/readiness
- `/actuator/prometheus` — metrics (admin role); ISPF gauges `ispf_event_history_records`, `ispf_alert_fires_total`, `ispf_object_change_queue_size`, …
- `/actuator/metrics` — Micrometer

`ISPF_PLATFORM_METRICS_PROBE_ENABLED=true` — in-process sync of `/api/v1/platform/metrics` to `root.platform.devices.platform-metrics-probe` (alternative to external Prometheus during load tests).

## Логирование

```yaml
logging.level.com.ispf: INFO   # default
logging.level.com.ispf: DEBUG  # local/dev
```

## Локальные данные

| Профиль | Путь данных |
|---------|-------------|
| local | `./data/ispf-local.mv.db` |
| dev | PostgreSQL volume `ispf_pg_data` |

Очистка стенда: удалить H2 file или `docker compose down -v` (PostgreSQL).

## Remote host (systemd + nginx)

Скрипт `deploy/remote-setup-ispf.sh` — одноразовая установка на Linux VPS (Ubuntu 24.04+):

1. Останавливает лишние сервисы на хосте (настраивается под конкретный сервер).
2. Ставит Temurin JDK 25.
3. Кладёт артефакты в `/opt/ispf`:
   - `ispf-server.jar` ← `/tmp/ispf-server.jar`
   - `web-console/` ← `/tmp/ispf-web-console-dist/`
4. Создаёт `ispf-server.service` (порт **8080**). На **prod VPS** (`ispf.iot-solutions.ru`) datasource задаётся через `/opt/ispf/ispf-server.env` → **PostgreSQL** в Docker (`ispf-postgres`), несмотря на `--spring.profiles.active=local` в unit-файле. Каталог `/opt/ispf/data/` — служебные файлы (auto-update, installation-id), **не** H2.
5. Настраивает nginx на **80** (`ai.iot-solutions.ru`): static UI + proxy `/api/`, `/ws/`, `/actuator/`.
6. Опционально: если в `/tmp/snmpd-ispf.conf` — ставит snmpd для demo `snmp-localhost`.

Подготовка и запуск на сервере:

```bash
# Локально: сборка
./gradlew :packages:ispf-server:bootJar
cd apps/web-console && npm run build
scp packages/ispf-server/build/libs/ispf-server-*.jar user@host:/tmp/ispf-server.jar
scp -r apps/web-console/dist user@host:/tmp/ispf-web-console-dist
scp deploy/remote-setup-ispf.sh deploy/snmpd-ispf.conf user@host:/tmp/
ssh user@host 'bash /tmp/remote-setup-ispf.sh'
```

Проверка: `http://host/` (UI), `http://host/api/v1/info`.

Дополнительно:

| Скрипт | Назначение |
|--------|------------|
| `deploy/remote-cleanup-apache-ispf-only.sh` | Убрать Apache/ISPmanager, оставить только ISPF на :80 |
| `deploy/update-snmp-mappings.sh` | Обновить OID mappings на уже работающем сервере |
| `deploy/start-snmp-driver.sh` | Login + start SNMP driver |
| `deploy/apply-platform-update.sh` | Установка jar + UI из staging и перезапуск systemd (вызывается API обновления) |
| `deploy/remote-update-ispf.sh` | Ручное обновление с `/tmp` артефактов |

### Автообновление с GitHub Releases

1. Опубликуйте релиз: `git tag v0.1.1 && git push origin v0.1.1` — workflow `.github/workflows/release.yml` соберёт `ispf-server.jar` и `web-console.zip`.
2. На VPS в `ispf-server.service` включено:
   - `ISPF_UPDATE_CHECK_ENABLED=true` — периодическая проверка (по умолчанию раз в час)
   - `ISPF_UPDATE_APPLY_ENABLED=true` — кнопка «Обновить и перезапустить» в админ-консоли
3. Админ видит баннер, когда на GitHub есть более новая версия, чем текущий jar (`/api/v1/info` → `version` из build-info).

Локально `apply-enabled=false` — только уведомление / ссылка на релиз.

### SNMP demo driver на remote

После деплоя SNMP-агент на хосте (`127.0.0.1:161`, см. `deploy/snmpd-ispf.conf`) и устройство `root.platform.devices.snmp-localhost`:

```bash
bash deploy/start-snmp-driver.sh
```

Скрипт логинится (`admin`/`admin`), вызывает `POST /api/v1/drivers/runtime/start?devicePath=...` и печатает status. По умолчанию `API=http://127.0.0.1:8080` (JVM); через nginx: `API=http://127.0.0.1`.

## Обновление до v0.8.0+

> **Runbook для апгрейда с pre-0.8.0.** Разовый breaking change [0010](decisions/0010-binding-rules-only.md): колонка `binding_expr` удалена (`V41`), checksum `V1` изменён — **проще пересоздать БД**, чем мигрировать legacy-привязки. На prod 0.9.x обычный deploy через [vps-deploy-direct.ps1](../deploy/vps-deploy-direct.ps1) — без пересоздания БД.

### Prod VPS (`ispf.iot-solutions.ru`) — PostgreSQL в Docker

БД: контейнер **`ispf-postgres`** (`timescale/timescaledb`), JDBC из `/opt/ispf/ispf-server.env` (`SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ispf`).

```bash
# 1. Backup (опционально)
docker exec ispf-postgres pg_dump -U ispf ispf > ispf-pre-0.8.27.sql

# 2. Остановить server, пересоздать БД
systemctl stop ispf-server
docker exec ispf-postgres psql -U ispf -d postgres \
  -c 'DROP DATABASE IF EXISTS ispf;' \
  -c 'CREATE DATABASE ispf OWNER ispf;'

# 3. Deploy jar + UI (deploy/vps-deploy-direct.ps1 или apply-platform-update.sh)
#    Если jar/UI уже в staging — достаточно systemctl start
systemctl start ispf-server

# 4. Flyway накатит схему с V1 без binding_expr на чистую БД
curl -sf https://ispf.iot-solutions.ru/api/v1/info
```

После пересоздания: binding rules — Web Console → «Привязки»; mini-TEC — `MiniTecPlatformBootstrap` при старте.

### PostgreSQL (generic / docker compose)

```bash
docker compose exec postgres psql -U ispf -d postgres \
  -c 'DROP DATABASE IF EXISTS ispf;' -c 'CREATE DATABASE ispf OWNER ispf;'
```

### H2 (local dev only)

Удалите `./data/ispf-local.mv.db` или смените `spring.datasource.url`. См. [BINDINGS.md](BINDINGS.md#обновление-с-v07x-legacy-bindingexpression).
