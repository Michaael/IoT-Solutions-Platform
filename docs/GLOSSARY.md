# Глоссарий ISPF

Краткий словарь терминов платформы. Обзор продукта: [PRODUCT.md](PRODUCT.md).

---

## Основной принцип (сквозной термин)

**Бизнес-логика в механизмах платформы** — архитектурный принцип ISPF: правила и поведение прикладного решения описываются declarative-конфигурацией **дерева объектов** (модели, переменные, события, функции, workflow, alert rules, correlators), а не отраслевым Java в `ispf-server`. Платформа реализует generic-движки; bundle deploy доставляет конфигурацию в эти механизмы. См. [ARCHITECTURE.md](ARCHITECTURE.md#основной-принцип-бизнес-логика-в-механизмах-платформы).

---

## A

**Alert rule** — правило автоматизации: CEL-условие на переменную объекта. При истинности — автоматический fire события. Узел типа `ALERT` в `root.platform.alert-rules`.

**Application (deploy-приложение)** — зарегистрированное прикладное решение с изолированной SQL-схемой, JSON-функциями и опциональным bundle. Регистрируется через `POST /applications`. Отображается в дереве под `root.platform.applications`.

**Application function** — функция приложения, описанная JSON script (шаги `selectOne`, `update`, …). Выполняется в sandbox без Java-кода в ядре.

---

## B

**Binding** — вычисляемая привязка переменной (`bindingExpression`). Значение пересчитывается при изменении переменных на том же объекте. Выражение — Google **CEL** или одна **platform function** (`counterRate`, `scale`, `clamp`, …). См. [BINDINGS.md](BINDINGS.md).

**BFF (Backend-for-Frontend)** — шлюз `POST /bff/invoke` для вызова функций приложения из UI. Wire profile `anima-operator-v1` — контракт для legacy manifest.

**Blueprint** — см. **Model**.

**Bundle** — ZIP-пакет приложения: manifest, SQL, функции, operator UI, отчёты. Деплоится через `POST /applications/{id}/deploy`.

**BPMN** — Business Process Model and Notation. Нотация для описания workflow. ISPF использует BPMN 2.0 XML с расширениями namespace `http://ispf.io/bpmn`.

---

## C

**CEL (Common Expression Language)** — язык выражений Google. Используется в bindings, alert rules, gateway conditions workflow.

**Claim** — действие оператора в work queue: закрепление user task за собой.

---

## D

**Dashboard** — объект типа `DASHBOARD` с layout JSON (сетка виджетов). Создаётся в Dashboard Builder, отображается в operator HMI.

**DataRecord** — типизированная запись переменной объекта. Содержит `DataSchema` (поля) и значения.

**DeviceDriver** — SPI интерфейс драйвера устройства. Реализации: mqtt, modbus, snmp, virtual, … (58 модулей).

**Driver runtime** — сервис опроса устройств. Старт/стоп через `POST /drivers/runtime/start|stop`.

---

## E

**Event** — типизированное уведомление с объекта. Имеет descriptor (имя, схема payload, уровень). Публикуется через `POST /events/fire` или alert rule.

**Event correlator** — правило: цепочка событий → запуск workflow. Узел типа `CORRELATOR` в `root.platform.correlators`.

**Explorer** — панель просмотра свойств выбранного узла дерева в admin console.

---

## F

**Flyway** — инструмент миграций SQL платформы. Миграции приложений **не** используют Flyway — только API `data/migrate`.

**Function (platform function)** — исполняемая функция на объекте платформы (не application function). Описывается в модели объекта.

---

## H

**HMI (Human-Machine Interface)** — операторский интерфейс. В ISPF — operator HMI на базе дашбордов.

---

## I

**Inspector** — панель деталей объекта: свойства, переменные, события, функции.

**Instance (workflow instance)** — запущенный экземпляр BPMN-процесса. Статусы: `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`.

---

## L

**Layout** — JSON-описание сетки виджетов дашборда. Хранится в переменной `layout` объекта `DASHBOARD`.

**Legacy manifest** — устаревший формат operator shell (`operatorManifest` в JSON). Заменён на `operatorUi` + platform dashboards.

---

## M

**Model (ModelDefinition)** — шаблон объекта: variables, events, functions, bindings. Типы: `RELATIVE`, `ABSOLUTE`, `INSTANCE`. При создании объекта модель применяется автоматически.

**Model engine** — плагин `ispf-plugin-model`, применяющий модели к объектам.

---

## O

**Object tree** — иерархия узлов платформы с dot-path адресацией (`root.platform.devices.sensor-01`).

**ObjectType** — тип узла: `DEVICE`, `DASHBOARD`, `WORKFLOW`, `ALERT`, `CORRELATOR`, `MODEL`, `APPLICATION`, `USER`, `PLATFORM`, `ALERT_RULES`, … Системные папки имеют семантический тип, не `CUSTOM`.

**Operator app** — конфигурация operator UI для конкретного приложения. Хранится в `operator_app_ui`, редактируется в `root.platform.operator-apps`.

**Operator HMI** — режим Web Console для операторов: read-only дашборды, work queue, event journal.

**Operator UI** — JSON-конфиг: title, список дашбордов, default dashboard. Загружается через `GET /operator-apps/{id}/ui`.

---

## P

**Platform object** — узел дерева объектов ISPF (в отличие от application record в таблице `applications`).

**Plugin** — расширение платформы. В репозитории: `ispf-plugin-model`, `ispf-plugin-workflow`. Коммерческие — вне `main`.

---

## R

**RBAC** — Role-Based Access Control. Роли: `admin`, `operator`.

**REQ-PF** — требования к платформенному слою приложений (Application Platform). Статус: [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md).

---

## S

**selectionKey** — имя слота динамического выбора объекта на дашборде. Виджет с `selectionKey: "device"` читает путь из `selection.device`, установленный кликом по таблице.

**Service task** — элемент BPMN: автоматическое действие (LOG, SET_VARIABLE, INVOKE_FUNCTION, PUBLISH_NATS).

**SPI (Service Provider Interface)** — контракт расширения. Пример: `DeviceDriver` для драйверов.

---

## U

**User task** — элемент BPMN: задача для оператора. Появляется в Work Queue до claim/complete.

---

## V

**Variable** — именованное значение на объекте. Типизируется через `DataRecord`. Может иметь CEL binding.

**Virtual driver** — драйвер-симулятор. Генерирует тестовые данные (например, синусоида температуры для `demo-sensor-01`).

---

## W

**Web Console** — React-приложение admin + operator UI. Каталог: `apps/web-console/`.

**WebSocket** — `WS /ws/objects` — live-обновления переменных и событий.

**Widget** — элемент дашборда: value, chart, object-table, work-queue, dashboard-link, …

**Work Queue** — очередь BPMN user tasks для операторов.

**Workflow** — объект типа `WORKFLOW` с BPMN XML. Статусы объекта: `DRAFT`, `ACTIVE`, `STOPPED`.

**Workflow engine** — чистый Java в `ispf-plugin-workflow` (без Camunda/Flowable).

---

## Сокращения

| Сокращение | Расшифровка |
|------------|-------------|
| ISPF | IoT Solutions Platform Framework |
| HMI | Human-Machine Interface |
| SCADA | Supervisory Control and Data Acquisition |
| MES | Manufacturing Execution System |
| OPC UA | Open Platform Communications Unified Architecture |
| SNMP | Simple Network Management Protocol |
| MQTT | Message Queuing Telemetry Transport |
| JWT | JSON Web Token |
| OIDC | OpenID Connect |
