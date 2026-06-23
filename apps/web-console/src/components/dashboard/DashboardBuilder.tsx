import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchObjects,
  fetchDashboard,
  saveDashboardLayout,
  saveDashboardTitle,
  saveDashboardRefreshInterval,
} from "../../api";
import type { DashboardLayout, DashboardWidget, WidgetType } from "../../types/dashboard";
import {
  layoutToJson,
  newWidget,
  parseLayoutJson,
  WIDGET_TYPES,
} from "../../types/dashboard";
import {
  DashboardProvider,
  mergeSession,
  type DashboardSession,
  type OpenDashboardOptions,
} from "./DashboardContext";
import DashboardGrid from "./DashboardGrid";
import DashboardModal from "./DashboardModal";
import WidgetEditorPanel from "./WidgetEditorPanel";
import DashboardSettingsPanel from "./DashboardSettingsPanel";

interface DashboardBuilderProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
  operatorMode?: boolean;
  embeddedModal?: boolean;
  onNavigateDashboard?: (path: string, options?: OpenDashboardOptions) => void;
  onOpenDashboardModal?: (path: string, title?: string, options?: OpenDashboardOptions) => void;
  session?: DashboardSession;
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  onSessionChange?: (next: DashboardSession) => void;
  onSelectionChange?: (next: Record<string, string>) => void;
  onParamsChange?: (next: Record<string, unknown>) => void;
  subDashboardDepth?: number;
}

interface ModalState {
  path: string;
  title: string;
  session: DashboardSession;
}

