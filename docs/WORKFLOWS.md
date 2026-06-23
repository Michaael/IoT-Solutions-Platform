# Workflow и BPMN

## Обзор

Workflow — объект типа `WORKFLOW` с моделью `workflow-v1`. Движок: чистый Java в `ispf-plugin-workflow` (без Camunda/Flowable).

Переменные workflow-объекта:

| Переменная | Описание |
|------------|----------|
| `title` | Название |
| `status` | `DRAFT` / `ACTIVE` / `STOPPED` |
| `bpmnXml` | BPMN 2.0 XML |
| `triggerJson` | Триггер по изменению переменных |
| `operatorAppId` | Operator App для user task в sidebar |
| `instanceState` | JSON состояния последнего экземпляра |
| `lastRunAt` | Время последнего запуска |
| `lastAction` | Последнее действие service task |

## Жизненный цикл

**Workflow object:**
- `DRAFT` — редактирование, запуск вручную
- `ACTIVE` — слушает триггеры + ручной run
- `STOPPED` — не запускается

**Instance (`InstanceStatus`):**
- `RUNNING` — выполняется
- `WAITING` — ожидает user task или signal catch
- `COMPLETED` — завершён
- `FAILED` — ошибка

## Поддерживаемые элементы BPMN

| Элемент | Поддержка |
|---------|-----------|
| `startEvent` | Да |
| `endEvent` | Да |
| `serviceTask` | LOG, SET_VARIABLE, PUBLISH_NATS, **INVOKE_FUNCTION** |
| `userTask` | Очередь оператора, claim/complete |
| `intermediateCatchEvent` | Ожидание BPMN-сигнала (`ispf:signal`) |
| `messageTask` | Публикация в NATS (если enabled) |
| `exclusiveGateway` | Условные переходы (CEL) |
| `parallelGateway` | Fork/join, execution tokens |
| `sequenceFlow` | `ispf:condition`, `ispf:default` |

Namespace расширений: `http://ispf.io/bpmn` (префикс `ispf:`).

## ISPF-атрибуты

### serviceTask

```xml
<serviceTask id="log1" name="Log alarm"
             ispf:action="log"
             ispf:message="Threshold exceeded"/>
```

| action | Параметры |
|--------|-----------|
| `log` | `ispf:message` |
| `set_variable` | `ispf:targetPath`, `ispf:variable`, `ispf:value` |
| `publish_nats` | `ispf:subject`, `ispf:message`, `ispf:channel` |
| `invoke_function` | `ispf:objectPath`, `ispf:functionName`, `ispf:inputMap`, `ispf:outputMap` |

Пример `invoke_function` (прикладные функции — [APPLICATIONS.md](APPLICATIONS.md)):

```xml
<serviceTask id="assign" name="Assign tank"
             ispf:action="invoke_function"
             ispf:objectPath="root.platform.devices.demo-sensor-01"
             ispf:functionName="myapp_acknowledge"
             ispf:inputMap="orderId=${workflow.orderId}"
             ispf:outputMap="assignResult=result"/>
```

### userTask

```xml
<userTask id="approve" name="Approve"
          ispf:title="Подтвердить аларм"
          ispf:instructions="Проверьте датчик"
          ispf:assigneeRole="operator"
          ispf:targetObjectPath="root.platform.devices.demo-sensor-01"
          ispf:function="acknowledgeAlarm"/>
```

### sequenceFlow

```xml
<sequenceFlow sourceRef="gw" targetRef="approve" ispf:condition="needsApproval"/>
<sequenceFlow sourceRef="gw" targetRef="end" ispf:default="true"/>
```

Условие — CEL в контексте переменных workflow instance.

### messageTask

```xml
<messageTask id="notify" name="Notify"
             ispf:subject="ispf.ops.alarm"
             ispf:message="Alarm fired"
             ispf:channel="nats"/>
```

### intermediateCatchEvent (signal)

```xml
<intermediateCatchEvent id="waitIncident" name="Wait incident"
                        ispf:signal="incidentRegistered"/>
```

Экземпляр переходит в `WAITING` до доставки сигнала. Альтернатива отмене через cancel API — инцидент может «разбудить» процесс и продолжить ветку обработки.

## Work Queue

User tasks попадают в `GET /api/v1/work-queue`.

