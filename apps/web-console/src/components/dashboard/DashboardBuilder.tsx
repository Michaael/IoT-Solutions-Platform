import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchObjects,
  fetchDashboard,
  saveDashboardLayout,
  saveDashboardTitle,
} from "../../api";
import type { DashboardLayout, DashboardWidget, WidgetType } from "../../types/dashboard";
import {
  layoutToJson,
  newWidget,
  parseLayoutJson,
  WIDGET_TYPES,
} from "../../types/dashboard";
import { DashboardProvider } from "./DashboardContext";
import DashboardGrid from "./DashboardGrid";
import DashboardModal from "./DashboardModal";
import WidgetEditorPanel from "./WidgetEditorPanel";

interface DashboardBuilderProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
  operatorMode?: boolean;
  /** Rendered inside DashboardModal — do not mount another backdrop */
  embeddedModal?: boolean;
  onNavigateDashboard?: (path: string) => void;
  onOpenDashboardModal?: (path: string, title?: string) => void;
  selection?: Record<string, string>;
  onSelectionChange?: (next: Record<string, string>) => void;
}

export default function DashboardBuilder({
  path,
  onClose,
  onOpenProperties,
  operatorMode = false,
  embeddedModal = false,
  onNavigateDashboard,
  onOpenDashboardModal,
  selection,
  onSelectionChange,
}: DashboardBuilderProps) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">(operatorMode ? "view" : "view");
  const [draftLayout, setDraftLayout] = useState<DashboardLayout | null>(null);
  const [draftTitle, setDraftTitle] = useState<string | null>(null);
  const [selectedWidgetId, setSelectedWidgetId] = useState<string | null>(null);
  const [showJson, setShowJson] = useState(false);
  const [modalDashboard, setModalDashboard] = useState<{ path: string; title: string } | null>(
    null
  );

  const handleNavigateDashboard = (targetPath: string) => {
    if (!embeddedModal) {
      setModalDashboard(null);
    }
    onNavigateDashboard?.(targetPath);
  };

  const handleOpenDashboardModal = (targetPath: string, title?: string) => {
    if (embeddedModal) {
      onOpenDashboardModal?.(targetPath, title);
      return;
    }
    setModalDashboard({
      path: targetPath,
      title: title?.trim() || targetPath.split(".").pop() || targetPath,
    });
  };

  const dashboard = useQuery({
    queryKey: ["dashboard", path],
    queryFn: () => fetchDashboard(path),
  });

  const objects = useQuery({
    queryKey: ["objects"],
    queryFn: () => fetchObjects(),
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
        widgets: Array.isArray(parsed.widgets) ? parsed.widgets : [],
      };
    }
    return parseLayoutJson(null);
  }, [dashboard.data, draftLayout]);

  const title = draftTitle ?? dashboard.data?.title ?? path;
  const refreshIntervalMs = dashboard.data?.refreshIntervalMs ?? 5000;
  const dirty =
    draftLayout !== null ||
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

  const selectedWidget =
    layout.widgets.find((widget) => widget.id === selectedWidgetId) ?? null;

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (draftTitle !== null) {
        await saveDashboardTitle(path, draftTitle);
      }
      await saveDashboardLayout(path, layoutToJson(layout));
    },
    onSuccess: (data) => {
      setDraftLayout(null);
      setDraftTitle(null);
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
          <span className="dashboard-edit-hint">Перетаскивайте виджет за фон, размер — за угол справа снизу</span>
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

      <div className={`dashboard-body ${!operatorMode && mode === "edit" ? "with-sidebar" : ""}`}>
        <DashboardProvider
          selection={selection}
          onSelectionChange={onSelectionChange}
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
            />
            {!operatorMode && showJson && (
              <pre className="dashboard-json-panel">{layoutToJson(layout)}</pre>
            )}
          </main>
        </DashboardProvider>
        {!operatorMode && mode === "edit" && (
          <WidgetEditorPanel
            widget={selectedWidget}
            objects={bindingObjects}
            onChange={updateWidget}
            onDelete={deleteWidget}
          />
        )}
      </div>
      {!embeddedModal && modalDashboard && (
        <DashboardModal
          path={modalDashboard.path}
          title={modalDashboard.title}
          selection={selection}
          onSelectionChange={onSelectionChange}
          onNavigateDashboard={handleNavigateDashboard}
          onOpenDashboardModal={(nextPath, nextTitle) =>
            setModalDashboard({
              path: nextPath,
              title: nextTitle?.trim() || nextPath.split(".").pop() || nextPath,
            })
          }
          onClose={() => setModalDashboard(null)}
        />
      )}
    </div>
  );
}
