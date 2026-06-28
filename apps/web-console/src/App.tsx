import { useMemo, useState, useEffect, useRef, useCallback, lazy, Suspense } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchPlatformInfo, reorderObjectChildren } from "./api";
import { logout } from "./auth/login";
import { getPrimaryRole, getStoredSession, isAdminSession, setStoredSession, type AuthSession } from "./auth/session";
import { SESSION_INVALID_EVENT, validateStoredSession } from "./auth/validateSession";
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
import type { EditorTab, ObjectType } from "./types";
import { resolveEditorObjectType, isSpecializedEditorObject } from "./utils/editorObject";
import { buildObjectTree } from "./utils/tree";
import { objectTreeKey, type TreeRowSelection } from "./utils/treeRowKey";
import { readSelectedPath, writeSelectedPath } from "./utils/treeExpanded";
import {
  clearInvalidAdminPathFromUrl,
  resolveInitialAdminPath,
  syncAdminPathToUrl,
} from "./utils/adminRouting";
import { useObjectWebSocket, useFederatedPathSubscription } from "./hooks/useObjectWebSocket";
import { useLazyObjectTree } from "./hooks/useLazyObjectTree";
import { useMobileLayout } from "./hooks/useMobileLayout";
import ObjectPropertiesEditor from "./components/ObjectPropertiesEditor";
import ObjectTree from "./components/ObjectTree";
import CreateObjectDialog from "./components/CreateObjectDialog";
import {
  emptySession,
  mergeSession,
  type DashboardSession,
  type OpenDashboardOptions,
} from "./components/dashboard/DashboardContext";
import AgentChatStatusBar from "./components/AgentChatStatusBar";
import LoginView from "./components/LoginView";
import PlatformUpdateBanner from "./components/PlatformUpdateBanner";
import LocaleSwitcher from "./components/LocaleSwitcher";
import { AgentChatProvider, useAgentChatOptional } from "./context/AgentChatContext";
import { isModelsPath } from "./types/models";
import { isOperatorAppChildPath } from "./utils/operatorAppsPath";
import { APPLICATIONS_ROOT } from "./utils/createObjectMode";

const OperatorView = lazy(() => import("./components/operator/OperatorView"));
const SystemView = lazy(() => import("./components/SystemView"));
const AiStudioPanel = lazy(() => import("./components/AiStudioPanel"));
const ReportBuilder = lazy(() => import("./components/report/ReportBuilder"));
const WorkflowBuilder = lazy(() => import("./components/workflow/WorkflowBuilder"));
const DashboardBuilder = lazy(() => import("./components/dashboard/DashboardBuilder"));
const ExplorerView = lazy(() => import("./components/ExplorerView"));
const DataSourceEditor = lazy(() => import("./components/platform/DataSourceEditor"));
const MigrationEditor = lazy(() => import("./components/platform/MigrationEditor"));
const SqlBindingEditor = lazy(() => import("./components/platform/SqlBindingEditor"));
const ScheduleEditor = lazy(() => import("./components/platform/ScheduleEditor"));
const ModelEditorPanel = lazy(() => import("./components/ModelEditorPanel"));

function LazyFallback() {
  return <div className="loading" />;
}

let tabCounter = 1;

