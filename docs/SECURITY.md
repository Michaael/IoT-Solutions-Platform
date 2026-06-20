# Безопасность и RBAC

## Модель

ISPF использует **ролевой доступ** на уровне HTTP API:

| Роль | Spring authority |
|------|------------------|
| `admin` | `ROLE_admin` |
| `operator` | `ROLE_operator` |

Per-object ACL — в roadmap ([ARCHITECTURE.md](ARCHITECTURE.md)).

## Профили аутентификации

### local

Файл: `application-local.yml`

- OAuth отключён (dummy issuer)
- RBAC включён; аутентификация по **Bearer-токену** после `POST /api/v1/auth/login`
- `ispf.security.token-auth-enabled: true`
- `ispf.security.local-default-role:` пусто — без токена доступ запрещён
- `LocalBearerTokenFilter` + опциональный fallback `X-ISPF-Role` (только dev)

Учётные записи по умолчанию (если БД пуста): `admin/admin`, `operator/operator`.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/objects
```

Web Console: экран входа; сессия хранится в `localStorage`. Админ управляет пользователями в дереве `root.platform.security.users`.

**Автозапуск приложения:** у пользователя можно включить `autoStartEnabled` и указать `autoStartApp` (id operator app, список — `GET /api/v1/operator-apps`). После входа Web Console открывает operator-приложение вместо админ-консоли.

### Управление пользователями (admin)

| Endpoint | Описание |
|----------|----------|
| `GET /api/v1/security/users` | Список пользователей |
| `POST /api/v1/security/users` | Создать пользователя |
| `PUT /api/v1/security/users/{username}` | Обновить (роли, enabled, displayName, **autoStartEnabled**, **autoStartApp**) |
| `DELETE /api/v1/security/users/{username}` | Удалить |
| `POST /api/v1/security/users/{username}/password` | Сменить пароль |
| `POST /api/v1/auth/logout` | Завершить сессию |
| `GET /api/v1/auth/me` | Текущий пользователь (с токеном) |

Пользователи и роли синхронизируются в дерево объектов (см. [OBJECT_MODEL.md](OBJECT_MODEL.md)).

### dev / default (production-like)

- OAuth2 Resource Server, JWT от Keycloak
- Issuer: `http://localhost:8180/realms/ispf`
- Роли из JWT: `realm_access.roles` → `ROLE_admin`, `ROLE_operator`

### test

- RBAC отключён (`rbac-enabled: false`)
- Все endpoints доступны без auth

## Матрица доступа

Правила: `IspfAuthorizationRules.java`.

| Endpoint | admin | operator | public |
|----------|:-----:|:--------:|:------:|
| `GET /api/v1/info` | ✓ | ✓ | ✓ |
| `POST /api/v1/auth/login` | | | ✓ |
| `GET /api/v1/auth/me` | ✓ | ✓ | |
| `GET /actuator/health` | ✓ | ✓ | ✓ |
| `WS /ws/**` | ✓ | ✓ | ✓ |
| `GET /api/v1/**` | ✓ | ✓ | |
| `POST /api/v1/events/**` | ✓ | ✓ | |
| `POST .../functions/invoke` | ✓ | ✓ | |
| `POST /api/v1/bff/**` | ✓ | ✓ | |
| `POST /api/v1/workflows/instances/*/cancel` | ✓ | ✓ | |
| `GET/POST /api/v1/work-queue/**` | ✓ | ✓ | |
| `POST/PUT/PATCH/DELETE /api/v1/**` | ✓ | | |
| `/api/v1/security/**` | ✓ | | |
| `/api/v1/applications/**` | ✓ | | |
| `/api/v1/schedules/**` | ✓ | | |
| `/api/v1/alert-rules/**` | ✓ | | |
| `/api/v1/correlators/**` | ✓ | | |
| `/api/v1/models/**` (write) | ✓ | | |

**operator** может: читать объекты/дашборды/workflows, вызывать функции, work queue, fire events.

**admin** может: всё выше + CRUD объектов, models, automation, driver runtime, сохранение layout/BPMN.

## Keycloak (dev)

Docker Compose поднимает Keycloak на порту **8180**.

Настройка realm `ispf`:

1. Admin console: http://localhost:8180 — user `admin` / `admin`
2. Create realm **ispf**
3. Create client (public или confidential) для web/API
4. Realm roles: `admin`, `operator`
5. Назначить роли пользователям
6. Запуск сервера: `--spring.profiles.active=dev`

Web Console (будущее): интеграция OIDC login; сейчас в dev можно использовать API напрямую с JWT.

## Переменные

| Переменная | Описание |
|------------|----------|
| `ISPF_OAUTH_ISSUER` | JWT issuer URI |
| `ispf.security.rbac-enabled` | Вкл/выкл RBAC |
| `ispf.security.token-auth-enabled` | Bearer-сессии (local) |
| `ispf.security.local-default-role` | Роль по умолчанию без токена (local, dev only) |

## Рекомендации для production

- Не использовать профиль `local`
- Keycloak или другой IdP с коротким TTL JWT
- TLS на ingress
- Ограничить `permitAll` endpoints
- Secrets через vault, не в compose
