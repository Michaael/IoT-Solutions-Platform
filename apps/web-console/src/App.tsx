import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchObjects, fetchPlatformInfo } from "./api";
import { getStoredRole, isAdminRole, setStoredRole, type IspfRole } from "./auth/role";
import type { EditorTab } from "./types";
import { buildObjectTree } from "./utils/tree";
import { useObjectWebSocket } from "./hooks/useObjectWebSocket";
import ObjectPropertiesEditor from "./components/ObjectPropertiesEditor";
import ObjectTree from "./components/ObjectTree";
import CreateObjectDialog from "./components/CreateObjectDialog";
import DashboardBuilder from "./components/dashboard/DashboardBuilder";
import ExplorerView from "./components/ExplorerView";
import WorkflowBuilder from "./components/workflow/WorkflowBuilder";
import OperatorView from "./components/operator/OperatorView";
import AutomationView from "./components/automation/AutomationView";

let tabCounter = 1;

function useAppMode(): ["admin" | "operator", (mode: "admin" | "operator") => void] {
  const initial = new URLSearchParams(window.location.search).get("mode") === "operator"
    ? "operator"
    : "admin";
  const [mode, setModeState] = useState<"admin" | "operator">(initial);
  const setMode = (next: "admin" | "operator") => {
    setModeState(next);
    const url = new URL(window.location.href);
    if (next === "operator") {
      url.searchParams.set("mode", "operator");
    } else {
      url.searchParams.delete("mode");
    }
    window.history.replaceState({}, "", url.toString());
  };
  return [mode, setMode];
}

