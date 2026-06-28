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
| **BL-01** | Correlator actions `SET_VARIABLE`, `OPEN_OPERATOR_REPORT` в Web Console | P0 | Planned | Automation UI |
| **BL-02** | Workflow ISPF actions `log`, `publishNats` в справочнике редактора | P0 | Planned | Workflow UI |
| **BL-03** | `DataRecordValueEditor` в `VariableFieldEditor` (RECORD/RECORD_LIST) | P1 | Planned | Inspector |
| **BL-04** | UI для Platform Change Sets (list/create/preview/apply) | P1 | Planned | Platform ops |
| **BL-05** | Edit lease: индикатор «кто редактирует» + acquire/release в Explorer | P1 | Planned | Collaboration |
| **BL-06** | Chart widget: реализовать candlestick/bubble/radar/range **или** убрать из редактора | P1 | Planned | Dashboard |
| **BL-07** | i18n: `WIDGET_TYPES`, `WIDGET_HISTORY_RANGE_OPTIONS`, `widgetEditorBinding` hints → locale | P1 | Planned | i18n |
| **BL-08** | Application Event Catalog viewer (`GET .../applications/{id}/events`) | P1 | Planned | Applications |

### Wave B — HMI и автоматизация (polish)

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-09** | Binding expression builder: каталог 18 platform bindings + autocomplete | P2 | Planned | Bindings |
| **BL-10** | `network-graph` widget: layout engine (Cytoscape / vis-network) | P2 | Planned | Dashboard |
| **BL-11** | `gantt-chart` widget: интерактивный timeline | P2 | Planned | Dashboard |
| **BL-12** | `history-table`: настраиваемое окно (сейчас ~5 min hardcoded) | P2 | Planned | Dashboard |
| **BL-13** | System settings: toggles Redis / NATS / ClickHouse journal / AI provider / MCP | P2 | Planned | System |
| **BL-14** | Automation index dashboard (`GET /platform/automation-index/stats`) | P2 | Planned | System |
| **BL-15** | Object change history: diff view (before/after по audit entries) | P2 | Planned | Journal |
| **BL-16** | Journal panels: export CSV/JSON | P2 | Planned | Journal |
| **BL-17** | Alert rules / correlators: опциональный list view (API `fetchAlertRules` / `fetchCorrelators`) | P3 | Planned | Automation UI |
| **BL-18** | Binding rules: визуальный редактор activators (`onEvent`, `periodicMs`) | P2 | Planned | Bindings |

### Wave C — Драйверы

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-20** | Write path: **Modbus** (tcp/rtu) | P1 | Planned | Driver |
| **BL-21** | Write path: **S7** | P1 | Planned | Driver |
| **BL-22** | Write path: **OPC UA** client | P1 | Planned | Driver |
| **BL-23** | Write path: **BACnet**, **IEC 104** | P2 | Planned | Driver |
| **BL-24** | **DNP3**: полный Class 0/1/2/3 poll (сейчас connectivity check) | P2 | Planned | Driver |
| **BL-25** | **DLMS** write | P2 | Planned | Driver |
| **BL-26** | Stub promotion: Ethernet/IP, OPC DA, OPC Bridge, CORBA, VMware, SMI-S | P3 | Planned | Driver |
| **BL-27** | `DriverMaturityRegistry` ↔ реальные capabilities (auto или manual matrix) | P2 | Planned | Driver catalog |
| **BL-28** | Device driver panel: write/command UI поверх runtime API | P2 | Planned | Driver UI |
| **BL-29** | CWMP `SetParameterValues` write | P3 | Planned | Driver |
| **BL-30** | Unit/integration tests для promoted drivers (loopback/mock) | P2 | Planned | Driver QA |

### Wave D — Scale, ops, federation

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-40** | ClickHouse **variable history** store (`VariableHistoryProperties` future) | P2 | Planned | History |
| **BL-41** | Redis correlator window + ACL cache: prod runbook + health в System | P2 | Planned | Scale |
| **BL-42** | NATS JetStream: UI статус + workflow `PUBLISH_NATS` smoke hint | P2 | Planned | Scale |
| **BL-43** | YARG PDF: LibreOffice path в System + hint в Report Builder | P2 | Planned | Reports |
| **BL-44** | Notification channels: webhook/email из alert rule / correlator action | P3 | Planned | Automation |
| **BL-45** | Federation: conflict resolution UI при catalog sync | P3 | Planned | Federation |
| **BL-46** | Federation: dashboard write на proxy (сейчас read-only) | P3 | Planned | Federation |
| **BL-47** | Platform backup/restore (export/import object tree + configs) | P3 | Planned | Platform ops |
| **BL-48** | MCP server admin UI (`ispf.mcp.enabled`) | P3 | Planned | AI |

