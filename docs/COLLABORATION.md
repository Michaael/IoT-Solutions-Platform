# Совместная работа (Phase 11)

Механизмы безопасной параллельной работы нескольких инженеров над одним деревом объектов ISPF.

## 11.1 Foundation — revision + audit

### Optimistic concurrency

Каждый узел `object_nodes` имеет поле **`revision`** (монотонно растёт при конфигурационных изменениях).

| Header | Значение |
|--------|----------|
| `If-Match` | Ожидаемая revision при PATCH/PUT переменных, metadata, functions, events |
| `X-ISPF-Force: true` | Admin-only: перезапись без проверки revision |

При mismatch → **409 Conflict**:

```json
{
  "error": "REVISION_CONFLICT",
  "objectPath": "root.platform.devices.pump-01",
  "expectedRevision": 3,
  "currentRevision": 5,
  "changedBy": "ivan",
  "changedAt": "2026-06-21T12:00:00Z"
}
```

Revision **не** инкрементируется при driver telemetry и computed bindings (`setDriverTelemetryValue`, `propagateBindings`).

### Config audit

Таблица `object_config_audit` — журнал изменений metadata, variables, functions, events.

```
GET /api/v1/objects/by-path/audit?path=...&limit=50
```

Web Console: вкладка **История изменений** в Object Properties Editor.

### Stale editor (Web Console)

WebSocket `/ws/objects` включает `revision` и `changedBy` в событиях `UPDATED` / `VARIABLE_UPDATED`.

Если редактор «грязный» и пришла revision выше baseline — banner с **Перезагрузить** / **Перезаписать** (admin).

## 11.2 Live collaboration

### Presence (soft)

Клиент шлёт по WS:

```json
{ "type": "presence", "path": "root.platform.devices", "username": "ivan", "mode": "edit" }
```

Сервер отвечает списком активных пользователей на path (TTL 30s). Не блокирует запись.

### Subtree lease (hard lock)

```
POST   /api/v1/objects/leases   { "pathPrefix": "...", "ttlMinutes": 120 }
DELETE /api/v1/objects/leases?pathPrefix=...
GET    /api/v1/objects/leases
```

При активном lease другого holder → **423 Locked** на write.

### Model merge

```
POST /api/v1/models/merge-preview
POST /api/v1/models/merge-apply
POST /api/v1/models/{id}/upgrade-instances?dryRun=true
```

Preview показывает конфликты переменных между двумя моделями; apply требует resolution per conflict.

## 11.3 Subtree ownership ACL

Расширенные permission на `object_acl_entries`:

| Permission | Права |
|------------|-------|
| `OWNER` | read + write + invoke + grant ACL + lease |
| `EDITOR` | read + write + invoke |
| `VIEWER` | read |

Наследование вверх по path (как READ/WRITE/INVOKE). Назначение через `PUT /api/v1/objects/by-path/acl?path=<prefix>`.

## 11.4 Change-sets и promotion

```
POST /api/v1/platform/change-sets
GET  /api/v1/platform/change-sets
POST /api/v1/platform/change-sets/{id}/preview
POST /api/v1/platform/change-sets/{id}/apply?force=false
```

Change-set — named пакет ops с optional `expectedRevision` per path. Preview показывает conflicts; apply transactional (или force).

Environment badge: `GET /api/v1/info` → `environment` (`ispf.environment`, default `dev`).

## Связанные документы

- [SECURITY.md](SECURITY.md) — RBAC и ACL
- [OBJECT_MODEL.md](OBJECT_MODEL.md) — дерево объектов
- [MODELS.md](MODELS.md) — diff/upgrade моделей
- [ROADMAP.md](ROADMAP.md) — Phase 11
