# Привязки переменных (binding rules)

**Binding rule** — декларативное правило вычисления значения переменной на объекте: **когда** (activators) → **если** (condition, CEL) → **как** (expression) → **куда** (target).

Правила хранятся в системной переменной `@bindingRules` (JSON-массив, reserved). Runtime — **`BindingRuleEngine`** (единственный механизм привязок с v0.8.0).

См. также: [OBJECT_MODEL.md](OBJECT_MODEL.md), [MODELS.md](MODELS.md), ADR [0017](decisions/0017-binding-rules-only.md).

---

## Модель `BindingRule`

```json
{
  "id": "member3-sine",
  "name": "Member3 sine",
  "enabled": true,
  "order": 20,
  "activators": {
    "onStartup": false,
    "onVariableChange": [
      { "objectPath": "root.platform.devices.virt-cluster.dev-03", "variableName": "sineWave" }
    ],
    "onEvent": null,
    "periodicMs": 0
  },
  "condition": "",
  "expression": "refAt(\"root.platform.devices.virt-cluster.dev-03\", sineWave)",
  "target": { "variableName": "member3Sine", "field": "value" }
}
```

| Поле | Назначение |
|------|------------|
| `activators.onStartup` | Пересчёт при старте сервера / attach модели |
| `activators.onVariableChange` | Список `{ objectPath, variableName }`; `"self"` + `"*"` = любая локальная переменная |
| `activators.periodicMs` | Периодический пересчёт (0 = выкл.) |
| `condition` | CEL; пусто = всегда |
| `expression` | CEL или одна platform function |
| `target` | Целевая переменная и поле схемы |

**Cross-object:** activator на remote path + `refAt("path", var)` в expression. При изменении remote переменной (в т.ч. driver telemetry) `BindingPropagationListener` пересчитывает правила на consumer-объектах.

**Default activators** (если не заданы): `refAt` в expression → auto remote activators; иначе local `self:*`.

---

## Выражения (expression)

Два вида (как раньше, но внутри `rule.expression`):

| Вид | Пример |
|-----|--------|
| **CEL** | `self.temperature.value + 1.0` |
| **Platform binding** | `counterRate(ifInOctets)`, `hysteresis(temperature, 80, 70)`, `refAt("root...dev-01", sineWave)` |

Валидация: `POST /api/v1/expressions/validate` или Web Console «Проверить».

Stateful bindings (`counterRate`, `hysteresis`, …) — состояние в `@bindingState` (см. прежнее поведение).

---

## REST API

```http
GET  /api/v1/objects/by-path/binding-rules?path={objectPath}
PUT  /api/v1/objects/by-path/binding-rules?path={objectPath}
DELETE /api/v1/objects/by-path/binding-rules/{ruleId}?path={objectPath}
```

Agent tool: `create_binding_rule` (path, id, targetVariable, expression, remoteObjectPath?, remoteVariableName?, condition?, onStartup?, order?).

---

## Модели

В модели — `ModelBindingRule` (полная схема или `ModelBindingRule.of(id, target, expression)`). При apply/instantiate правила мержатся через `ModelBindingRulesMerger` **после** переменных.

Поле `defaultBinding` на переменной модели **удалено** (v0.8.0).

---

## Обновление с v0.7.x (legacy `bindingExpression`)

Поле `bindingExpression` на переменной и колонка `binding_expr` **удалены** (ADR-0017, v0.8.0). Привязки — только `@bindingRules`.

**Prod** (`ispf.iot-solutions.ru`): PostgreSQL в Docker (`ispf-postgres`), не H2. **Local dev:** H2 file или docker compose PostgreSQL.

```bash
# Prod VPS (Docker postgres)
systemctl stop ispf-server
docker exec ispf-postgres psql -U ispf -d postgres -c 'DROP DATABASE IF EXISTS ispf;' -c 'CREATE DATABASE ispf OWNER ispf;'
systemctl start ispf-server

# Local H2: удалить ./data/ispf-local.mv.db
# Local/dev compose: docker compose exec postgres psql ...
```

Существующая БД без пересоздания: Flyway `V41__drop_binding_expr.sql` снимает колонку; при **checksum mismatch V1** нужно пересоздание (см. [DEPLOYMENT.md](DEPLOYMENT.md#обновление-до-v080)).

---

## UI

Web Console → Object Inspector → вкладка **«Привязки»**. Переменные создаются без binding; вычисляемые значения — через правила.

---

## Не путать с

- **SQL bindings** (`ApplicationSqlBindingService`) — отдельный scheduler, не object binding rules
- **Alert rules**, correlators, workflows — отдельные подсистемы
