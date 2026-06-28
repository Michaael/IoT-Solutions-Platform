# Справочник виджетов дашборда

Полное описание всех типов виджетов ISPF: назначение, использование на HMI, поля layout и примеры.

**См. также:** [DASHBOARDS.md](DASHBOARDS.md) (сетка, `selectionKey`, навигация), [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md) (роль operator), [BINDINGS.md](BINDINGS.md) (platform bindings в переменных), [SPREADSHEET_WIDGET.md](SPREADSHEET_WIDGET.md) (формулы и таблицы).

---

## Общие поля (все виджеты)

Каждый виджет в `layout.widgets[]` имеет позицию на сетке и опциональную привязку к данным.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | string | Уникальный идентификатор в layout (напр. `temp-value`) |
| `type` | string | Тип виджета (`value`, `chart`, …) |
| `title` | string | Заголовок карточки на HMI |
| `x`, `y` | number | Позиция на сетке (0-based) |
| `w`, `h` | number | Ширина и высота в единицах сетки (`columns` обычно 12) |
| `objectPath` | string | Статический путь объекта (`root.platform.devices.foo`) |
| `selectionKey` | string | Имя слота выбора; путь берётся из `selection[key]` (см. [DASHBOARDS.md](DASHBOARDS.md)) |
| `variableName` | string | Имя переменной на объекте |
| `valueField` | string | Поле `DataRecord`: `value` (по умолчанию), `raw`, `online`, `unit`, `int`, `string` |
| `paramKey` | string | Читать значение из `session.params[key]` |
| `contextPathKey` | string | Путь объекта из `session.params[key]` |
| `modelHintPath` | string | Только редактор: образец объекта для dropdown переменных |
| `stylesJson` | string | JSON стилей элементов карточки (см. [DASHBOARDS.md § stylesJson](DASHBOARDS.md#стили-элементов-stylesjson)) |
| `demoPreviewJson` | string | Превью в редакторе без live-данных |

**Приоритет пути объекта:** если задан `selectionKey` и в session есть выбор → используется выбранный путь; иначе `objectPath`.

### JSON-поля (всегда строка в layout)

| Поле | Содержимое |
|------|------------|
| `columnsJson` | Колонки `object-table` |
| `variablesJson` | Массив имён переменных |
| `fieldsJson` | Поля `function-form` / `input-form` |
| `childrenJson` | Вложенные виджеты |
| `tabsJson` | Вкладки `tab-panel` |
| `slidesJson` | Слайды `carousel` |
| `stepsJson` | Шаги `steps-panel` |
| `itemsJson` | Пункты `nav-menu` |
| `eventNamesJson` | Фильтр имён событий |
| `contextSelectionJson` / `contextParamsJson` | Контекст при открытии дашборда |
| `rowParamsJson` / `cardParamsJson` | Params при клике строки/карточки |
| `sheetConfigJson` | Конфиг spreadsheet |
| `stylesJson` | Inline CSS по элементам карточки |

---

## Категории виджетов

| Категория | Привязка данных | Примеры |
|-----------|-----------------|---------|
| object-variable | Переменные одного объекта | value, chart, gauge |
| object-only | Объект для invoke/write | function, function-form, input-form |
| parent-catalog | Дети папки `parentPath` | object-table, map, card-grid |
| external | Отчёты, события, очередь, другие дашборды | report, work-queue, dashboard-link |
| session / static | Session или статический контент | label, image, breadcrumbs |
| composition | Вложенные виджеты | panel, tab-panel, nav-menu |

---

## object-variable — показатели и графики

### value — Значение

**Назначение:** крупное отображение числа или текста из переменной объекта.

**Оператор:** только просмотр (если не настроен write elsewhere).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** Имя переменной |
| `valueField` | Поле записи (по умолчанию `value`) |
| `decimals` | Знаков после запятой для чисел |
| `unit` | Статическая единица (`°C`, `%`) |
| `unitField` | Взять unit из поля переменной (напр. `unit`) |

**Пример:** температура датчика

```json
{
  "id": "temp",
  "type": "value",
  "title": "Температура",
  "x": 0, "y": 0, "w": 3, "h": 2,
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "valueField": "value",
  "decimals": 1,
  "unit": "°C"
}
```

**Сценарий:** SNMP monitoring — `selectionKey: "device"` + `variableName: "sysName"` для имени выбранного хоста.

---

### indicator — Индикатор (лампа)

**Назначение:** булево или строковое состояние (ON/OFF, alarm).

**Оператор:** просмотр; цвет и подписи по конфигурации.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** |
| `valueField` | Для online-статуса: `variableName: status`, `valueField: online` |
| `trueLabel` / `falseLabel` | Подписи состояний |
| `trueColor` / `falseColor` | CSS-цвета |
| `alarmMode` | `true` = true означает тревогу (красный); иначе true = OK (зелёный) |

**Пример:**

```json
{
  "type": "indicator",
  "title": "Связь",
  "variableName": "status",
  "valueField": "online",
  "trueLabel": "Online",
  "falseLabel": "Offline"
}
```

---

### toggle — Переключатель

**Назначение:** запись bool-переменной (writable) по клику оператора.

**Оператор:** клик переключает значение на сервере.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** Writable bool |
| `trueLabel` / `falseLabel` | Подписи ON/OFF |

---

### chart — График / тренд

**Назначение:** история переменной (line/area/bar и др.).

**Оператор:** просмотр; переключатель диапазона на виджете (если включён в UI).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** На переменной нужен `historyEnabled: true` |
| `chartStyle` | `line` \| `area` |
| `chartType` | `line`, `area`, `bar`, `candlestick`, `bubble`, `radar`, `range` |
| `historyRange` | `live` (скользящее окно), `1h`, `6h`, `24h`, `7d`, `all` |
| `maxPoints` | Макс. точек (~120 по умолчанию) |
| `color` | Цвет линии |
| `decimals`, `unit`, `unitField` | Формат оси/подписи |

**Пример:**

```json
{
  "type": "chart",
  "title": "Температура",
  "w": 8, "h": 4,
  "selectionKey": "device",
  "variableName": "temperature",
  "historyRange": "24h",
  "chartStyle": "area",
  "decimals": 1,
  "unit": "°C"
}
```

---

### sparkline — Спарклайн

**Назначение:** компактный мини-тренд без осей (в карточке метрики).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** + `historyEnabled` |
| `historyRange`, `maxPoints`, `color`, `decimals` | Как у `chart` |

---

### gauge — Радиальная шкала

**Назначение:** значение на шкале min–max.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** Текущее значение |
| `minValue` / `maxValue` | Константы границ |
| `minVariable` / `maxVariable` | Или границы из других переменных того же объекта |
| `unit`, `decimals` | Подпись и точность |

**Ошибка:** задать только `minValue` без `maxValue` — шкала не отрисуется.

---

### linear-gauge — Линейная шкала

**Назначение:** горизонтальная шкала; поля как у `gauge`.

---

### liquid-gauge — «Жидкий» gauge

**Назначение:** вертикальный уровень «жидкости»; поля как у `gauge` (`minValue`/`maxValue` или variables).

---

### progress — Прогресс-бар

**Назначение:** отношение текущего значения к максимуму (загрузка, выполнение).

| Поле | Описание |
|------|----------|
| `currentVariable` | **Обязательно.** Не `variableName`! |
| `maxVariable` | **Обязательно.** |
| `unit`, `decimals` | Подпись и точность |

**Пример:**

```json
{
  "type": "progress",
  "title": "Выполнение",
  "objectPath": "root.platform.orders.o-1",
  "currentVariable": "doneQty",
  "maxVariable": "orderQty",
  "unit": "шт"
}
```

---

### status-badge — Badge статуса

**Назначение:** компактный текстовый/цветной статус (driver, phase).

| Поле | Описание |
|------|----------|
| `variableName` | По умолчанию `status` |
| `valueField` | По умолчанию `value` |

---

### pie-chart — Круговая диаграмма

**Назначение:** доли из `RECORD_LIST` (несколько строк = секторы).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** RECORD_LIST |
| `labelField` | Поле подписи сектора |
| `valueField` | Поле значения (если не `value`) |
| `decimals` | Точность |

---

### history-table — Таблица истории (5 мин)

**Назначение:** последние samples переменной + среднее за окно.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** + `historyEnabled` |
| `valueField`, `decimals` | Формат |

---

### timer — Таймер

**Назначение:** обратный отсчёт или прошедшее время.

| Поле | Описание |
|------|----------|
| `mode` | `countdown` — от `durationSeconds`; `elapsed` — от timestamp в `variableName` |
| `durationSeconds` | Для countdown |
| `variableName` | Для elapsed — переменная со временем старта |

**Оператор:** countdown показывает оставшееся время; elapsed — сколько прошло с момента в переменной.

---

### gantt-chart — Диаграмма Ганта

**Назначение:** горизонтальные полосы из `RECORD_LIST` (задачи, интервалы).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** RECORD_LIST |
| `labelField` | Подпись строки |
| `startField` / `endField` | Поля начала и конца (ISO time или ms) |

---

### network-graph — Граф сети

**Назначение:** узлы и рёбра из двух RECORD_LIST переменных.

| Поле | Описание |
|------|----------|
| `nodesVariable` | RECORD_LIST узлов |
| `edgesVariable` | RECORD_LIST рёбер |
| `labelField` | Подпись узла |

---

### svg-widget — SVG-кнопка

**Назначение:** интерактивная SVG-иконка (lab, mimic).

**Оператор:** клик вызывает функцию или переключает переменную.

| Поле | Описание |
|------|----------|
| `svgUrl` | **Обязательно.** URL SVG |
| `clickAction` | `function` \| `toggle` |
| `functionName` | При `function` |
| `toggleVariable` | При `toggle` |
| `confirmMessage` | Подтверждение перед действием |

---

### spreadsheet — Электронная таблица

**Назначение:** сетка A1 с формулами (встроенный движок ISPF).

**Полное описание:** [SPREADSHEET_WIDGET.md](SPREADSHEET_WIDGET.md). Операторский сценарий — [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md).

| Поле | Описание |
|------|----------|
| `sheetMode` | `free` (по умолчанию) \| `configured` |
| `sheetConfigJson` | rows, cols, cells, filters, dataRegion |
| `persistMode` | `session` \| `variable` |
| `valuesVariable` | Переменная RECORD_LIST при `variable` persist |
| `sessionKey` | Ключ session (default `sheet:{id}`) |
| `editable` | `false` — только просмотр |

---

### mini-tec-sld — Однолинейная схема mini-TEC

**Назначение:** live SCADA mimic для demo mini-TEC (optional bootstrap). Без доп. полей — данные с объекта по `objectPath` / `selectionKey`.

**См.:** [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md).

---

## object-only — действия и формы

### function — Кнопка вызова функции

**Назначение:** один клик → `POST .../functions/{name}/invoke`.

**Оператор:** нажать кнопку; при `confirmMessage` — подтвердить диалог.

| Поле | Описание |
|------|----------|
| `functionName` | **Обязательно.** |
| `buttonLabel` | Текст кнопки |
| `confirmMessage` | Диалог подтверждения |
| `inputJson` | Статические input rows для invoke |
| `workflowPath` | Альтернатива: запуск workflow вместо function |
| `objectPath` / `selectionKey` | Объект для invoke |

**Пример:**

```json
{
  "type": "function",
  "title": "Сброс",
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "resetAlarm",
  "buttonLabel": "Сбросить alarm"
}
```

---

### function-form — Форма → функция

**Назначение:** поля ввода + кнопка → invoke с аргументами из формы.

**Оператор:** заполнить поля → «Отправить»; поддерживается многошаговый wizard.

| Поле | Описание |
|------|----------|
| `functionName` | **Обязательно.** |
| `fieldsJson` | Массив полей (см. ниже) |
| `buttonLabel` | Текст кнопки |
| `wizardStepsJson` | `[{ "id", "label" }]` — шаги мастера |
| `validateFunctionName` | BFF-валидация на «Далее» |
| `paramBindingsJson` | Поле формы ← session param |
| `requireSessionParamsJson` | Обязательные params перед submit |
| `syncFieldsToSessionJson` | Синхронизация полей в session |
| `clearSessionParamsJson` | Очистка params после успеха |
| `closeModalOnSuccess` | Закрыть модальное окно (default true в modal) |

**Поля `fieldsJson`:**

| Свойство | Описание |
|----------|----------|
| `name` | Имя аргумента функции |
| `label` | Подпись на форме |
| `type` | `text`, `number`, `select`, `multiselect`, `time`, `checkbox`, `textarea` |
| `optionsFrom` | parentPath — options = дети каталога |
| `optionsFromReport` | Options из SQL-отчёта |
| `staticOptions` | Фиксированный список |
| `defaultValue` | Значение по умолчанию |
| `required`, `hidden`, `hint` | UX |
| `step` | id шага wizard |
| `showWhenJson` | Условное отображение |
| `colSpan` | 1 = половина строки, 2 = полная |

**Пример (lab append row):**

```json
{
  "type": "function-form",
  "title": "Append row",
  "objectPath": "root.platform.devices.lab-userA-01",
  "functionName": "appendTableRow",
  "buttonLabel": "Append",
  "fieldsJson": "[{\"name\":\"int\",\"label\":\"Int\",\"type\":\"number\"},{\"name\":\"string\",\"label\":\"String\",\"type\":\"text\"}]"
}
```

---

### input-form — Форма записи в переменные

**Назначение:** оператор вводит значения → `setVariable` на объект (HMI-форма без function).

| Поле | Описание |
|------|----------|
| `fieldsJson` | Поля формы |
| `buttonLabel` | Кнопка сохранения |

**Поля:** `name`, `label`, `type` (`text`, `number`, `textarea`, `select`, `slider`, `checkbox`, `radio`, `datetime`, `time`), `variableName` (куда писать), `optionsFrom`, `min`/`max`/`step`, `defaultValue`.

---

### variable-editor — Редактор переменных

**Назначение:** список переменных объекта с inline-редактированием writable полей.

**Оператор:** изменить значение в таблице → сохранение на сервер.

| Поле | Описание |
|------|----------|
| `variablesJson` | JSON-массив имён; пусто = все переменные объекта |
| `objectPath` / `selectionKey` | Объект |

---

## parent-catalog — списки объектов

### object-table — Таблица объектов

**Назначение:** таблица **дочерних** объектов папки `parentPath`.

**Оператор:** просмотр; клик по строке → выбор (`selectionKey`) и/или переход на другой дашборд.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** Папка, не device |
| `columnsJson` | Колонки (см. ниже) |
| `selectionKey` | Публиковать `selection[key]=path` при клике |
| `namePattern` | Glob по имени (`gpu-*`) |
| `objectType` | Фильтр типа (`DEVICE`, …) |
| `rowTargetDashboard` | Открыть дашборд при клике |
| `rowOpenMode` | `navigate` \| `modal` |
| `rowSelectionKey` | Ключ selection в целевом дашборде |
| `rowParamsJson` | Доп. params при клике |

**Колонка `columnsJson`:**

```json
[
  { "variable": "sysName", "label": "Имя" },
  { "variable": "status", "label": "On", "field": "online", "trueLabel": "OK", "falseLabel": "—" },
  { "objectField": "displayName", "label": "Объект" }
]
```

| Свойство колонки | Описание |
|------------------|----------|
| `variable` | Имя переменной на строке-объекте |
| `label` | Заголовок |
| `field` | Поле DataRecord (default `value`) |
| `objectField` | `displayName` или `path` без variable |

---

### card-grid — Карточки объектов

**Назначение:** плитки дочерних объектов с несколькими переменными на карточке.

**Оператор:** клик по карточке → selection / навигация.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** |
| `variablesJson` | `["temperature","status"]` — что показать |
| `cardTargetDashboard`, `cardOpenMode` | Навигация |
| `cardSelectionKey`, `cardParamsJson` | Контекст при клике |

---

### map — Карта

**Назначение:** маркеры для каждого ребёнка `parentPath` (MapLibre + OSM).

**Оператор:** клик по маркеру → selection / дашборд.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** |
| `latVariable` | Переменная с geo на child |
| `latField` / `lonField` | Поля координат (default `lat`/`lon`) |
| `labelVariable` | Подпись маркера |
| `zoom`, `centerLat`, `centerLon` | Вид карты |
| `tileUrl`, `mapStyleUrl`, `tileAttribution` | Кастомные тайлы |
| `rowTargetDashboard`, `rowOpenMode`, `rowSelectionKey`, `rowParamsJson` | Как у таблицы |

---

### object-tree — Дерево объектов

**Назначение:** иерархия под `parentPath` для навигации/выбора.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** |
| `selectionKey` | Выбор узла |
| `maxDepth` | Глубина обхода |

---

## external — платформа и навигация

### dashboard-link — Переход на дашборд

**Назначение:** кнопка открывает другой дашборд (вкладка или modal).

**Оператор:** нажать кнопку; при `requireSessionParamsJson` кнопка неактивна без нужного контекста.

| Поле | Описание |
|------|----------|
| `targetDashboardPath` | **Обязательно.** |
| `openMode` | `navigate` \| `modal` |
| `buttonLabel` | Текст кнопки |
| `modalTitle` | Заголовок modal |
| `confirmMessage` | Подтверждение |
| `contextSelectionJson` | Передать selection |
| `contextParamsJson` | Передать params |
| `requireSessionParamsJson` | JSON-массив обязательных keys |

---

### sub-dashboard — Вложенный дашборд

**Назначение:** embed другого layout внутри виджета.

| Поле | Описание |
|------|----------|
| `targetDashboardPath` | Статический путь |
| `targetDashboardPathKey` | Путь из `session.params[key]` |
| `inheritContext` | `true` — передать selection родителя |

---

### report — SQL-отчёт

**Назначение:** таблица из REPORT объекта платформы.

**Оператор:** просмотр; экспорт CSV/PDF/XLSX; опционально выбор строки.

| Поле | Описание |
|------|----------|
| `reportPath` | **Обязательно.** |
| `emptyMessage` | Текст при пустом результате |
| `parametersJson` | Статические параметры run |
| `contextParamsJson` | session param → report param |
| `showCsv`, `showPdf`, `showXlsx`, `showHtml` | Кнопки экспорта |
| `selectable` | Клик по строке → session |
| `rowSelectionKey`, `rowParamsFromRowJson` | Маппинг колонок в params |
| `autoSelectFirstRow` | Выбрать первую строку при загрузке |
| `statusDotColumnsJson` | Колонки со status-dot |

---

### event-feed — Лента событий

**Назначение:** последние события платформы (журнал automation).

| Поле | Описание |
|------|----------|
| `objectPathPrefix` | Фильтр по префиксу пути |
| `eventNamesJson` | `["thresholdExceeded", …]` |
| `payloadFilterExpr` | Клиентский фильтр payload |
| `maxItems` | Лимит строк |

---

### work-queue — Очередь BPMN-задач

**Назначение:** user tasks для оператора (claim / complete).

**Оператор:** Claim → выполнить действие → Complete.

| Поле | Описание |
|------|----------|
| `operatorId` | Фильтр по оператору |
| `operatorAppId` | Фильтр по operator app |
| `maxItems` | Лимит задач |

---

## session / static — оформление

### label — Метка

**Назначение:** статический или динамический текст.

| Поле | Описание |
|------|----------|
| `text` | Фиксированный текст |
| `textJson` | JSON для шаблона |
| `paramKey` | Подставить `session.params[key]` |

---

### breadcrumbs — Хлебные крошки

**Назначение:** путь из session (selection или params).

| Поле | Описание |
|------|----------|
| `pathKey` | Ключ в selection/params |
| `separator` | Разделитель (default `/`) |

---

### context-list — Контекст (отладка)

**Назначение:** показывает `selection` и `params` дашборда. Для разработки HMI.

---

### image — Изображение

| Поле | Описание |
|------|----------|
| `imageUrl` | URL картинки |
| `alt` | Alt-текст |

---

### html-snippet — HTML-фрагмент

| Поле | Описание |
|------|----------|
| `htmlJson` | HTML (осторожно с XSS — только доверенный контент) |

---

## composition — композиция экрана

### panel — Панель-контейнер

**Назначение:** группа вложенных виджетов с общим заголовком.

| Поле | Описание |
|------|----------|
| `childrenJson` | Массив виджетов |
| `collapsible` | Сворачиваемая панель |
| `variant` | `simple` |

---

### tab-panel — Вкладки

**Назначение:** несколько вкладок, каждая со своим набором виджетов.

| Поле | Описание |
|------|----------|
| `tabsJson` | `[{ "id", "label", "children": [ …widgets ] }]` |

**Оператор:** переключать вкладки кликом.

---

### drawer-panel — Выдвижная панель

| Поле | Описание |
|------|----------|
| `drawerLabel` | Подпись кнопки открытия |
| `childrenJson` | Содержимое drawer |

---

### carousel — Карусель

| Поле | Описание |
|------|----------|
| `slidesJson` | `[{ "title", "children": [ … ] }]` |
| `autoplayMs` | Автопрокрутка (0 = выкл) |

---

### steps-panel — Пошаговая панель

| Поле | Описание |
|------|----------|
| `stepsJson` | `[{ "id", "label", "children": [ … ] }]` |
| `activeStepKey` | Активный шаг (или из session) |

---

### composite-widget — Композитный виджет

**Назначение:** плоский список вложенных виджетов без обёртки panel.

| Поле | Описание |
|------|----------|
| `childrenJson` | Массив виджетов |

---

### nav-menu — Меню навигации

**Назначение:** список ссылок на дашборды внутри экрана.

| Поле | Описание |
|------|----------|
| `itemsJson` | `[{ "label", "dashboardPath" }]` |

---

## Типичные ошибки

| Ошибка | Решение |
|--------|---------|
| `progress` с `variableName` | Использовать `currentVariable` + `maxVariable` |
| `object-table` с `objectPath` | Нужен `parentPath` (папка) |
| Пустой `chart` | Включить `historyEnabled` на переменной |
| `gauge` без max | Задать `maxValue` или `maxVariable` |
| `pie-chart` не RECORD_LIST | Переменная должна быть таблицей строк |
| Колонка «—» | Имя variable не совпадает с моделью |
| Два виджета с одним `selectionKey` | Нормально для детализации; разные ключи для независимых выборов |
| `columnsJson` как объект в API | В layout — **escaped string** |

---

## Рекомендуемые размеры (w × h)

| Виджет | Размер |
|--------|--------|
| value, indicator, toggle | w=2–4, h=2 |
| chart | w=6–12, h=4–6 |
| object-table | w=12, h=4–6 |
| function-form | w=4–6, h=4 |
| spreadsheet | w=8–12, h=5–8 |
| map | w=8–12, h=6–8 |

---

## Исходники

| Что | Где |
|-----|-----|
| TypeScript типы | `apps/web-console/src/types/dashboard.ts` |
| View-компоненты | `apps/web-console/src/components/dashboard/widgets/` |
| Редактор свойств | `widgetEditorFields.tsx`, `WidgetEditorPanel` |
| Образцы из палитры | `widgetSamples.ts` |
| AI / agent spec | `AgentWidgetPropertiesGuide.java` |