### Привязка к Operator App

На объекте workflow (модель `workflow-v1`) задаётся переменная **`operatorAppId`** — id Operator App, в sidebar «Задачи» которого попадут user task этого процесса.

- Настройка: редактор workflow → панель **Operator App** (или `PUT /api/v1/workflows/by-path/operator-app`)
- При создании user task значение копируется в `workflow_user_tasks.operator_app_id`
- Фильтр sidebar: `GET /api/v1/work-queue?operatorAppId=platform`

Если `operatorAppId` пуст — задачи **не показываются** в sidebar Operator Apps (виджет `work-queue` на дашборде по-прежнему показывает все).

| Статус задачи | Действие |
|---------------|----------|
| `OPEN` | claim → `CLAIMED` |
| `CLAIMED` | complete → workflow продолжается |

```http
POST /api/v1/work-queue/claim?taskId=...&operatorId=operator
POST /api/v1/work-queue/complete?taskId=...&operatorId=operator
```

При `ispf:function` на user task — функция вызывается при complete.

## Триггеры

`triggerJson` на workflow-объекте. `WorkflowTriggerListener` сравнивает изменения переменных с условием и запускает ACTIVE workflow.

## Event Correlators → Workflow

Коррелятор с action `RUN_WORKFLOW` и `actionTarget` = path workflow. См. [AUTOMATION.md](AUTOMATION.md).

## Демо workflow

`root.platform.workflows.demo-alarm-handler` — BPMN с gateway, user task, service log.

Определение: `WorkflowDefinitions.DEMO_ALARM_HANDLER`.

## UI

- **WorkflowBuilder** — статус, run, редактор BPMN (bpmn-js)
- **BpmnDiagramEditor** / **BpmnDiagramViewer** — custom moddle `ispf-moddle.json`

### Диаграмма без разметки (DI)

BPMN из движка или скриптов часто содержит только логику процесса, **без секции `bpmndi`** (Diagram Interchange).  
`bpmn-js` в этом случае выдаёт ошибку `no diagram to display`.

Web Console автоматически вызывает **`bpmn-auto-layout`** (`src/bpmn/ensureDiagram.ts`) перед отображением.  
Шаблон нового процесса (`EMPTY_BPMN` в `constants.ts`) уже включает минимальную разметку.

Если диаграмма не отображается: откройте вкладку **Исходник**, убедитесь что `bpmnXml` не пустой, сохраните — layout будет сгенерирован при следующем открытии.

## Отмена экземпляра

```http
POST /api/v1/workflows/instances/{instanceId}/cancel
Content-Type: application/json

{
  "reason": "incident",
  "detailJson": "{\"incidentId\":\"...\"}",
  "cancelledBy": "operator-1"
}
```

Экземпляр переводится в `FAILED`, запись пишется в `workflow_cancel_journal`.  
Роли: `operator`, `admin`.

## Доставка сигнала

```http
POST /api/v1/workflows/instances/{instanceId}/signal
Content-Type: application/json

{"signal":"incidentRegistered","operatorId":"operator-1"}
```

Broadcast ко всем `WAITING` экземплярам workflow, ожидающим этот сигнал:

```http
POST /api/v1/workflows/signal
Content-Type: application/json

{
  "workflowPath": "root.platform.workflows.signal-demo",
  "signal": "incidentRegistered",
  "operatorId": "operator-1"
}
```

## API

```http
GET  /api/v1/workflows/by-path?path=...
PUT  /api/v1/workflows/by-path/bpmn?path=...
PUT  /api/v1/workflows/by-path/status?path=...   body: { "status": "ACTIVE" }
POST /api/v1/workflows/by-path/run?path=...
POST /api/v1/workflows/instances/{instanceId}/cancel
POST /api/v1/workflows/instances/{instanceId}/signal
POST /api/v1/workflows/signal
```

## Персистентность

Таблицы: `workflow_instances`, `workflow_user_tasks` (Flyway V2).

## Тесты

`WorkflowEngineTest`, `WorkflowEngineV2Test`, `WorkflowEngineV3Test`, `WorkflowEngineSignalTest`, `BpmnParserTest`, `WorkflowApiTest`, `WorkflowSignalApiTest`, `WorkQueueApiTest`.