### Wave E — Operator, spreadsheet, QA

| ID | Задача | P | Статус | Область |
| -- | ------ | - | ------ | ------- |
| **BL-50** | Playwright admin e2e smoke (дублирует [ROADMAP 18.1](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers)) | P1 | Planned | QA |
| **BL-51** | Operator manifest: screen types chart / map / embedded dashboard | P3 | Planned | Operator |
| **BL-52** | Operator shell: responsive / mobile layout breakpoints | P3 | Planned | Operator |
| **BL-53** | Spreadsheet: расширение Excel function set + warning UX при import | P2 | Planned | Spreadsheet |
| **BL-54** | Spreadsheet: binding ячеек к variable history | P3 | Planned | Spreadsheet |
| **BL-55** | Frontend component tests (dashboard widgets, inspector dialogs) | P2 | Planned | QA |

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

**Acceptance:** нет silent fallback на area без предупреждения.

---

### BL-09 — Binding expression builder

**Backend:** 18 functions в `PlatformBindingRegistry.java` (`rate`, `movingAvg`, `hysteresis`, `callFunction`, …).

**Задачи:**

- [ ] Статический каталог в `utils/platformBindings.ts` (sync с registry)
- [ ] Autocomplete в `BindingExpressionField`
- [ ] Optional: snippet insert по клику

**Acceptance:** инженер собирает `movingAvg(ref(self, "temp"), 60)` без чтения BINDINGS.md.

---

### BL-20…25 — Driver write matrix

| Driver | Файл | Текущее | BL |
| ------ | ---- | ------- | -- |
| Modbus | `ModbusDeviceDriver.java` | read + partial | BL-20 |
| S7 | `S7DeviceDriver.java` | `write not implemented` | BL-21 |
| OPC UA | `OpcUaDeviceDriver.java` | `write not implemented` | BL-22 |
| BACnet | `BacnetDeviceDriver.java` | `write not implemented` | BL-23 |
| IEC 104 | `Iec104DeviceDriver.java` | `write not implemented` | BL-23 |
| DNP3 | `Dnp3DeviceDriver.java` | connectivity only | BL-24 |
| DLMS | `DlmsDeviceDriver.java` | `write not implemented in v0.1` | BL-25 |

**Acceptance (каждый):** integration test loopback/mock; maturity label обновлён; docs в [DRIVERS.md](DRIVERS.md).

---

### BL-40 — ClickHouse variable history

**Проблема:** event journal уже в ClickHouse; variable history — только JDBC/Timescale.

```java
// VariableHistoryProperties.java
// Future: clickhouse.
```

**Задачи:**

- [ ] `ClickHouseVariableHistoryWriteStore` + query path
- [ ] Config `ispf.variable-history.store=clickhouse`
- [ ] Deploy: `vps-clickhouse-verify.sh` расширить
- [ ] BL-13 UI toggle

---

### BL-50 — Playwright e2e

Синхронизировано с [ROADMAP Phase 18.1](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers).

**Сценарии:**

1. Login (OIDC mock / test user)
2. Explorer: select device, open variables tab
3. Operator deep link `?mode=operator&app=…`
4. Dashboard preview render

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

## Sprint planning (рекомендация)

```text
Sprint BL-A (2 недели) — P0/P1 UI↔API
  BL-01, BL-02, BL-03, BL-07, BL-08

Sprint BL-B (2 недели) — Platform ops + dashboard
  BL-04, BL-05, BL-06, BL-12

Sprint BL-C (3 недели) — HMI polish
  BL-09, BL-10, BL-13, BL-14, BL-15

Sprint BL-D (ongoing) — Drivers по demand ([0002](decisions/0002-dogfooding-gate.md))
  BL-20…25 — порядок по app-команде
  BL-27, BL-30

Sprint BL-E (2 недели) — QA
  BL-50, BL-55

Backlog P3 (квартал)
  BL-40…48, BL-51…54, BL-44…47

Backlog P3 — semantic (по запросу, после ADR)
  BL-56…62 — Haystack/Brick ([§ BL-56…62](CODE_AUDIT_BACKLOG.md#bl-5662--haystack--brick-schema-semantic-layer))
```

Параллельно: [ROADMAP Phase 18](ROADMAP.md#phase-18--frontend-e2e--demand-driven-drivers) (18.1 = BL-50, 18.2 = BL-26 по запросу).

---

## История

| Дата | Изменение |
| ---- | --------- |
| 2026-06-28 | Wave G: Haystack/Brick semantic layer → BL-56…62 (P3, deferred) |
| 2026-06-28 | Первая версия: code audit → BL-01…BL-55, Wave A–F |