function AiStudioWorkspaceTabButton({
  active,
  onClick,
}: {
  active: boolean;
  onClick: () => void;
}) {
  const chat = useAgentChatOptional();
  const busy = chat?.isPending;
  const { t } = useTranslation("shell");
  return (
    <button type="button" className={active ? "active" : ""} onClick={onClick}>
      {t("admin.tab.aiStudio")}
      {busy && <span className="tab-pending-dot" title={t("admin.agentBusyTitle")} />}
    </button>
  );
}

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
  const { t } = useTranslation(["shell", "common", "explorer"]);
  const [session, setSession] = useState<AuthSession | null>(() => getStoredSession());
  const [authBootstrapping, setAuthBootstrapping] = useState(
    () => Boolean(getStoredSession()?.token)
  );
  const [appMode, setAppMode] = useAppMode(session);
  const queryClient = useQueryClient();
  const [workspaceTab, setWorkspaceTab] = useState<"explorer" | string>("explorer");
  const [editorTabs, setEditorTabs] = useState<EditorTab[]>([]);
  const [dashboardSessions, setDashboardSessions] = useState<Record<string, DashboardSession>>({});
  const [propertiesTabPath, setPropertiesTabPath] = useState<string | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedPath, setSelectedPath] = useState<string | null>(() =>
    resolveInitialAdminPath(window.location.search, readSelectedPath())
  );
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [visibleRowKeys, setVisibleRowKeys] = useState<string[]>([]);
  const selectionAnchorRef = useRef<string | null>(null);
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
    if (!getStoredSession()?.token) {
      setAuthBootstrapping(false);
      return;
    }
    let cancelled = false;
    void validateStoredSession().then((valid) => {
      if (cancelled) {
        return;
      }
      if (!valid) {
        setSession(null);
      }
      setAuthBootstrapping(false);
    });
    return () => {
      cancelled = true;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps -- validate stored token once on load

  useEffect(() => {
    const onSessionInvalid = () => setSession(null);
    window.addEventListener(SESSION_INVALID_EVENT, onSessionInvalid);
    return () => window.removeEventListener(SESSION_INVALID_EVENT, onSessionInvalid);
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
  const [createParentPath, setCreateParentPath] = useState<string | null>(null);
  const [createPresetType, setCreatePresetType] = useState<ObjectType | null>(null);
  const [treeFilter, setTreeFilter] = useState("");
  const isMobileLayout = useMobileLayout();
  const [mobileExplorerPane, setMobileExplorerPane] = useState<"tree" | "detail">("tree");
  const sidebarRef = useRef<HTMLElement | null>(null);
  const explorerMainRef = useRef<HTMLElement | null>(null);

  useObjectWebSocket();
  useFederatedPathSubscription(selectedPath);

  const info = useQuery({ queryKey: ["info"], queryFn: fetchPlatformInfo });
  const { tree: lazyTree, objects: objectList, loadChildren, invalidateAll, treeLoadError } =
    useLazyObjectTree(Boolean(session));

  useEffect(() => {
    if (!selectedPath || !objectList.length) {
      return;
    }
    if (objectList.some((obj) => obj.path === selectedPath)) {
      return;
    }
    // Lazy tree refresh can briefly drop rows; don't jump to root during that window.
    const timer = window.setTimeout(() => {
      if (!objectList.some((obj) => obj.path === selectedPath)) {
        setSelectedPath("root");
        writeSelectedPath("root");
        clearInvalidAdminPathFromUrl();
      }
    }, 2000);
    return () => window.clearTimeout(timer);
  }, [objectList, selectedPath]);

  const reorderMutation = useMutation({
    mutationFn: ({ parentPath, orderedPaths }: { parentPath: string; orderedPaths: string[] }) =>
      reorderObjectChildren(parentPath, orderedPaths),
    onSuccess: () => void invalidateAll(),
  });

  const handleTreeReorder = useCallback(
    (parentPath: string, orderedPaths: string[]) => {
      void loadChildren(parentPath, true).then(() => {
        reorderMutation.mutate({ parentPath, orderedPaths });
      });
    },
    [loadChildren, reorderMutation],
  );

  const tree = useMemo(() => {
    if (!treeFilter.trim()) {
      return lazyTree;
    }
    let list = objectList;
    const q = treeFilter.toLowerCase();
    const included = new Set<string>();
    const addAncestors = (path: string) => {
      let p: string | null = path;
      while (p) {
        included.add(p);
        const dot = p.lastIndexOf(".");
        p = dot === -1 ? null : p.slice(0, dot);
      }
    };
    for (const c of list) {
      if (c.path.toLowerCase().includes(q) || c.displayName.toLowerCase().includes(q)) {
        addAncestors(c.path);
        if (c.groupContextPath) {
          addAncestors(c.groupContextPath);
        }
        included.add(objectTreeKey(c));
      }
    }
    list = list.filter(
      (c) =>
        included.has(c.path)
        || included.has(objectTreeKey(c))
        || (c.groupContextPath != null && included.has(c.groupContextPath)),
    );
    return buildObjectTree(list);
  }, [objectList, lazyTree, treeFilter]);

  const selectPathInExplorer = (path: string) => {
    setSelectedPath(path);
    setSelectedKeys(new Set([path]));
    selectionAnchorRef.current = path;
    setWorkspaceTab("explorer");
    if (isMobileLayout) {
      setMobileExplorerPane("detail");
    }
  };

  const handleTreeRowSelect = useCallback(
    (row: TreeRowSelection, event: { metaKey: boolean; shiftKey: boolean }) => {
      setSelectedPath(row.path);
      writeSelectedPath(row.path);
      syncAdminPathToUrl(row.path);
      setWorkspaceTab("explorer");
      if (isMobileLayout) {
        setMobileExplorerPane("detail");
      }

      if (event.shiftKey && selectionAnchorRef.current) {
        const start = visibleRowKeys.indexOf(selectionAnchorRef.current);
        const end = visibleRowKeys.indexOf(row.key);
        if (start !== -1 && end !== -1) {
          const [from, to] = start < end ? [start, end] : [end, start];
          setSelectedKeys(new Set(visibleRowKeys.slice(from, to + 1)));
          return;
        }
      }

      if (event.metaKey) {
        setSelectedKeys((current) => {
          const next = new Set(current);
          if (next.has(row.key)) {
            next.delete(row.key);
          } else {
            next.add(row.key);
          }
          return next;
        });
        selectionAnchorRef.current = row.key;
        return;
      }

      setSelectedKeys(new Set([row.key]));
      selectionAnchorRef.current = row.key;
    },
    [isMobileLayout, visibleRowKeys],
  );

  const showObjectTreeSidebar =
    workspaceTab !== "system"
    && workspaceTab !== "ai-studio"
    && (!isMobileLayout || (workspaceTab === "explorer" && mobileExplorerPane === "tree"));

  const showExplorerMain =
    workspaceTab === "explorer"
    && (!isMobileLayout || mobileExplorerPane === "detail");

  const applyDashboardOpenOptions = (tabId: string, options?: OpenDashboardOptions) => {
    if (!options) {
      return;
    }
    setDashboardSessions((current) => ({
      ...current,
      [tabId]: mergeSession(current[tabId] ?? emptySession(), options),
    }));
  };

  const openEditor = (path: string, options?: OpenDashboardOptions) => {
    const ctx = objectList.find((c) => c.path === path);
    if (!isSpecializedEditorObject(path, ctx?.type, ctx?.templateId)) {
      setSelectedPath(path);
      setWorkspaceTab("explorer");
      if (isMobileLayout) {
        setMobileExplorerPane("detail");
      }
      return;
    }
    const existing = editorTabs.find((t) => t.path === path);
    if (existing) {
      if (existing.objectType === "DASHBOARD") {
        applyDashboardOpenOptions(existing.id, options);
      }
      setWorkspaceTab(existing.id);
      return;
    }
    const objectType = resolveEditorObjectType(path, ctx?.type, ctx?.templateId);
    const tab: EditorTab = {
      id: `editor-${tabCounter++}`,
      path,
      title: ctx?.displayName ?? path.split(".").pop() ?? path,
      objectType,
    };
    setEditorTabs((tabs) => [...tabs, tab]);
    if (objectType === "DASHBOARD") {
      applyDashboardOpenOptions(tab.id, options);
    }
    setWorkspaceTab(tab.id);
  };

  const closeEditor = (tabId: string) => {
    const tab = editorTabs.find((t) => t.id === tabId);
    setEditorTabs((tabs) => tabs.filter((t) => t.id !== tabId));
    setDashboardSessions((current) => {
      if (!(tabId in current)) {
        return current;
      }
      const next = { ...current };
      delete next[tabId];
      return next;
    });
    if (tab && propertiesTabPath === tab.path) {
      setPropertiesTabPath(null);
    }
    if (workspaceTab === tabId) {
      setWorkspaceTab("explorer");
    }
  };

  const activeEditor = editorTabs.find((t) => t.id === workspaceTab);
  const isSpecializedEditor = activeEditor != null && (
    activeEditor.objectType === "DASHBOARD"
    || activeEditor.objectType === "REPORT"
    || activeEditor.objectType === "WORKFLOW"
    || activeEditor.objectType === "MODEL"
    || activeEditor.objectType === "DATA_SOURCE"
    || activeEditor.objectType === "MIGRATION"
    || activeEditor.objectType === "BINDING"
    || activeEditor.objectType === "SCHEDULE"
    || isModelsPath(activeEditor.path)
  );
  const showPropertiesEditor =
    activeEditor != null
    && isSpecializedEditor
    && propertiesTabPath === activeEditor.path;

  useEffect(() => {
    if (!activeEditor || workspaceTab !== activeEditor.id) {
      return;
    }
    const ctx = objectList.find((c) => c.path === activeEditor.path);
    if (!isSpecializedEditorObject(activeEditor.path, ctx?.type, ctx?.templateId)) {
      setSelectedPath(activeEditor.path);
      setPropertiesTabPath(null);
      setWorkspaceTab("explorer");
      setEditorTabs((tabs) => tabs.filter((t) => t.id !== activeEditor.id));
    }
  }, [activeEditor, objectList, workspaceTab]);

  const parentForCreate = createParentPath ?? selectedPath ?? "root";
  const selectedObject = useMemo(
    () => objectList.find((obj) => obj.path === selectedPath) ?? null,
    [objectList, selectedPath]
  );
  const isAdmin = isAdminSession(session);
  const primaryRole = getPrimaryRole(session);

  const handleLogout = async () => {
    await logout();
    setSession(null);
    queryClient.clear();
  };

  const selectOperatorApp = (appId: string) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set("mode", "operator");
        next.set("app", appId);
        next.delete("screen");
        next.delete("dashboard");
        next.delete("report");
        return next;
      },
      { replace: true }
    );
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

  if (oidcBootstrapping || authBootstrapping) {
    return (
      <div className="login-shell">
        <div className="login-card">
          <p className="login-sub">{t("shell:login.oidcCompleting")}</p>
        </div>
      </div>
    );
  }

  if (!session?.token) {
    return <LoginView onLoggedIn={handleLoggedIn} />;
  }

  if (shouldOpenOperatorShell(session, appMode)) {
    const operatorAppId = resolveOperatorAppId(session, searchParams);
    return (
      <Suspense fallback={<LazyFallback />}>
        <OperatorView
          appId={operatorAppId}
          operatorId={session.username}
          onSelectApp={selectOperatorApp}
          onSwitchAdmin={isAdmin ? () => setAppMode("admin") : undefined}
          session={session}
          onLogout={() => void handleLogout()}
        />
      </Suspense>
    );
  }

  return (
    <AgentChatProvider enabled={isAdmin}>
    <div className="admin-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">ISPF</span>
          <div>
            <strong>{t("shell:admin.title")}</strong>
            <span className="brand-sub">
              {session.displayName} · {primaryRole ?? t("common:empty.dash")}
              {info.data?.version ? ` · v${info.data.version}` : ""}
              {info.data?.springBootVersion ? ` · Boot ${info.data.springBootVersion}` : ""}
              {info.data?.javaVersion ? ` · Java ${info.data.javaVersion}` : ""}
            </span>
          </div>
        </div>
        <div className="topbar-actions">
          <LocaleSwitcher />
          <button type="button" className="btn" onClick={() => void handleLogout()}>
            {t("common:action.logout")}
          </button>
        </div>
      </header>

      {isAdmin && <PlatformUpdateBanner />}

      <nav className="workspace-tabs">
        <button
          type="button"
          className={workspaceTab === "explorer" ? "active" : ""}
          onClick={() => {
            setWorkspaceTab("explorer");
            if (isMobileLayout) {
              setMobileExplorerPane("tree");
            }
          }}
        >
          {t("shell:admin.tab.explorer")}
        </button>
        {isAdmin && (
          <button
            type="button"
            className={workspaceTab === "system" ? "active" : ""}
            onClick={() => setWorkspaceTab("system")}
          >
            {t("shell:admin.tab.system")}
          </button>
        )}
        {isAdmin && (
          <AiStudioWorkspaceTabButton
            active={workspaceTab === "ai-studio"}
            onClick={() => setWorkspaceTab("ai-studio")}
          />
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

      <AgentChatStatusBar
        workspaceTab={workspaceTab}
        onOpenAiStudio={() => setWorkspaceTab("ai-studio")}
      />

      <div
        className={`workspace${isMobileLayout && workspaceTab === "explorer" ? ` workspace-mobile workspace-mobile-${mobileExplorerPane}` : ""}`}
      >
        {showObjectTreeSidebar && (
          <aside className="sidebar" ref={sidebarRef}>
            <div className="sidebar-head">
              <h3>{t("shell:admin.treeTitle")}</h3>
              <input
                type="search"
                placeholder={t("common:action.search")}
                value={treeFilter}
                onChange={(e) => setTreeFilter(e.target.value)}
              />
            </div>
            <div className="sidebar-body">
              {treeLoadError && <p className="sidebar-msg error">{treeLoadError}</p>}
              {!treeLoadError && objectList.length === 0 && (
                <p className="sidebar-msg">{t("shell:admin.treeLoading")}</p>
              )}
              {!treeLoadError && tree.length > 0 && (
                <ObjectTree
                  nodes={tree}
                  objects={objectList}
                  selectedPath={selectedPath}
                  selectedKeys={selectedKeys}
                  onRowSelect={handleTreeRowSelect}
                  onOpenEditor={openEditor}
                  canReorder={isAdmin && !treeFilter.trim()}
                  onReorder={handleTreeReorder}
                  onLoadChildren={(path) => void loadChildren(path)}
                  onVisibleRowKeysChange={setVisibleRowKeys}
                  bulkActions={
                    isAdmin
                      ? {
                          visibleRowKeys,
                          selectedKeys,
                          objects: objectList,
                          onSelectionChange: setSelectedKeys,
                          onDeleted: () => void invalidateAll(),
                          onMembersChanged: () => void invalidateAll(),
                          contextPath: selectedPath,
                          contextObjectType: selectedObject?.type,
                          onCreateChild: (parentPath) => {
                            setCreatePresetType(null);
                            setCreateParentPath(parentPath);
                            setShowCreate(true);
                          },
                          onCreateVisualGroup: (parentPath) => {
                            setCreatePresetType("VISUAL_GROUP");
                            setCreateParentPath(parentPath);
                            setShowCreate(true);
                          },
                        }
                      : undefined
                  }
                />
              )}
            </div>
          </aside>
        )}

        {showExplorerMain && (
          <main className="main explorer-main" ref={explorerMainRef}>
            <Suspense fallback={<LazyFallback />}>
              <ExplorerView
                selectedPath={selectedPath}
                selectedObject={selectedObject}
                onOpenEditor={openEditor}
                onDeleted={() => {
                  setSelectedPath("root");
                  if (isMobileLayout) {
                    setMobileExplorerPane("tree");
                  }
                }}
                onSelectPath={selectPathInExplorer}
                onMembersChanged={() => void invalidateAll()}
                allObjects={objectList}
                isAdmin={isAdmin}
                showBackToTree={isMobileLayout}
                onBackToTree={() => setMobileExplorerPane("tree")}
              />
            </Suspense>
          </main>
        )}

        {workspaceTab === "system" && isAdmin && (
          <Suspense fallback={<LazyFallback />}>
            <SystemView />
          </Suspense>
        )}

        {isAdmin && (
          <main
            className={`main ai-studio-main${workspaceTab === "ai-studio" ? "" : " ai-studio-main-dormant"}`}
          >
            <Suspense fallback={<LazyFallback />}>
              <AiStudioPanel />
            </Suspense>
          </main>
        )}

        {activeEditor && workspaceTab === activeEditor.id && !showPropertiesEditor && (
          <main className="main editor-main dashboard-main">
            <Suspense fallback={<LazyFallback />}>
              {activeEditor.objectType === "DASHBOARD" ? (
                <DashboardBuilder
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                  session={dashboardSessions[activeEditor.id]}
                  onSessionChange={(next) => {
                    setDashboardSessions((current) => ({
                      ...current,
                      [activeEditor.id]: next,
                    }));
                  }}
                  onNavigateDashboard={openEditor}
                />
              ) : activeEditor.objectType === "REPORT" ? (
                <ReportBuilder
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
              ) : activeEditor.objectType === "DATA_SOURCE" ? (
                <DataSourceEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "MIGRATION" ? (
                <MigrationEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "BINDING" ? (
                <SqlBindingEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : activeEditor.objectType === "SCHEDULE" ? (
                <ScheduleEditor
                  path={activeEditor.path}
                  onClose={() => closeEditor(activeEditor.id)}
                  onOpenProperties={() => setPropertiesTabPath(activeEditor.path)}
                />
              ) : null}
            </Suspense>
          </main>
        )}

        {activeEditor && workspaceTab === activeEditor.id && showPropertiesEditor && (
          <main className="main editor-main">
            <ObjectPropertiesEditor
              key={activeEditor.path}
              path={activeEditor.path}
              canManage={isAdmin}
              onClose={() => {
                setPropertiesTabPath(null);
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
          presetType={createPresetType ?? undefined}
          onClose={() => {
            setShowCreate(false);
            setCreateParentPath(null);
            setCreatePresetType(null);
          }}
          onCreated={(path) => {
            setShowCreate(false);
            setCreateParentPath(null);
            setCreatePresetType(null);
            void invalidateAll();
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
    </AgentChatProvider>
  );
}
