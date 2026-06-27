# Spreadsheet demo — эталон «объект + виджет»

Минимальный importable bundle: устройство с переменной **`sheetValues`** и два дашборда с виджетом **spreadsheet**.

## Развёртывание

```bash
curl -X POST "https://localhost:8443/api/v1/applications/sheet-demo/deploy" \
  -H "Authorization: Bearer …" \
  -H "Content-Type: application/json" \
  --data-binary @examples/spreadsheet-demo/bundle.json
```

Operator app: `appId=sheet-demo`, дашборды:

| Путь | Назначение |
|------|------------|
| `root.platform.dashboards.sheet-demo` | `persistMode: variable` → запись в `sheetValues` |
| `root.platform.dashboards.sheet-demo-session` | `persistMode: session` → только сессия браузера |

## 1. Объект (куда пишется таблица)

**Путь:** `root.platform.devices.sheet-demo-01`  
**Модель:** `sheet-storage-v1`

Переменная для persist:

| Имя | Тип | Содержимое |
|-----|-----|------------|
| `sheetValues` | RECORD_LIST | `{ cell: "A1", value: "10" }`, `{ cell: "B2", value: "=A1*2" }`, … |

Схема совпадает с lab-моделью (`LabModelBootstrap`) — см. `sheetCellRow` + `rows`.

## 2. Виджет (правильные поля)

Читаемый JSON: [`widget-reference.json`](widget-reference.json).

### Источник данных

| Поле | Значение | Комментарий |
|------|----------|-------------|
| **objectPath** | `root.platform.devices.sheet-demo-01` | Полный путь устройства |
| **selectionKey** | *(пусто)* | Нужен только если объект выбирается на дашборде |
| **contextPathKey** | *(пусто)* | Имя ключа в `session.params`, не путь объекта |
| **modelHintPath** | *(опционально)* | Только подсказка списка переменных в редакторе |

Поля **variableName** / **valueField** в блоке «Источник данных» для spreadsheet **не используются**.

### Секция «Таблица»

| Поле | Рекомендация |
|------|----------------|
| **sheetMode** | `free` — свободная сетка; `configured` — шаблон с `kind` |
| **persistMode** | `session` — черновик в браузере; `variable` — в `sheetValues` на объекте |
| **valuesVariable** | `sheetValues` (только при `persistMode: variable`) |
| **sessionKey** | по умолчанию `sheet:{id виджета}` |
| **editable** | включено |
| **sheetConfigJson** | `{"rows":12,"cols":8,"cells":{}}` |

## 3. Права (почему бывает 403)

`persistMode: variable` вызывает `PUT /api/v1/objects/by-path/variables?name=sheetValues`.

Нужен **WRITE** на `root.platform.devices.sheet-demo-01` (ACL или роль admin).

Если у оператора нет прав — используйте **`persistMode: session`** или выдайте ACL (как в `LabSecurityBootstrap` для lab-user-a).

## 4. Проверка

1. Откройте дашборд `sheet-demo`, введите значения в ячейки.
2. При `variable`: F5 — данные остаются; в Explorer → `sheetValues` видны строки `{cell, value}`.
3. При `session`: данные сохраняются в sessionStorage до закрытия вкладки.

Подробнее: [docs/SPREADSHEET_WIDGET.md](../../docs/SPREADSHEET_WIDGET.md).