export default function App() {
  const [appMode, setAppMode] = useAppMode();
  const [ispfRole, setIspfRole] = useState<IspfRole>(() => getStoredRole());
  const queryClient = useQueryClient();
  const [workspaceTab, setWorkspaceTab] = useState<"explorer" | "automation" | string>("explorer");
  const [editorTabs, setEditorTabs] = useState<EditorTab[]>([]);
  const [propertiesTabPath, setPropertiesTabPath] = useState<string | null>(null);
  const [selectedPath, setSelectedPath] = useState<string | null>("root");
  const [showCreate, setShowCreate] = useState(false);
  const [treeFilter, setTreeFilter] = useState("");

  useObjectWebSocket();

  const info = useQuery({ queryKey: ["info"], queryFn: fetchPlatformInfo });
  const objects = useQuery({
    queryKey: ["objects"],
    queryFn: () => fetchObjects(),
    refetchInterval: 30_000,
  });

  const tree = useMemo(() => {
    if (!objects.data) return [];
    let list = objects.data;
    if (treeFilter) {
      const q = treeFilter.toLowerCase();
      const included = new Set<string>();
      for (const c of list) {
        if (c.path.toLowerCase().includes(q) || c.displayName.toLowerCase().includes(q)) {
          let p: string | null = c.path;
          while (p) {
            included.add(p);
            const dot = p.lastIndexOf(".");
            p = dot === -1 ? null : p.slice(0, dot);
          }
        }
      }
      list = list.filter((c) => included.has(c.path));
    }
    return buildObjectTree(list);
  }, [objects.data, treeFilter]);

  const openEditor = (path: string) => {
    const existing = editorTabs.find((t) => t.path === path);
    if (existing) {
      setWorkspaceTab(existing.id);
      return;
    }
    const ctx = objects.data?.find((c) => c.path === path);
    const tab: EditorTab = {
      id: `editor-${tabCounter++}`,
      path,
      title: ctx?.displayName ?? path.split(".").pop() ?? path,
      objectType: ctx?.type,
    };
    setEditorTabs((tabs) => [...tabs, tab]);
    setWorkspaceTab(tab.id);
  };

  const closeEditor = (tabId: string) => {
    const tab = editorTabs.find((t) => t.id === tabId);
    setEditorTabs((tabs) => tabs.filter((t) => t.id !== tabId));
    if (tab && propertiesTabPath === tab.path) {
      setPropertiesTabPath(null);
    }
    if (workspaceTab === tabId) {
      setWorkspaceTab("explorer");
    }
  };

  const activeEditor = editorTabs.find((t) => t.id === workspaceTab);
  const isSpecializedEditor =
    activeEditor?.objectType === "DASHBOARD" || activeEditor?.objectType === "WORKFLOW";
  const showPropertiesEditor =
    activeEditor &&
    (propertiesTabPath === activeEditor.path || !isSpecializedEditor);
  const parentForCreate = selectedPath ?? "root";
  const isAdmin = isAdminRole(ispfRole);

  const changeRole = (role: IspfRole) => {
    setStoredRole(role);
    setIspfRole(role);
    queryClient.invalidateQueries();
  };

  if (appMode === "operator") {
    const operatorAppId = new URLSearchParams(window.location.search).get("app");
    return <OperatorView appId={operatorAppId} onSwitchAdmin={() => setAppMode("admin")} />;
  }

  return (
    <div className="admin-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">ISPF</span>
          <div>
            <strong>Консоль администратора</strong>
            <span className="brand-sub">
              {info.data?.name ?? "IoT Solutions Platform Framework"}
              {info.data?.version ? ` · v${info.data.version}` : ""}
            </span>
          </div>
        </div>
        <div className="topbar-actions">
          <label className="role-select">
            <span className="role-select-label">Роль</span>
            <select
              value={ispfRole}
              onChange={(e) => changeRole(e.target.value as IspfRole)}
              title="Local: заголовок X-ISPF-Role; dev: роли Keycloak admin/operator"
            >
              <option value="admin">admin</option>
              <option value="operator">operator</option>
            </select>
          </label>
          <button type="button" className="btn" onClick={() => setAppMode("operator")}>
            Оператор
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => queryClient.invalidateQueries({ queryKey: ["objects"] })}
          >
            Обновить
          </button>
          {isAdmin && (
            <button type="button" className="btn primary" onClick={() => setShowCreate(true)}>
              + Объект
            </button>
          )}
        </div>
      </header>

      <nav className="workspace-tabs">
        <button
          type="button"
          className={workspaceTab === "explorer" ? "active" : ""}
          onClick={() => setWorkspaceTab("explorer")}
        >
          Обозреватель
        </button>
        <button
          type="button"
          className={workspaceTab === "automation" ? "active" : ""}
          onClick={() => setWorkspaceTab("automation")}
        >
          Автоматизация
        </button>
        {editorTabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`editor-tab ${workspaceTab === tab.id ? "active" : ""}`}
            onClick={() => setWorkspaceTab(tab.id)}
          >
            <span>{tab.title}</span>
            <span
              className="tab-close"
              onClick={(e) => {
                e.stopPropagation();
                closeEditor(tab.id);
              }}
            >
              ×
            </span>
          </button>
        ))}
      </nav>

      <div className="workspace">
        {workspaceTab === "explorer" && (
          <>
            <aside className="sidebar">
              <div className="sidebar-head">
                <h3>Дерево объектов</h3>
                <input
                  type="search"
                  placeholder="Поиск…"
                  value={treeFilter}
                  onChange={(e) => setTreeFilter(e.target.value)}
                />
              </div>
              {objects.isLoading && <p className="sidebar-msg">Загрузка…</p>}
              {objects.error && (
                <p className="sidebar-msg error">
                  Ошибка API. Запустите сервер с профилем <code>local</code>.
                </p>
              )}
              {tree.length > 0 && (
                <ObjectTree
                  nodes={tree}
                  selectedPath={selectedPath}
                  onSelect={setSelectedPath}
                  onOpenEditor={openEditor}
                />
              )}
            </aside>
            <main className="main">
              <ExplorerView
                selectedPath={selectedPath}
                onOpenEditor={openEditor}
                onDeleted={() => setSelectedPath("root")}
              />
            </main>
          </>
        )}

        {workspaceTab === "automation" && <AutomationView readOnly={!isAdmin} />}

        {activeEditor && workspaceTab === activeEditor.id && !showPropertiesEditor && (
          <main className="main editor-main dashboard-main">
            {activeEditor.objectType === "DASHBOARD" ? (
              <DashboardBuilder
                path={activeEditor.path}
                onClose={() => closeEditor(activeEditor.id)}
                onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
              />
            ) : activeEditor.objectType === "WORKFLOW" ? (
              <WorkflowBuilder
                path={activeEditor.path}
                onClose={() => closeEditor(activeEditor.id)}
                onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
              />
            ) : (
              <ObjectPropertiesEditor
                path={activeEditor.path}
                onClose={() => closeEditor(activeEditor.id)}
                onDeleted={() => {
                  setSelectedPath("root");
                  closeEditor(activeEditor.id);
                }}
              />
            )}
          </main>
        )}

        {activeEditor && workspaceTab === activeEditor.id && showPropertiesEditor && (
          <main className="main editor-main">
            <ObjectPropertiesEditor
              path={activeEditor.path}
              onClose={() => {
                setPropertiesTabPath(null);
                if (!isSpecializedEditor) {
                  closeEditor(activeEditor.id);
                }
              }}
              onDeleted={() => {
                setSelectedPath("root");
                setPropertiesTabPath(null);
                closeEditor(activeEditor.id);
              }}
            />
          </main>
        )}
      </div>

      {showCreate && (
        <CreateObjectDialog
          parentPath={parentForCreate}
          onClose={() => setShowCreate(false)}
          onCreated={(path) => {
            setShowCreate(false);
            queryClient.invalidateQueries({ queryKey: ["objects"] });
            setSelectedPath(path);
            openEditor(path);
          }}
        />
      )}
    </div>
  );
}
