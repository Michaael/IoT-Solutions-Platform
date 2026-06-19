# Web Console

React 19 + Vite 6 + TanStack Query. Каталог: `apps/web-console/`.

## Режимы работы

### Администратор (по умолчанию)

URL: `http://localhost:5173`

| Область | Функции |
|---------|---------|
| Дерево объектов | Поиск, раскрытие, CRUD |
| Explorer | Список дочерних объектов |
| Inspector | Свойства, переменные, события |
| Вкладка «Автоматизация» | Alert rules, correlators |

### Редакторы по типу объекта

Двойной клик в дереве:

| ObjectType | Компонент |
|------------|-----------|
| `DASHBOARD` | `DashboardBuilder` |
| `WORKFLOW` | `WorkflowBuilder` |
| Остальные | `ObjectPropertiesEditor` |

### Operator HMI

URL: `http://localhost:5173?mode=operator`

- Полноэкранный дашборд (read-only)
- Sidebar: Work Queue + Event Journal
- Без редактирования layout и объектов

### Operator app (manifest + BFF)

URL: `http://localhost:5173?mode=operator&app=demo`

Generic operator shell без отраслевого кода в `main`:

- Manifest: сначала `GET /api/v1/applications/{appId}/operator-manifest` (из deploy bundle), fallback `public/operator-apps/<appId>.manifest.json`.
- Все вызовы: `POST /api/v1/bff/invoke` + `wireProfile: anima-operator-v1` (`src/api/bff.ts`)
- Экраны: `screens[]` с `actions` и опциональной `table` (список из `result[]`)

Полный SCR terminal (SCR-00…07) — ветка `feature/oil-terminal-reference`, см. [PLUGINS.md](PLUGINS.md).

## Навигация

Клиентский роутер не используется. Состояние в `App.tsx`:

- `mode`: `admin` | `operator`
- `app`: operator manifest id (опционально)
- `screen`: id экрана в manifest
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
├── types/                  # dashboard, workflow, bff, operatorManifest
├── hooks/
│   ├── useObjectWebSocket.ts
│   ├── useBoundVariable.ts
│   ├── useTrendSeries.ts
│   └── useOperatorManifest.ts
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
    ├── automation/         # Alert/correlator CRUD
    └── operator/           # OperatorView, manifest shell, sidebar panels
```

`public/operator-apps/` — JSON manifest для `?app=`.

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

## Зависимости UI

| Пакет | Назначение |
|-------|------------|
| @tanstack/react-query | Server state |
| recharts | chart, sparkline |
| bpmn-js | BPMN editor/viewer |
| bpmn-auto-layout | Генерация DI для BPMN без графической разметки |

## Кастомизация

- Стили: `src/styles.css` (CSS variables `--bg`, `--border`, …)
- Новый виджет: type в `types/dashboard.ts`, view в `widgets/`, case в `DashboardGrid.tsx`, editor в `WidgetEditorPanel.tsx`
