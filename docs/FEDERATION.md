# Object federation (PF-13 spike)

Spike реализации REQ-PF-13: реестр peer-инстансов, proxy read/write объектов и catalog sync.

Полная vision — [PLATFORM_DEVELOPER_BACKLOG.md §9](PLATFORM_DEVELOPER_BACKLOG.md#9-распределённая-архитектура-и-федерация-roadmap-p3).

## Принцип

**Object path ≠ service endpoint.** Путь `root.platform.devices.x` — стабильный идентификатор в каталоге. URL удалённого ISPF хранится в peer registry.

## Компоненты (spike)

| Компонент | Описание |
|-----------|----------|
| `federation_peers` (V26) | Таблица peer: name, baseUrl, authToken, pathPrefix, enabled |
| `GET/POST/PUT/DELETE /api/v1/federation/peers` | CRUD (admin) |
| `POST /api/v1/federation/remote-token` | Логин на remote ISPF (server-side), возвращает Bearer для `authToken` |
| `POST /api/v1/security/users/{username}/federation-token` | Выпуск Bearer-сессии для service user на **этом** узле |
| `POST /api/v1/federation/peers/{id}/sync-catalog` | Импорт remote object list в локальное дерево |
| Proxy-узлы | AGENT с переменными `federationProxy`, `federationPeerId`, `federationRemotePath` |
| `GET /api/v1/federation/proxy/objects/by-path` | Прямой proxy-read без catalog sync |
| `PATCH /api/v1/federation/proxy/objects/by-path/variables/value` | Proxy-write переменной на remote peer |
| `POST /api/v1/federation/proxy/objects/by-path/functions/invoke` | Proxy-invoke функции на remote peer |
| `GET /api/v1/objects/by-path` | Для proxy-узлов в дереве — прозрачный read через peer |
| `GET /api/v1/dashboards/by-path` | Proxy layout; widget paths remapped на `root.platform.federation.{peer}.*` |
| `GET /api/v1/objects/by-path/variables/history*` | Proxy historian для federated paths |
| `FederationWebSocketFanoutService` | Fan-out platform events к подписчикам federated paths (базовый notify) |

## Catalog sync

`POST /api/v1/federation/peers/{peerId}/sync-catalog` загружает список объектов с peer и создаёт локальные proxy-узлы:

```text
root.platform.federation.{peer-name}.devices.demo-sensor-01
  federationPeerId = <uuid>
  federationRemotePath = root.platform.devices.demo-sensor-01
  federationProxy = true
```

Web Console: кнопка **Sync** на панели Federation peers.

Повторный sync **идемпотентен**: уже импортированные узлы обновляют proxy-метаданные (`updated`), новые не дублируются. При loopback peer пути `root.platform.federation.*` на remote side игнорируются, чтобы не создавать вложенные зеркала каталога.

## pathPrefix

Если peer обслуживает ту же иерархию, но консоль передаёт относительный путь:

- `path=devices.demo-sensor-01` + `pathPrefix=root.platform` → remote `root.platform.devices.demo-sensor-01`

## Auth между инстансами

В peer сохраняется `authToken` (Bearer service account на удалённом ISPF). Токен не возвращается в list API (`hasAuthToken: true/false`).

Если `authToken` не задан, исходящие запросы к peer используют Bearer-токен текущего пользователя (удобно для loopback на `127.0.0.1` с включённым RBAC). Для фоновых задач без HTTP-контекста token на peer обязателен.

### Web Console

Панель **Federation peers** (Explorer → Platform → Federation):

1. **Токен для federation (этот узел)** — выбор пользователя и TTL, кнопка «Выпустить токен». Токен копируется в peer на другом ISPF.
2. При создании peer:
   - **Текущий сессионный токен** — подставляет Bearer вашей сессии (loopback).
   - **Получить токен с remote** — `POST /api/v1/federation/remote-token` с `baseUrl` из формы и учётными данными remote (пароль не сохраняется на сервере).

Токены — обычные platform session tokens (TTL по умолчанию 12 ч, макс. 168 ч), не долгоживущие API keys. Нужен профиль `local` или `ispf.security.token-auth-enabled: true`.

## Ограничения spike / production gaps

- Write proxy — variable patch и function invoke; полная двусторонняя синхронизация дерева не поддерживается.
- Catalog sync — только import (не merge конфликтов без оператора).
- WS fan-out — базовый notify; subscribe-by-path в Web Console — backlog.
- Tenant scope на federation API — частично (см. §9 backlog).

## Пример

```http
POST /api/v1/federation/peers
{ "name": "site-a", "baseUrl": "https://ispf-site-a.example", "pathPrefix": "root.platform" }

GET /api/v1/federation/proxy/objects/by-path?peerId=<uuid>&path=devices.demo-sensor-01
Authorization: Bearer <admin-token>

PATCH /api/v1/federation/proxy/objects/by-path/variables/value?peerId=<uuid>&path=devices.demo-sensor-01&name=temperature
Authorization: Bearer <admin-token>
{ "schema": { "name": "temperature", "fields": [{"name": "value", "type": "DOUBLE"}] }, "rows": [{"value": 22.5}] }
```
