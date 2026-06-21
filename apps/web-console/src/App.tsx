import { useMemo, useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { fetchObjects, fetchPlatformInfo, reorderObjectChildren } from "./api";
import { logout } from "./auth/login";
import { getPrimaryRole, getStoredSession, isAdminSession, setStoredSession, type AuthSession } from "./auth/session";
import {
  clearOidcCallbackParams,
  completeOidcLogin,
  fetchAuthConfig,
  readOidcCallback,
} from "./auth/oidc";
import {
  resolveInitialAppMode,
  resolveOperatorAppId,
  shouldOpenOperatorShell,
} from "./auth/routing";
import type { EditorTab } from "./types";
import { buildObjectTree } from "./utils/tree";
import { readSelectedPath, writeSelectedPath } from "./utils/treeExpanded";
import {
  clearInvalidAdminPathFromUrl,
  resolveInitialAdminPath,
  syncAdminPathToUrl,
} from "./utils/adminRouting";
import { useObjectWebSocket } from "./hooks/useObjectWebSocket";
import ObjectPropertiesEditor from "./components/ObjectPropertiesEditor";
import ObjectTree from "./components/ObjectTree";
import CreateObjectDialog from "./components/CreateObjectDialog";
import DashboardBuilder from "./components/dashboard/DashboardBuilder";
import ExplorerView from "./components/ExplorerView";
import WorkflowBuilder from "./components/workflow/WorkflowBuilder";
import OperatorView from "./components/operator/OperatorView";
import SystemView from "./components/SystemView";
import LoginView from "./components/LoginView";
import PlatformUpdateBanner from "./components/PlatformUpdateBanner";
import ModelEditorPanel from "./components/ModelEditorPanel";
import { isModelsPath } from "./types/models";
import { isOperatorAppChildPath } from "./utils/operatorAppsPath";
import { APPLICATIONS_ROOT } from "./utils/createObjectMode";

let tabCounter = 1;

function useAppMode(session: AuthSession | null): ["admin" | "operator", (mode: "admin" | "operator") => void] {
  const [mode, setModeState] = useState<"admin" | "operator">(() => resolveInitialAppMode(session));
  const setMode = (next: "admin" | "operator") => {
    setModeState(next);
    const url = new URL(window.location.href);
    if (next === "operator") {
      url.searchParams.set("mode", "operator");
      if (!url.searchParams.get("app") && session?.autoStartApp) {
        url.searchParams.set("app", session.autoStartApp);
      }
    } else {
      url.searchParams.delete("mode");
      url.searchParams.delete("app");
      url.searchParams.delete("screen");
    }
    window.history.replaceState({}, "", url.toString());
  };
  return [mode, setMode];
}

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(() => getStoredSession());
  const [appMode, setAppMode] = useAppMode(session);
  const queryClient = useQueryClient();
  const [workspaceTab, setWorkspaceTab] = useState<"explorer" | string>("explorer");
  const [editorTabs, setEditorTabs] = useState<EditorTab[]>([]);
  const [propertiesTabPath, setPropertiesTabPath] = useState<string | null>(null);
  const [searchParams] = useSearchParams();
  const [selectedPath, setSelectedPath] = useState<string | null>(() =>
    resolveInitialAdminPath(window.location.search, readSelectedPath())
  );
  const [oidcBootstrapping, setOidcBootstrapping] = useState(() => readOidcCallback() != null);

  useEffect(() => {
    const callback = readOidcCallback();
    if (!callback) {
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const config = await fetchAuthConfig();
        const result = await completeOidcLogin(config, callback.code, callback.state);
        if (cancelled) {
          return;
        }
        const nextSession: AuthSession = {
          token: result.accessToken,
          username: result.principal,
          displayName: result.principal,
          roles: result.roles,
        };
        setStoredSession(nextSession);
        setSession(nextSession);
        clearOidcCallbackParams();
      } catch (error) {
        if (!cancelled) {
          console.error(error);
        }
      } finally {
        if (!cancelled) {
          setOidcBootstrapping(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!session?.autoStartEnabled || !session.autoStartApp) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    if (params.get("mode") === "admin" && isAdminSession(session)) {
      return;
    }
    if (!params.get("app")) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", session.autoStartApp);
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    }
  }, [session, setAppMode]);

  useEffect(() => {
    const fromUrl = searchParams.get("path");
    if (fromUrl && fromUrl !== selectedPath) {
      setSelectedPath(fromUrl);
    }
  }, [searchParams]);

  useEffect(() => {
    if (selectedPath) {
      writeSelectedPath(selectedPath);
      syncAdminPathToUrl(selectedPath);
    }
  }, [selectedPath]);

  const [showCreate, setShowCreate] = useState(false);
  const [treeFilter, setTreeFilter] = useState("");

  useObjectWebSocket();

  const info = useQuery({ queryKey: ["info"], queryFn: fetchPlatformInfo });
  const objects = useQuery({
    queryKey: ["objects"],
    queryFn: () => fetchObjects(),
    refetchInterval: 30_000,
  });

  useEffect(() => {
    if (!objects.data || !selectedPath) {
      return;
    }
    if (objects.data.some((obj) => obj.path === selectedPath)) {
      return;
    }
    setSelectedPath("root");
    writeSelectedPath("root");
    clearInvalidAdminPathFromUrl();
  }, [objects.data, selectedPath]);

  const reorderMutation = useMutation({
    mutationFn: ({ parentPath, orderedPaths }: { parentPath: string; orderedPaths: string[] }) =>
      reorderObjectChildren(parentPath, orderedPaths),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["objects"] }),
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

  const selectPathInExplorer = (path: string) => {
    setSelectedPath(path);
    setWorkspaceTab("explorer");
  };

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
    activeEditor?.objectType === "DASHBOARD"
    || activeEditor?.objectType === "WORKFLOW"
    || activeEditor?.objectType === "MODEL"
    || (activeEditor != null && isModelsPath(activeEditor.path));
  const showPropertiesEditor =
    activeEditor &&
    (propertiesTabPath === activeEditor.path || !isSpecializedEditor);
  const parentForCreate = selectedPath ?? "root";
  const selectedObject = useMemo(
    () => objects.data?.find((obj) => obj.path === selectedPath) ?? null,
    [objects.data, selectedPath]
  );
  const isAdmin = isAdminSession(session);
  const primaryRole = getPrimaryRole(session);

  const handleLogout = async () => {
    await logout();
    setSession(null);
    queryClient.clear();
  };

  const selectOperatorApp = (appId: string) => {
    const url = new URL(window.location.href);
    url.searchParams.set("mode", "operator");
    url.searchParams.set("app", appId);
    url.searchParams.delete("screen");
    url.searchParams.delete("dashboard");
    window.history.replaceState({}, "", url.toString());
    setAppMode("operator");
  };

  const handleLoggedIn = (next: AuthSession) => {
    setSession(next);
    queryClient.invalidateQueries();
    const params = new URLSearchParams(window.location.search);
    const urlApp = params.get("app");
    if (next.autoStartEnabled && next.autoStartApp && !urlApp) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", next.autoStartApp);
      url.searchParams.delete("screen");
      url.searchParams.delete("dashboard");
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    } else if (params.get("mode") === "operator" || urlApp) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      if (urlApp) {
        url.searchParams.set("app", urlApp);
      }
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    } else if (!isAdminSession(next)) {
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.delete("app");
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
      setAppMode("operator");
    }
  };

  if (oidcBootstrapping) {
    return (
      <div className="login-shell">
        <div className="login-card">
          <p className="login-sub">Завершение OIDC входа…</p>
        </div>
      </div>
    );
  }

  if (!session?.token) {
    return <LoginView onLoggedIn={handleLoggedIn} />;
  }

  if (shouldOpenOperatorShell(session, appMode)) {
    const operatorAppId = resolveOperatorAppId(session);
    return (
      <OperatorView
        appId={operatorAppId}
        onSelectApp={selectOperatorApp}
        onSwitchAdmin={isAdmin ? () => setAppMode("admin") : undefined}
        session={session}
        onLogout={() => void handleLogout()}
      />
    );
  }

  return (
    <div className="admin-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">ISPF</span>
          <div>
            <strong>Консоль администратора</strong>
            <span className="brand-sub">
              {session.displayName} · {primaryRole ?? "—"}
              {info.data?.version ? ` · v${info.data.version}` : ""}
              {info.data?.springBootVersion ? ` · Boot ${info.data.springBootVersion}` : ""}
              {info.data?.javaVersion ? ` · Java ${info.data.javaVersion}` : ""}
            </span>
          </div>
        </div>
        <div className="topbar-actions">
          {(primaryRole === "admin" || primaryRole === "operator") && (
            <button type="button" className="btn" onClick={() => selectOperatorApp("demo")} title="Открыть operator UI (demo)">
              Оператор · demo
            </button>
          )}
          <button
            type="button"
            className="btn"
            onClick={() => queryClient.invalidateQueries({ queryKey: ["objects"] })}
          >
            Обновить
          </button>
          <button type="button" className="btn" onClick={() => void handleLogout()}>
            Выйти
          </button>
        </div>
      </header>

      {isAdmin && <PlatformUpdateBanner />}

      <nav className="workspace-tabs">
        <button
          type="button"
          className={workspaceTab === "explorer" ? "active" : ""}
          onClick={() => setWorkspaceTab("explorer")}
        >
          Обозреватель
        </button>
        {isAdmin && (
          <button
            type="button"
            className={workspaceTab === "system" ? "active" : ""}
            onClick={() => setWorkspaceTab("system")}
          >
            Система
          </button>
        )}
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
        {workspaceTab !== "system" && (
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
                onSelect={selectPathInExplorer}
                onOpenEditor={openEditor}
                canReorder={isAdmin && !treeFilter.trim()}
                onReorder={(parentPath, orderedPaths) =>
                  reorderMutation.mutate({ parentPath, orderedPaths })
                }
              />
            )}
          </aside>
        )}

        {workspaceTab === "explorer" && (
          <main className="main">
            <ExplorerView
              selectedPath={selectedPath}
              selectedObject={selectedObject}
              onOpenEditor={openEditor}
              onCreateChild={() => setShowCreate(true)}
              onDeleted={() => setSelectedPath("root")}
              onSelectPath={setSelectedPath}
              isAdmin={isAdmin}
            />
          </main>
        )}

        {workspaceTab === "system" && isAdmin && <SystemView />}

        {activeEditor && workspaceTab === activeEditor.id && !showPropertiesEditor && (
          <main className="main editor-main dashboard-main">
            {activeEditor.objectType === "DASHBOARD" ? (
              <DashboardBuilder
                path={activeEditor.path}
                onClose={() => closeEditor(activeEditor.id)}
                onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                onNavigateDashboard={openEditor}
              />
            ) : activeEditor.objectType === "WORKFLOW" ? (
              <WorkflowBuilder
                path={activeEditor.path}
                onClose={() => closeEditor(activeEditor.id)}
                onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
              />
            ) : activeEditor.objectType === "MODEL" || isModelsPath(activeEditor.path) ? (
              <ModelEditorPanel
                selectedPath={activeEditor.path}
                canManage={isAdmin}
                title={activeEditor.title}
                onClose={() => closeEditor(activeEditor.id)}
                onSelectPath={(path) => {
                  setSelectedPath(path);
                  openEditor(path);
                }}
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
            const stayInExplorer =
              isOperatorAppChildPath(path) || path.startsWith(`${APPLICATIONS_ROOT}.`);
            if (!stayInExplorer) {
              openEditor(path);
            }
          }}
        />
      )}
    </div>
  );
}
