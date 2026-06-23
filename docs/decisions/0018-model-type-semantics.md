# ADR-0018: Blueprint + три вида моделей

Статус: **Accepted**  
Дата: 2026-06-23

## Контекст

Типы `RELATIVE`, `INSTANCE`, `ABSOLUTE` объявлены в `ModelType`, но семантика реализована неравномерно: один `templateId` на объект, in-memory attachments, `applyRelativeModels()` не вызывается, `ABSOLUTE` без логики. Смешение в одном каталоге «Models» и одном API создаёт путаницу.

## Решение

### 1. Один Blueprint, три вида снаружи

- **Внутри:** общий `ModelDefinition` + `ModelEngine` + таблица `model_definitions`.
- **Снаружи:** три каталога в дереве:
  - `root.platform.relative-models` — mixin (RELATIVE)
  - `root.platform.instance-types` — шаблоны экземпляров (INSTANCE)
  - `root.platform.absolute-models` — singleton (ABSOLUTE)
- Три API-фасада: `/api/v1/relative-models`, `/instance-types`, `/absolute-models` (общий registry, разная валидация и операции).
- `root.platform.models` — legacy; узлы мигрируют в типизированные каталоги при старте.

### 2. Связь объект ↔ модели

| Поле | Назначение |
|------|------------|
| `templateId` | Primary model (INSTANCE/ABSOLUTE); backward compat |
| `appliedModelIds` | Упорядоченный JSON-массив id всех применённых моделей |

`ModelAttachment` восстанавливается из `appliedModelIds` при старте (metadata only, без re-merge).

### 3. RELATIVE applicability

- `targetObjectType` + опциональный CEL `suitabilityExpression`.
- Авто-применение при create объекта (`applyRelativeModels`), флаг `autoApplyRelativeModels` (default true).
- Явное apply, companion-models, driver provisioning — как раньше.

### 4. INSTANCE как виртуальный тип

- UI показывает имя INSTANCE-модели как «тип»; в БД: `type = targetObjectType`, `templateId = model.id`.
- Enum `ObjectType` не расширяется под пользовательские модели.

### 5. ABSOLUTE singleton

- При создании модели — ровно один объект по `parameters.absoluteInstancePath` (default `root.platform.instances.{name}`).
- `PUT` модели синхронизирует структуру в singleton (merge, runtime values сохраняются).
- Повторный instantiate → 409.

### 6. Merge conflicts: warn + last-wins

При коллизии имён variables/events/functions при apply нескольких моделей:

- **last-wins** — apply не откатывается, HTTP 200.
- **warnings** в `ModelApplyResult` — пользователь видит конфликт и решает сам.

### 7. Binding rules

`ModelApplicationService.applyModelWithRules()` — единая точка: merge structure + `ModelBindingRulesMerger` во всех путях (create, template, instantiate, driver, relative auto-apply).

## Последствия

- Flyway `V39__object_applied_models.sql`.
- `PlatformObject.appliedModelIds`, расширенный `ObjectDto`.
- UI: три каталога, create dialog с INSTANCE-типами, инспектор со списком применённых моделей.
- Документация: [MODELS.md](../MODELS.md), [OBJECT_MODEL.md](../OBJECT_MODEL.md).

## Связанные материалы

- [MODELS.md](../MODELS.md)
- [BINDINGS.md](../BINDINGS.md)
- ADR-0017 (binding rules only)