export default function DashboardBuilder({
  path,
  onClose,
  onOpenProperties,
  operatorMode = false,
  embeddedModal = false,
  onNavigateDashboard,
  onOpenDashboardModal,
  session,
  selection,
  params,
  onSessionChange,
  onSelectionChange,
  onParamsChange,
  subDashboardDepth = 0,
}: DashboardBuilderProps) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">(operatorMode ? "view" : "view");
  const [draftLayout, setDraftLayout] = useState<DashboardLayout | null>(null);
  const [draftTitle, setDraftTitle] = useState<string | null>(null);
  const [draftRefreshMs, setDraftRefreshMs] = useState<number | null>(null);
  const [selectedWidgetId, setSelectedWidgetId] = useState<string | null>(null);
  const [showJson, setShowJson] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [modalDashboard, setModalDashboard] = useState<ModalState | null>(null);

  const currentSession = useMemo<DashboardSession>(
    () =>
      session ?? {
        selection: selection ?? {},
        params: params ?? {},
      },
    [session, selection, params]
  );

  const applySession = (patch?: OpenDashboardOptions): DashboardSession => {
    const next = mergeSession(currentSession, patch);
    if (onSessionChange) {
      onSessionChange(next);
    } else {
      if (patch?.selection) {
        onSelectionChange?.(next.selection);
      }
      if (patch?.params) {
        onParamsChange?.(next.params);
      }
    }
    return next;
  };

  const handleNavigateDashboard = (targetPath: string, options?: OpenDashboardOptions) => {
    applySession(options);
    if (!embeddedModal) {
      setModalDashboard(null);
    }
    onNavigateDashboard?.(targetPath, options);
  };

  const handleOpenDashboardModal = (
    targetPath: string,
    title?: string,
    options?: OpenDashboardOptions
  ) => {
    const nextSession = applySession(options);
    if (embeddedModal) {
      onOpenDashboardModal?.(targetPath, title, options);
      return;
    }
    setModalDashboard({
      path: targetPath,
      title: title?.trim() || targetPath.split(".").pop() || targetPath,
      session: nextSession,
    });
  };

  const dashboard = useQuery({
    queryKey: ["dashboard", path],
    queryFn: () => fetchDashboard(path),
  });

  const objects = useQuery({
    queryKey: ["objects", "dashboard-bindings"],
    queryFn: () => fetchObjects(undefined, false),
  });

  const layout = useMemo(() => {
    if (draftLayout) return draftLayout;
    if (dashboard.data?.layoutJson) {
      return parseLayoutJson(dashboard.data.layoutJson);
    }
    if (dashboard.data?.layout && typeof dashboard.data.layout === "object") {
      const parsed = dashboard.data.layout as DashboardLayout;
      return {
        columns: parsed.columns ?? 12,
        rowHeight: parsed.rowHeight ?? 72,
        theme: parsed.theme,
        widgets: Array.isArray(parsed.widgets) ? parsed.widgets : [],
      };
    }
    return parseLayoutJson(null);
  }, [dashboard.data, draftLayout]);

  const title = draftTitle ?? dashboard.data?.title ?? path;
  const refreshIntervalMs =
    draftRefreshMs ?? dashboard.data?.refreshIntervalMs ?? 5000;
  const dirty =
    draftLayout !== null ||
    draftRefreshMs !== null ||
    (draftTitle !== null && draftTitle !== dashboard.data?.title);

  const bindingObjects = useMemo(
    () =>
      (objects.data ?? [])
        .filter((obj) => obj.variableNames.length > 0)
        .map((obj) => ({
          path: obj.path,
          displayName: obj.displayName,
          variableNames: obj.variableNames,
        })),
    [objects.data]
  );

  const dashboardObjects = useMemo(
    () =>
      (objects.data ?? [])
        .filter((obj) => obj.type === "DASHBOARD")
        .map((obj) => ({ path: obj.path, displayName: obj.displayName })),
    [objects.data]
  );

  const selectedWidget =
    layout.widgets.find((widget) => widget.id === selectedWidgetId) ?? null;

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (draftTitle !== null) {
        await saveDashboardTitle(path, draftTitle);
      }
      if (draftRefreshMs !== null) {
        await saveDashboardRefreshInterval(path, draftRefreshMs);
      }
      await saveDashboardLayout(path, layoutToJson(layout));
    },
    onSuccess: (data) => {
      setDraftLayout(null);
      setDraftTitle(null);
      setDraftRefreshMs(null);
      queryClient.setQueryData(["dashboard", path], data);
      queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
    },
  });

  const addWidget = (type: WidgetType) => {
    const widget = newWidget(type, layout.widgets.length);
    const next = { ...layout, widgets: [...layout.widgets, widget] };
    setDraftLayout(next);
    setSelectedWidgetId(widget.id);
    setMode("edit");
  };

  const updateWidget = (widget: DashboardWidget) => {
    const next = {
      ...layout,
      widgets: layout.widgets.map((item) => (item.id === widget.id ? widget : item)),
    };
    setDraftLayout(next);
  };

  const updateLayoutSettings = (patch: Partial<DashboardLayout>) => {
    setDraftLayout({ ...layout, ...patch });
  };

  const deleteWidget = () => {
    if (!selectedWidgetId) return;
    const next = {
      ...layout,
      widgets: layout.widgets.filter((item) => item.id !== selectedWidgetId),
    };
    setDraftLayout(next);
    setSelectedWidgetId(null);
  };

  const handleLayoutChange = (widgets: DashboardWidget[]) => {
    setDraftLayout({ ...layout, widgets });
  };

  if (dashboard.isLoading) {
    return <div className="dashboard-shell loading">Загрузка дашборда…</div>;
  }

  if (dashboard.error) {
    return (
      <div className="dashboard-shell error">
        Не удалось загрузить дашборд: {(dashboard.error as Error).message}
      </div>
    );
  }

  return (
    <div className={`dashboard-shell ${operatorMode ? "operator-dashboard-shell" : ""}`}>
      {!operatorMode && (
        <header className="dashboard-toolbar">
          <div>
            <div className="dashboard-kicker">Dashboard · HMI</div>
            {mode === "edit" ? (
              <input
                className="dashboard-title-input"
                value={title}
                onChange={(e) => setDraftTitle(e.target.value)}
              />
            ) : (
              <h2>{title}</h2>
            )}
            <code className="path-code">{path}</code>
          </div>
          <div className="dashboard-toolbar-actions">
            <button
              type="button"
              className={`btn ${mode === "view" ? "primary" : ""}`}
              onClick={() => setMode("view")}
            >
              Просмотр
            </button>
            <button
              type="button"
              className={`btn ${mode === "edit" ? "primary" : ""}`}
              onClick={() => setMode("edit")}
            >
              Редактор
            </button>
            <button type="button" className="btn" onClick={() => setShowJson((v) => !v)}>
              JSON
            </button>
            {mode === "edit" && (
              <button
                type="button"
                className={`btn ${showSettings ? "primary" : ""}`}
                onClick={() => setShowSettings((v) => !v)}
              >
                Дашборд
              </button>
            )}
            {onOpenProperties && (
              <button type="button" className="btn" onClick={onOpenProperties}>
                Свойства
              </button>
            )}
            {dirty && (
              <button
                type="button"
                className="btn primary"
                disabled={saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
              >
                Сохранить
              </button>
            )}
            {onClose && (
              <button type="button" className="btn" onClick={onClose}>
                Закрыть
              </button>
            )}
          </div>
        </header>
      )}

      {!operatorMode && mode === "edit" && (
        <div className="dashboard-edit-bar">
          <span className="dashboard-edit-hint">
            Перетаскивайте виджет за фон, размер — за угол справа снизу
          </span>
          {WIDGET_TYPES.map((item) => (
            <button
              key={item.type}
              type="button"
              className="btn"
              onClick={() => addWidget(item.type)}
            >
              + {item.label}
            </button>
          ))}
        </div>
      )}

      <div
        className={`dashboard-body ${!operatorMode && mode === "edit" ? "with-sidebar" : ""}`}
      >
        <DashboardProvider
          session={currentSession}
          onSessionChange={onSessionChange}
          onSelectionChange={onSelectionChange}
          onParamsChange={onParamsChange}
          onNavigateDashboard={handleNavigateDashboard}
          onOpenDashboardModal={handleOpenDashboardModal}
        >
          <main className="dashboard-canvas">
            <DashboardGrid
              layout={layout}
              refreshIntervalMs={refreshIntervalMs}
              editable={!operatorMode && mode === "edit"}
              selectedWidgetId={!operatorMode && mode === "edit" ? selectedWidgetId : null}
              onSelectWidget={!operatorMode && mode === "edit" ? setSelectedWidgetId : undefined}
              onLayoutChange={!operatorMode && mode === "edit" ? handleLayoutChange : undefined}
              subDashboardDepth={subDashboardDepth}
            />
            {!operatorMode && showJson && (
              <pre className="dashboard-json-panel">{layoutToJson(layout)}</pre>
            )}
          </main>
        </DashboardProvider>
        {!operatorMode && mode === "edit" && showSettings && (
          <DashboardSettingsPanel
            layout={layout}
            refreshIntervalMs={refreshIntervalMs}
            dashboardPath={path}
            onLayoutChange={updateLayoutSettings}
            onRefreshIntervalChange={setDraftRefreshMs}
          />
        )}
        {!operatorMode && mode === "edit" && !showSettings && (
          <WidgetEditorPanel
            widget={selectedWidget}
            objects={bindingObjects}
            dashboards={dashboardObjects}
            onChange={updateWidget}
            onDelete={deleteWidget}
          />
        )}
      </div>
      {!embeddedModal && modalDashboard && (
        <DashboardModal
          path={modalDashboard.path}
          title={modalDashboard.title}
          session={modalDashboard.session}
          onSessionChange={onSessionChange}
          onSelectionChange={onSelectionChange}
          onParamsChange={onParamsChange}
          onNavigateDashboard={handleNavigateDashboard}
          onOpenDashboardModal={(nextPath, nextTitle, options) => {
            const nextSession = applySession(options);
            setModalDashboard({
              path: nextPath,
              title: nextTitle?.trim() || nextPath.split(".").pop() || nextPath,
              session: nextSession,
            });
          }}
          onClose={() => setModalDashboard(null)}
        />
      )}
    </div>
  );
}
