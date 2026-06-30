# Code audit backlog (2026-06-28)

Живой беклог по **аудиту кодовой базы** (не документации). Источник: обход `packages/`, `apps/web-console/`, REST-контроллеров, driver packs, виджетов.

**Связанные документы:**

| Документ | Роль |
| -------- | ---- |
| [ROADMAP.md § Phase 20](ROADMAP.md#phase-20--code-audit-backlog-ui-drivers-scale) | Волны и статусы |
| [GAP_REGISTRY.md](GAP_REGISTRY.md) | Сводка пробелов для sprint planning |
| [PLATFORM_DEVELOPER_BACKLOG.md](PLATFORM_DEVELOPER_BACKLOG.md) | REQ-PF/FW baseline (закрыт) |

**Правило обновления:** при закрытии BL-XX — статус `Done`, ссылка на PR, строка в [GAP_REGISTRY.md](GAP_REGISTRY.md) и Phase 20 в [ROADMAP.md](ROADMAP.md).

**Приоритеты:**

| P | Смысл | Ориентир |
| - | ----- | -------- |
| **P0** | Backend/UI рассинхрон, ломает сценарии | 1 спринт |
| **P1** | Высокая ценность, API уже есть | 1–2 спринта |
| **P2** | Качество HMI / драйверов / ops | 1–2 месяца |
| **P3** | Стратегия, масштаб | квартал+ |

---

## Сводная матрица BL-XX

### Wave A — UI ↔ API (быстрые wins)

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-01** | Correlator actions `SET_VARIABLE`, `OPEN_OPERATOR_REPORT` в Web Console | P0 | Done | Automation UI |
| **BL-02** | Workflow ISPF actions `log`, `publishNats` в справочнике редактора | P0 | Done | Workflow UI |
| **BL-03** | `DataRecordValueEditor` в `VariableFieldEditor` (RECORD/RECORD_LIST) | P1 | Done | Inspector |
| **BL-04** | UI для Platform Change Sets (list/create/preview/apply) | P1 | Done | Platform ops |
| **BL-05** | Edit lease: индикатор «кто редактирует» + acquire/release в Explorer | P1 | Done | Collaboration |
| **BL-06** | Chart widget: реализовать candlestick/bubble/radar/range **или** убрать из редактора | P1 | Done (opt. B) | Dashboard |
| **BL-07** | i18n: `WIDGET_TYPES`, `WIDGET_HISTORY_RANGE_OPTIONS`, `widgetEditorBinding` hints → locale | P1 | Done | i18n |
| **BL-08** | Application Event Catalog viewer (`GET .../applications/{id}/events`) | P1 | Done | Applications |

### Wave B — HMI и автоматизация (polish)

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-09** | Binding expression builder: каталог 18 platform bindings + autocomplete | P2 | Done | Bindings |
| **BL-10** | `network-graph` widget: layout engine (Cytoscape / vis-network) | P2 | Done | Dashboard |
| **BL-11** | `gantt-chart` widget: интерактивный timeline | P2 | Done | Dashboard |
| **BL-12** | `history-table`: настраиваемое окно (сейчас ~5 min hardcoded) | P2 | Done | Dashboard |
| **BL-63** | Chart widget: тип **range** (min/max band из historian aggregate) | P2 | Done | Dashboard |
| **BL-64** | Chart widget: тип **candlestick** (OHLC по bucket / schema) | P2 | Done | Dashboard |
| **BL-65** | Chart widget: типы **bubble** и **radar** (multi-axis / categorical) | P3 | Done | Dashboard |
| **BL-13** | System settings: toggles Redis / NATS / ClickHouse journal / AI provider / MCP | P2 | Done | System |
| **BL-14** | Automation index dashboard (`GET /platform/automation-index/stats`) | P2 | Done | System |
| **BL-15** | Object change history: diff view (before/after по audit entries) | P2 | Done | Journal |
| **BL-16** | Journal panels: export CSV/JSON | P2 | Done | Journal |
| **BL-17** | Alert rules / correlators: опциональный list view (API `fetchAlertRules` / `fetchCorrelators`) | P3 | Done | Automation UI |
| **BL-18** | Binding rules: визуальный редактор activators (`onEvent`, `periodicMs`) | P2 | Done | Bindings |

### Wave C — Драйверы

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-20** | Write path: **Modbus** (tcp/rtu) | P1 | Done | Driver |
| **BL-21** | Write path: **S7** | P1 | Done | Driver |
| **BL-22** | Write path: **OPC UA** client | P1 | Done | Driver |
| **BL-23** | Write path: **BACnet**, **IEC 104** | P2 | Done | Driver |
| **BL-24** | **DNP3**: полный Class 0/1/2/3 poll (сейчас connectivity check) | P2 | Done | Driver |
| **BL-25** | **DLMS** write | P2 | Done | Driver |
| **BL-26** | Stub promotion: Ethernet/IP, OPC DA, OPC Bridge, CORBA, VMware, SMI-S | P3 | Partial | Driver |
| **BL-27** | `DriverMaturityRegistry` ↔ реальные capabilities (auto или manual matrix) | P2 | Done | Driver catalog |
| **BL-28** | Device driver panel: write/command UI поверх runtime API | P2 | Done | Driver UI |
| **BL-29** | CWMP `SetParameterValues` write | P3 | Done | Driver |
| **BL-30** | Unit/integration tests для promoted drivers (loopback/mock) | P2 | Partial | Driver QA |

### Wave D — Scale, ops, federation

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-40** | ClickHouse **variable history** store (`VariableHistoryProperties` future) | P2 | Done | History |
| **BL-41** | Redis correlator window + ACL cache: prod runbook + health в System | P2 | Done | Scale |
| **BL-42** | NATS JetStream: UI статус + workflow `PUBLISH_NATS` smoke hint | P2 | Done | Scale |
| **BL-43** | YARG PDF: LibreOffice path в System + hint в Report Builder | P2 | Done | Reports |
| **BL-44** | Notification channels: webhook/email из alert rule / correlator action | P3 | Done | Automation |
| **BL-45** | Federation: conflict resolution UI при catalog sync | P3 | Done | Federation |
| **BL-46** | Federation: dashboard write на proxy (сейчас read-only) | P3 | Done | Federation |
| **BL-47** | Platform backup/restore (export/import object tree + configs) | P3 | Done | Platform ops |
| **BL-48** | MCP server admin UI (`ispf.mcp.enabled`) | P3 | Done | AI |

### Wave E — Operator, spreadsheet, QA

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-50** | Playwright admin e2e smoke (дублирует [ROADMAP 18.1](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers)) | P1 | Done | QA |
| **BL-51** | Operator manifest: screen types chart / map / embedded dashboard | P3 | Done | Operator |
| **BL-52** | Operator shell: responsive / mobile layout breakpoints | P3 | Done | Operator |
| **BL-53** | Spreadsheet: расширение Excel function set + warning UX при import | P2 | Done | Spreadsheet |
| **BL-54** | Spreadsheet: binding ячеек к variable history | P3 | Done | Spreadsheet |
| **BL-55** | Frontend vitest: binding activators, journal export, chart/gantt utils; RTL dashboard widgets + inspector | P2 | Done | QA |

### Wave G — Semantic interoperability (Haystack / Brick)

Опциональный семантический слой поверх object tree (не замена dot-path). Решение по приоритету — после Wave A/B и по запросу app-команды ([0002](decisions/0002-dogfooding-gate.md)).

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-56** | ADR/spike: Haystack tags как metadata overlay (object tree = source of truth) | P3 | Planned | Architecture |
| **BL-57** | RELATIVE model `haystack-metadata-v1`: `haystackTags`, `haystackRef`, `kind` на DEVICE/variables | P3 | Planned | Models |
| **BL-58** | Haystack export: JSON read по subtree + опциональный `GET /api/v1/.../haystack` | P3 | Planned | API |
| **BL-59** | Driver point mappings → нормализация Haystack tags (`point`, `sensor`, `unit`) | P3 | Planned | Drivers |
| **BL-60** | Brick Schema overlay: `brickClass`, RDF/Turtle export (demand-driven, BIM/digital twin) | P3 | Planned | Semantic |
| **BL-61** | `ispf-driver-haystack`: poll external Haystack server (SkySpark/FIN) → variables | P3 | Planned | Driver |
| **BL-62** | Auto-bind dashboard widgets по tag query (`equip` + `point` + `temp`) | P3 | Planned | Dashboard |

### Wave H — Time & timezones

Контракт store-UTC / display-local ([ADR-0020](decisions/0020-time-and-timezones.md)). Зависимости: BL-66 → BL-67, BL-68; BL-68 → BL-69; BL-67 → BL-70.

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-66** | ADR-0020 + spike: inventory `toLocaleString` / `Instant.now()` hot paths | P2 | Done | Architecture |
| **BL-67** | User `timeZone` (IANA) в profile + `TimezoneSwitcher` + `formatDateTime` в UI | P2 | Done | Security / UI |
| **BL-68** | Device `timeZone` metadata + inheritance от folder/site | P2 | Done | Models / Drivers |
| **BL-69** | Historian `observedAt` + driver SPI source timestamps | P3 | Done | History / Drivers |
| **BL-70** | Calendar-boundary history queries + reports (`timeZone` param) | P3 | Done | History / Reports |
| **BL-71** | Event fire optional `occurredAt` override + skew guard | P3 | Done | Events / API |

### Wave F — Уже сделано / не требует BL (reference)

| Тема | Статус | Где в коде |
| ---- | ------ | ---------- |
| Journal framework (virtual list, live/history) | **Done** | `components/journal/` |
| Binding / function invoke journals | **Done** | `BindingInvokeJournalPanel`, `FunctionInvokeJournalPanel` |
| Object config audit / change history panel | **Done** | `ObjectChangeHistoryPanel` |
| DataSchema editor в create/edit variable dialogs | **Done** | `DataSchemaEditor`, `CreateVariableDialog` |
| `mini-tec-sld` в palette | **Done** | `WidgetPalette.tsx` |
| 43 widget types + view components | **Done** | `types/dashboard.ts`, `widgets/` |
| Correlator `SET_VARIABLE` на backend | **Done** | `CorrelatorActionType`, Phase 15.2 |
| Event journal → ClickHouse | **Done** (opt-in) | `ClickHouseEventJournalStore` |

---

## Детали по ключевым BL-XX

### BL-01 — Correlator actions в UI

**Проблема:** backend и MiniTec bootstrap используют 4 action types; UI знает только 2.

```typescript
// apps/web-console/src/types/automation.ts — сейчас
export type CorrelatorActionType = "RUN_WORKFLOW" | "FIRE_EVENT";
```

```java
// packages/ispf-server/.../CorrelatorActionType.java
RUN_WORKFLOW, FIRE_EVENT, SET_VARIABLE, OPEN_OPERATOR_REPORT
```

**Задачи:**

- [ ] Расширить `CorrelatorActionType` в `types/automation.ts`
- [ ] `CreateCorrelatorDialog`, `CorrelatorInspector` — options + labels + validation `actionTarget`
- [ ] i18n ключи в `automation.json` (4 locales)
- [ ] Тест: create correlator с `SET_VARIABLE` через UI

**Acceptance:** оператор настраивает correlator «N alarm events → `alarmLatched=true`» без JSON в дереве.

---

### BL-02 — Workflow actions в UI

**Проблема:** `WorkflowActionType` — 7 значений; `WORKFLOW_ISPF_ACTIONS` — 5. `BpmnDiagramEditor` знает `publishNats`, но не в user-facing справочнике.

**Файлы:** `types/automation.ts`, `workflow/BpmnDiagramEditor.tsx`, `locales/*/automation.json`

**Acceptance:** в палитре ISPF actions видны `log` и `publishNats` с атрибутами.

---

### BL-03 — Structured record editing inline

**Проблема:** `VariableFieldEditor` для `RECORD`/`RECORD_LIST` — JSON textarea; `DataRecordValueEditor` уже есть в диалогах.

**Файлы:** `VariableFieldEditor.tsx`, `ObjectPropertiesEditor.tsx` (variables tab)

**Acceptance:** редактирование RECORD-полей на вкладке variables — те же контролы, что в Create/Edit Variable.

---

### BL-04 — Platform Change Sets UI

**API:** `PlatformChangeSetController` — `/api/v1/platform/change-sets` (CRUD, preview, apply).

**Задачи:**

- [ ] `api/platformChangeSets.ts`
- [ ] Panel в System или Explorer (platform root)
- [ ] Preview diff + confirm apply

**Acceptance:** админ создаёт change set, preview, apply без curl.

---

### BL-05 — Edit leases

**API:** `fetchEditLeases`, `acquireEditLease`, `releaseEditLease` в `api.ts`; `ObjectEditLeaseService` на сервере.

**Задачи:**

- [ ] Badge в Explorer header при активной lease на выбранном path
- [ ] «Захватить / освободить» при `canManage`
- [ ] WebSocket или polling при конфликте 409

**Acceptance:** два админа видят, кто держит lease на объекте.

---

### BL-06 — Chart types

**Проблема:** `ChartType` включает candlestick/bubble/radar/range; `ChartWidgetView` рендерит bar/line/area.

**Варианты (выбрать в спринте):**

- **A:** реализовать через chart library (recharts extensions / chart.js)
- **B:** убрать лишние options из `widgetEditorFields.tsx`

**Решение (2026-06-28):** закрыто **вариантом B** — убраны обманчивые options; `ChartType` в `types/dashboard.ts` сохранён для обратной совместимости JSON. Реализация типов → **BL-63…65**.

**Acceptance:** нет silent fallback на area без предупреждения.

---

### BL-11 — Gantt interactive timeline

**Контекст:** `gantt-chart` рендерил статичные полосы без шкалы времени; оператор не мог смотреть длинные планы и сдвигать задачи.

**Задачи:**

- [x] `ganttChartView.ts`: viewport (fit/pan/zoom/clamp), layout полос, ticks, patch row times
- [x] `GanttChartWidgetView`: wheel zoom, drag pan, optional bar drag → `setVariable` при writable RECORD_LIST
- [x] Редактор: `interactive`, `allowBarDrag`; i18n en/ru/de/zh; CSS axis + interactive cursors
- [x] Vitest: `ganttChartView.test.ts`

**Acceptance:** в operator mode — scroll zoom, drag pan, double-click reset; при writable variable — drag bar с persist; в editor preview — только pan/zoom без persist.

**Статус (2026-06-28):** Done — `ganttChartView`, `GanttChartWidgetView`, editor toggles, i18n, styles.

---

### BL-53 — Spreadsheet Excel functions + import warnings

**Контекст:** при импорте XLSX неподдерживаемые функции давали `#NAME?` без явного списка; tail Excel functions (CHAR/CODE/REPT/PROPER, MAXIFS/MINIFS, NA/ISLOGICAL/ISODD/ISEVEN/CLEAN) отсутствовали или не были покрыты тестами.

**Задачи:**

- [x] `ispfSheetEval`: NA, ISLOGICAL, ISODD, ISEVEN, CLEAN (+ ru aliases)
- [x] `sheetFormulaNormalize`: SUPPORTED_SHEET_FUNCTIONS sync
- [x] `sheetXlsx`: structured `SheetImportReport` (unsupportedFunctions + truncations)
- [x] `SpreadsheetImportNotice` + free-grid import banner (dismiss, fn list, truncation notes)
- [x] i18n en/ru/de/zh; vitest `sheetFormulaEngine.test.ts`

**Acceptance:** после импорта книги с LET/SUM — banner с перечнем unsupported fn; CHAR/MAXIFS/NA работают в free mode.

**Статус (2026-06-28):** Done — `SpreadsheetImportNotice`, `sheetXlsx` report, formula engine extensions.

---

### BL-63 — Chart `range` (min/max band)

**Контекст:** наиболее полезный для SCADA из отложенных типов; historian aggregate уже отдаёт `min`/`max`/`avg` по bucket (`useVariableHistory`, `fetchVariableHistoryAggregate`).

**Задачи:**

- [ ] `ChartWidgetView`: ветка `chartType === "range"` — `Area` с двумя сериями или band между min/max
- [ ] Включить aggregate path при `range` + `historyRange` ≠ `live`
- [ ] Вернуть option в `widgetEditorFields.tsx` только после рендера
- [ ] i18n + demo preview в редакторе

**Acceptance:** оператор видит коридор min–max за 24h/7d без silent fallback.

**Статус (2026-06-28):** Done — `useChartTrendSeries`, aggregate buckets, `ChartWidgetView` ComposedChart.

---

### BL-64 — Chart `candlestick` (OHLC)

**Контекст:** candlestick требует open/high/low/close на интервал; скалярный historian — только value samples.

**Варианты:**

- **A:** синтетический OHLC из aggregate bucket (open=first, close=last, min/max в bucket) — MVP
- **B:** отдельная historian schema / multi-field RECORD на variable

**Задачи:**

- [x] ADR или spike: выбрать A vs B → **вариант A** (синтетический OHLC)
- [x] `ChartWidgetView` + recharts `ComposedChart` / custom shapes
- [x] Поля редактора: bucket size через historyRange (как range)
- [x] Вернуть option в редактор

**Acceptance:** candlestick на demo device с историей; tooltip показывает O/H/L/C.

**Статус (2026-06-28):** Done — вариант A (синтетический OHLC из aggregate buckets + live chunking), `chartOhlcUtils`, `CandlestickChartBody`, редактор.

---

### BL-65 — Chart `bubble` и `radar`

**Контекст:** не time-series «одна переменная → линия»; нужны несколько осей или категории.

**Задачи:**

- [x] **bubble:** x/y/size — binding к 2–3 variables или static params; `ScatterChart` + `ZAxis`
- [x] **radar:** categorical axes — список variables или JSON `axesJson`; `RadarChart`
- [x] Отдельные секции редактора (не переиспользовать только `variableName`)
- [x] Вернуть options в редактор

**Acceptance:** bubble/radar на sample dashboard; документированы ограничения data model.

**Реализация (2026-06-28):** `ChartBubbleWidgetView` / `ChartRadarWidgetView`, `chartRadarBubbleUtils.ts`, поля `bubbleXVariable`…`radarAxesJson`, sample builders `buildChartBubbleSampleWidget` / `buildChartRadarSampleWidget`.

**Ограничения:** bubble trajectory — zip по индексу live/history рядов X и Y (не по timestamp); radar — только latest snapshot (≥3 осей); `bubblePointsJson` перекрывает trajectory mode.

**Приоритет:** P3 — после BL-63/64 и по запросу HMI-команды.

---

### BL-09 — Binding expression builder

**Backend:** 18 functions в `PlatformBindingRegistry.java` (`rate`, `movingAvg`, `hysteresis`, `callFunction`, …).

**Задачи:**

- [x] Статический каталог в `utils/platformBindings.ts` (sync с registry)
- [x] Autocomplete в `BindingExpressionField`
- [x] Snippet insert по клику + guided composer (параметры, переменные объекта)

**Acceptance:** инженер собирает `movingAvg(temperature, 60)` без чтения BINDINGS.md.

**Статус (2026-06-28):** Done — каталог 18 функций, datalist/inline autocomplete, `PlatformBindingComposer` с полями параметров и chips переменных объекта.

---

### BL-20…25 — Driver write matrix

| Driver | Файл | Текущее | BL |
| ------ | ---- | ------- | -- |
| Modbus | `ModbusTcpDeviceDriver.java`, `ModbusRtuDeviceDriver.java` | read + write (HOLDING/COIL) | BL-20 Done |
| S7 | `S7DeviceDriver.java` | `write not implemented` | BL-21 |
| OPC UA | `OpcUaDeviceDriver.java` | read + write (node Value) | BL-22 Done |
| BACnet | `BacnetDeviceDriver.java` | `write not implemented` | BL-23 |
| IEC 104 | `Iec104DeviceDriver.java` | `write not implemented` | BL-23 |
| DNP3 | `Dnp3DeviceDriver.java` | Class 0/1/2/3 poll (read) | BL-24 Done |
| DLMS | `DlmsDeviceDriver.java` | read + write (REGISTER attr 2) | BL-25 Done |

**Acceptance (каждый):** integration test loopback/mock; maturity label обновлён; docs в [DRIVERS.md](DRIVERS.md).

**Статус (2026-06-28):** BL-20 Done — `writePoint` для `modbus-tcp`/`modbus-rtu` (FC5 coil, FC6 holding register); loopback tests в `ModbusTcpDeviceDriverTest`, guard-rail tests в `ModbusRtuDeviceDriverTest`; docs в DRIVERS.md. BL-22 Done — `writePoint` для `opcua` (Milo `writeValue`, type coercion по текущему Variant); loopback test `OpcUaDeviceDriverTest` против `opcua-server`; docs в DRIVERS.md.

**Статус (2026-06-28):** BL-21 Done — `writePoint` для `s7` (encode + `S7Connector.write`, BOOL read-modify-write); mock tests в `S7DeviceDriverTest`, codec roundtrip в `S7ValueCodecTest`; docs в DRIVERS.md.

**Статус (2026-06-28):** BL-23 Done — `writePoint` для `bacnet` (`RequestUtils.writeProperty`, AO/BO/AV/BV/MO/MV) и `iec104` (`singleCommand` / `setShortFloatCommand` / `setNormalizedValueCommand`); loopback `Iec104DeviceDriverTest` против `iec104-server`; guard tests `BacnetDeviceDriverTest`; maturity **beta**.

**Статус (2026-06-28):** BL-27 Done — `DriverMetadata.capabilities`, `DriverCapabilityRegistry`, merge в `DriverCatalog`; default maturity **beta** (whitelist PRODUCTION/STUB); UI gating `DriverWriteForm` по `capabilities.includes("write")`; `DriverCatalogConsistencyTest`.

**Статус (2026-06-30):** BL-30 Partial — loopback tests для modbus/opcua/s7/iec104/dnp3/dlms/cwmp/http; BACnet — `BacnetLoopbackServer` + connect smoke (property exchange на hardware/simulator).

**Статус (2026-06-28):** BL-24 Done — `io.stepfunc:dnp3` master TCP, Class 0/1/2/3 integrity poll; loopback `Dnp3DeviceDriverTest` + `Dnp3LoopbackOutstation`; config `localAddress`/`outstationAddress`; docs в DRIVERS.md.

**Статус (2026-06-28):** BL-25 Done — Gurux `GXDLMSClient` WRAPPER association, read/write REGISTER (default attr 2); mapping `ld:obis[:type[:attr]]`; loopback `DlmsDeviceDriverTest` + `DlmsLoopbackServer`; `DriverCapabilityRegistry` write; docs в DRIVERS.md.

---

### BL-28 — Driver write UI

**Проблема:** runtime API `POST /drivers/runtime/write` и `poll` были без UI; инженер не мог отправить write из Explorer/inspector.

**Задачи:**

- [x] `POST /api/v1/drivers/runtime/write?devicePath&pointId` + `DriverRuntimeService.writePoint`
- [x] `DriverWriteForm` + `DriverWriteDialog` в web-console
- [x] Вкладка Driver в inspector (`DeviceDriverPanel`)
- [x] Explorer toolbar + context menu (DEVICE): poll / write
- [x] i18n en/ru/de/zh

**Acceptance:** admin выбирает mapped point, вводит value, driver running → write через UI; poll now обновляет variables.

**Статус (2026-06-28):** Done — UI в `DeviceDriverPanel`, `ExplorerView`, `TreeBulkContextMenu`; API clients `pollDriver` / `writeDriverPoint`.

---

### BL-40 — ClickHouse variable history

**Проблема:** event journal уже в ClickHouse; variable history — только JDBC/Timescale.

**Задачи:**

- [x] `ClickHouseVariableHistoryStore` write + query path (`VariableHistoryQueryStore`)
- [x] Config `ispf.variable-history.store=clickhouse` (properties + `application.yml` env vars)
- [x] Deploy: `vps-clickhouse-verify.sh` расширен (variable_samples table + optional write smoke)
- [x] System settings UI: store toggle + ClickHouse connection fields (`PlatformRuntimeSettingsCatalog`, `SystemSettingsView`)

**Статус (2026-06-28):** Done — `ClickHouseVariableHistoryStore` (append + query/aggregate), `JdbcVariableHistoryQueryStore` для jdbc/jpa, Timescale skip при `store=clickhouse`, unit test + verify script.

### BL-41 — Redis correlator window + ACL cache health

**Проблема:** Redis optional backend (ACL cache, correlator sliding windows) без операторского статуса в UI.

**Задачи:**

- [x] `GET /api/v1/platform/redis/health` — connection, store backends, TTLs, correlator key count
- [x] `RedisHealthCard` в System → Metrics (рядом с метриками JVM/DB)
- [x] i18n `redisHealth.*` (en/ru/de/zh); inline runbook hint (env vars)
- [x] Док: [MESSAGING.md § Redis correlator windows](MESSAGING.md#redis-correlator-windows-optional-0014)

**Acceptance:** админ видит connected/disconnected, correlator store (redis/jdbc), ACL cache backend, TTLs; ключи окон при Redis.

**Статус (2026-06-28):** Done.

---

### BL-42 — NATS JetStream UI + PUBLISH_NATS smoke hint

**Проблема:** NATS/JetStream replica fan-out и workflow `publishNats` без видимого статуса и smoke-подсказки.

**Задачи:**

- [x] `GET /api/v1/platform/nats/health` — connection, JetStream stream/consumer stats, `publishNatsAvailable`
- [x] `NatsJetStreamHealthCard` в System → Metrics
- [x] i18n `natsHealth.*` (en/ru/de/zh); smoke hint в карточке и `workflow.json` → `BpmnDiagramEditor`
- [x] Auth: `/api/v1/platform/nats/**` в admin rules

**Acceptance:** админ видит JetStream ready, stream messages/bytes, consumer pending; в BPMN editor при `publishNats` — hint со smoke subject.

**Статус (2026-06-28):** Done.

---

### BL-50 — Playwright e2e

Синхронизировано с [ROADMAP Phase 18.1](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers).

**Статус:** Done — smoke baseline в `apps/web-console` (`playwright.config.ts`, `e2e/smoke.spec.ts`, `e2e/live.spec.ts`, `e2e/fixtures/apiMocks.ts`, `npm run test:e2e`). Локальный runbook: [`apps/web-console/e2e/README.md`](../apps/web-console/e2e/README.md).

- [x] Login page smoke (mock `/api/v1/auth/config`)
- [x] Admin Explorer shell smoke (mock session + platform API)
- [x] Operator deep link `?mode=operator&app=demo` (public manifest)
- [x] Optional live login when `E2E_USERNAME` / `E2E_PASSWORD` set
- [x] CI job `web-console` (mocked e2e + vitest + build)
- [x] Explorer: expand Devices, select device (`?path=` deep link)
- [x] Dashboard layout API mock (label widget payload)
- [x] Explorer: Variables tab after inspector load
- [x] Dashboard builder UI render (double-click / Open in editor)
- [x] Bindings tab: platform function catalog (BL-09 builder)
- [x] Optional live smoke: `e2e/live.spec.ts` + workflow `.github/workflows/e2e-live.yml` (`E2E_BASE_URL` + secrets)

**Статус:** Done (mocked CI + optional live workflow; prod run needs `E2E_USERNAME` / `E2E_PASSWORD` secrets).

```bash
cd apps/web-console
npm ci && npm run test:e2e:install
npm run test:e2e
# live backend:
E2E_BASE_URL=http://localhost:8080 E2E_USERNAME=admin E2E_PASSWORD=admin npm run test:e2e
```

---

### BL-56…62 — Haystack / Brick Schema (semantic layer)

**Контекст:** ISPF использует dot-path + `DataSchema` + models; Project Haystack и Brick Schema дают стандартную семантику для BMS/energy/BIM. В коде интеграции **нет** — только обсуждение 2026-06-28.

**Принцип (предварительный):**

```text
ISPF object tree     — runtime source of truth (пути, bindings, history)
Haystack tags        — optional metadata overlay (RELATIVE model / variables)
Brick Schema         — optional formal graph export (P3, по заказчику)
```

**BL-56 — ADR**

- [ ] `docs/decisions/00xx-haystack-semantic-overlay.md`: scope, mapping tree↔tags, out of scope (не менять пути)
- [ ] Сравнение с OPC UA semantic model, ASHRAE 223P

**BL-57 — Haystack mixin model**

- [ ] `haystack-metadata-v1` RELATIVE model: variables `haystackTags` (MARKER list), `haystackRef`, `haystackKind`
- [ ] Inspector: tag editor (multiselect common markers: `equip`, `point`, `sensor`, `temp`, `his`)
- [ ] Пример на virtual-lab / Mini-TEC chiller

**BL-58 — Export**

- [ ] Bundle script или REST: export subtree as Haystack JSON grid
- [ ] Mapping: ISPF variable → `{tags, cur, unit, dis}`

**BL-59 — Driver conventions**

- [ ] Документ + пример в `driverPointMappingsJson`: protocol address + haystack tags
- [ ] BACnet/OPC UA mapping profile (spike)

**BL-60 — Brick (demand-only)**

- [ ] `brickClass` URI на object; export Turtle/JSON-LD subset
- [ ] Связи `hasPoint` через `refAt` bindings или explicit refs

**BL-61 — External Haystack driver**

- [ ] `ispf-driver-haystack`: HTTP Zinc/JSON client, subscribe/poll remote refs

**BL-62 — Semantic HMI**

- [ ] Dashboard builder: «bind all points where tags match `equip and temp`»
- [ ] AI agent tool: `search_by_haystack_tags`

**Acceptance (минимум для закрытия волны):** BL-56 ADR принят + BL-57 пример equip/point на demo device + BL-58 JSON export одного subtree.

**Не в scope без отдельного REQ:** замена `root.platform.devices.*` на Haystack refs; полный Brick reasoner в runtime.

---

### BL-55 — Frontend vitest (utils + widget logic)

**Статус (2026-06-30):** Done — acceptance выполнен (inspector panels + widget editors + smoke RTL widget views).

**Сделано:**

- [x] `bindingActivatorsUtils.test.ts` — event select/remote path/summary (17 tests)
- [x] `journalExport.test.ts` — CSV mappers, row union, `downloadJournalExport` (node stubs)
- [x] `chartOhlcUtils.test.ts` — live/bucket OHLC + stats (7 tests)
- [x] `ganttChartView.test.ts` — viewport, bar layout, ticks, row patch (12 tests)
- [x] RTL: `BindingActivatorsEditor`, `PlatformBindingComposer`
- [x] RTL: `PlatformLicenseCard`, `CreateVariableDialog`
- [x] RTL: `LabelWidgetView`, `ValueWidgetView`, `WidgetEditorPanel`, `ProgressWidgetView`, `GaugeWidgetView`, `TimerWidgetView`
- [x] `renderWithDashboard` / `renderWithInspector` test helpers

**Acceptance:** smoke RTL на ключевые inspector panels + widget editors — выполнен.

---

### BL-66 — ADR time & timezones + spike

**Проблема:** UTC в storage, но нет контракта user/device TZ; UI = browser default; historian = ingestion time.

**Задачи:**

- [ ] Принять [ADR-0020](decisions/0020-time-and-timezones.md) (Proposed → Accepted)
- [ ] Inventory hot paths: журналы, charts, reports, drivers, schedules (appendix в ADR)
- [ ] Политика DST / ambiguous local time при парсинге device-local строк

**Acceptance:** ADR merged; список затронутых файлов в ADR appendix.

---

### BL-67 — User timezone preference

**Задачи:**

- [ ] Flyway: `platform_users.time_zone VARCHAR(64)` default `'UTC'`
- [ ] Переменная `timeZone` на user object + sync в `PlatformUserService`
- [ ] Profile API → `{ timeZone: "Europe/Moscow" }`
- [ ] `TimezoneSwitcher` в `ShellPreferences.tsx`; `localStorage` `ispf.ui.timeZone`
- [ ] Утилита `formatDateTime(iso, { timeZone, locale })` — журналы, charts, metrics, AI chat

**Acceptance:** два пользователя с разными TZ видят одни события в своём локальном времени; API отдаёт UTC.

---

### BL-68 — Device timezone metadata

**Задачи:**

- [ ] RELATIVE model `device-timezone-v1` или расширение device-driver blueprint: `timeZone` (IANA, optional)
- [ ] Наследование: device → parent folder/site → `UTC`
- [ ] Helper `resolveTimeZone(objectPath)`; Inspector UI
- [ ] Документация: [DRIVERS.md](DRIVERS.md), [OBJECT_MODEL.md](OBJECT_MODEL.md)

**Acceptance:** устройство с `Asia/Yekaterinburg` видно в inspector; helper готов для CEL (фаза 2+).

---

### BL-69 — Source timestamps (driver SPI + historian)

**Задачи:**

- [x] `VariableHistoryWriteRecord`: `observedAt` + `ingestedAt` (`sampled_at`); V56 migration
- [x] JDBC/CH write+query: chart/filter по `COALESCE(observed_at, sampled_at)`; API `ingestedAt` в sample
- [x] `VariableHistoryService.recordObservedSample(...)` для явного device timestamp
- [x] Driver poll SPI: `DriverObject.updateVariable(..., observedAt)`; virtual + MQTT pilot

**Acceptance:** historian query по `observedAt`; lag = `ingestedAt - observedAt` — **Done** (poll SPI — follow-up).

---

### BL-70 — Calendar-boundary queries & reports

**Задачи:**

- [x] History API: `calendarRange=today|yesterday` + optional `timeZone` → UTC `from`/`to`
- [x] Widget / inspector `historyRange`: «today» / «yesterday» в **user TZ**
- [x] Report Builder: `calendarRange` + `reportTimeZone`/`timeZone` → `from`/`to`/`fromTs`/`toTs` в `ReportService.run`

**Acceptance:** график «за сегодня» у оператора в MSK совпадает с полуночью MSK — **Done** (reports — follow-up).

---

### BL-71 — Event fire `occurredAt` override

**Задачи:**

- [ ] `POST /events/fire` body: optional `occurredAt` (ISO-8601)
- [ ] `ObjectEvent` factory с explicit timestamp; skew guard
- [ ] Тесты + public API docs

**Acceptance:** edge gateway шлёт событие с device time; journal хранит переданный `occurredAt`.

---

## Sprint planning (рекомендация)

```text
Sprint BL-A (2 недели) — P0/P1 UI↔API
  BL-01, BL-02, BL-03, BL-07, BL-08

Sprint BL-B (2 недели) — Platform ops + dashboard
  BL-04, BL-05, BL-06, BL-12

Sprint BL-C (3 недели) — HMI polish
  BL-09, BL-10, BL-13, BL-14, BL-15
  опционально: BL-63 (chart range) если приоритет SCADA-трендов

Sprint BL-D (ongoing) — Drivers по demand ([0002](decisions/0002-dogfooding-gate.md))
  BL-20…25 — порядок по app-команде
  BL-27, BL-30

Sprint BL-E (2 недели) — QA
  BL-50, BL-55

Backlog P3 (квартал)
  BL-51…54 (BL-44…48 Done — Wave D ops/federation tail закрыт)

Backlog P3 — semantic (по запросу, после ADR)
  BL-56…62 — Haystack/Brick ([§ BL-56…62](CODE_AUDIT_BACKLOG.md#bl-5662--haystack--brick-schema-semantic-layer))

Backlog P2/P3 — time & timezones ([ADR-0020](decisions/0020-time-and-timezones.md), [ROADMAP Phase 21](ROADMAP.md#phase-21--time--timezones))
  BL-66…68 — user + device TZ (после ADR)
  BL-69…71 — historian observedAt, calendar queries, event occurredAt
```

Параллельно: [ROADMAP Phase 18](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers) (18.1 = BL-50, 18.2 = BL-26 по запросу); [Phase 21](ROADMAP.md#phase-21--time--timezones) (BL-66…71).

---

## История

| Дата | Изменение |
| ---- | --------- |
| 2026-06-30 | BL-55 Done: RTL `GaugeWidgetView`, `TimerWidgetView`; inspector + dashboard widget smoke complete |
| 2026-06-30 | BL-55: RTL `LabelWidgetView`, `ValueWidgetView`, `WidgetEditorPanel`; BL-30: `HttpDeviceDriverTest` loopback |
| 2026-06-30 | BL-29 Done: CWMP `SetParameterValues` write + runtime API test; BL-55: RTL `PlatformLicenseCard`, `CreateVariableDialog` |
| 2026-06-30 | Wave H: time & timezones → BL-66…71, [ADR-0020](decisions/0020-time-and-timezones.md), [ROADMAP Phase 21](ROADMAP.md#phase-21--time--timezones) |
| 2026-06-28 | BL-44…47 Done: notifications (webhook/email), federation catalog preview + dashboard write proxy, platform backup export/import |
| 2026-06-28 | BL-23, BL-27 Done; BL-30 Partial; BL-43, BL-48 Done (YARG/MCP health cards) |
| 2026-06-28 | BL-40 Done: ClickHouse variable history write/query, `VariableHistoryQueryStore`, verify script |
| 2026-06-28 | BL-50: Variables tab + Dashboard builder Playwright smoke; AI `/api/v1/ai/*` mocks; `AiStudioSettingsTab` tools guard |
| 2026-06-28 | BL-11: gantt interactive timeline (pan/zoom, bar drag, `ganttChartView`, vitest) |
| 2026-06-28 | BL-41, BL-42 Done: Redis/NATS health API + System Metrics cards, i18n, BPMN publishNats smoke hint |
| 2026-06-28 | BL-20: Modbus tcp/rtu `writePoint` (FC5/FC6), loopback + guard-rail tests, DRIVERS.md |
| 2026-06-28 | BL-64: chart candlestick OHLC (вариант A, `chartOhlcUtils`, `CandlestickChartBody`) |
| 2026-06-28 | BL-54: spreadsheet binding cells `historyMinutes` + historian fetch in `useSheetBindings` |
| 2026-06-28 | BL-65: chart bubble (ScatterChart) + radar (RadarChart), editor sections, sample builders |
| 2026-06-28 | Wave G: Haystack/Brick semantic layer → BL-56…62 (P3, deferred) |
| 2026-06-28 | Первая версия: code audit → BL-01…BL-55, Wave A–F |
