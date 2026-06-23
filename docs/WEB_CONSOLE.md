# Web Console

React 19 + Vite 6 + TanStack Query. Каталог: `apps/web-console/`.

## Режимы работы

### Администратор (по умолчанию)

URL: `http://localhost:5173`

| Область | Функции |
|---------|---------|
| Дерево объектов | Поиск, раскрытие, CRUD, drag-and-drop порядок соседей |
| Explorer | Список дочерних объектов |
| Inspector | Свойства, переменные, события; alert rules и correlators |

### Редакторы по типу объекта

Двойной клик в дереве:

| ObjectType | Компонент |
|------------|-----------|
| `DASHBOARD` | `DashboardBuilder` |
| `WORKFLOW` | `WorkflowBuilder` |
| `ALERT` | `AlertRuleInspector` |
| `CORRELATOR` | `CorrelatorInspector` |
| Остальные | `ObjectPropertiesEditor` |

### Operator HMI

URL: `http://localhost:5173?mode=operator`

- Полноэкранный дашборд (read-only)
- Sidebar: Work Queue + Event Journal
- Без редактирования layout и объектов

### Operator app (дашборды)

URL: `http://localhost:5173?mode=operator&app=platform`

Generic operator shell без отраслевого кода в `main`:

- Operator UI: `GET /api/v1/operator-apps/{appId}/ui` (встроенные app, настройка в `root.platform.operator-apps`), затем `GET /api/v1/applications/{appId}/operator-ui` (bundle), legacy fallback `public/operator-apps/<appId>.ui.json`
- Навигация по вкладкам дашбордов; виджеты — те же что в Dashboard Builder (`DashboardBuilder` + `operatorMode`)
- BFF (`POST /api/v1/bff/invoke`) — только если виджеты/функции app вызывают backend (не отдельный manifest shell)

### Legacy: operator manifest

`?mode=operator&app=demo` + `operatorManifest` — deprecated. Используйте `operatorUi` и объекты `DASHBOARD`.

## Навигация

Клиентский роутер не используется. Состояние в `App.tsx`:

- `mode`: `admin` | `operator`
- `app`: operator application id (опционально)
- `dashboard`: path объекта `DASHBOARD` (опционально)
- `screen`: id экрана в legacy manifest (deprecated)
- `selectedPath`, `editorPath`

## Роли

Селектор в шапке → `X-ISPF-Role: admin|operator` (`src/auth/role.ts`).

| Роль | UI |
|------|-----|
| `admin` | Полный доступ, автоматизация, сохранение |
| `operator` | Просмотр, функции, work queue |

## Структура исходников

```
src/
├── App.tsx                 # Shell, tabs, role selector
├── api.ts                  # REST client
├── api/bff.ts              # POST /bff/invoke (anima-operator-v1)
├── types/                  # dashboard, workflow, bff, operatorUi, operatorManifest (legacy)
├── hooks/
│   ├── useObjectWebSocket.ts
│   ├── useBoundVariable.ts
│   ├── useTrendSeries.ts
│   ├── useOperatorUi.ts
│   └── useOperatorManifest.ts (legacy)
├── bpmn/
│   ├── ispf-moddle.json    # ISPF BPMN extensions
│   ├── constants.ts        # EMPTY_BPMN (с минимальным bpmndi)
│   └── ensureDiagram.ts    # auto-layout при отсутствии DI
└── components/
    ├── ObjectTree.tsx
    ├── ExplorerView.tsx
    ├── ObjectPropertiesEditor.tsx
    ├── dashboard/          # Builder + 14 widgets
    ├── workflow/           # BPMN editor/viewer
    ├── automation/         # AlertRuleInspector, CorrelatorInspector, automationPath
    └── operator/           # OperatorView, OperatorDashboardApp, sidebar panels
```

`public/operator-apps/` — legacy fallback `{appId}.ui.json` (dev). Настройка встроенных app — админка → `root.platform.operator-apps`.

## Dashboard Builder

- Сетка 12 колонок, drag-and-drop, resize
- Панель добавления виджетов (все 14 типов)
- `WidgetEditorPanel` — свойства выбранного виджета
- JSON preview layout
- Сохранение через API

См. [DASHBOARDS.md](DASHBOARDS.md).

## Workflow Builder

- Переключение DRAFT/ACTIVE/STOPPED
- Кнопка Run
- BPMN editor (bpmn-js) с ISPF moddle
- **Auto-layout:** если XML без `bpmndi`, `ensureDiagram.ts` вызывает `bpmn-auto-layout` перед отображением (см. [WORKFLOWS.md](WORKFLOWS.md#диаграмма-без-разметки-di))
- Fallback XML viewer

См. [WORKFLOWS.md](WORKFLOWS.md).

## Live-данные

1. **Polling** — `refetchInterval` = `refreshIntervalMs` дашборда
2. **WebSocket** — `useObjectWebSocket` инвалидирует cache при `VARIABLE_UPDATED`

Proxy (`vite.config.ts`): `/api`, `/ws` → `:8080`.

## Сборка

```bash
cd apps/web-console
npm install
npm run dev      # dev server
npm run build    # production dist/
```

## Федерация

Раздел **Федерация** (`root.platform.federation`, только admin) — вкладки:

| Вкладка | Содержимое |
|---------|------------|
| **Узлы** | Таблица peers, форма нового узла, получение authToken |
| **Токены** | Выпуск локального federation-токена для другого ISPF |
| **Туннель** | Inbound registrations, outbound agents, secrets-key (если capability `federation-tunnel`) |
| **Проверка** | Proxy read probe по выбранному узлу |

На объекте устройства/дашборда — панель **Привязка к федерации** (`FederationBindPanel`): проверка remote path, bind/rebind, inline-подтверждение отвязки. Для зеркала каталога — callout «Разместить локально».

Компоненты: `FederationPeersPanel`, `components/federation/*`, `FederationBindPanel`.

## AI Studio

Вкладка **AI Studio** (admin): режимы **Агент** | **Пакет bundle** | **Настройки** (`.tabs`).

- **Агент** — боковая панель чатов; состояние в `AgentChatProvider` (не размонтируется при переключении вкладок или разделов консоли)
- **Настройки** — провайдер LLM, Context Pack, корневой путь сессий, список инструментов, очистка локального кэша чатов
- **Пакет bundle** — промпт, генерация/валидация/dry-run/publish, история deploy

Фоновое выполнение: HTTP-запрос агента продолжается при переходе в Обозреватель; индикатор в шапке и точка на вкладке AI Studio. После закрытия окна незавершённый запрос восстанавливается из `localStorage` (poll сессии на сервере).

См. [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md#platform-studio-fw-43).

## Зависимости UI

| Пакет | Назначение |
|-------|------------|
| @tanstack/react-query | Server state |
| recharts | chart, sparkline |
| bpmn-js | BPMN editor/viewer |
| bpmn-auto-layout | Генерация DI для BPMN без графической разметки |

## Кастомизация

- Стили: `src/styles.css` (CSS variables `--bg`, `--border`, …)
- Новый виджет: type в `types/dashboard.ts`, view в `widgets/`, dispatch в `renderDashboardWidget.tsx` / `DashboardWidgetContent.tsx`, editor в `WidgetEditorPanel.tsx`
- **40+ типов** виджетов (см. `WIDGET_TYPES` в `types/dashboard.ts`)
